import {
  Component,
  OnDestroy,
  OnInit,
  computed,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePipe, registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es-CO';

import { Icono } from '../../../shared/icono/icono';
import {
  ActividadService,
  Actividad,
  ActividadRequest,
  PropuestaActividad,
  separarPorFecha
} from '../../../core/actividades/actividad.service';
import {
  ActividadAnalitica,
  AnaliticaService,
  MedicionAnalitica,
  PoblacionAnalitica,
  SaludAnalitica
} from '../../../core/analitica/analitica.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  OrganizacionService,
  PersonaMayorOrganizacion,
  VoluntarioOrganizacion
} from '../../../core/organizacion/organizacion.service';
import { alCambiar } from '../../../core/tiempo-real/tiempo-real.service';
import { NOMBRE_INDICADOR, indicadoresFueraDeRango } from '../../../core/signos-vitales/rangos';
import { formatearHora } from '../../../core/medicamentos/medicamento.service';
import { formatearFechaCita } from '../../../core/citas-medicas/cita-medica.service';
import { AltoPantalla } from '../../../shared/alto-pantalla/alto-pantalla';
import { diasDesde, fechaLocal, haceDias, proximoCumpleanos } from '../../../core/fechas/fechas';

registerLocaleData(localeEs);

/** Tarjeta de indicador. */
interface StatCard {
  icon: string;
  value: number | null;   // null = cargando o sin datos
  label: string;
  detalle: string | null;
  tono: 'normal' | 'alerta';
}

type Prioridad = 'Alta' | 'Media' | 'Baja';

/** Algo que la organización debería atender. */
interface Alerta {
  clave: string;
  prioridad: Prioridad;
  titulo: string;
  descripcion: string;
  enlace: string;
}

/** Botón de acceso rápido a otra sección del panel. */
interface AccionRapida {
  icon: string;
  label: string;
  route: string;
  /**
   * Si es true, el enlace lleva ?abrir=registrar para que la página destino
   * (Personas mayores o Actividades) abra su formulario de una vez.
   */
  abrirFormulario?: boolean;
}

/** Persona que cumple años pronto. */
interface Cumpleanos {
  idUsuario: number;
  nombre: string;
  dias: number;
  edad: number;
  fecha: string; // YYYY-MM-DD del próximo cumpleaños
}

const MINUTO = 60_000;
const DIA = 24 * 60 * MINUTO;

/** Días hacia atrás del resumen de participación. */
const DIAS_PARTICIPACION = 30;
/** Pasados estos días sin medición de signos vitales, se avisa. */
const DIAS_SIN_MEDICION = 30;
/** Cumpleaños que se anuncian: los de los próximos días. */
const DIAS_CUMPLEANOS = 30;

const ORDEN_PRIORIDAD: Record<Prioridad, number> = { Alta: 0, Media: 1, Baja: 2 };

const RUTA = '/panel/organizacion';

/** Formulario vacío de actividad. */
function actividadVacia(): ActividadRequest {
  return {
    nombre: '',
    descripcion: null,
    fecha: null,
    hora: null,
    lugar: null,
    tipo: null,
    cupos: null,
    responsable: null,
    frecuenciaDias: null
  };
}

/**
 * Inicio del panel de la organización:
 *  1. Saludo e indicadores (personas mayores, acompañantes, voluntarios y
 *     alertas activas).
 *  2. Alertas calculadas con sus datos y cumpleaños próximos.
 *  3. Actividades con registro y edición rápidos.
 *  4. Acciones rápidas y un resumen de datos (participación del último mes
 *     e intereses más comunes) que lleva a la Analítica.
 * Todo sale del backend: analitica-service (población, salud y
 * participación), voluntario-service y actividad-service.
 */
@Component({
  selector: 'app-panel-organizacion',
  imports: [FormsModule, Icono, DatePipe, RouterLink],
  templateUrl: './organizacion.html',
  styleUrl: './organizacion.css',
  hostDirectives: [AltoPantalla]
})
export class PanelOrganizacion implements OnInit, OnDestroy {

  protected readonly ruta = RUTA;

  /** Hora actual; se refresca cada minuto (cambio de día, "Hoy"/"Mañana"). */
  protected readonly ahora = signal(new Date());
  private intervaloReloj?: ReturnType<typeof setInterval>;

  /** Nombre de la organización; se actualiza si lo cambian en "Mi información". */
  protected readonly nombreUsuario = signal('');

  protected readonly poblacion = signal<PoblacionAnalitica | null>(null);
  protected readonly salud = signal<SaludAnalitica | null>(null);
  /** Actividades de los últimos DIAS_PARTICIPACION días, con inscritos y asistencia. */
  protected readonly participacion = signal<ActividadAnalitica[] | null>(null);
  protected readonly voluntarios = signal<VoluntarioOrganizacion[] | null>(null);
  protected readonly solicitudesVoluntarios = signal<VoluntarioOrganizacion[]>([]);
  protected readonly propuestas = signal<PropuestaActividad[]>([]);
  /** Personas mayores que pidieron unirse a la organización. */
  protected readonly solicitudesPersonas = signal<PersonaMayorOrganizacion[]>([]);

  protected readonly formatearHora = formatearHora;

  protected readonly accionesRapidas: AccionRapida[] = [
    {
      icon: 'user',
      label: 'Registrar persona mayor',
      route: `${RUTA}/personas-mayores`,
      abrirFormulario: true
    },
    {
      icon: 'activity',
      label: 'Registrar actividad',
      route: `${RUTA}/actividades`,
      abrirFormulario: true
    },
    {
      icon: 'heart',
      label: 'Registrar signos vitales',
      route: `${RUTA}/signos-vitales`
    },
    {
      icon: 'bar-chart',
      label: 'Generar reporte',
      route: `${RUTA}/analitica`
    }
  ];

  // Actividades (registro y edición rápidos)

  protected readonly actividades = signal<Actividad[]>([]);
  protected readonly errorActividades = signal<string | null>(null);

  /** Formulario rápido de nueva actividad. */
  protected nuevaActividad: ActividadRequest = actividadVacia();

  /** Actividad que se está editando en la lista; null si ninguna. */
  protected actividadEditandoId: number | null = null;
  protected actividadEditando: ActividadRequest = actividadVacia();

  constructor(
    private actividadService: ActividadService,
    private analiticaService: AnaliticaService,
    private authService: AuthService,
    private organizacionService: OrganizacionService
  ) {
    this.nombreUsuario.set(this.authService.getNombreUsuario());

    alCambiar(['actividades'], () => {
      this.cargarActividades();
      this.cargarPropuestas();
      this.cargarParticipacion();
    });
    alCambiar(['usuarios'], () => this.cargarInformacionOrganizacion());
    alCambiar(['organizaciones', 'usuarios', 'acompanamientos', 'gustos'], () => this.cargarPoblacion());
    alCambiar(['organizaciones', 'usuarios'], () => this.cargarSolicitudesPersonas());
    alCambiar(['signos-vitales', 'organizaciones'], () => this.cargarSalud());
    alCambiar(['voluntarios', 'usuarios'], () => this.cargarVoluntarios());
  }

  ngOnInit(): void {
    this.intervaloReloj = setInterval(() => this.ahora.set(new Date()), MINUTO);

    this.cargarInformacionOrganizacion();
    this.cargarActividades();
    this.cargarPropuestas();
    this.cargarParticipacion();
    this.cargarPoblacion();
    this.cargarSalud();
    this.cargarVoluntarios();
    this.cargarSolicitudesPersonas();
  }

  ngOnDestroy(): void {
    clearInterval(this.intervaloReloj);
  }

  /**
   * En pantallas grandes la página ocupa justo el alto de la ventana (sin
   * scroll): se calcula cuánto queda debajo de la barra superior y se pasa al
   * CSS en --alto-disponible. En pantallas pequeñas el CSS no la usa.
   */
  // Carga de datos

  private cargarInformacionOrganizacion(): void {
    this.organizacionService.obtenerInformacion().subscribe({
      next: (data) => this.nombreUsuario.set(data.nombre),
      error: (error) => console.error('Error al cargar la información de la organización:', error)
    });
  }

  private cargarPoblacion(): void {
    this.analiticaService.poblacion().subscribe({
      next: (poblacion) => this.poblacion.set(poblacion),
      error: () => this.poblacion.set(null)
    });
  }

  private cargarSalud(): void {
    this.analiticaService.salud().subscribe({
      next: (salud) => this.salud.set(salud),
      error: () => this.salud.set(null)
    });
  }

  private cargarParticipacion(): void {
    const hoy = this.ahora();
    const desde = new Date(hoy.getTime() - DIAS_PARTICIPACION * DIA);
    this.analiticaService.actividades(fechaLocal(desde), fechaLocal(hoy)).subscribe({
      next: (actividades) => this.participacion.set(actividades),
      error: () => this.participacion.set([])
    });
  }

  private cargarVoluntarios(): void {
    this.organizacionService.obtenerVoluntarios().subscribe({
      next: (voluntarios) => this.voluntarios.set(voluntarios),
      error: () => this.voluntarios.set([])
    });

    this.organizacionService.obtenerSolicitudesVoluntarios().subscribe({
      next: (solicitudes) => this.solicitudesVoluntarios.set(solicitudes),
      error: () => this.solicitudesVoluntarios.set([])
    });
  }

  private cargarSolicitudesPersonas(): void {
    this.organizacionService.obtenerSolicitudesPersonasMayores().subscribe({
      next: (solicitudes) => this.solicitudesPersonas.set(solicitudes),
      error: () => this.solicitudesPersonas.set([])
    });
  }

  private cargarPropuestas(): void {
    this.actividadService.listarPropuestasPendientes().subscribe({
      next: (propuestas) => this.propuestas.set(propuestas),
      error: () => this.propuestas.set([])
    });
  }

  private cargarActividades(): void {
    this.actividadService.listarMias().subscribe({
      next: (actividades) => {
        // Primero las próximas (la más cercana arriba) y luego las ya realizadas.
        const { proximas, pasadas } = separarPorFecha(actividades);
        this.actividades.set([...proximas, ...pasadas]);
      },
      error: () => {
        this.errorActividades.set('No se pudieron cargar las actividades');
      }
    });
  }

  // Datos derivados

  /** Última medición de cada persona vinculada, por idUsuario. */
  private readonly ultimaMedicion = computed(() => {
    const ultimas = new Map<number, MedicionAnalitica>();
    for (const m of this.salud()?.mediciones ?? []) {
      const actual = ultimas.get(m.idPersonaMayor);
      if (!actual || m.fechaHora > actual.fechaHora) {
        ultimas.set(m.idPersonaMayor, m);
      }
    }
    return ultimas;
  });

  /**
   * Alertas calculadas con los datos de la organización:
   *  - Alta: personas cuya última medición tiene valores fuera de rango.
   *  - Media: asistencia sin registrar, solicitudes de personas mayores y
   *    de voluntarios, y propuestas de actividades por revisar.
   *  - Baja: personas sin mediciones hace más de DIAS_SIN_MEDICION días.
   */
  protected readonly alertas = computed<Alerta[]>(() => {
    const alertas: Alerta[] = [];
    const hoy = fechaLocal(this.ahora());

    const salud = this.salud();
    if (salud) {
      const ultimas = this.ultimaMedicion();
      const sinMedicion: string[] = [];

      for (const persona of salud.personas) {
        const ultima = ultimas.get(persona.idUsuario);
        if (!ultima) {
          sinMedicion.push(persona.nombre);
          continue;
        }

        const fuera = indicadoresFueraDeRango(ultima).map((i) => NOMBRE_INDICADOR[i]);
        if (fuera.length > 0) {
          alertas.push({
            clave: `signos-${persona.idUsuario}`,
            prioridad: 'Alta',
            titulo: persona.nombre,
            descripcion: `${fuera.join(', ')} fuera de lo habitual en su última medición (${haceDias(ultima.fechaHora, this.ahora())}).`,
            enlace: `${RUTA}/signos-vitales`
          });
        }

        if (diasDesde(ultima.fechaHora, this.ahora()) > DIAS_SIN_MEDICION) {
          sinMedicion.push(persona.nombre);
        }
      }

      // Se agrupan en una sola alerta para no llenar la lista.
      if (sinMedicion.length > 0) {
        alertas.push({
          clave: 'sin-medicion',
          prioridad: 'Baja',
          titulo: sinMedicion.length === 1
            ? `${sinMedicion[0]} no tiene mediciones recientes`
            : `${sinMedicion.length} personas sin mediciones recientes`,
          descripcion: `Sin signos vitales registrados en los últimos ${DIAS_SIN_MEDICION} días.`,
          enlace: `${RUTA}/signos-vitales`
        });
      }
    }

    for (const act of this.participacion() ?? []) {
      if (act.fecha < hoy && act.inscritos > 0 && act.conRegistro < act.inscritos) {
        alertas.push({
          clave: `asistencia-${act.idActividad}`,
          prioridad: 'Media',
          titulo: `Asistencia sin registrar: ${act.nombre}`,
          descripcion: `${this.formatearDia(act.fecha)} · faltan ${act.inscritos - act.conRegistro} de ${act.inscritos} inscritos.`,
          enlace: `${RUTA}/actividades`
        });
      }
    }

    const solicitudesPersonas = this.solicitudesPersonas();
    if (solicitudesPersonas.length > 0) {
      alertas.push({
        clave: 'solicitudes-personas',
        prioridad: 'Media',
        titulo: solicitudesPersonas.length === 1
          ? `${solicitudesPersonas[0].nombre} quiere unirse a tu organización`
          : `${solicitudesPersonas.length} personas mayores quieren unirse`,
        descripcion: 'Acéptalas o recházalas en la sección Personas mayores.',
        enlace: `${RUTA}/personas-mayores`
      });
    }

    const solicitudes = this.solicitudesVoluntarios();
    if (solicitudes.length > 0) {
      alertas.push({
        clave: 'solicitudes-voluntarios',
        prioridad: 'Media',
        titulo: solicitudes.length === 1
          ? `${solicitudes[0].nombre} quiere ser voluntario`
          : `${solicitudes.length} solicitudes de voluntarios`,
        descripcion: 'Acéptalas o recházalas en la sección Voluntarios.',
        enlace: `${RUTA}/voluntarios`
      });
    }

    const propuestas = this.propuestas();
    if (propuestas.length > 0) {
      alertas.push({
        clave: 'propuestas',
        prioridad: 'Media',
        titulo: propuestas.length === 1
          ? `Propuesta por revisar: ${propuestas[0].nombre}`
          : `${propuestas.length} propuestas de actividades por revisar`,
        descripcion: 'Las enviaron voluntarios o personas mayores.',
        enlace: `${RUTA}/actividades`
      });
    }

    return alertas.sort((a, b) => ORDEN_PRIORIDAD[a.prioridad] - ORDEN_PRIORIDAD[b.prioridad]);
  });

  protected readonly stats = computed<StatCard[]>(() => {
    const poblacion = this.poblacion();
    const voluntarios = this.voluntarios();
    const solicitudes = this.solicitudesVoluntarios().length;
    const alertas = this.alertas();
    const urgentes = alertas.filter((a) => a.prioridad === 'Alta').length;
    const personas = poblacion?.personas.length ?? null;

    return [
      {
        icon: 'user',
        value: personas,
        label: 'Personas mayores registradas',
        detalle: poblacion && personas ? `${poblacion.personasConIntereses} con intereses registrados` : null,
        tono: 'normal'
      },
      {
        icon: 'users',
        value: poblacion?.acompanantesActivos ?? null,
        label: 'Acompañantes activos',
        detalle: null,
        tono: 'normal'
      },
      {
        icon: 'star',
        value: voluntarios?.length ?? null,
        label: 'Voluntarios en el programa',
        detalle: solicitudes > 0 ? `${solicitudes} ${solicitudes === 1 ? 'solicitud nueva' : 'solicitudes nuevas'}` : null,
        tono: 'normal'
      },
      {
        icon: 'bell',
        value: alertas.length,
        label: 'Alertas activas',
        detalle: urgentes > 0 ? `${urgentes} ${urgentes === 1 ? 'urgente' : 'urgentes'}` : null,
        tono: urgentes > 0 ? 'alerta' : 'normal'
      }
    ];
  });

  /** Participación de las actividades ya realizadas en los últimos DIAS_PARTICIPACION días. */
  protected readonly resumenParticipacion = computed(() => {
    const hoy = fechaLocal(this.ahora());
    const realizadas = (this.participacion() ?? []).filter((a) => a.fecha <= hoy);
    const inscritos = realizadas.reduce((t, a) => t + a.inscritos, 0);
    const conRegistro = realizadas.reduce((t, a) => t + a.conRegistro, 0);
    const asistentes = realizadas.reduce((t, a) => t + a.asistentes, 0);

    return {
      actividades: realizadas.length,
      inscritos,
      asistencia: conRegistro > 0 ? Math.round((asistentes / conRegistro) * 100) : null
    };
  });

  /** Los 5 intereses más comunes, con su porcentaje respecto al más común. */
  protected readonly interesesTop = computed(() => {
    const intereses = (this.poblacion()?.intereses ?? []).slice(0, 5);
    const maximo = intereses[0]?.personas || 1;
    return intereses.map((i) => ({ ...i, porcentaje: Math.round((i.personas / maximo) * 100) }));
  });

  /** Personas que cumplen años en los próximos DIAS_CUMPLEANOS días. */
  protected readonly cumpleanos = computed<Cumpleanos[]>(() => {
    const lista: Cumpleanos[] = [];
    for (const p of this.poblacion()?.personas ?? []) {
      if (!p.fechaNacimiento) {
        continue;
      }
      const cumple = proximoCumpleanos(p.fechaNacimiento, this.ahora());
      if (cumple.dias <= DIAS_CUMPLEANOS) {
        lista.push({ idUsuario: p.idUsuario, nombre: p.nombre, ...cumple });
      }
    }

    return lista.sort((a, b) => a.dias - b.dias);
  });

  // Formatos

  /** "Hoy", "Mañana" o "jueves 2 de octubre"; "Sin fecha" si no tiene. */
  protected formatearDia(fecha: string | null): string {
    return fecha ? formatearFechaCita(fecha, this.ahora()) : 'Sin fecha';
  }

  protected cuandoCumple(c: Cumpleanos): string {
    if (c.dias === 0) return '¡Hoy!';
    if (c.dias === 1) return 'Mañana';
    return this.formatearDia(c.fecha);
  }
  // Registro y edición rápidos de actividades

  /** Fecha de hoy (YYYY-MM-DD): no se pueden registrar actividades en días pasados. */
  protected hoy(): string {
    return fechaLocal(this.ahora());
  }

  /** Nombre, fecha, hora y lugar son obligatorios para registrar o guardar una actividad. */
  protected esValida(actividad: ActividadRequest): boolean {
    return !!actividad.nombre?.trim() && !!actividad.fecha && !!actividad.hora && !!actividad.lugar?.trim();
  }

  crearActividad(): void {
    if (!this.esValida(this.nuevaActividad)) {
      return;
    }

    this.errorActividades.set(null);

    this.actividadService.crear(this.nuevaActividad).subscribe({
      next: () => {
        this.nuevaActividad = actividadVacia();
        this.cargarActividades();
      },
      error: () => {
        this.errorActividades.set('No se pudo crear la actividad');
      }
    });
  }

  /** Pasa una fila de la lista a modo edición. */
  editarActividad(actividad: Actividad): void {
    this.actividadEditandoId = actividad.idActividad;
    this.actividadEditando = {
      nombre: actividad.nombre,
      descripcion: actividad.descripcion,
      fecha: actividad.fecha,
      hora: actividad.hora,
      lugar: actividad.lugar,
      tipo: actividad.tipo,
      cupos: actividad.cupos,
      responsable: actividad.responsable,
      // Esta edición rápida no muestra la repetición: se conserva la que tenía.
      frecuenciaDias: actividad.frecuenciaDias
    };
  }

  cancelarEdicionActividad(): void {
    this.actividadEditandoId = null;
    this.actividadEditando = actividadVacia();
  }

  guardarActividad(): void {
    if (this.actividadEditandoId === null || !this.esValida(this.actividadEditando)) {
      return;
    }

    this.errorActividades.set(null);

    this.actividadService.actualizar(this.actividadEditandoId, this.actividadEditando).subscribe({
      next: () => {
        this.cancelarEdicionActividad();
        this.cargarActividades();
      },
      error: () => {
        this.errorActividades.set('No se pudo actualizar la actividad');
      }
    });
  }

  eliminarActividad(actividad: Actividad): void {
    this.errorActividades.set(null);

    this.actividadService.eliminar(actividad.idActividad).subscribe({
      next: () => this.cargarActividades(),
      error: () => {
        this.errorActividades.set('No se pudo eliminar la actividad');
      }
    });
  }
}
