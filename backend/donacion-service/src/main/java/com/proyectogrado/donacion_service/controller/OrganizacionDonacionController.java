package com.proyectogrado.donacion_service.controller;

import com.proyectogrado.donacion_service.dto.NotaRequest;
import com.proyectogrado.donacion_service.dto.RecibirDonacionRequest;
import com.proyectogrado.donacion_service.model.Donacion;
import com.proyectogrado.donacion_service.model.DonacionArticulo;
import com.proyectogrado.donacion_service.model.UsuarioLookup;
import com.proyectogrado.donacion_service.repository.DonacionRepository;
import com.proyectogrado.donacion_service.repository.UsuarioLookupRepository;
import com.proyectogrado.donacion_service.service.DonacionMapper;
import com.proyectogrado.donacion_service.service.InventarioService;
import com.proyectogrado.donacion_service.service.VinculosService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.proyectogrado.donacion_service.service.Validaciones.revisarCantidad;
import static com.proyectogrado.donacion_service.service.Validaciones.textoOpcional;

/**
 * Lado de la organización: ver las donaciones que le programaron y, cuando
 * llegan, marcarlas recibidas (sus artículos entran al inventario) o no
 * entregadas.
 *
 * El X-User-Id es el de la cuenta que inició sesión, no el id_organizacion;
 * por eso cada endpoint empieza por idOrganizacionDeCuenta().
 */
@RestController
@RequestMapping("/api/organizacion/donaciones")
public class OrganizacionDonacionController {

    private static final int MAX_NOTA = 300;

    private final DonacionRepository donacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final VinculosService vinculosService;
    private final InventarioService inventarioService;
    private final DonacionMapper donacionMapper;

    public OrganizacionDonacionController(
            DonacionRepository donacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            VinculosService vinculosService,
            InventarioService inventarioService,
            DonacionMapper donacionMapper
    ) {
        this.donacionRepository = donacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.vinculosService = vinculosService;
        this.inventarioService = inventarioService;
        this.donacionMapper = donacionMapper;
    }

    /** Todas las donaciones hacia la organización, por fecha de entrega. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestHeader("X-User-Id") Integer idUsuario) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        return ResponseEntity.ok(donacionMapper.aRespuestas(
                donacionRepository.findByIdOrganizacionOrderByFechaEntregaAsc(idOrganizacion)));
    }

    /**
     * Marca la donación como recibida y suma al inventario lo que llegó. Si
     * no se indica la cantidad recibida de un artículo, se toma la anunciada.
     * Todo pasa en una transacción: si algo falla, ni la donación ni el
     * inventario cambian.
     */
    @Transactional
    @PutMapping("/{idDonacion}/recibir")
    public ResponseEntity<?> recibir(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idDonacion,
            @RequestBody(required = false) RecibirDonacionRequest request
    ) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        Donacion donacion = donacionDeLaOrganizacion(idDonacion, idOrganizacion);
        if (donacion == null) {
            return noEncontrada();
        }

        if (!donacion.estaProgramada()) {
            return ResponseEntity.badRequest().body("Esta donación ya fue procesada");
        }

        Map<Integer, BigDecimal> recibidas = new HashMap<>();
        if (request != null && request.articulos() != null) {
            for (RecibirDonacionRequest.CantidadRecibida recibida : request.articulos()) {
                if (recibida != null && recibida.idArticulo() != null && recibida.cantidad() != null) {
                    recibidas.put(recibida.idArticulo(), recibida.cantidad());
                }
            }
        }

        // Primero se revisa todo sin tocar la donación: la entidad está
        // dentro de la transacción y cualquier cambio se guardaría aunque se
        // responda con error.
        boolean llegoAlgo = false;
        for (DonacionArticulo articulo : donacion.getArticulos()) {
            BigDecimal cantidad = recibidas.getOrDefault(articulo.getIdArticulo(), articulo.getCantidad());

            String error = revisarCantidad(cantidad, true, articulo.getDescripcion());
            if (error != null) {
                return ResponseEntity.badRequest().body(error);
            }

            llegoAlgo |= cantidad.signum() > 0;
        }

        if (!llegoAlgo) {
            return ResponseEntity.badRequest()
                    .body("Si no llegó nada, marca la donación como no entregada");
        }

        for (DonacionArticulo articulo : donacion.getArticulos()) {
            articulo.setCantidadRecibida(
                    recibidas.getOrDefault(articulo.getIdArticulo(), articulo.getCantidad()));
        }

        String detalle = "Donación de " + usuarioLookupRepository.findById(donacion.getIdDonante())
                .map(UsuarioLookup::getNombreUsuario)
                .orElse("un donante");

        for (DonacionArticulo articulo : donacion.getArticulos()) {
            if (articulo.getCantidadRecibida().signum() > 0) {
                inventarioService.entradaPorDonacion(
                        idOrganizacion,
                        articulo.getDescripcion(),
                        articulo.getCategoria(),
                        articulo.getUnidad(),
                        articulo.getCantidadRecibida(),
                        articulo.getFechaVencimiento(),
                        detalle,
                        donacion.getIdDonacion(),
                        idUsuario);
            }
        }

        donacion.responder(Donacion.RECIBIDA);

        return ResponseEntity.ok(donacionMapper.aRespuesta(donacionRepository.save(donacion)));
    }

    /** El donante no la llevó. La nota opcional la ve el donante. */
    @PutMapping("/{idDonacion}/no-entregada")
    public ResponseEntity<?> noEntregada(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idDonacion,
            @RequestBody(required = false) NotaRequest request
    ) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        Donacion donacion = donacionDeLaOrganizacion(idDonacion, idOrganizacion);
        if (donacion == null) {
            return noEncontrada();
        }

        if (!donacion.estaProgramada()) {
            return ResponseEntity.badRequest().body("Esta donación ya fue procesada");
        }

        String nota = request != null ? textoOpcional(request.nota()) : null;
        if (nota != null && nota.length() > MAX_NOTA) {
            return ResponseEntity.badRequest().body("La nota puede tener máximo " + MAX_NOTA + " caracteres");
        }

        donacion.setNotaOrganizacion(nota);
        donacion.responder(Donacion.NO_ENTREGADA);

        return ResponseEntity.ok(donacionMapper.aRespuesta(donacionRepository.save(donacion)));
    }

    private Donacion donacionDeLaOrganizacion(Integer idDonacion, Integer idOrganizacion) {
        return donacionRepository.findById(idDonacion)
                .filter(donacion -> donacion.getIdOrganizacion().equals(idOrganizacion))
                .orElse(null);
    }

    private static ResponseEntity<String> sinOrganizacion() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("El usuario no tiene una organización asociada");
    }

    private static ResponseEntity<String> noEncontrada() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontró la donación");
    }
}
