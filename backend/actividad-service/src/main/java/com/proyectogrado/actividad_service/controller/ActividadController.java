package com.proyectogrado.actividad_service.controller;

import com.proyectogrado.actividad_service.config.ZonaHoraria;
import com.proyectogrado.actividad_service.dto.ActividadDisponibleResponse;
import com.proyectogrado.actividad_service.dto.ActividadRequest;
import com.proyectogrado.actividad_service.dto.ActividadResponse;
import com.proyectogrado.actividad_service.dto.AsistenciaRequest;
import com.proyectogrado.actividad_service.dto.ParticipanteActividadResponse;
import com.proyectogrado.actividad_service.dto.PropuestaActividadRequest;
import com.proyectogrado.actividad_service.dto.PropuestaActividadResponse;

import com.proyectogrado.actividad_service.model.Actividad;
import com.proyectogrado.actividad_service.model.OrganizacionLookup;
import com.proyectogrado.actividad_service.model.Participacion;
import com.proyectogrado.actividad_service.model.PersonaMayorAcompananteLookup;
import com.proyectogrado.actividad_service.model.UsuarioLookup;

import com.proyectogrado.actividad_service.repository.AcompananteLookupRepository;
import com.proyectogrado.actividad_service.repository.ActividadRepository;
import com.proyectogrado.actividad_service.repository.OrganizacionLookupRepository;
import com.proyectogrado.actividad_service.repository.ParticipacionRepository;
import com.proyectogrado.actividad_service.repository.PersonaMayorAcompananteLookupRepository;
import com.proyectogrado.actividad_service.repository.PersonaMayorLookupRepository;
import com.proyectogrado.actividad_service.repository.PersonaMayorOrganizacionLookupRepository;
import com.proyectogrado.actividad_service.repository.UsuarioLookupRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Actividades de las organizaciones: la organización las crea y registra la
 * asistencia, la persona mayor se inscribe y el acompañante consulta las de
 * las personas que acompaña y puede inscribirlas. Un voluntario o una persona mayor pueden
 * proponer actividades a sus organizaciones; mientras la organización no
 * las acepte, nadie más las ve.
 *
 * El id del usuario autenticado llega en el encabezado X-User-Id, que pone
 * el gateway después de validar el token. Este servicio no recibe el rol:
 * lo deduce consultando (solo lectura) las tablas usuario, persona_mayor y
 * acompanante.
 */
@RestController
@RequestMapping("/api/actividades")
public class ActividadController {

    /** Lo más espaciada que puede repetirse una actividad periódica (un año). */
    static final int MAX_FRECUENCIA_DIAS = 365;

    /** Lo que se supone que dura una actividad vieja, sin hora de fin, para ver si otra se le cruza. */
    static final int MINUTOS_SIN_HORA_FIN = 60;

    private final ActividadRepository actividadRepository;
    private final ParticipacionRepository participacionRepository;
    private final UsuarioLookupRepository usuarioLookupRepository;
    private final PersonaMayorLookupRepository personaMayorLookupRepository;
    private final AcompananteLookupRepository acompananteLookupRepository;
    private final PersonaMayorOrganizacionLookupRepository personaMayorOrganizacionRepository;
    private final PersonaMayorAcompananteLookupRepository personaMayorAcompananteRepository;
    private final OrganizacionLookupRepository organizacionLookupRepository;

    public ActividadController(
            ActividadRepository actividadRepository,
            ParticipacionRepository participacionRepository,
            UsuarioLookupRepository usuarioLookupRepository,
            PersonaMayorLookupRepository personaMayorLookupRepository,
            AcompananteLookupRepository acompananteLookupRepository,
            PersonaMayorOrganizacionLookupRepository personaMayorOrganizacionRepository,
            PersonaMayorAcompananteLookupRepository personaMayorAcompananteRepository,
            OrganizacionLookupRepository organizacionLookupRepository
    ) {
        this.actividadRepository = actividadRepository;
        this.participacionRepository = participacionRepository;
        this.usuarioLookupRepository = usuarioLookupRepository;
        this.personaMayorLookupRepository = personaMayorLookupRepository;
        this.acompananteLookupRepository = acompananteLookupRepository;
        this.personaMayorOrganizacionRepository = personaMayorOrganizacionRepository;
        this.personaMayorAcompananteRepository = personaMayorAcompananteRepository;
        this.organizacionLookupRepository = organizacionLookupRepository;
    }

    /** Actividades que puede ver el usuario, según su rol. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestHeader("X-User-Id") Integer idUsuario) {

        // Organización: solo ve sus propias actividades (las propuestas
        // aparecen aquí cuando las acepta).
        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        if (idOrganizacion != null) {
            return ResponseEntity.ok(
                    visiblesDe(idOrganizacion)
                            .stream()
                            .map(this::aResponse)
                            .toList()
            );
        }

        // Persona mayor: actividades de las organizaciones con vínculo aceptado.
        if (esPersonaMayor(idUsuario)) {

            Set<Integer> idsOrganizaciones = organizacionesAceptadasDe(idUsuario);

            return ResponseEntity.ok(
                    actividadesDe(idsOrganizaciones)
                            .stream()
                            .map(this::aResponse)
                            .toList()
            );
        }

        // Acompañante: actividades de las organizaciones de las personas
        // mayores que acompaña (solo vínculos aceptados).
        if (esAcompanante(idUsuario)) {

            List<PersonaMayorAcompananteLookup> relacionesAcompanante =
                    personaMayorAcompananteRepository
                            .findById_IdAcompananteAndEstado(idUsuario, "ACEPTADA");

            Set<Integer> idsOrganizaciones = new HashSet<>();

            for (PersonaMayorAcompananteLookup relacion : relacionesAcompanante) {
                idsOrganizaciones.addAll(
                        organizacionesAceptadasDe(relacion.getId().getIdPersonaMayor())
                );
            }

            return ResponseEntity.ok(
                    actividadesDe(idsOrganizaciones)
                            .stream()
                            .map(this::aResponse)
                            .toList()
            );
        }

        // Voluntario: actividades de las organizaciones a las que está vinculado.
        if (esVoluntario(idUsuario)) {
            return ResponseEntity.ok(
                    actividadesDe(new HashSet<>(usuarioLookupRepository.organizacionesDeVoluntario(idUsuario)))
                            .stream()
                            .map(this::aResponse)
                            .toList()
            );
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("No tienes permisos para consultar actividades");
    }

    /** Actividades de la organización del usuario. */
    @GetMapping("/mias")
    public ResponseEntity<?> listarMias(@RequestHeader("X-User-Id") Integer idUsuario) {

        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una organización tiene actividades propias");
        }

        return ResponseEntity.ok(
                visiblesDe(idOrganizacion)
                        .stream()
                        .map(this::aResponse)
                        .toList()
        );
    }

    /** Actividades que puede ver la persona mayor, marcando en cuáles ya está inscrita. */
    @GetMapping("/disponibles")
    public ResponseEntity<?> listarDisponibles(@RequestHeader("X-User-Id") Integer idPersonaMayor) {

        if (!esPersonaMayor(idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una persona mayor puede ver esto");
        }

        return ResponseEntity.ok(disponiblesPara(idPersonaMayor));
    }

    /**
     * Lo mismo que ve la persona mayor en /disponibles, pero pedido por un
     * acompañante con vínculo aceptado, para inscribirla desde el seguimiento.
     */
    @GetMapping("/acompanadas/{idPersonaMayor}/disponibles")
    public ResponseEntity<?> listarDisponiblesDeAcompanada(
            @PathVariable Integer idPersonaMayor,
            @RequestHeader("X-User-Id") Integer idAcompanante
    ) {

        if (!acompanaA(idAcompanante, idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo puedes ver las actividades de las personas mayores que acompañas");
        }

        return ResponseEntity.ok(disponiblesPara(idPersonaMayor));
    }

    /** Actividades de las organizaciones de la persona mayor, marcando en cuáles está inscrita. */
    private List<ActividadDisponibleResponse> disponiblesPara(Integer idPersonaMayor) {

        Set<Integer> idsOrganizaciones = organizacionesAceptadasDe(idPersonaMayor);

        List<Actividad> actividades = actividadesDe(idsOrganizaciones);

        Set<Integer> idsInscritos =
                participacionRepository.findById_IdPersonaMayor(idPersonaMayor)
                        .stream()
                        .map(p -> p.getId().getIdActividad())
                        .collect(Collectors.toSet());

        return actividades.stream()
                .map(a -> {
                    ActividadDisponibleResponse disponible = new ActividadDisponibleResponse(
                            a.getIdActividad(),
                            a.getNombre(),
                            a.getDescripcion(),
                            a.getFecha(),
                            a.getHora(),
                            a.getLugar(),
                            a.getTipo(),
                            a.getCupos(),
                            idsInscritos.contains(a.getIdActividad())
                    );
                    disponible.setHoraFin(a.getHoraFin());
                    disponible.setFrecuenciaDias(a.getFrecuenciaDias());
                    disponible.setLatitud(a.getLatitud());
                    disponible.setLongitud(a.getLongitud());
                    return disponible;
                })
                .toList();
    }

    /** Inscritos en una actividad y si asistieron. Solo para la organización dueña. */
    @GetMapping("/{id}/participantes")
    public ResponseEntity<?> listarParticipantes(
            @PathVariable Integer id,
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {

        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una organización puede consultar participantes");
        }

        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null) {
            return ResponseEntity.notFound().build();
        }

        if (!idOrganizacion.equals(actividad.getIdOrganizacion())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Esta actividad no pertenece a tu organización");
        }

        List<ParticipanteActividadResponse> participantes =
                participacionRepository.findById_IdActividad(id)
                        .stream()
                        .map(p -> {
                            Integer idPersonaMayor = p.getId().getIdPersonaMayor();
                            UsuarioLookup usuario =
                                    usuarioLookupRepository.findById(idPersonaMayor).orElse(null);

                            return new ParticipanteActividadResponse(
                                    idPersonaMayor,
                                    usuario != null ? usuario.getNombreUsuario() : null,
                                    usuario != null ? usuario.getCelular() : null,
                                    p.getAsistio()
                            );
                        })
                        .toList();

        return ResponseEntity.ok(participantes);
    }

    /** Marca si una persona inscrita asistió. Solo para la organización dueña. */
    @PutMapping("/{id}/participantes/{idPersonaMayor}/asistencia")
    public ResponseEntity<?> registrarAsistencia(
            @PathVariable Integer id,
            @PathVariable Integer idPersonaMayor,
            @RequestBody AsistenciaRequest request,
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {

        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una organización puede registrar asistencia");
        }

        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null) {
            return ResponseEntity.notFound().build();
        }

        if (!idOrganizacion.equals(actividad.getIdOrganizacion())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Esta actividad no pertenece a tu organización");
        }

        Participacion participacion =
                participacionRepository
                        .findById_IdPersonaMayorAndId_IdActividad(idPersonaMayor, id)
                        .orElse(null);

        if (participacion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("La persona mayor no está inscrita en esta actividad");
        }

        participacion.setAsistio(request.getAsistio());
        participacionRepository.save(participacion);

        return ResponseEntity.ok().build();
    }

    /**
     * Inscribe a la persona mayor. Exige que tenga vínculo aceptado con la
     * organización, que la actividad no haya pasado y que queden cupos.
     */
    @PostMapping("/{id}/inscribirse")
    public ResponseEntity<?> inscribirse(
            @PathVariable Integer id,
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {

        if (!esPersonaMayor(idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una persona mayor puede inscribirse");
        }

        return inscribir(idPersonaMayor, id, false);
    }

    /**
     * El acompañante inscribe a una persona mayor que acompaña (vínculo
     * aceptado), con las mismas reglas que si ella se inscribiera.
     */
    @PostMapping("/{id}/inscritos/{idPersonaMayor}")
    public ResponseEntity<?> inscribirAcompanada(
            @PathVariable Integer id,
            @PathVariable Integer idPersonaMayor,
            @RequestHeader("X-User-Id") Integer idAcompanante
    ) {

        if (!acompanaA(idAcompanante, idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo puedes inscribir a las personas mayores que acompañas");
        }

        return inscribir(idPersonaMayor, id, true);
    }

    /**
     * Exige que la persona mayor tenga vínculo aceptado con la organización,
     * que la actividad no haya pasado y que queden cupos. porAcompanante
     * cambia los mensajes: el acompañante habla de la persona, no de sí mismo.
     */
    private ResponseEntity<?> inscribir(Integer idPersonaMayor, Integer id, boolean porAcompanante) {

        Actividad actividad = actividadRepository.findById(id).orElse(null);

        // Una propuesta pendiente o rechazada no existe para la persona mayor
        if (actividad == null || !actividad.esVisible()) {
            return ResponseEntity.notFound().build();
        }

        boolean asociada = organizacionesAceptadasDe(idPersonaMayor)
                .contains(actividad.getIdOrganizacion());

        if (!asociada) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(porAcompanante
                            ? "La persona mayor no está asociada a la organización de esta actividad"
                            : "No puedes inscribirte en actividades de una organización a la que no estás asociado");
        }

        if (yaPaso(actividad)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(porAcompanante
                            ? "No se puede inscribir en una actividad que ya pasó"
                            : "No puedes inscribirte en una actividad que ya pasó");
        }

        boolean yaInscrito =
                participacionRepository
                        .findById_IdPersonaMayorAndId_IdActividad(idPersonaMayor, id)
                        .isPresent();

        if (yaInscrito) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(porAcompanante
                            ? "La persona mayor ya está inscrita en esta actividad"
                            : "Ya estás inscrito en esta actividad");
        }

        if (actividad.getCupos() != null) {

            long participantesActuales =
                    participacionRepository.findById_IdActividad(id).size();

            if (participantesActuales >= actividad.getCupos()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("La actividad ya alcanzó el límite de cupos");
            }
        }

        participacionRepository.save(new Participacion(idPersonaMayor, id));

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** Cancela la inscripción, siempre que la actividad no haya pasado. */
    @DeleteMapping("/{id}/inscribirse")
    public ResponseEntity<?> cancelarInscripcion(
            @PathVariable Integer id,
            @RequestHeader("X-User-Id") Integer idPersonaMayor
    ) {

        if (!esPersonaMayor(idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una persona mayor puede cancelar su inscripción");
        }

        return cancelar(idPersonaMayor, id, false);
    }

    /** El acompañante cancela la inscripción de una persona mayor que acompaña. */
    @DeleteMapping("/{id}/inscritos/{idPersonaMayor}")
    public ResponseEntity<?> cancelarInscripcionDeAcompanada(
            @PathVariable Integer id,
            @PathVariable Integer idPersonaMayor,
            @RequestHeader("X-User-Id") Integer idAcompanante
    ) {

        if (!acompanaA(idAcompanante, idPersonaMayor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo puedes cancelar inscripciones de las personas mayores que acompañas");
        }

        return cancelar(idPersonaMayor, id, true);
    }

    private ResponseEntity<?> cancelar(Integer idPersonaMayor, Integer id, boolean porAcompanante) {

        Participacion participacion =
                participacionRepository
                        .findById_IdPersonaMayorAndId_IdActividad(idPersonaMayor, id)
                        .orElse(null);

        if (participacion == null) {
            return ResponseEntity.notFound().build();
        }

        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad != null && yaPaso(actividad)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(porAcompanante
                            ? "No se puede cancelar la inscripción de una actividad que ya pasó"
                            : "No puedes cancelar la inscripción de una actividad que ya pasó");
        }

        participacionRepository.delete(participacion);

        return ResponseEntity.noContent().build();
    }

    /**
     * Una actividad de hoy todavía cuenta como próxima. Se usa la fecha de
     * Colombia para que no dependa de la zona horaria del servidor.
     */
    private boolean yaPaso(Actividad actividad) {
        return actividad.getFecha() != null
                && actividad.getFecha().isBefore(ZonaHoraria.ahora().toLocalDate());
    }

    /** Crea una actividad de la organización del usuario. La fecha no puede ser anterior a hoy. */
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestBody ActividadRequest request,
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {

        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una organización puede crear actividades");
        }

        String error = validarNueva(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        Actividad cruce = cruceCon(idOrganizacion, request.getFecha(), request.getHora(), request.getHoraFin(), null);
        if (cruce != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mensajeCruce(cruce));
        }

        Actividad actividad = new Actividad();
        actividad.setIdOrganizacion(idOrganizacion);
        aplicarDatos(actividad, request);

        actividad = actividadRepository.save(actividad);

        return ResponseEntity.status(HttpStatus.CREATED).body(aResponse(actividad));
    }

    // =========================================================
    // PROPUESTAS DE VOLUNTARIOS Y PERSONAS MAYORES
    // =========================================================

    /**
     * Un voluntario o una persona mayor propone una actividad a una
     * organización con la que tiene vínculo aceptado. Usa las mismas
     * validaciones que la creación de la organización y queda PENDIENTE
     * hasta que la organización responda.
     */
    @PostMapping("/propuestas")
    public ResponseEntity<?> proponer(
            @RequestBody PropuestaActividadRequest request,
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {

        boolean voluntario = esVoluntario(idUsuario);
        boolean personaMayor = !voluntario && esPersonaMayor(idUsuario);

        if (!voluntario && !personaMayor) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo un voluntario o una persona mayor puede proponer actividades");
        }

        if (request.getIdOrganizacion() == null) {
            return ResponseEntity.badRequest()
                    .body("Selecciona la organización a la que quieres presentar la actividad");
        }

        boolean vinculado = voluntario
                ? usuarioLookupRepository.voluntarioVinculado(idUsuario, request.getIdOrganizacion())
                : organizacionesAceptadasDe(idUsuario).contains(request.getIdOrganizacion());

        if (!vinculado) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo puedes proponer actividades a organizaciones a las que estás vinculado");
        }

        String error = validarNueva(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        Actividad cruce = cruceCon(request.getIdOrganizacion(), request.getFecha(),
                request.getHora(), request.getHoraFin(), null);
        if (cruce != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mensajeCruce(cruce));
        }

        Actividad actividad = new Actividad();
        actividad.setIdOrganizacion(request.getIdOrganizacion());
        if (voluntario) {
            actividad.setIdVoluntario(idUsuario);
        } else {
            actividad.setIdPersonaMayorProponente(idUsuario);
        }
        actividad.setEstado(Actividad.PENDIENTE);
        aplicarDatos(actividad, request);

        actividad = actividadRepository.save(actividad);

        return ResponseEntity.status(HttpStatus.CREATED).body(aPropuesta(actividad));
    }

    /** Propuestas del voluntario o la persona mayor en todos sus estados, de la más nueva a la más vieja. */
    @GetMapping("/propuestas/mias")
    public ResponseEntity<?> listarPropuestasMias(@RequestHeader("X-User-Id") Integer idUsuario) {

        List<Actividad> propuestas;

        if (esVoluntario(idUsuario)) {
            propuestas = actividadRepository.findByIdVoluntario(idUsuario);
        } else if (esPersonaMayor(idUsuario)) {
            propuestas = actividadRepository.findByIdPersonaMayorProponente(idUsuario);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo un voluntario o una persona mayor tiene propuestas de actividades");
        }

        return ResponseEntity.ok(
                propuestas.stream()
                        .sorted(Comparator.comparing(Actividad::getIdActividad).reversed())
                        .map(this::aPropuesta)
                        .toList()
        );
    }

    /** Propuestas pendientes que los voluntarios y las personas mayores le presentaron a la organización. */
    @GetMapping("/propuestas")
    public ResponseEntity<?> listarPropuestasPendientes(@RequestHeader("X-User-Id") Integer idUsuario) {

        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una organización recibe propuestas de actividades");
        }

        return ResponseEntity.ok(
                actividadRepository.findByIdOrganizacionAndEstado(idOrganizacion, Actividad.PENDIENTE)
                        .stream()
                        .sorted(Comparator.comparing(Actividad::getIdActividad))
                        .map(this::aPropuesta)
                        .toList()
        );
    }

    /** La organización acepta la propuesta: desde ahora la ven sus personas mayores. */
    @PutMapping("/propuestas/{id}/aceptar")
    public ResponseEntity<?> aceptarPropuesta(
            @PathVariable Integer id,
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {
        return responderPropuesta(id, idUsuario, Actividad.ACEPTADA);
    }

    /** La organización rechaza la propuesta: no la ve ninguna persona mayor. */
    @PutMapping("/propuestas/{id}/rechazar")
    public ResponseEntity<?> rechazarPropuesta(
            @PathVariable Integer id,
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {
        return responderPropuesta(id, idUsuario, Actividad.RECHAZADA);
    }

    private ResponseEntity<?> responderPropuesta(Integer id, Integer idUsuario, String nuevoEstado) {

        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        if (idOrganizacion == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo una organización puede responder propuestas");
        }

        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null || !actividad.esPropuesta()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontró la propuesta");
        }

        if (!idOrganizacion.equals(actividad.getIdOrganizacion())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Esta propuesta no fue presentada a tu organización");
        }

        if (!Actividad.PENDIENTE.equals(actividad.getEstado())) {
            return ResponseEntity.badRequest().body("Esta propuesta ya fue respondida");
        }

        if (Actividad.ACEPTADA.equals(nuevoEstado) && yaPaso(actividad)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("No puedes aceptar una actividad cuya fecha ya pasó");
        }

        // Entre la propuesta y la respuesta la organización pudo haber
        // confirmado otra actividad a esa hora.
        if (Actividad.ACEPTADA.equals(nuevoEstado)) {
            Actividad cruce = cruceCon(idOrganizacion, actividad.getFecha(), actividad.getHora(),
                    actividad.getHoraFin(), actividad.getIdActividad());
            if (cruce != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("No puedes aceptarla: ya hay una actividad confirmada en ese horario ("
                                + describir(cruce) + "). Pídele a quien la propuso que elija otro horario.");
            }
        }

        actividad.setEstado(nuevoEstado);
        actividad = actividadRepository.save(actividad);

        return ResponseEntity.ok(aPropuesta(actividad));
    }

    /** Edita una actividad. Solo para la organización dueña. */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @RequestBody ActividadRequest request,
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {

        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null) {
            return ResponseEntity.notFound().build();
        }

        if (idOrganizacion == null || !idOrganizacion.equals(actividad.getIdOrganizacion())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Esta actividad no pertenece a tu organización");
        }

        if (request.getNombre() == null || request.getNombre().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre es obligatorio");
        }

        if (request.getCupos() != null && request.getCupos() <= 0) {
            return ResponseEntity.badRequest().body("Los cupos deben ser mayores a 0");
        }

        String errorHorario = validarHorario(request);
        if (errorHorario != null) {
            return ResponseEntity.badRequest().body(errorHorario);
        }

        String errorFrecuencia = validarFrecuencia(request);
        if (errorFrecuencia != null) {
            return ResponseEntity.badRequest().body(errorFrecuencia);
        }

        Actividad cruce = cruceCon(idOrganizacion, request.getFecha(), request.getHora(),
                request.getHoraFin(), actividad.getIdActividad());
        if (cruce != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mensajeCruce(cruce));
        }

        aplicarDatos(actividad, request);

        actividad = actividadRepository.save(actividad);

        return ResponseEntity.ok(aResponse(actividad));
    }

    /** Borra una actividad con sus inscripciones. Solo para la organización dueña. */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminar(
            @PathVariable Integer id,
            @RequestHeader("X-User-Id") Integer idUsuario
    ) {

        Integer idOrganizacion = resolverIdOrganizacion(idUsuario);

        Actividad actividad = actividadRepository.findById(id).orElse(null);

        if (actividad == null) {
            return ResponseEntity.notFound().build();
        }

        if (idOrganizacion == null || !idOrganizacion.equals(actividad.getIdOrganizacion())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Esta actividad no pertenece a tu organización");
        }

        // Primero las inscripciones: si la llave foránea de participacion no
        // borra en cascada, el DELETE de la actividad fallaría cuando hay
        // personas mayores inscritas y la actividad les seguiría apareciendo.
        // Todo va en una transacción: o se borra todo o nada.
        participacionRepository.deleteAllInBatch(
                participacionRepository.findById_IdActividad(id)
        );

        actividadRepository.deleteById(id);

        return ResponseEntity.noContent().build();
    }

    /** Organización de la cuenta, o null si el usuario no es una organización. */
    private Integer resolverIdOrganizacion(Integer idUsuario) {
        return usuarioLookupRepository.findById(idUsuario)
                .map(UsuarioLookup::getIdOrganizacion)
                .orElse(null);
    }

    /** Mismas reglas para la actividad de una organización y una propuesta. */
    private String validarNueva(ActividadRequest request) {
        if (request.getNombre() == null || request.getNombre().isBlank()) {
            return "El nombre es obligatorio";
        }

        if (request.getFecha() != null && request.getFecha().isBefore(LocalDate.now())) {
            return "No se puede crear una actividad con una fecha anterior a hoy";
        }

        if (request.getCupos() != null && request.getCupos() <= 0) {
            return "Los cupos deben ser mayores a 0";
        }

        String errorHorario = validarHorario(request);
        if (errorHorario != null) {
            return errorHorario;
        }

        return validarFrecuencia(request);
    }

    /** Si tiene hora de inicio, también necesita la de fin, y la de fin va después. */
    private String validarHorario(ActividadRequest request) {
        boolean conInicio = request.getHora() != null && !request.getHora().isBlank();
        boolean conFin = request.getHoraFin() != null && !request.getHoraFin().isBlank();

        if (!conInicio && !conFin) {
            return null;
        }

        if (!conInicio) {
            return "Indica a qué hora empieza la actividad";
        }

        if (!conFin) {
            return "Indica a qué hora termina la actividad";
        }

        LocalTime inicio = leerHora(request.getHora());
        LocalTime fin = leerHora(request.getHoraFin());

        if (inicio == null || fin == null) {
            return "La hora no es válida";
        }

        if (!fin.isAfter(inicio)) {
            return "La hora de fin debe ser posterior a la hora de inicio";
        }

        return null;
    }

    /**
     * Actividad confirmada de la organización (creada por ella o propuesta
     * aceptada) que se cruza con ese horario el mismo día, o null si no hay.
     * Las propuestas pendientes no cuentan: todavía no ocupan el horario.
     * Que una termine justo cuando empieza la otra no es cruce.
     * idExcluida es la misma actividad cuando se edita o se acepta.
     */
    private Actividad cruceCon(Integer idOrganizacion, LocalDate fecha, String hora,
                               String horaFin, Integer idExcluida) {
        LocalTime inicio = leerHora(hora);

        if (fecha == null || inicio == null) {
            return null;
        }

        LocalTime fin = finDe(inicio, horaFin);

        for (Actividad otra : actividadRepository.findConfirmadasDelDia(idOrganizacion, fecha)) {
            LocalTime inicioOtra = leerHora(otra.getHora());

            if (otra.getIdActividad().equals(idExcluida) || inicioOtra == null) {
                continue;
            }

            if (inicio.isBefore(finDe(inicioOtra, otra.getHoraFin())) && inicioOtra.isBefore(fin)) {
                return otra;
            }
        }

        return null;
    }

    private String mensajeCruce(Actividad cruce) {
        return "Ya hay una actividad confirmada de la organización en ese horario ("
                + describir(cruce) + "). Selecciona otro horario para la actividad.";
    }

    /** Por ejemplo: "Bailoterapia" de 09:00 a 10:00. */
    private String describir(Actividad actividad) {
        LocalTime inicio = leerHora(actividad.getHora());
        return "\"" + actividad.getNombre() + "\" de " + inicio + " a " + finDe(inicio, actividad.getHoraFin());
    }

    /** Hora de fin; si no tiene, MINUTOS_SIN_HORA_FIN después del inicio, sin pasar de medianoche. */
    private static LocalTime finDe(LocalTime inicio, String horaFin) {
        LocalTime fin = leerHora(horaFin);
        if (fin != null) {
            return fin;
        }
        LocalTime supuesto = inicio.plusMinutes(MINUTOS_SIN_HORA_FIN);
        return supuesto.isAfter(inicio) ? supuesto : LocalTime.of(23, 59);
    }

    /** "HH:mm" del formulario, o null si no hay o no es una hora. */
    private static LocalTime leerHora(String hora) {
        if (hora == null || hora.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(hora.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Una actividad periódica necesita fecha (de ahí se cuentan los días) y entre 1 y 365 días. */
    private String validarFrecuencia(ActividadRequest request) {
        Integer frecuencia = request.getFrecuenciaDias();

        if (frecuencia == null) {
            return null;
        }

        if (frecuencia < 1 || frecuencia > MAX_FRECUENCIA_DIAS) {
            return "La actividad debe repetirse cada 1 a " + MAX_FRECUENCIA_DIAS + " días";
        }

        if (request.getFecha() == null) {
            return "Una actividad que se repite necesita una fecha de inicio";
        }

        return null;
    }

    private void aplicarDatos(Actividad actividad, ActividadRequest request) {
        actividad.setNombre(request.getNombre());
        actividad.setDescripcion(request.getDescripcion());
        actividad.setFecha(request.getFecha());
        actividad.setHora(request.getHora());
        actividad.setHoraFin(request.getHoraFin());
        // La latitud y la longitud no vienen del formulario: analitica-service
        // ubica el lugar sola (UbicadorAutomatico) y las llena.
        actividad.setLugar(request.getLugar());
        actividad.setTipo(request.getTipo());
        actividad.setCupos(request.getCupos());
        actividad.setResponsable(request.getResponsable());
        actividad.setFrecuenciaDias(request.getFrecuenciaDias());
    }

    /** Actividades de la organización que ven las personas mayores (sin propuestas pendientes ni rechazadas). */
    private List<Actividad> visiblesDe(Integer idOrganizacion) {
        return actividadRepository.findByIdOrganizacion(idOrganizacion)
                .stream()
                .filter(Actividad::esVisible)
                .toList();
    }

    // Se mira el rol: hay cuentas viejas con rol VOLUNTARIO sin fila en la tabla voluntario.
    private boolean esVoluntario(Integer idUsuario) {
        return usuarioLookupRepository.tieneRol(idUsuario, "VOLUNTARIO");
    }

    private boolean esPersonaMayor(Integer idUsuario) {
        return personaMayorLookupRepository.existsById(idUsuario);
    }

    private boolean esAcompanante(Integer idUsuario) {
        return acompananteLookupRepository.existsById(idUsuario);
    }

    /** Si el usuario acompaña a la persona mayor con vínculo aceptado. */
    private boolean acompanaA(Integer idAcompanante, Integer idPersonaMayor) {
        return personaMayorAcompananteRepository
                .existsById_IdPersonaMayorAndId_IdAcompananteAndEstado(idPersonaMayor, idAcompanante, "ACEPTADA");
    }

    /** Organizaciones con las que la persona mayor tiene vínculo aceptado. */
    private Set<Integer> organizacionesAceptadasDe(Integer idPersonaMayor) {
        return personaMayorOrganizacionRepository
                .findById_IdPersonaMayorAndEstado(idPersonaMayor, "ACEPTADA")
                .stream()
                .map(relacion -> relacion.getId().getIdOrganizacion())
                .collect(Collectors.toSet());
    }

    private List<Actividad> actividadesDe(Set<Integer> idsOrganizaciones) {
        return idsOrganizaciones.stream()
                .flatMap(idOrganizacion -> visiblesDe(idOrganizacion).stream())
                .toList();
    }

    private PropuestaActividadResponse aPropuesta(Actividad a) {
        String nombreOrganizacion = organizacionLookupRepository.findById(a.getIdOrganizacion())
                .map(OrganizacionLookup::getNombre)
                .orElse(null);

        String nombreVoluntario = nombreDe(a.getIdVoluntario());
        String nombrePersonaMayor = nombreDe(a.getIdPersonaMayorProponente());

        return new PropuestaActividadResponse(
                a.getIdActividad(),
                a.getIdOrganizacion(),
                nombreOrganizacion,
                a.getIdVoluntario(),
                nombreVoluntario,
                a.getIdPersonaMayorProponente(),
                nombrePersonaMayor,
                a.getEstado(),
                a.getNombre(),
                a.getDescripcion(),
                a.getFecha(),
                a.getHora(),
                a.getHoraFin(),
                a.getLugar(),
                a.getTipo(),
                a.getCupos(),
                a.getResponsable(),
                a.getFrecuenciaDias(),
                a.getLatitud(),
                a.getLongitud()
        );
    }

    /** Nombre del usuario, o null si no hay id. */
    private String nombreDe(Integer idUsuario) {
        return idUsuario == null ? null
                : usuarioLookupRepository.findById(idUsuario)
                        .map(UsuarioLookup::getNombreUsuario)
                        .orElse(null);
    }

    private ActividadResponse aResponse(Actividad a) {
        ActividadResponse response = new ActividadResponse(
                a.getIdActividad(),
                a.getIdOrganizacion(),
                a.getNombre(),
                a.getDescripcion(),
                a.getFecha(),
                a.getHora(),
                a.getLugar(),
                a.getTipo(),
                a.getCupos(),
                a.getResponsable()
        );
        response.setHoraFin(a.getHoraFin());
        response.setFrecuenciaDias(a.getFrecuenciaDias());
        response.setLatitud(a.getLatitud());
        response.setLongitud(a.getLongitud());
        return response;
    }
}
