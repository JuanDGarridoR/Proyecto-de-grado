package com.proyectogrado.donacion_service.controller;

import com.proyectogrado.donacion_service.dto.ArticuloRequest;
import com.proyectogrado.donacion_service.dto.NuevaDonacionRequest;
import com.proyectogrado.donacion_service.dto.OrganizacionDonableResponse;
import com.proyectogrado.donacion_service.model.Catalogo;
import com.proyectogrado.donacion_service.model.Donacion;
import com.proyectogrado.donacion_service.model.DonacionArticulo;
import com.proyectogrado.donacion_service.model.OrganizacionLookup;
import com.proyectogrado.donacion_service.model.UsuarioLookup;
import com.proyectogrado.donacion_service.repository.DonacionRepository;
import com.proyectogrado.donacion_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.donacion_service.service.DonacionMapper;
import com.proyectogrado.donacion_service.service.VinculosService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.proyectogrado.donacion_service.service.Validaciones.revisarCantidad;
import static com.proyectogrado.donacion_service.service.Validaciones.revisarTexto;
import static com.proyectogrado.donacion_service.service.Validaciones.textoOpcional;

/**
 * Lado del donante (acompañante o voluntario): ver a qué organizaciones
 * puede donar, programar una donación, ver las suyas y cancelar las que aún
 * están programadas.
 *
 * El id del usuario autenticado llega en el encabezado X-User-Id, que pone
 * el gateway después de validar el token.
 */
@RestController
@RequestMapping("/api/donaciones")
public class DonacionController {

    private static final int MAX_ARTICULOS = 20;
    private static final int MAX_OBSERVACIONES = 500;

    private final DonacionRepository donacionRepository;
    private final OrganizacionLookupRepository organizacionLookupRepository;
    private final VinculosService vinculosService;
    private final DonacionMapper donacionMapper;

    public DonacionController(
            DonacionRepository donacionRepository,
            OrganizacionLookupRepository organizacionLookupRepository,
            VinculosService vinculosService,
            DonacionMapper donacionMapper
    ) {
        this.donacionRepository = donacionRepository;
        this.organizacionLookupRepository = organizacionLookupRepository;
        this.vinculosService = vinculosService;
        this.donacionMapper = donacionMapper;
    }

    /** Organizaciones vinculadas a las que puede donar, por nombre. */
    @GetMapping("/organizaciones")
    public ResponseEntity<?> organizaciones(@RequestHeader("X-User-Id") Integer idUsuario) {
        String rol = vinculosService.rolDonante(idUsuario);
        if (rol == null) {
            return noPuedeDonar();
        }

        List<OrganizacionDonableResponse> respuesta = vinculosService.organizacionesVinculadas(idUsuario, rol)
                .stream()
                .map(idOrganizacion -> {
                    OrganizacionLookup organizacion =
                            organizacionLookupRepository.findById(idOrganizacion).orElse(null);
                    if (organizacion == null) {
                        return null;
                    }

                    UsuarioLookup cuenta = vinculosService.cuentaOrganizacion(idOrganizacion);
                    return new OrganizacionDonableResponse(
                            idOrganizacion,
                            organizacion.getNombre(),
                            organizacion.getDireccion(),
                            cuenta != null ? cuenta.getCelular() : null,
                            cuenta != null ? cuenta.getFotoPerfil() : null);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        OrganizacionDonableResponse::nombre,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return ResponseEntity.ok(respuesta);
    }

    /** Donaciones del usuario, las más recientes primero. */
    @GetMapping
    public ResponseEntity<?> misDonaciones(@RequestHeader("X-User-Id") Integer idUsuario) {
        if (vinculosService.rolDonante(idUsuario) == null) {
            return noPuedeDonar();
        }

        return ResponseEntity.ok(donacionMapper.aRespuestas(
                donacionRepository.findByIdDonanteOrderByFechaRegistroDesc(idUsuario)));
    }

    /** Programa una donación para una organización vinculada. */
    @PostMapping
    public ResponseEntity<?> programar(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @RequestBody NuevaDonacionRequest request
    ) {
        String rol = vinculosService.rolDonante(idUsuario);
        if (rol == null) {
            return noPuedeDonar();
        }

        if (request.idOrganizacion() == null) {
            return ResponseEntity.badRequest().body("Elige la organización a la que vas a donar");
        }

        if (!vinculosService.organizacionesVinculadas(idUsuario, rol).contains(request.idOrganizacion())) {
            return ResponseEntity.badRequest()
                    .body("Solo puedes donar a organizaciones con las que estás vinculado");
        }

        String error = revisarFechaYHora(request.fechaEntrega(), request.horaEntrega());
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        String observaciones = textoOpcional(request.observaciones());
        if (observaciones != null && observaciones.length() > MAX_OBSERVACIONES) {
            return ResponseEntity.badRequest()
                    .body("Las observaciones pueden tener máximo " + MAX_OBSERVACIONES + " caracteres");
        }

        List<ArticuloRequest> articulos = request.articulos();
        if (articulos == null || articulos.isEmpty()) {
            return ResponseEntity.badRequest().body("Agrega al menos una cosa para donar");
        }

        if (articulos.size() > MAX_ARTICULOS) {
            return ResponseEntity.badRequest()
                    .body("Una donación puede tener máximo " + MAX_ARTICULOS + " artículos");
        }

        Donacion donacion = new Donacion(idUsuario, rol, request.idOrganizacion(), request.fechaEntrega());
        donacion.setObservaciones(observaciones);
        if (request.horaEntrega() != null && !request.horaEntrega().isBlank()) {
            donacion.setHoraEntrega(LocalTime.parse(request.horaEntrega().trim()));
        }

        for (ArticuloRequest articulo : articulos) {
            String errorArticulo = revisarArticulo(articulo);
            if (errorArticulo != null) {
                return ResponseEntity.badRequest().body(errorArticulo);
            }

            boolean esDinero = Catalogo.DINERO.equals(articulo.categoria());
            String descripcion = textoOpcional(articulo.descripcion());

            donacion.agregarArticulo(new DonacionArticulo(
                    articulo.categoria(),
                    esDinero && descripcion == null ? "Dinero" : descripcion,
                    articulo.cantidad(),
                    esDinero ? Catalogo.UNIDAD_DINERO : textoOpcional(articulo.unidad()),
                    esDinero ? null : articulo.fechaVencimiento()));
        }

        Donacion guardada = donacionRepository.save(donacion);

        return ResponseEntity.status(HttpStatus.CREATED).body(donacionMapper.aRespuesta(guardada));
    }

    /** Cancela una donación propia que todavía no se ha llevado. */
    @PutMapping("/{idDonacion}/cancelar")
    public ResponseEntity<?> cancelar(
            @RequestHeader("X-User-Id") Integer idUsuario,
            @PathVariable Integer idDonacion
    ) {
        Donacion donacion = donacionRepository.findById(idDonacion)
                .filter(d -> d.getIdDonante().equals(idUsuario))
                .orElse(null);

        if (donacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No se encontró la donación");
        }

        if (!donacion.estaProgramada()) {
            return ResponseEntity.badRequest().body("Solo puedes cancelar una donación programada");
        }

        donacion.responder(Donacion.CANCELADA);

        return ResponseEntity.ok(donacionMapper.aRespuesta(donacionRepository.save(donacion)));
    }

    /** La fecha no puede ser pasada ni a más de un año; la hora, si viene, debe ser HH:mm. */
    private static String revisarFechaYHora(LocalDate fechaEntrega, String horaEntrega) {
        if (fechaEntrega == null) {
            return "Indica la fecha en que llevarás la donación";
        }

        LocalDate hoy = Catalogo.hoy();
        if (fechaEntrega.isBefore(hoy)) {
            return "La fecha de entrega no puede ser anterior a hoy";
        }

        if (fechaEntrega.isAfter(hoy.plusYears(1))) {
            return "La fecha de entrega debe estar dentro del próximo año";
        }

        if (horaEntrega != null && !horaEntrega.isBlank()) {
            try {
                LocalTime.parse(horaEntrega.trim());
            } catch (DateTimeParseException e) {
                return "La hora de entrega no es válida";
            }
        }

        return null;
    }

    /** Categoría válida, cantidad positiva y, salvo el dinero, descripción y unidad. */
    private static String revisarArticulo(ArticuloRequest articulo) {
        if (articulo == null || !Catalogo.CATEGORIAS.contains(articulo.categoria())) {
            return "Elige una categoría válida para cada artículo";
        }

        boolean esDinero = Catalogo.DINERO.equals(articulo.categoria());
        String descripcion = textoOpcional(articulo.descripcion());
        String que = descripcion != null ? descripcion : (esDinero ? "dinero" : "cada artículo");

        String error = revisarCantidad(articulo.cantidad(), false, que);
        if (error != null) {
            return error;
        }

        if (esDinero) {
            return descripcion != null && descripcion.length() > 120
                    ? "La descripción puede tener máximo 120 caracteres"
                    : null;
        }

        error = revisarTexto(descripcion, 120, "Lo que vas a donar");
        if (error != null) {
            return error;
        }

        error = revisarTexto(textoOpcional(articulo.unidad()), 30, "La unidad de " + descripcion);
        if (error != null) {
            return error;
        }

        if (articulo.fechaVencimiento() != null && articulo.fechaVencimiento().isBefore(Catalogo.hoy())) {
            return descripcion + " ya está vencido: no se puede donar";
        }

        return null;
    }

    private static ResponseEntity<String> noPuedeDonar() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Las donaciones son para acompañantes y voluntarios");
    }
}
