package com.proyectogrado.donacion_service.service;

import com.proyectogrado.donacion_service.dto.ArticuloResponse;
import com.proyectogrado.donacion_service.dto.DonacionResponse;
import com.proyectogrado.donacion_service.model.Donacion;
import com.proyectogrado.donacion_service.model.OrganizacionLookup;
import com.proyectogrado.donacion_service.model.UsuarioLookup;
import com.proyectogrado.donacion_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.donacion_service.repository.UsuarioLookupRepository;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Arma DonacionResponse con los datos del donante y de la organización.
 * En una lista, cada persona y organización se consulta una sola vez.
 */
@Component
public class DonacionMapper {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final UsuarioLookupRepository usuarioLookupRepository;
    private final OrganizacionLookupRepository organizacionLookupRepository;
    private final VinculosService vinculosService;

    public DonacionMapper(
            UsuarioLookupRepository usuarioLookupRepository,
            OrganizacionLookupRepository organizacionLookupRepository,
            VinculosService vinculosService
    ) {
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.organizacionLookupRepository = organizacionLookupRepository;
        this.vinculosService = vinculosService;
    }

    public DonacionResponse aRespuesta(Donacion donacion) {
        return aRespuestas(List.of(donacion)).get(0);
    }

    public List<DonacionResponse> aRespuestas(List<Donacion> donaciones) {
        Map<Integer, Optional<UsuarioLookup>> donantes = new HashMap<>();
        Map<Integer, Optional<OrganizacionLookup>> organizaciones = new HashMap<>();
        Map<Integer, Optional<UsuarioLookup>> cuentas = new HashMap<>();

        return donaciones.stream().map(donacion -> {
            UsuarioLookup donante = donantes.computeIfAbsent(donacion.getIdDonante(),
                    usuarioLookupRepository::findById).orElse(null);
            OrganizacionLookup organizacion = organizaciones.computeIfAbsent(donacion.getIdOrganizacion(),
                    organizacionLookupRepository::findById).orElse(null);
            UsuarioLookup cuenta = cuentas.computeIfAbsent(donacion.getIdOrganizacion(),
                    id -> Optional.ofNullable(vinculosService.cuentaOrganizacion(id))).orElse(null);

            return new DonacionResponse(
                    donacion.getIdDonacion(),
                    donacion.getEstado(),
                    donacion.getFechaEntrega(),
                    donacion.getHoraEntrega() != null ? donacion.getHoraEntrega().format(HORA) : null,
                    donacion.getObservaciones(),
                    donacion.getNotaOrganizacion(),
                    donacion.getFechaRegistro(),
                    donacion.getFechaRespuesta(),
                    donacion.getIdOrganizacion(),
                    organizacion != null ? organizacion.getNombre() : "Organización",
                    organizacion != null ? organizacion.getDireccion() : null,
                    cuenta != null ? cuenta.getFotoPerfil() : null,
                    donacion.getIdDonante(),
                    donante != null ? donante.getNombreUsuario() : null,
                    donante != null ? donante.getCelular() : null,
                    donante != null ? donante.getFotoPerfil() : null,
                    donacion.getRolDonante(),
                    donacion.getArticulos().stream()
                            .map(articulo -> new ArticuloResponse(
                                    articulo.getIdArticulo(),
                                    articulo.getCategoria(),
                                    articulo.getDescripcion(),
                                    articulo.getCantidad(),
                                    articulo.getUnidad(),
                                    articulo.getFechaVencimiento(),
                                    articulo.getCantidadRecibida()))
                            .toList()
            );
        }).toList();
    }
}
