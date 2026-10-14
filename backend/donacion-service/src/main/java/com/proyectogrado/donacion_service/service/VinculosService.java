package com.proyectogrado.donacion_service.service;

import com.proyectogrado.donacion_service.model.UsuarioLookup;
import com.proyectogrado.donacion_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.donacion_service.repository.UsuarioLookupRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Quién puede donar y a qué organizaciones:
 * - El voluntario, a las organizaciones donde está vinculado.
 * - El acompañante, a las organizaciones de las personas mayores que acompaña.
 * En los dos casos los vínculos tienen que estar ACEPTADA y la organización
 * activa.
 */
@Service
public class VinculosService {

    public static final String VOLUNTARIO = "VOLUNTARIO";
    public static final String ACOMPANANTE = "ACOMPANANTE";

    private final UsuarioLookupRepository usuarioLookupRepository;
    private final OrganizacionLookupRepository organizacionLookupRepository;

    public VinculosService(
            UsuarioLookupRepository usuarioLookupRepository,
            OrganizacionLookupRepository organizacionLookupRepository
    ) {
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.organizacionLookupRepository = organizacionLookupRepository;
    }

    /** VOLUNTARIO o ACOMPANANTE, o null si el usuario no puede donar. */
    public String rolDonante(Integer idUsuario) {
        if (usuarioLookupRepository.tieneRol(idUsuario, VOLUNTARIO)) {
            return VOLUNTARIO;
        }

        if (usuarioLookupRepository.tieneRol(idUsuario, ACOMPANANTE)) {
            return ACOMPANANTE;
        }

        return null;
    }

    /** Ids de las organizaciones activas a las que el usuario puede donar. */
    public List<Integer> organizacionesVinculadas(Integer idUsuario, String rol) {
        List<Integer> ids = VOLUNTARIO.equals(rol)
                ? organizacionLookupRepository.idsVinculadasAVoluntario(idUsuario)
                : organizacionLookupRepository.idsVinculadasAAcompanante(idUsuario);

        return ids.stream()
                .distinct()
                .filter(idOrganizacion -> cuentaOrganizacion(idOrganizacion) != null)
                .toList();
    }

    /**
     * Cuenta activa de la organización, de donde salen su celular y su foto.
     * null si no tiene ninguna activa (la organización está inactiva).
     */
    public UsuarioLookup cuentaOrganizacion(Integer idOrganizacion) {
        return usuarioLookupRepository.findCuentasOrganizacion(idOrganizacion)
                .stream()
                .filter(UsuarioLookup::estaActivo)
                .findFirst()
                .orElse(null);
    }

    /**
     * Organización de la cuenta autenticada, o null si no es una cuenta de
     * organización. El X-User-Id es el id de la cuenta, no el id_organizacion.
     */
    public Integer idOrganizacionDeCuenta(Integer idUsuario) {
        return usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getIdOrganizacion)
                .orElse(null);
    }
}
