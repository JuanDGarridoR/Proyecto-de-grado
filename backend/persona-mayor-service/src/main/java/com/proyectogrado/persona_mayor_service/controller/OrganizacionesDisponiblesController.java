package com.proyectogrado.persona_mayor_service.controller;

import com.proyectogrado.persona_mayor_service.dto.OrganizacionDisponibleResponse;
import com.proyectogrado.persona_mayor_service.model.UsuarioLookup;
import com.proyectogrado.persona_mayor_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.persona_mayor_service.repository.PersonaMayorOrganizacionRepository;
import com.proyectogrado.persona_mayor_service.repository.UsuarioLookupRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Todas las organizaciones a las que la persona mayor puede pedir unirse:
 * las que no tienen con ella un vínculo aceptado ni una solicitud pendiente
 * (enviada por cualquiera de las dos). Las que la rechazaron sí aparecen,
 * porque puede volver a solicitar. Se muestran en "Ver todas las
 * organizaciones", debajo de las recomendadas.
 */
@RestController
public class OrganizacionesDisponiblesController {

    private final OrganizacionLookupRepository organizacionLookupRepository;
    private final PersonaMayorOrganizacionRepository relacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;

    public OrganizacionesDisponiblesController(
            OrganizacionLookupRepository organizacionLookupRepository,
            PersonaMayorOrganizacionRepository relacionRepository,
            UsuarioLookupRepository usuarioLookupRepository
    ) {
        this.organizacionLookupRepository = organizacionLookupRepository;
        this.relacionRepository = relacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
    }

    /** Ordenadas por nombre. El contacto sale de la primera cuenta de cada organización. */
    @GetMapping("/api/persona-mayor/organizaciones/disponibles")
    public List<OrganizacionDisponibleResponse> disponibles(@RequestHeader("X-User-Id") Integer idPersonaMayor) {
        Set<Integer> ocupadas = new HashSet<>();
        for (String estado : List.of("ACEPTADA", "PENDIENTE")) {
            relacionRepository.findById_IdPersonaMayorAndEstado(idPersonaMayor, estado)
                    .forEach(relacion -> ocupadas.add(relacion.getId().getIdOrganizacion()));
        }

        return organizacionLookupRepository.findAllByOrderByNombreAsc()
                .stream()
                .filter(organizacion -> !ocupadas.contains(organizacion.getIdOrganizacion()))
                .map(organizacion -> {
                    List<UsuarioLookup> cuentas =
                            usuarioLookupRepository.findByIdOrganizacion(organizacion.getIdOrganizacion());
                    UsuarioLookup cuenta = cuentas.isEmpty() ? null : cuentas.get(0);

                    return new OrganizacionDisponibleResponse(
                            organizacion.getIdOrganizacion(),
                            organizacion.getNombre(),
                            organizacion.getDireccion(),
                            cuenta != null ? cuenta.getCelular() : null,
                            cuenta != null ? cuenta.getCorreo() : null
                    );
                })
                .toList();
    }
}
