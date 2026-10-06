package com.proyectogrado.analitica_service.recomendacion;

import java.util.List;

/** Respuestas de la recomendación de organizaciones a una persona mayor. */
public final class RecomendacionDtos {

    private RecomendacionDtos() {
    }

    /**
     * Una organización recomendada. puntaje va de 0 a 100. distanciaKm es
     * null si no se pudo ubicar la dirección de la persona o de la
     * organización. barrio: el que se reconoció en su dirección (o null).
     */
    public record OrganizacionRecomendada(
            Integer idOrganizacion,
            String nombre,
            String direccion,
            String barrio,
            Double distanciaKm,
            String celular,
            String correo,
            int puntaje,
            List<String> razones,
            List<String> gustosCoincidentes,
            List<String> actividadesCoincidentes,
            int personasAfines
    ) {
    }

    /**
     * conGustos: si la persona registró intereses (sin ellos solo cuenta la
     * cercanía). direccionReconocida: si se pudo ubicar su dirección (sin
     * ella no se calculan distancias).
     */
    public record RecomendacionesResponse(
            boolean conGustos,
            boolean direccionReconocida,
            List<OrganizacionRecomendada> recomendaciones
    ) {
    }
}
