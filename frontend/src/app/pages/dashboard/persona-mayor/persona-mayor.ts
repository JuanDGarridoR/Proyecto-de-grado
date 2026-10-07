import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  signal
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es-CO';

import { Icon } from '../../../shared/icon/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { ActividadService, ActividadDisponible } from '../../../core/actividades/actividad.service';
import { AcompananteService, Acompanante } from '../../../core/acompanantes/acompanante.service';
import { EmergenciaService } from '../../../core/emergencia/emergencia.service';
import { OrganizacionService, OrganizacionSolicitud } from '../../../core/organizacion/organizacion.service';
import { PersonaMayorService } from '../../../core/persona-mayor/persona-mayor.service';
import { alCambiar } from '../../../core/tiempo-real/tiempo-real.service';
import {
  SignosVitalesService,
  SignoVitalResponse
} from '../../../core/signos-vitales/signos-vitales.services';
import { RANGOS, evaluarIndicador } from '../../../core/signos-vitales/rangos';
import {
  MedicamentoService,
  Medicamento,
  formatearHora,
  formatearProximaToma,
  tomasDeHoy
} from '../../../core/medicamentos/medicamento.service';
import {
  CitaMedicaService,
  CitaMedica,
  formatearConsultorio,
  momentoDeCita
} from '../../../core/citas-medicas/cita-medica.service';
import {
  CondicionSaludService,
  CondicionSaludRegistrada,
  TipoCondicionSalud
} from '../../../core/condiciones-salud/condicion-salud.service';

registerLocaleData(localeEs);

/** Estado de un elemento de la agenda de hoy. */
type EstadoEvento =
  | 'hecho'      // medicamento ya tomado
  | 'pasado'     // actividad o cita que ya ocurrió
  | 'atrasado'   // medicamento cuya hora ya pasó y no se ha tomado
  | 'siguiente'  // lo próximo que viene
  | 'pendiente'; // más tarde hoy

type TipoEvento = 'medicamento' | 'actividad' | 'cita';

/** Elemento de la agenda de hoy: una toma de medicamento, una actividad o una cita médica. */
interface EventoAgenda {
  clave: string;
  tipo: TipoEvento;
  momento: Date | null;   // null = actividad de hoy sin hora
  titulo: string;
  detalle: string | null;
  estado: EstadoEvento;
  enlace: string;
}

/** Actividad o cita médica de los próximos días. */
interface EventoProximo {
  clave: string;
  tipo: 'actividad' | 'cita';
  fecha: string;
  hora: string | null;
  titulo: string;
  detalle: string | null;
  enlace: string;
}

/** Algo que la persona mayor tiene por atender (aparece arriba de todo). */
interface Pendiente {
  icono: string;
  texto: string;
  accion: string;
  enlace: string;
  urgente: boolean;
}

/** Estado de un signo vital respecto a rangos de referencia generales. */
interface SignoResumen {
  etiqueta: string;
  valor: string;
  unidad: string;
  normal: boolean;
}

/** Grupo de datos de salud (enfermedades, alergias o discapacidades). */
interface GrupoCondiciones {
  tipo: TipoCondicionSalud;
  titulo: string;
  icono: string;
  nombres: string[];
}

const MINUTO = 60_000;
const HORA = 60 * MINUTO;
const DIA = 24 * HORA;

/** El cumpleaños se anuncia en el saludo cuando faltan estos días o menos. */
const DIAS_AVISO_CUMPLEANOS = 30;

const RUTA = '/panel/persona-mayor';

/** Cómo se muestra cada grupo de datos de salud, en orden de importancia. */
const GRUPOS_CONDICIONES: Omit<GrupoCondiciones, 'nombres'>[] = [
  { tipo: 'ALERGIA', titulo: 'Alergias', icono: 'alert-circle' },
  { tipo: 'ENFERMEDAD', titulo: 'Enfermedades', icono: 'stethoscope' },
  { tipo: 'DISCAPACIDAD', titulo: 'Discapacidades', icono: 'user' }
];

/**
 * Inicio de la persona mayor, en forma de "agenda del día":
 *  1. Saludo (con aviso de cumpleaños) y botón de emergencia, siempre arriba.
 *  2. Pendientes: medicamentos sin tomar y solicitudes por responder.
 *  3. Columna principal: tu día de hoy (medicamentos, actividades y citas),
 *     los próximos días y actividades a las que se puede inscribir.
 *  4. Columna lateral: acompañantes, salud, datos de salud y organizaciones.
 */
@Component({
  selector: 'app-persona-mayor-dashboard',
  imports: [Icon, DatePipe, RouterLink],
  templateUrl: './persona-mayor.html',
  styleUrl: './persona-mayor.css',
})
export class PersonaMayorDashboard implements OnInit, AfterViewInit, OnDestroy {

  protected readonly nombreUsuario: string;
  protected readonly ruta = RUTA;

  /**
   * Hora actual. Se refresca cada minuto para que la agenda cambie sola: lo
   * que pasó se atenúa y "Siguiente" avanza.
   */
  protected readonly ahora = signal(new Date());
  private intervaloReloj?: ReturnType<typeof setInterval>;

  protected readonly medicamentos = signal<Medicamento[]>([]);
  /** Todas las actividades visibles; las inscritas van a la agenda y el resto a sugerencias. */
  protected readonly actividades = signal<ActividadDisponible[]>([]);
  protected readonly citas = signal<CitaMedica[]>([]);
  protected readonly acompanantes = signal<Acompanante[]>([]);
  protected readonly organizaciones = signal<OrganizacionSolicitud[]>([]);
  protected readonly solicitudesAcompanantes = signal<Acompanante[]>([]);
  protected readonly solicitudesOrganizaciones = signal<OrganizacionSolicitud[]>([]);
  protected readonly condiciones = signal<CondicionSaludRegistrada[]>([]);
  protected readonly fechaNacimiento = signal<string | null>(null);
  protected readonly ultimoSignoVital = signal<SignoVitalResponse | null>(null);

  protected readonly cargandoAgenda = signal(true);
  protected readonly cargandoAcompanantes = signal(true);
  protected readonly cargandoOrganizaciones = signal(true);
  protected readonly cargandoCondiciones = signal(true);
  protected readonly cargandoSignos = signal(true);
  private pendientesAgenda = 3; // medicamentos, actividades y citas

  protected readonly formatearHora = formatearHora;
  protected readonly formatearProximaToma = formatearProximaToma;

  // Botón de emergencia
  protected readonly mostrandoConfirmacionEmergencia = signal(false);
  protected readonly enviandoEmergencia = signal(false);
  protected readonly mensajeEmergencia = signal<string | null>(null);
  protected readonly errorEmergencia = signal<string | null>(null);

  constructor(
    private elemento: ElementRef<HTMLElement>,
    private authService: AuthService,
    private actividadService: ActividadService,
    private acompananteService: AcompananteService,
    private citaMedicaService: CitaMedicaService,
    private condicionSaludService: CondicionSaludService,
    private emergenciaService: EmergenciaService,
    private medicamentoService: MedicamentoService,
    private organizacionService: OrganizacionService,
    private personaMayorService: PersonaMayorService,
    private signosVitalesService: SignosVitalesService
  ) {
    this.nombreUsuario = this.authService.getNombreUsuario();

    alCambiar(['medicamentos'], () => this.cargarMedicamentos());
    alCambiar(['actividades'], () => this.cargarActividades());
    alCambiar(['citas-medicas'], () => this.cargarCitas());
    alCambiar(['acompanamientos', 'usuarios'], () => this.cargarAcompanantes());
    alCambiar(['organizaciones', 'usuarios'], () => this.cargarOrganizaciones());
    alCambiar(['condiciones-salud'], () => this.cargarCondiciones());
    alCambiar(['usuarios'], () => this.cargarPerfil());
    alCambiar(['signos-vitales'], () => this.cargarSignosVitales());
  }

  ngOnInit(): void {
    this.intervaloReloj = setInterval(() => this.ahora.set(new Date()), MINUTO);

    this.cargarMedicamentos();
    this.cargarActividades();
    this.cargarCitas();
    this.cargarAcompanantes();
    this.cargarOrganizaciones();
    this.cargarCondiciones();
    this.cargarPerfil();
    this.cargarSignosVitales();
  }

  ngAfterViewInit(): void {
    this.ajustarAltoPantalla();
  }

  ngOnDestroy(): void {
    clearInterval(this.intervaloReloj);
  }

  /**
   * En pantallas grandes la página ocupa justo el alto de la ventana (sin
   * scroll): se calcula cuánto queda debajo de la barra superior y se pasa al
   * CSS en --alto-disponible. Si algo no cabe, se desplaza dentro de su
   * tarjeta. En celulares el CSS no usa la variable y la página se desplaza
   * normalmente.
   */
  @HostListener('window:resize')
  protected ajustarAltoPantalla(): void {
    const host = this.elemento.nativeElement;
    const contenedor = host.parentElement;
    const inicio = host.getBoundingClientRect().top + window.scrollY;
    const margenInferior = contenedor ? parseFloat(getComputedStyle(contenedor).paddingBottom) || 0 : 0;

    host.style.setProperty('--alto-disponible', `${Math.floor(window.innerHeight - inicio - margenInferior)}px`);
  }

  private cargarMedicamentos(): void {
    this.medicamentoService.listar().subscribe({
      next: (medicamentos) => this.medicamentos.set(medicamentos),
      complete: () => this.terminarCargaAgenda(),
      error: () => this.terminarCargaAgenda()
    });
  }

  private cargarActividades(): void {
    this.actividadService.listarDisponibles().subscribe({
      next: (actividades) => this.actividades.set(actividades),
      complete: () => this.terminarCargaAgenda(),
      error: () => this.terminarCargaAgenda()
    });
  }

  private cargarCitas(): void {
    this.citaMedicaService.listar().subscribe({
      next: (citas) => this.citas.set(citas),
      complete: () => this.terminarCargaAgenda(),
      error: () => this.terminarCargaAgenda()
    });
  }

  /** Acompañantes vinculados y solicitudes que aún no responde. */
  private cargarAcompanantes(): void {
    this.acompananteService.obtenerAcompanantes().subscribe({
      next: (acompanantes) => this.acompanantes.set(acompanantes),
      complete: () => this.cargandoAcompanantes.set(false),
      error: () => this.cargandoAcompanantes.set(false)
    });

    this.acompananteService.obtenerSolicitudesDeAcompanantes().subscribe({
      next: (solicitudes) => this.solicitudesAcompanantes.set(solicitudes),
      error: () => this.solicitudesAcompanantes.set([])
    });
  }

  /** Organizaciones vinculadas y solicitudes que aún no responde. */
  private cargarOrganizaciones(): void {
    this.organizacionService.obtenerOrganizaciones().subscribe({
      next: (organizaciones) => this.organizaciones.set(organizaciones),
      complete: () => this.cargandoOrganizaciones.set(false),
      error: () => this.cargandoOrganizaciones.set(false)
    });

    this.organizacionService.obtenerSolicitudesOrganizaciones().subscribe({
      next: (solicitudes) => this.solicitudesOrganizaciones.set(solicitudes),
      error: () => this.solicitudesOrganizaciones.set([])
    });
  }

  private cargarCondiciones(): void {
    this.condicionSaludService.listar().subscribe({
      next: (condiciones) => this.condiciones.set(condiciones),
      complete: () => this.cargandoCondiciones.set(false),
      error: () => this.cargandoCondiciones.set(false)
    });
  }

  /** Del perfil solo se usa la fecha de nacimiento (aviso de cumpleaños). */
  private cargarPerfil(): void {
    this.personaMayorService.obtenerInformacion().subscribe({
      next: (perfil) => this.fechaNacimiento.set(perfil.fechaNacimiento || null),
      error: () => this.fechaNacimiento.set(null)
    });
  }

  /** La lista llega de la más reciente a la más antigua: basta con la primera. */
  private cargarSignosVitales(): void {
    this.signosVitalesService.listarPropios().subscribe({
      next: (registros) => this.ultimoSignoVital.set(registros[0] ?? null),
      complete: () => this.cargandoSignos.set(false),
      error: () => this.cargandoSignos.set(false)
    });
  }

  /** La agenda deja de mostrar "cargando" cuando llegan medicamentos, actividades y citas. */
  private terminarCargaAgenda(): void {
    this.pendientesAgenda--;
    if (this.pendientesAgenda <= 0) {
      this.cargandoAgenda.set(false);
    }
  }

  /** Actividades en las que está inscrita. */
  private readonly inscritas = computed(() => this.actividades().filter((a) => a.inscrito));

  /**
   * Agenda de hoy: tomas de medicamentos, actividades y citas médicas en
   * orden de hora, cada una con su estado (ver EstadoEvento).
   */
  protected readonly agendaHoy = computed<EventoAgenda[]>(() => {
    const ahora = this.ahora();
    const hoy = this.fechaLocal(ahora);

    const eventos: Omit<EventoAgenda, 'estado'>[] = [];
    const estados = new Map<string, EstadoEvento>();

    // Medicamentos: la toma ya hecha hoy y las que faltan hoy.
    for (const med of this.medicamentos()) {
      for (const toma of tomasDeHoy(med, ahora)) {
        eventos.push({
          clave: toma.clave,
          tipo: 'medicamento',
          momento: toma.momento,
          titulo: med.nombre,
          detalle: med.dosis || null,
          enlace: `${RUTA}/recordatorios`
        });
        estados.set(toma.clave, toma.estado);
      }
    }

    // Actividades de hoy en las que está inscrita.
    for (const act of this.inscritas()) {
      if (act.fecha !== hoy) {
        continue;
      }

      const momento = act.hora ? this.combinar(act.fecha, act.hora) : null;
      const clave = `a${act.idActividad}`;
      eventos.push({
        clave,
        tipo: 'actividad',
        momento,
        titulo: act.nombre,
        detalle: act.lugar,
        enlace: `${RUTA}/actividades`
      });
      estados.set(clave, momento && momento < ahora ? 'pasado' : 'pendiente');
    }

    // Citas médicas de hoy.
    for (const cita of this.citas()) {
      if (cita.fecha !== hoy) {
        continue;
      }

      const momento = momentoDeCita(cita);
      const clave = `c${cita.idCita}`;
      eventos.push({
        clave,
        tipo: 'cita',
        momento,
        titulo: cita.titulo,
        detalle: this.lugarDeCita(cita),
        enlace: `${RUTA}/citas-medicas`
      });
      estados.set(clave, momento < ahora ? 'pasado' : 'pendiente');
    }

    // Orden por hora; las que no tienen hora van al final.
    eventos.sort((a, b) =>
      (a.momento?.getTime() ?? Number.MAX_SAFE_INTEGER) - (b.momento?.getTime() ?? Number.MAX_SAFE_INTEGER)
    );

    // El primer pendiente es "lo siguiente".
    const siguiente = eventos.find((e) => estados.get(e.clave) === 'pendiente');
    if (siguiente) {
      estados.set(siguiente.clave, 'siguiente');
    }

    return eventos.map((e) => ({ ...e, estado: estados.get(e.clave)! }));
  });

  /** Tomas de medicamentos de hoy: cuántas lleva y cuántas son en total. */
  protected readonly tomasHoy = computed(() => {
    const tomas = this.agendaHoy().filter((e) => e.tipo === 'medicamento');
    const hechas = tomas.filter((e) => e.estado === 'hecho').length;
    return {
      hechas,
      total: tomas.length,
      porcentaje: tomas.length ? Math.round((hechas / tomas.length) * 100) : 0
    };
  });

  /** Lo que conviene atender ya: tomas atrasadas y solicitudes sin responder. */
  protected readonly pendientes = computed<Pendiente[]>(() => {
    const lista: Pendiente[] = [];

    const atrasados = this.agendaHoy().filter((e) => e.estado === 'atrasado').length;
    if (atrasados > 0) {
      lista.push({
        icono: 'pill',
        texto: atrasados === 1
          ? 'Tienes 1 medicamento sin tomar.'
          : `Tienes ${atrasados} medicamentos sin tomar.`,
        accion: 'Ver recordatorios',
        enlace: `${RUTA}/recordatorios`,
        urgente: true
      });
    }

    const deAcompanantes = this.solicitudesAcompanantes().length;
    if (deAcompanantes > 0) {
      lista.push({
        icono: 'users',
        texto: deAcompanantes === 1
          ? `${this.solicitudesAcompanantes()[0].nombre} quiere ser tu acompañante.`
          : `${deAcompanantes} personas quieren ser tus acompañantes.`,
        accion: 'Responder',
        enlace: `${RUTA}/contactos`,
        urgente: false
      });
    }

    const deOrganizaciones = this.solicitudesOrganizaciones().length;
    if (deOrganizaciones > 0) {
      lista.push({
        icono: 'building',
        texto: deOrganizaciones === 1
          ? `${this.solicitudesOrganizaciones()[0].nombre} te invitó a unirte.`
          : `${deOrganizaciones} organizaciones te invitaron a unirte.`,
        accion: 'Responder',
        enlace: `${RUTA}/organizaciones`,
        urgente: false
      });
    }

    return lista;
  });

  /** Actividades confirmadas y citas médicas de los próximos días (después de hoy). */
  protected readonly proximosDias = computed<EventoProximo[]>(() => {
    const hoy = this.fechaLocal(this.ahora());

    const actividades: EventoProximo[] = this.inscritas()
      .filter((a) => a.fecha && a.fecha > hoy)
      .map((a) => ({
        clave: `a${a.idActividad}`,
        tipo: 'actividad',
        fecha: a.fecha!,
        hora: a.hora,
        titulo: a.nombre,
        detalle: a.lugar,
        enlace: `${RUTA}/actividades`
      }));

    const citas: EventoProximo[] = this.citas()
      .filter((c) => c.fecha > hoy)
      .map((c) => ({
        clave: `c${c.idCita}`,
        tipo: 'cita',
        fecha: c.fecha,
        hora: c.hora,
        titulo: c.titulo,
        detalle: this.lugarDeCita(c),
        enlace: `${RUTA}/citas-medicas`
      }));

    return [...actividades, ...citas]
      .sort((a, b) => a.fecha.localeCompare(b.fecha) || (a.hora ?? '').localeCompare(b.hora ?? ''))
      .slice(0, 4);
  });

  /** Si hoy no hay nada: lo próximo que viene (medicamento, actividad o cita). */
  protected readonly loProximo = computed(() => {
    const med = [...this.medicamentos()]
      .filter((m) => m.activo !== false && !!m.proximaToma)
      .sort((a, b) => new Date(a.proximaToma).getTime() - new Date(b.proximaToma).getTime())[0];

    const evento = this.proximosDias()[0];

    const momentoMed = med ? new Date(med.proximaToma).getTime() : Infinity;
    const momentoEvento = evento ? this.combinar(evento.fecha, evento.hora ?? '00:00').getTime() : Infinity;

    if (momentoMed === Infinity && momentoEvento === Infinity) {
      return null;
    }

    return momentoMed <= momentoEvento
      ? { titulo: med!.nombre, cuando: formatearProximaToma(med!.proximaToma) }
      : { titulo: evento!.titulo, cuando: this.formatearDia(evento!.fecha) + (evento!.hora ? `, ${formatearHora(evento!.hora)}` : '') };
  });

  /** Actividades de hoy en adelante a las que aún no se ha inscrito. */
  protected readonly actividadesSugeridas = computed(() => {
    const hoy = this.fechaLocal(this.ahora());
    return this.actividades()
      .filter((a) => !a.inscrito && a.fecha && a.fecha >= hoy)
      .sort((a, b) =>
        (a.fecha ?? '').localeCompare(b.fecha ?? '') || (a.hora ?? '').localeCompare(b.hora ?? ''))
      .slice(0, 3);
  });

  /** Días que faltan para el cumpleaños y la edad que cumplirá; null si está lejos o no hay fecha. */
  protected readonly cumpleanos = computed(() => {
    const fecha = this.fechaNacimiento();
    if (!fecha) {
      return null;
    }

    const [anioNacimiento, mes, dia] = fecha.split('-').map(Number);
    const hoy = this.inicioDelDia(this.ahora());

    let anio = hoy.getFullYear();
    let proximo = new Date(anio, mes - 1, dia);
    if (proximo < hoy) {
      anio++;
      proximo = new Date(anio, mes - 1, dia);
    }

    const dias = Math.round((proximo.getTime() - hoy.getTime()) / DIA);
    return dias <= DIAS_AVISO_CUMPLEANOS ? { dias, edad: anio - anioNacimiento } : null;
  });

  /** Datos de salud agrupados, sin los grupos vacíos. */
  protected readonly gruposCondiciones = computed<GrupoCondiciones[]>(() =>
    GRUPOS_CONDICIONES
      .map((grupo) => ({
        ...grupo,
        nombres: this.condiciones()
          .filter((c) => c.tipo === grupo.tipo)
          .map((c) => (c.esOtra && c.detalle ? c.detalle : c.nombre))
      }))
      .filter((grupo) => grupo.nombres.length > 0)
  );

  /**
   * Resumen de la última medición. Los rangos de referencia están en
   * core/signos-vitales/rangos.ts y son los mismos de la analítica.
   */
  protected readonly signosResumen = computed<SignoResumen[]>(() => {
    const s = this.ultimoSignoVital();
    if (!s) {
      return [];
    }

    const lista: SignoResumen[] = [];

    if (evaluarIndicador(s, 'presion') !== null) {
      lista.push({
        etiqueta: 'Presión',
        valor: `${s.presionSistolica ?? '-'}/${s.presionDiastolica ?? '-'}`,
        unidad: RANGOS.sistolica.unidad,
        normal: evaluarIndicador(s, 'presion')!
      });
    }
    if (s.frecuenciaCardiaca !== null) {
      lista.push({ etiqueta: 'Pulso', valor: `${s.frecuenciaCardiaca}`, unidad: RANGOS.pulso.unidad,
        normal: evaluarIndicador(s, 'pulso')! });
    }
    if (s.temperatura !== null) {
      lista.push({ etiqueta: 'Temperatura corporal', valor: `${s.temperatura}`, unidad: RANGOS.temperatura.unidad,
        normal: evaluarIndicador(s, 'temperatura')! });
    }
    if (s.saturacionOxigeno !== null) {
      lista.push({ etiqueta: 'Oxígeno', valor: `${s.saturacionOxigeno}`, unidad: RANGOS.oxigeno.unidad,
        normal: evaluarIndicador(s, 'oxigeno')! });
    }

    return lista;
  });

  protected readonly hayValoresFueraDeRango = computed(() =>
    this.signosResumen().some((s) => !s.normal)
  );

  /** "hoy", "ayer" o "hace N días". */
  protected hace(fechaHora: string): string {
    const dias = Math.floor(
      (this.inicioDelDia(this.ahora()).getTime() - this.inicioDelDia(new Date(fechaHora)).getTime()) / DIA
    );
    if (dias <= 0) return 'hoy';
    if (dias === 1) return 'ayer';
    return `hace ${dias} días`;
  }

  protected horaDe(fecha: Date | null): string {
    return fecha
      ? fecha.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' })
      : 'Sin hora';
  }

  /** "Hoy", "Mañana" o "jueves 2 de octubre". */
  protected formatearDia(fecha: string | null): string {
    if (!fecha) {
      return 'Sin fecha';
    }

    const hoy = this.ahora();
    const manana = new Date(hoy.getTime() + DIA);

    if (fecha === this.fechaLocal(hoy)) return 'Hoy';
    if (fecha === this.fechaLocal(manana)) return 'Mañana';

    const [anio, mes, dia] = fecha.split('-').map(Number);
    return new Date(anio, mes - 1, dia)
      .toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
  }

  protected iconoDe(tipo: TipoEvento): string {
    return tipo === 'medicamento' ? 'pill' : tipo === 'cita' ? 'stethoscope' : 'activity';
  }

  protected inicial(nombre: string): string {
    return nombre.charAt(0).toUpperCase();
  }

  /** "Hospital San José · Consultorio 204". */
  private lugarDeCita(cita: CitaMedica): string | null {
    return [cita.lugar, formatearConsultorio(cita.consultorio)].filter(Boolean).join(' · ') || null;
  }

  /** Une una fecha "yyyy-MM-dd" y una hora "HH:mm" en un Date local. */
  private combinar(fecha: string, hora: string): Date {
    const [anio, mes, dia] = fecha.split('-').map(Number);
    const [h, m] = hora.split(':').map(Number);
    return new Date(anio, mes - 1, dia, h || 0, m || 0);
  }

  private inicioDelDia(fecha: Date): Date {
    const d = new Date(fecha);
    d.setHours(0, 0, 0, 0);
    return d;
  }

  /** Fecha YYYY-MM-DD en hora local (toISOString() usaría UTC). */
  private fechaLocal(fecha: Date): string {
    return fecha.toLocaleDateString('en-CA');
  }

  activarConfirmacionEmergencia(): void {
    this.mostrandoConfirmacionEmergencia.set(true);
    this.mensajeEmergencia.set(null);
    this.errorEmergencia.set(null);
  }

  cancelarEmergencia(): void {
    this.mostrandoConfirmacionEmergencia.set(false);
  }

  /** Envía la alerta a los acompañantes y organizaciones de la persona mayor. */
  confirmarEmergencia(): void {
    this.enviandoEmergencia.set(true);
    this.errorEmergencia.set(null);
    this.mensajeEmergencia.set(null);

    this.emergenciaService.activarEmergencia().subscribe({
      next: (respuesta) => {
        this.enviandoEmergencia.set(false);
        this.mostrandoConfirmacionEmergencia.set(false);
        this.mensajeEmergencia.set(respuesta);
      },
      error: (error) => {
        this.enviandoEmergencia.set(false);

        const mensaje =
          error?.error || 'No se pudo enviar la alerta de emergencia.';

        this.errorEmergencia.set(mensaje);
      }
    });
  }
}
