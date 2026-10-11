import {
  Component,
  OnDestroy,
  OnInit,
  computed,
  signal
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es-CO';
import { catchError, forkJoin, of } from 'rxjs';

import { Icono } from '../../../shared/icono/icono';
import {
  ActividadService,
  Actividad,
  separarPorFecha
} from '../../../core/actividades/actividad.service';
import {
  AcompananteService,
  EmergenciaReciente,
  MedicamentoSeguimiento,
  PersonaMayorAcompanada,
  SolicitudAcompanamiento
} from '../../../core/acompanantes/acompanante.service';
import { AuthService } from '../../../core/auth/auth.service';
import { alCambiar } from '../../../core/tiempo-real/tiempo-real.service';
import { formatearHora, tomasDeHoy } from '../../../core/medicamentos/medicamento.service';
import {
  CitaMedica,
  formatearFechaCita,
  lugarDeCita,
  momentoDeCita
} from '../../../core/citas-medicas/cita-medica.service';
import { SignoVitalResponse } from '../../../core/signos-vitales/signos-vitales.services';
import { NOMBRE_INDICADOR, indicadoresFueraDeRango } from '../../../core/signos-vitales/rangos';
import { AltoPantalla } from '../../../shared/alto-pantalla/alto-pantalla';
import { diasDesde, fechaLocal, haceDias } from '../../../core/fechas/fechas';
import { iniciales } from '../../../core/formato/formato';

registerLocaleData(localeEs);

/** Medicamentos, citas y última medición de una persona mayor. */
interface DatosPersona {
  medicamentos: MedicamentoSeguimiento[];
  citas: CitaMedica[];
  ultimoSigno: SignoVitalResponse | null;
}

/** Estado de un elemento de la agenda de hoy. */
type EstadoEvento = 'hecho' | 'pasado' | 'atrasado' | 'siguiente' | 'pendiente';

/** Toma de medicamento o cita médica de hoy de alguna de sus personas mayores. */
interface EventoAgenda {
  clave: string;
  tipo: 'medicamento' | 'cita';
  momento: Date;
  titulo: string;
  detalle: string | null;
  persona: PersonaMayorAcompanada;
  estado: EstadoEvento;
}

/** Resumen de cómo está una persona mayor (para su tarjeta). */
interface ResumenPersona {
  tomasHechas: number;
  tomasTotal: number;
  sinTomar: number;
  ultimaMedicion: { hace: string; normal: boolean } | null;
  proximaCita: string | null;
}

type Prioridad = 'Emergencia' | 'Alta' | 'Media' | 'Baja';

/** Algo de una persona mayor que el acompañante debería revisar. */
interface Alerta {
  clave: string;
  prioridad: Prioridad;
  persona: PersonaMayorAcompanada;
  texto: string;
}

/** Cita médica próxima con el nombre de la persona. */
interface CitaProxima {
  clave: string;
  cita: CitaMedica;
  persona: PersonaMayorAcompanada;
}

const MINUTO = 60_000;
const DIA = 24 * 60 * MINUTO;

/** Pasados estos días sin medición de signos vitales, se avisa. */
const DIAS_SIN_MEDICION = 30;

const ORDEN_PRIORIDAD: Record<Prioridad, number> = { Emergencia: 0, Alta: 1, Media: 2, Baja: 3 };

const DATOS_VACIOS: DatosPersona = { medicamentos: [], citas: [], ultimoSigno: null };

/**
 * Inicio del panel del acompañante:
 *  1. Saludo y acceso para agregar una persona mayor.
 *  2. Solicitudes de acompañamiento por responder.
 *  3. Columna principal: sus personas mayores con un resumen de cada una
 *     y la agenda de hoy (tomas de medicamentos y citas de todas).
 *  4. Columna lateral: alertas (primero las emergencias de las últimas 24
 *     horas) calculadas con sus datos, próximas citas
 *     médicas y próximas actividades.
 * Todo sale del backend (seguimiento del acompañante y actividades).
 */
@Component({
  selector: 'app-panel-acompanante',
  imports: [Icono, DatePipe, RouterLink],
  templateUrl: './acompanante.html',
  styleUrl: './acompanante.css',
  hostDirectives: [AltoPantalla]
})
export class PanelAcompanante implements OnInit, OnDestroy {

  protected readonly nombreUsuario: string;
  protected readonly ruta = '/panel/acompanante';

  /** Hora actual; se refresca cada minuto para que la agenda avance sola. */
  protected readonly ahora = signal(new Date());
  private intervaloReloj?: ReturnType<typeof setInterval>;

  /** Personas mayores con vínculo aceptado. */
  protected readonly personasMayores = signal<PersonaMayorAcompanada[]>([]);
  /** Datos de seguimiento de cada persona mayor, por idUsuario. */
  private readonly datos = signal<Record<number, DatosPersona>>({});
  protected readonly solicitudes = signal<SolicitudAcompanamiento[]>([]);
  /** Emergencias de las últimas 24 horas, la más reciente primero. */
  protected readonly emergencias = signal<EmergenciaReciente[]>([]);
  /** Próximas actividades de las organizaciones de sus personas mayores. */
  protected readonly actividades = signal<Actividad[]>([]);

  protected readonly cargandoPersonas = signal(true);
  protected readonly cargandoDatos = signal(true);
  protected readonly cargandoActividades = signal(true);

  protected readonly formatearHora = formatearHora;

  constructor(
    private actividadService: ActividadService,
    private acompananteService: AcompananteService,
    private authService: AuthService
  ) {
    this.nombreUsuario = this.authService.getNombreUsuario();

    // Se recarga cuando otro usuario cambia actividades, vínculos o datos de salud.
    alCambiar(['actividades'], () => this.cargarActividades());
    alCambiar(['acompanamientos', 'usuarios'], () => this.cargarPersonasMayores());
    alCambiar(['medicamentos', 'citas-medicas', 'signos-vitales'], () => this.cargarDatos(this.personasMayores()));
    // El botón de emergencia publica "notificaciones": el aviso aparece sin recargar.
    alCambiar(['notificaciones', 'acompanamientos'], () => this.cargarEmergencias());
  }

  ngOnInit(): void {
    this.intervaloReloj = setInterval(() => this.ahora.set(new Date()), MINUTO);

    this.cargarActividades();
    this.cargarPersonasMayores();
    this.cargarEmergencias();
  }

  ngOnDestroy(): void {
    clearInterval(this.intervaloReloj);
  }

  /**
   * En pantallas grandes la página ocupa justo el alto de la ventana (sin
   * scroll): se calcula cuánto queda debajo de la barra superior y se pasa al
   * CSS en --alto-disponible. En celulares el CSS no la usa.
   */
  private cargarActividades(): void {
    this.actividadService.listar().subscribe({
      // Solo las próximas, de la más cercana a la más lejana.
      next: (actividades) => this.actividades.set(separarPorFecha(actividades).proximas.slice(0, 4)),
      complete: () => this.cargandoActividades.set(false),
      error: () => this.cargandoActividades.set(false)
    });
  }

  private cargarEmergencias(): void {
    this.acompananteService.obtenerEmergenciasRecientes().subscribe({
      next: (emergencias) => this.emergencias.set(emergencias),
      error: () => this.emergencias.set([])
    });
  }

  /** Personas mayores, sus datos de seguimiento y las solicitudes pendientes. */
  private cargarPersonasMayores(): void {
    this.acompananteService.obtenerPersonasMayores().subscribe({
      next: (personas) => {
        this.personasMayores.set(personas);
        this.cargandoPersonas.set(false);
        this.cargarDatos(personas);
      },
      error: () => {
        this.cargandoPersonas.set(false);
        this.cargandoDatos.set(false);
      }
    });

    this.acompananteService.obtenerSolicitudes().subscribe({
      next: (solicitudes) => this.solicitudes.set(solicitudes),
      error: () => this.solicitudes.set([])
    });
  }

  /**
   * Pide en paralelo medicamentos, citas y signos vitales de cada persona.
   * Si una parte falla, esa parte queda vacía y el resto se muestra igual.
   */
  private cargarDatos(personas: PersonaMayorAcompanada[]): void {
    if (personas.length === 0) {
      this.datos.set({});
      this.cargandoDatos.set(false);
      return;
    }

    forkJoin(
      personas.map((p) =>
        forkJoin({
          medicamentos: this.acompananteService.obtenerMedicamentosSeguimiento(p.idUsuario)
            .pipe(catchError(() => of([] as MedicamentoSeguimiento[]))),
          citas: this.acompananteService.obtenerCitasMedicasSeguimiento(p.idUsuario)
            .pipe(catchError(() => of([] as CitaMedica[]))),
          signos: this.acompananteService.obtenerSignosVitalesSeguimiento(p.idUsuario)
            .pipe(catchError(() => of([] as SignoVitalResponse[])))
        })
      )
    ).subscribe((respuestas) => {
      const datos: Record<number, DatosPersona> = {};
      respuestas.forEach((r, i) => {
        // Los signos llegan del más reciente al más antiguo.
        datos[personas[i].idUsuario] = { medicamentos: r.medicamentos, citas: r.citas, ultimoSigno: r.signos[0] ?? null };
      });
      this.datos.set(datos);
      this.cargandoDatos.set(false);
    });
  }

  private datosDe(persona: PersonaMayorAcompanada): DatosPersona {
    return this.datos()[persona.idUsuario] ?? DATOS_VACIOS;
  }

  /** Tomas de medicamentos y citas de hoy de todas sus personas mayores, en orden de hora. */
  protected readonly agendaHoy = computed<EventoAgenda[]>(() => {
    const ahora = this.ahora();
    const hoy = fechaLocal(ahora);
    const eventos: EventoAgenda[] = [];

    for (const persona of this.personasMayores()) {
      const { medicamentos, citas } = this.datosDe(persona);

      for (const med of medicamentos) {
        for (const toma of tomasDeHoy(med, ahora)) {
          eventos.push({
            clave: `${persona.idUsuario}-${toma.clave}`,
            tipo: 'medicamento',
            momento: toma.momento,
            titulo: med.nombre,
            detalle: med.dosis,
            persona,
            estado: toma.estado
          });
        }
      }

      for (const cita of citas) {
        if (cita.fecha !== hoy) {
          continue;
        }
        const momento = momentoDeCita(cita);
        eventos.push({
          clave: `${persona.idUsuario}-c${cita.idCita}`,
          tipo: 'cita',
          momento,
          titulo: cita.titulo,
          detalle: lugarDeCita(cita),
          persona,
          estado: momento < ahora ? 'pasado' : 'pendiente'
        });
      }
    }

    eventos.sort((a, b) => a.momento.getTime() - b.momento.getTime());

    // El primer pendiente es "lo siguiente".
    const siguiente = eventos.find((e) => e.estado === 'pendiente');
    if (siguiente) {
      siguiente.estado = 'siguiente';
    }

    return eventos;
  });

  /** Resumen de cada persona mayor, por idUsuario. */
  protected readonly resumenes = computed<Record<number, ResumenPersona>>(() => {
    const ahora = this.ahora();
    const resumenes: Record<number, ResumenPersona> = {};

    for (const persona of this.personasMayores()) {
      const { medicamentos, citas, ultimoSigno } = this.datosDe(persona);
      const tomas = medicamentos.flatMap((m) => tomasDeHoy(m, ahora));
      const cita = citas
        .filter((c) => momentoDeCita(c) > ahora)
        .sort((a, b) => momentoDeCita(a).getTime() - momentoDeCita(b).getTime())[0];

      resumenes[persona.idUsuario] = {
        tomasHechas: tomas.filter((t) => t.estado === 'hecho').length,
        tomasTotal: tomas.length,
        sinTomar: tomas.filter((t) => t.estado === 'atrasado').length,
        ultimaMedicion: ultimoSigno
          ? { hace: haceDias(ultimoSigno.fechaHora, this.ahora()), normal: indicadoresFueraDeRango(ultimoSigno).length === 0 }
          : null,
        proximaCita: cita ? this.cuandoCita(cita) : null
      };
    }

    return resumenes;
  });

  /** La más reciente emergencia de cada persona (si activó el botón varias veces, cuenta una). */
  protected readonly emergenciasPorPersona = computed(() => {
    const vistas = new Set<number>();
    return this.emergencias().filter((e) => !vistas.has(e.idPersonaMayor) && !!vistas.add(e.idPersonaMayor));
  });

  /**
   * Alertas calculadas con los datos de seguimiento:
   *  - Emergencia: activó el botón de emergencia en las últimas 24 horas.
   *  - Alta: medicamentos sin tomar o última medición fuera de rango.
   *  - Media: cita médica hoy o mañana.
   *  - Baja: sin mediciones de signos vitales hace más de DIAS_SIN_MEDICION días.
   */
  protected readonly alertas = computed<Alerta[]>(() => {
    const ahora = this.ahora();
    const hoy = fechaLocal(ahora);
    const manana = fechaLocal(new Date(ahora.getTime() + DIA));
    const alertas: Alerta[] = [];

    for (const e of this.emergenciasPorPersona()) {
      const persona = this.personasMayores().find((p) => p.idUsuario === e.idPersonaMayor)
        ?? { idUsuario: e.idPersonaMayor, nombre: e.nombre ?? 'Persona mayor', celular: '' };
      alertas.push({
        clave: `emergencia-${e.idEmergencia}`,
        prioridad: 'Emergencia',
        persona,
        texto: `Activó el botón de emergencia ${this.haceTiempo(e.fechaHora)}. Verifica que se encuentre bien.`
      });
    }

    for (const persona of this.personasMayores()) {
      const { medicamentos, citas, ultimoSigno } = this.datosDe(persona);
      const id = persona.idUsuario;

      const sinTomar = medicamentos
        .filter((m) => tomasDeHoy(m, ahora).some((t) => t.estado === 'atrasado'))
        .map((m) => m.nombre);
      if (sinTomar.length > 0) {
        alertas.push({
          clave: `${id}-medicamentos`,
          prioridad: 'Alta',
          persona,
          texto: `No ha tomado: ${sinTomar.join(', ')}.`
        });
      }

      if (ultimoSigno) {
        const fuera = indicadoresFueraDeRango(ultimoSigno).map((i) => NOMBRE_INDICADOR[i]);
        if (fuera.length > 0) {
          alertas.push({
            clave: `${id}-signos`,
            prioridad: 'Alta',
            persona,
            texto: `${fuera.join(', ')} fuera de lo habitual en la última medición (${haceDias(ultimoSigno.fechaHora, this.ahora())}).`
          });
        }
      }

      for (const cita of citas) {
        if ((cita.fecha === hoy && momentoDeCita(cita) > ahora) || cita.fecha === manana) {
          alertas.push({
            clave: `${id}-cita-${cita.idCita}`,
            prioridad: 'Media',
            persona,
            texto: `Cita médica ${this.cuandoCita(cita).toLowerCase()}: ${cita.titulo}.`
          });
        }
      }

      const diasSinMedir = ultimoSigno ? diasDesde(ultimoSigno.fechaHora, this.ahora()) : null;
      if (diasSinMedir === null || diasSinMedir > DIAS_SIN_MEDICION) {
        alertas.push({
          clave: `${id}-sin-medicion`,
          prioridad: 'Baja',
          persona,
          texto: diasSinMedir === null
            ? 'Aún no tiene signos vitales registrados.'
            : `No se le miden los signos vitales hace ${diasSinMedir} días.`
        });
      }
    }

    return alertas.sort((a, b) => ORDEN_PRIORIDAD[a.prioridad] - ORDEN_PRIORIDAD[b.prioridad]);
  });

  /** Citas médicas de los próximos días (las de hoy ya están en la agenda). */
  protected readonly proximasCitas = computed<CitaProxima[]>(() => {
    const hoy = fechaLocal(this.ahora());

    return this.personasMayores()
      .flatMap((persona) =>
        this.datosDe(persona).citas
          .filter((c) => c.fecha > hoy)
          .map((cita) => ({ clave: `${persona.idUsuario}-${cita.idCita}`, cita, persona })))
      .sort((a, b) => momentoDeCita(a.cita).getTime() - momentoDeCita(b.cita).getTime())
      .slice(0, 4);
  });

  /** "Hoy, 9:00 a. m.", "Mañana, ..." o "jueves 2 de octubre, ...". */
  protected cuandoCita(cita: CitaMedica): string {
    return `${formatearFechaCita(cita.fecha, this.ahora())}, ${formatearHora(cita.hora)}`;
  }

  /** "Hoy", "Mañana" o "jueves 2 de octubre"; "Sin fecha" si no tiene. */
  protected formatearDia(fecha: string | null): string {
    return fecha ? formatearFechaCita(fecha, this.ahora()) : 'Sin fecha';
  }

  protected horaDe(fecha: Date): string {
    return fecha.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' });
  }

  protected readonly iniciales = iniciales;

  /** "Rosa Elvira Díaz" -> "Rosa". */
  protected primerNombre(nombre: string): string {
    return nombre.trim().split(/\s+/)[0] ?? nombre;
  }

  /** "hace un momento", "hace 5 minutos", "hace 3 horas (10:30 a. m.)". */
  protected haceTiempo(fechaHora: string): string {
    const fecha = new Date(fechaHora);
    const minutos = Math.max(0, Math.round((this.ahora().getTime() - fecha.getTime()) / MINUTO));
    const hora = this.horaDe(fecha);

    if (minutos < 1) return `hace un momento (${hora})`;
    if (minutos < 60) return `hace ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'} (${hora})`;
    const horas = Math.floor(minutos / 60);
    return `hace ${horas} ${horas === 1 ? 'hora' : 'horas'} (${hora})`;
  }
}
