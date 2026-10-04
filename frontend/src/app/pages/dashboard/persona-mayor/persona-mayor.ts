import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es-CO';

import { Icon } from '../../../shared/icon/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { ActividadService, ActividadDisponible } from '../../../core/actividades/actividad.service';
import { AcompananteService, Acompanante } from '../../../core/acompanantes/acompanante.service';
import { EmergenciaService } from '../../../core/emergencia/emergencia.service';
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
  formatearProximaToma
} from '../../../core/medicamentos/medicamento.service';

registerLocaleData(localeEs);

/** Estado de un elemento de la agenda de hoy. */
type EstadoEvento =
  | 'hecho'      // medicamento ya tomado
  | 'pasado'     // actividad que ya ocurrió
  | 'atrasado'   // medicamento cuya hora ya pasó y no se ha tomado
  | 'siguiente'  // lo próximo que viene
  | 'pendiente'; // más tarde hoy

/** Elemento de la agenda de hoy: una toma de medicamento o una actividad. */
interface EventoAgenda {
  clave: string;
  tipo: 'medicamento' | 'actividad';
  momento: Date | null;   // null = actividad de hoy sin hora
  titulo: string;
  detalle: string | null;
  estado: EstadoEvento;
  enlace: string;
}

/** Estado de un signo vital respecto a rangos de referencia generales. */
interface SignoResumen {
  etiqueta: string;
  valor: string;
  unidad: string;
  normal: boolean;
}

const MINUTO = 60_000;
const HORA = 60 * MINUTO;

/**
 * Inicio de la persona mayor, en forma de "agenda del día":
 *  1. Saludo y botón de emergencia (lo más urgente, siempre arriba).
 *  2. Tu día de hoy: medicamentos y actividades de hoy en orden de hora.
 *  3. Tu acompañante y tu salud (última medición).
 *  4. Próximamente: actividades de los siguientes días.
 */
@Component({
  selector: 'app-persona-mayor-dashboard',
  imports: [Icon, DatePipe, RouterLink],
  templateUrl: './persona-mayor.html',
  styleUrl: './persona-mayor.css',
})
export class PersonaMayorDashboard implements OnInit, OnDestroy {

  protected readonly nombreUsuario: string;

  /**
   * Hora actual. Se refresca cada minuto para que la agenda cambie sola: lo
   * que pasó se atenúa y "Siguiente" avanza.
   */
  protected readonly ahora = signal(new Date());
  private intervaloReloj?: ReturnType<typeof setInterval>;

  protected readonly medicamentos = signal<Medicamento[]>([]);
  protected readonly actividades = signal<ActividadDisponible[]>([]);
  protected readonly acompanante = signal<Acompanante | null>(null);
  protected readonly ultimoSignoVital = signal<SignoVitalResponse | null>(null);

  protected readonly cargandoAgenda = signal(true);
  protected readonly cargandoAcompanante = signal(true);
  protected readonly cargandoSignos = signal(true);
  private pendientesAgenda = 2; // medicamentos y actividades

  protected readonly formatearHora = formatearHora;
  protected readonly formatearProximaToma = formatearProximaToma;

  // Botón de emergencia
  protected readonly mostrandoConfirmacionEmergencia = signal(false);
  protected readonly enviandoEmergencia = signal(false);
  protected readonly mensajeEmergencia = signal<string | null>(null);
  protected readonly errorEmergencia = signal<string | null>(null);

  constructor(
    private authService: AuthService,
    private actividadService: ActividadService,
    private acompananteService: AcompananteService,
    private emergenciaService: EmergenciaService,
    private medicamentoService: MedicamentoService,
    private signosVitalesService: SignosVitalesService
  ) {
    this.nombreUsuario = this.authService.getNombreUsuario();

    alCambiar(['medicamentos'], () => this.cargarMedicamentos());
    alCambiar(['actividades'], () => this.cargarActividades());
    alCambiar(['acompanamientos', 'usuarios'], () => this.cargarAcompanante());
    alCambiar(['signos-vitales'], () => this.cargarSignosVitales());
  }

  ngOnInit(): void {
    this.intervaloReloj = setInterval(() => this.ahora.set(new Date()), MINUTO);

    this.cargarMedicamentos();
    this.cargarActividades();
    this.cargarAcompanante();
    this.cargarSignosVitales();
  }

  ngOnDestroy(): void {
    clearInterval(this.intervaloReloj);
  }

  private cargarMedicamentos(): void {
    this.medicamentoService.listar().subscribe({
      next: (medicamentos) => this.medicamentos.set(medicamentos),
      complete: () => this.terminarCargaAgenda(),
      error: () => this.terminarCargaAgenda()
    });
  }

  /** Solo interesan las actividades en las que está inscrita. */
  private cargarActividades(): void {
    this.actividadService.listarDisponibles().subscribe({
      next: (actividades) => this.actividades.set(actividades.filter((a) => a.inscrito)),
      complete: () => this.terminarCargaAgenda(),
      error: () => this.terminarCargaAgenda()
    });
  }

  /** En el inicio se muestra solo el primer acompañante. */
  private cargarAcompanante(): void {
    this.acompananteService.obtenerAcompanantes().subscribe({
      next: (acompanantes) => this.acompanante.set(acompanantes[0] ?? null),
      complete: () => this.cargandoAcompanante.set(false),
      error: () => this.cargandoAcompanante.set(false)
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

  /** La agenda deja de mostrar "cargando" cuando llegan medicamentos y actividades. */
  private terminarCargaAgenda(): void {
    this.pendientesAgenda--;
    if (this.pendientesAgenda <= 0) {
      this.cargandoAgenda.set(false);
    }
  }

  /**
   * Agenda de hoy: tomas de medicamentos y actividades en orden de hora,
   * cada una con su estado (ver EstadoEvento).
   */
  protected readonly agendaHoy = computed<EventoAgenda[]>(() => {
    const ahora = this.ahora();
    const inicioHoy = new Date(ahora);
    inicioHoy.setHours(0, 0, 0, 0);
    const finHoy = new Date(inicioHoy.getTime() + 24 * HORA);
    const hoy = this.fechaLocal(ahora);

    const eventos: Omit<EventoAgenda, 'estado'>[] = [];
    const estados = new Map<string, EstadoEvento>();

    // Medicamentos: la toma ya hecha hoy y las que faltan hoy.
    for (const med of this.medicamentos()) {
      if (med.activo === false || (med.fechaFin && med.fechaFin < hoy)) {
        continue;
      }

      const enlace = '/panel/persona-mayor/recordatorios';

      if (med.ultimaToma) {
        const ultima = new Date(med.ultimaToma);
        if (ultima >= inicioHoy && ultima < finHoy) {
          const clave = `m${med.idMedicamento}-hecho`;
          eventos.push({ clave, tipo: 'medicamento', momento: ultima, titulo: med.nombre, detalle: med.dosis || null, enlace });
          estados.set(clave, 'hecho');
        }
      }

      if (!med.proximaToma) {
        continue;
      }

      const intervalo = Math.max(1, med.intervaloHoras || 24) * HORA;
      let toma = new Date(med.proximaToma);

      // Si quedó pendiente desde antes de hoy, se muestra como atrasada.
      if (toma < inicioHoy) {
        const clave = `m${med.idMedicamento}-atrasado`;
        eventos.push({ clave, tipo: 'medicamento', momento: toma, titulo: med.nombre, detalle: med.dosis || null, enlace });
        estados.set(clave, 'atrasado');
        continue;
      }

      for (let i = 0; toma < finHoy && i < 24; i++) {
        const clave = `m${med.idMedicamento}-${toma.getTime()}`;
        eventos.push({ clave, tipo: 'medicamento', momento: new Date(toma), titulo: med.nombre, detalle: med.dosis || null, enlace });
        estados.set(clave, toma <= ahora ? 'atrasado' : 'pendiente');
        toma = new Date(toma.getTime() + intervalo);
      }
    }

    // Actividades de hoy en las que está inscrita.
    for (const act of this.actividades()) {
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
        enlace: '/panel/persona-mayor/actividades'
      });
      estados.set(clave, momento && momento < ahora ? 'pasado' : 'pendiente');
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

  /** Si hoy no hay nada: lo próximo que viene (medicamento o actividad). */
  protected readonly loProximo = computed(() => {
    const med = [...this.medicamentos()]
      .filter((m) => m.activo !== false && !!m.proximaToma)
      .sort((a, b) => new Date(a.proximaToma).getTime() - new Date(b.proximaToma).getTime())[0];

    const act = this.proximasActividades()[0];

    const momentoMed = med ? new Date(med.proximaToma).getTime() : Infinity;
    const momentoAct = act?.fecha ? this.combinar(act.fecha, act.hora ?? '00:00').getTime() : Infinity;

    if (momentoMed === Infinity && momentoAct === Infinity) {
      return null;
    }

    return momentoMed <= momentoAct
      ? { titulo: med!.nombre, cuando: formatearProximaToma(med!.proximaToma) }
      : { titulo: act!.nombre, cuando: this.formatearDia(act!.fecha) + (act!.hora ? `, ${formatearHora(act!.hora)}` : '') };
  });

  /** Actividades confirmadas de los próximos días (después de hoy). */
  protected readonly proximasActividades = computed(() => {
    const hoy = this.fechaLocal(this.ahora());
    return this.actividades()
      .filter((a) => a.fecha && a.fecha > hoy)
      .sort((a, b) =>
        (a.fecha ?? '').localeCompare(b.fecha ?? '') || (a.hora ?? '').localeCompare(b.hora ?? ''))
      .slice(0, 3);
  });

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
      (this.inicioDelDia(this.ahora()).getTime() - this.inicioDelDia(new Date(fechaHora)).getTime())
        / (24 * HORA)
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
    const manana = new Date(hoy.getTime() + 24 * HORA);

    if (fecha === this.fechaLocal(hoy)) return 'Hoy';
    if (fecha === this.fechaLocal(manana)) return 'Mañana';

    const [anio, mes, dia] = fecha.split('-').map(Number);
    return new Date(anio, mes - 1, dia)
      .toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
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
