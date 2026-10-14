package com.proyectogrado.donacion_service.controller;

import com.proyectogrado.donacion_service.dto.InventarioArticuloRequest;
import com.proyectogrado.donacion_service.dto.InventarioArticuloResponse;
import com.proyectogrado.donacion_service.dto.MovimientoRequest;
import com.proyectogrado.donacion_service.dto.MovimientoResponse;
import com.proyectogrado.donacion_service.model.Catalogo;
import com.proyectogrado.donacion_service.model.InventarioArticulo;
import com.proyectogrado.donacion_service.model.UsuarioLookup;
import com.proyectogrado.donacion_service.repository.InventarioArticuloRepository;
import com.proyectogrado.donacion_service.repository.InventarioMovimientoRepository;
import com.proyectogrado.donacion_service.repository.UsuarioLookupRepository;
import com.proyectogrado.donacion_service.service.InventarioService;
import com.proyectogrado.donacion_service.service.VinculosService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.proyectogrado.donacion_service.service.Validaciones.formatear;
import static com.proyectogrado.donacion_service.service.Validaciones.revisarCantidad;
import static com.proyectogrado.donacion_service.service.Validaciones.revisarTexto;
import static com.proyectogrado.donacion_service.service.Validaciones.textoOpcional;

/**
 * Inventario de la organización: sus artículos con la existencia actual,
 * crearlos, editarlos y eliminarlos, y registrar entradas y salidas. Las
 * donaciones recibidas entran solas (ver OrganizacionDonacionController).
 *
 * La cantidad nunca se edita directamente: cambia con movimientos, para que
 * siempre se sepa por qué.
 */
@RestController
@RequestMapping("/api/organizacion/inventario")
public class InventarioController {

    private static final int MAX_DETALLE = 300;

    private final InventarioArticuloRepository articuloRepository;
    private final InventarioMovimientoRepository movimientoRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final VinculosService vinculosService;
    private final InventarioService inventarioService;

    public InventarioController(
            InventarioArticuloRepository articuloRepository,
            InventarioMovimientoRepository movimientoRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            VinculosService vinculosService,
            InventarioService inventarioService
    ) {
        this.articuloRepository = articuloRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.vinculosService = vinculosService;
        this.inventarioService = inventarioService;
    }

    /** Artículos de la organización, por categoría y nombre. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestHeader("X-User-Id") Integer idUsuario) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        return ResponseEntity.ok(articuloRepository
                .findByIdOrganizacionOrderByCategoriaAscNombreAsc(idOrganizacion)
                .stream()
                .map(InventarioController::aRespuesta)
                .toList());
    }

    /** Crea un artículo. Si trae cantidad, queda como su primera entrada. */
    @Transactional
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @RequestBody InventarioArticuloRequest request
    ) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        String error = revisarDatos(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        BigDecimal cantidad = request.cantidad() != null ? request.cantidad() : BigDecimal.ZERO;
        error = revisarCantidad(cantidad, true, textoOpcional(request.nombre()));
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        String motivo = request.motivoEntrada() != null ? request.motivoEntrada() : "OTRO";
        if (!Catalogo.MOTIVOS_ENTRADA.contains(motivo)) {
            return ResponseEntity.badRequest().body("El motivo de la entrada no es válido");
        }

        String nombre = textoOpcional(request.nombre());
        String unidad = unidadDe(request);

        if (buscarIgual(idOrganizacion, request.categoria(), nombre, unidad).isPresent()) {
            return ResponseEntity.badRequest().body(yaExiste(nombre, unidad));
        }

        InventarioArticulo articulo = new InventarioArticulo(idOrganizacion, nombre, request.categoria(), unidad);
        articulo.setCantidadMinima(request.cantidadMinima());
        articulo.setFechaVencimiento(esDinero(request) ? null : request.fechaVencimiento());
        articulo = articuloRepository.save(articulo);

        if (cantidad.signum() > 0) {
            articulo = inventarioService.entrada(
                    articulo, cantidad, null, motivo, "Existencia inicial", null, idUsuario);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(aRespuesta(articulo));
    }

    /** Edita nombre, categoría, unidad, mínimo y vencimiento. La cantidad no cambia aquí. */
    @PutMapping("/{idInventario}")
    public ResponseEntity<?> editar(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idInventario,
            @RequestBody InventarioArticuloRequest request
    ) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        InventarioArticulo articulo = articuloDeLaOrganizacion(idInventario, idOrganizacion);
        if (articulo == null) {
            return noEncontrado();
        }

        String error = revisarDatos(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        String nombre = textoOpcional(request.nombre());
        String unidad = unidadDe(request);

        Optional<InventarioArticulo> igual = buscarIgual(idOrganizacion, request.categoria(), nombre, unidad);
        if (igual.isPresent() && !igual.get().getIdInventario().equals(idInventario)) {
            return ResponseEntity.badRequest().body(yaExiste(nombre, unidad));
        }

        articulo.setNombre(nombre);
        articulo.setCategoria(request.categoria());
        articulo.setUnidad(unidad);
        articulo.setCantidadMinima(request.cantidadMinima());
        articulo.setFechaVencimiento(esDinero(request) ? null : request.fechaVencimiento());
        articulo.marcarActualizado();

        return ResponseEntity.ok(aRespuesta(articuloRepository.save(articulo)));
    }

    /** Elimina el artículo y su historial. */
    @Transactional
    @DeleteMapping("/{idInventario}")
    public ResponseEntity<?> eliminar(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idInventario
    ) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        InventarioArticulo articulo = articuloDeLaOrganizacion(idInventario, idOrganizacion);
        if (articulo == null) {
            return noEncontrado();
        }

        movimientoRepository.deleteByIdInventario(idInventario);
        articuloRepository.delete(articulo);

        return ResponseEntity.ok("Artículo eliminado del inventario");
    }

    /** Historial de entradas y salidas del artículo (los 100 más recientes). */
    @GetMapping("/{idInventario}/movimientos")
    public ResponseEntity<?> movimientos(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idInventario
    ) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        if (articuloDeLaOrganizacion(idInventario, idOrganizacion) == null) {
            return noEncontrado();
        }

        Map<Integer, String> nombres = new HashMap<>();

        return ResponseEntity.ok(movimientoRepository.findTop100ByIdInventarioOrderByFechaDesc(idInventario)
                .stream()
                .map(movimiento -> new MovimientoResponse(
                        movimiento.getIdMovimiento(),
                        movimiento.getTipo(),
                        movimiento.getCantidad(),
                        movimiento.getMotivo(),
                        movimiento.getDetalle(),
                        movimiento.getIdDonacion(),
                        nombres.computeIfAbsent(movimiento.getIdUsuario(), id -> usuarioLookupRepository
                                .findById(id)
                                .map(UsuarioLookup::getNombreUsuario)
                                .orElse(null)),
                        movimiento.getFecha()))
                .toList());
    }

    /** Registra una entrada o salida manual. Una salida no puede dejar la cantidad negativa. */
    @Transactional
    @PostMapping("/{idInventario}/movimientos")
    public ResponseEntity<?> registrarMovimiento(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idInventario,
            @RequestBody MovimientoRequest request
    ) {
        Integer idOrganizacion = vinculosService.idOrganizacionDeCuenta(idUsuario);
        if (idOrganizacion == null) {
            return sinOrganizacion();
        }

        InventarioArticulo articulo = articuloDeLaOrganizacion(idInventario, idOrganizacion);
        if (articulo == null) {
            return noEncontrado();
        }

        boolean esEntrada = Catalogo.ENTRADA.equals(request.tipo());
        if (!esEntrada && !Catalogo.SALIDA.equals(request.tipo())) {
            return ResponseEntity.badRequest().body("Indica si es una entrada o una salida");
        }

        String error = revisarCantidad(request.cantidad(), false, articulo.getNombre());
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        if (!(esEntrada ? Catalogo.MOTIVOS_ENTRADA : Catalogo.MOTIVOS_SALIDA).contains(request.motivo())) {
            return ResponseEntity.badRequest().body("Elige un motivo válido");
        }

        String detalle = textoOpcional(request.detalle());
        if (detalle != null && detalle.length() > MAX_DETALLE) {
            return ResponseEntity.badRequest()
                    .body("El detalle puede tener máximo " + MAX_DETALLE + " caracteres");
        }

        if (!esEntrada && request.cantidad().compareTo(articulo.getCantidad()) > 0) {
            return ResponseEntity.badRequest().body("Solo hay " + formatear(articulo.getCantidad()) + " "
                    + articulo.getUnidad() + " de " + articulo.getNombre() + " en el inventario");
        }

        InventarioArticulo actualizado = esEntrada
                ? inventarioService.entrada(articulo, request.cantidad(), request.fechaVencimiento(),
                        request.motivo(), detalle, null, idUsuario)
                : inventarioService.salida(articulo, request.cantidad(), request.motivo(), detalle, idUsuario);

        return ResponseEntity.ok(aRespuesta(actualizado));
    }

    /** Nombre, categoría, unidad y cantidad mínima válidos. */
    private static String revisarDatos(InventarioArticuloRequest request) {
        if (request == null || !Catalogo.CATEGORIAS.contains(request.categoria())) {
            return "Elige una categoría válida";
        }

        String error = revisarTexto(textoOpcional(request.nombre()), 120, "El nombre");
        if (error != null) {
            return error;
        }

        if (!esDinero(request)) {
            error = revisarTexto(textoOpcional(request.unidad()), 30, "La unidad");
            if (error != null) {
                return error;
            }
        }

        if (request.cantidadMinima() != null) {
            return revisarCantidad(request.cantidadMinima(), true, "aviso de mínimo");
        }

        return null;
    }

    private static boolean esDinero(InventarioArticuloRequest request) {
        return Catalogo.DINERO.equals(request.categoria());
    }

    /** El dinero siempre va en COP. */
    private static String unidadDe(InventarioArticuloRequest request) {
        return esDinero(request) ? Catalogo.UNIDAD_DINERO : textoOpcional(request.unidad());
    }

    private Optional<InventarioArticulo> buscarIgual(Integer idOrganizacion, String categoria, String nombre,
                                                     String unidad) {
        return articuloRepository.findFirstByIdOrganizacionAndCategoriaAndNombreIgnoreCaseAndUnidadIgnoreCase(
                idOrganizacion, categoria, nombre, unidad);
    }

    private static String yaExiste(String nombre, String unidad) {
        return "Ya tienes " + nombre + " (" + unidad + ") en el inventario: registra una entrada en ese artículo";
    }

    private InventarioArticulo articuloDeLaOrganizacion(Integer idInventario, Integer idOrganizacion) {
        return articuloRepository.findById(idInventario)
                .filter(articulo -> articulo.getIdOrganizacion().equals(idOrganizacion))
                .orElse(null);
    }

    private static InventarioArticuloResponse aRespuesta(InventarioArticulo articulo) {
        return new InventarioArticuloResponse(
                articulo.getIdInventario(),
                articulo.getNombre(),
                articulo.getCategoria(),
                articulo.getUnidad(),
                articulo.getCantidad(),
                articulo.getCantidadMinima(),
                articulo.getFechaVencimiento(),
                articulo.getFechaActualizacion());
    }

    private static ResponseEntity<String> sinOrganizacion() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("El usuario no tiene una organización asociada");
    }

    private static ResponseEntity<String> noEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontró el artículo");
    }
}
