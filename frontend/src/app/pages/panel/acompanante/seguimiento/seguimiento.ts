import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import {
  AcompananteService,
  PersonaMayorAcompanada,
  MedicamentoSeguimiento
} from '../../../../core/acompanantes/acompanante.service';
import {
  formatearHora,
  formatearProximaToma
} from '../../../../core/medicamentos/medicamento.service';
import {
  CitaMedica,
  formatearConsultorio,
  formatearFechaCita,
  momentoDeCita,
  necesitaAcompanamiento,
  separarCitas,
  tiempoParaCita
} from '../../../../core/citas-medicas/cita-medica.service';
import { AcompanamientoCita } from '../../../../shared/acompanamiento-cita/acompanamiento-cita';
import { HojaFecha } from '../../../../shared/hoja-fecha/hoja-fecha';
import { SignoVitalResponse } from '../../../../core/signos-vitales/signos-vitales.services';
import {
  INDICADORES,
  Indicador,
  NOMBRE_INDICADOR,
  evaluarIndicador,
  indicadoresFueraDeRango
} from '../../../../core/signos-vitales/rangos';
import { Icono } from '../../../../shared/icono/icono';
import { SignosVitalesLista } from '../../../../shared/signos-vitales-lista/signos-vitales-lista';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { haceDias } from '../../../../core/fechas/fechas';
import { iniciales, mensajeDeError } from '../../../../core/formato/formato';
import { Cargando } from '../../../../shared/cargando/cargando';
import {
  ActividadDisponible,
  ActividadService,
  separarPorFecha
} from '../../../../core/actividades/actividad.service';
import { TarjetaActividad } from '../../../../shared/tarjeta-actividad/tarjeta-actividad';

/** Pestañas con la información de la persona seleccionada. */
type Pestana = 'medicamentos' | 'citas' | 'signos' | 'actividades';

/** Citas pasadas que se muestran en el seguimiento (las más recientes). */
const CITAS_PASADAS_VISIBLES = 5;

/** Colores de los avatares, para distinguir de un vistazo a cada persona. */
const COLORES_AVATAR = ['#12355b', '#2ec4b6', '#8e7cc3', '#e0a526', '#d1665a', '#3a86c8'];

/** Un valor de la última medición, con su estado frente a los rangos de referencia. */
interface ValorSigno {
  indicador: Indicador;
  nombre: string;
  valor: string;
  unidad: string;
  normal: boolean;
}

/**
 * Seguimiento de las personas mayores que acompaña el usuario. Arriba se
 * elige a la persona (tarjetas con su avatar); debajo, su ficha con un
 * resumen (medicamentos, próxima cita y última medición) y pestañas con el
 * detalle de medicamentos, citas médicas y signos vitales, y las próximas
 * actividades de sus organizaciones, donde el acompañante puede inscribirla.
 */
@Component({
  selector: 'app-seguimiento',
  standalone: true,
  imports: [Icono, SignosVitalesLista, Cargando, AcompanamientoCita, HojaFecha, TarjetaActividad],
  templateUrl: './seguimiento.html',
  styleUrl: './seguimiento.css'
})
export class Seguimiento implements OnInit, OnDestroy {

  protected readonly formatearHora = formatearHora;
  protected readonly formatearProximaToma = formatearProximaToma;
  protected readonly formatearConsultorio = formatearConsultorio;
  protected readonly necesitaAcompanamiento = necesitaAcompanamiento;

  /** Hora actual; se refresca cada minuto para "en 3 horas", "hace 2 días", etc. */
  protected readonly ahora = signal(new Date());
  private intervaloReloj?: ReturnType<typeof setInterval>;

  protected readonly personasMayores = signal<PersonaMayorAcompanada[]>([]);
  protected readonly personaSeleccionada = signal<PersonaMayorAcompanada | null>(null);
  protected readonly pestana = signal<Pestana>('medicamentos');

  protected readonly medicamentos = signal<MedicamentoSeguimiento[]>([]);
  protected readonly citas = signal<CitaMedica[]>([]);
  protected readonly signos = signal<SignoVitalResponse[]>([]);
  protected readonly actividades = signal<ActividadDisponible[]>([]);

  protected readonly cargandoPersonas = signal(true);
  protected readonly cargandoMedicamentos = signal(false);
  protected readonly cargandoCitas = signal(false);
  protected readonly cargandoSignos = signal(false);
  protected readonly cargandoActividades = signal(false);

  protected readonly errorPersonas = signal('');
  protected readonly errorMedicamentos = signal('');
  protected readonly errorCitas = signal('');
  protected readonly errorSignos = signal<string | null>(null);
  protected readonly errorActividades = signal('');

  /** Actividad cuya inscripción se está enviando; deshabilita su botón. */
  protected readonly procesandoActividad = signal<number | null>(null);
  /** Por qué no se pudo inscribir o cancelar (sin cupos, ya pasó...). */
  protected readonly errorInscripcion = signal('');

  // ---- Medicamentos ----

  /** Activos primero, por próxima toma; los inactivos al final. */
  protected readonly medicamentosOrdenados = computed(() =>
    [...this.medicamentos()].sort((a, b) => {
      if (a.activo !== b.activo) {
        return a.activo ? -1 : 1;
      }
      return (a.proximaToma ?? '9999').localeCompare(b.proximaToma ?? '9999');
    })
  );

  protected readonly medicamentosActivos = computed(() =>
    this.medicamentos().filter((m) => m.activo)
  );

  protected readonly proximoMedicamento = computed(() =>
    this.medicamentosOrdenados().find((m) => m.activo && m.proximaToma) ?? null
  );

  // ---- Citas ----

  protected readonly citasProximas = computed(() => separarCitas(this.citas(), this.ahora()).proximas);
  protected readonly citasPasadas = computed(() =>
    separarCitas(this.citas(), this.ahora()).pasadas.slice(0, CITAS_PASADAS_VISIBLES)
  );

  // ---- Signos vitales ----

  /** La lista llega de la más reciente a la más antigua. */
  protected readonly ultimoSigno = computed(() => this.signos()[0] ?? null);

  protected readonly valoresUltimoSigno = computed<ValorSigno[]>(() => {
    const s = this.ultimoSigno();
    if (!s) {
      return [];
    }

    const valores: Record<Indicador, { valor: string; unidad: string }> = {
      presion: { valor: `${s.presionSistolica ?? '-'}/${s.presionDiastolica ?? '-'}`, unidad: 'mmHg' },
      pulso: { valor: `${s.frecuenciaCardiaca}`, unidad: 'lpm' },
      temperatura: { valor: `${s.temperatura}`, unidad: '°C' },
      oxigeno: { valor: `${s.saturacionOxigeno}`, unidad: '%' }
    };

    return INDICADORES
      .filter((i) => evaluarIndicador(s, i) !== null)
      .map((i) => ({
        indicador: i,
        nombre: NOMBRE_INDICADOR[i],
        ...valores[i],
        normal: evaluarIndicador(s, i)!
      }));
  });

  /** Nombres de los valores fuera de rango en la última medición ("Presión, Pulso"). */
  protected readonly fueraDeRango = computed(() => {
    const s = this.ultimoSigno();
    return s ? indicadoresFueraDeRango(s).map((i) => NOMBRE_INDICADOR[i]).join(', ') : '';
  });

  // ---- Actividades ----

  /** Solo las próximas: en las pasadas ya no se puede inscribir. */
  protected readonly actividadesProximas = computed(() => separarPorFecha(this.actividades()).proximas);

  protected readonly actividadesInscritas = computed(() =>
    this.actividadesProximas().filter((a) => a.inscrito)
  );

  constructor(
    private acompananteService: AcompananteService,
    private actividadService: ActividadService,
    private route: ActivatedRoute
  ) {
    alCambiar(['acompanamientos', 'usuarios'], () => this.cargarPersonasMayores(false));
    alCambiar(['medicamentos'], () => this.recargarDetalle('medicamentos'));
    alCambiar(['citas-medicas'], () => this.recargarDetalle('citas'));
    alCambiar(['signos-vitales'], () => this.recargarDetalle('signos'));
    alCambiar(['actividades', 'organizaciones'], () => this.recargarDetalle('actividades'));
  }

  /** Al aceptar o cancelar un acompañamiento, se actualiza la cita en la lista. */
  protected citaActualizada(cita: CitaMedica): void {
    this.citas.update((citas) => citas.map((c) => (c.idCita === cita.idCita ? { ...c, ...cita } : c)));
  }

  ngOnInit(): void {
    this.cargarPersonasMayores();
    this.intervaloReloj = setInterval(() => this.ahora.set(new Date()), 60_000);
  }

  ngOnDestroy(): void {
    clearInterval(this.intervaloReloj);
  }

  /**
   * Carga las personas mayores. Al recargar por cambios de otros usuarios
   * se conserva la persona seleccionada (si sigue en la lista).
   */
  private cargarPersonasMayores(mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargandoPersonas.set(true);
    }
    this.errorPersonas.set('');

    this.acompananteService.obtenerPersonasMayores().subscribe({
      next: (personas) => {
        this.personasMayores.set(personas);
        this.cargandoPersonas.set(false);

        // La primera vez puede venir elegida desde el inicio (?persona=id).
        const idActual = this.personaSeleccionada()?.idUsuario;
        const idPedido = idActual ?? Number(this.route.snapshot.queryParamMap.get('persona'));
        const siguiente = personas.find((p) => p.idUsuario === idPedido) ?? personas[0] ?? null;

        if (siguiente?.idUsuario !== idActual) {
          this.seleccionarPersona(siguiente);
        } else {
          // Misma persona: solo se actualizan sus datos (nombre, celular).
          this.personaSeleccionada.set(siguiente);
        }
      },
      error: () => {
        this.cargandoPersonas.set(false);
        this.errorPersonas.set('No se pudieron cargar las personas mayores asociadas.');
      }
    });
  }

  protected seleccionarPersona(persona: PersonaMayorAcompanada | null): void {
    this.personaSeleccionada.set(persona);
    this.medicamentos.set([]);
    this.citas.set([]);
    this.signos.set([]);
    this.actividades.set([]);
    this.errorInscripcion.set('');

    if (!persona) {
      return;
    }

    this.cargarMedicamentos(persona.idUsuario);
    this.cargarCitas(persona.idUsuario);
    this.cargarSignos(persona.idUsuario);
    this.cargarActividades(persona.idUsuario);
  }

  /** Vuelve a pedir una sección de la persona seleccionada, sin mostrar "cargando". */
  private recargarDetalle(seccion: Pestana): void {
    const id = this.personaSeleccionada()?.idUsuario;
    if (id === undefined) {
      return;
    }

    if (seccion === 'medicamentos') this.cargarMedicamentos(id, false);
    if (seccion === 'citas') this.cargarCitas(id, false);
    if (seccion === 'signos') this.cargarSignos(id, false);
    if (seccion === 'actividades') this.cargarActividades(id, false);
  }

  /**
   * Si se cambió de persona mientras llegaba la respuesta, la respuesta ya
   * no sirve: así no se muestran datos de otra persona.
   */
  private sigueSeleccionada(idPersonaMayor: number): boolean {
    return this.personaSeleccionada()?.idUsuario === idPersonaMayor;
  }

  private mensajeError(error: { status?: number }, que: string): string {
    return error?.status === 403
      ? 'No tienes autorización para consultar esta información.'
      : `No se pudieron cargar ${que}.`;
  }

  private cargarMedicamentos(idPersonaMayor: number, mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargandoMedicamentos.set(true);
    }
    this.errorMedicamentos.set('');

    this.acompananteService.obtenerMedicamentosSeguimiento(idPersonaMayor).subscribe({
      next: (data) => {
        if (!this.sigueSeleccionada(idPersonaMayor)) return;
        this.medicamentos.set(data);
        this.cargandoMedicamentos.set(false);
      },
      error: (error) => {
        if (!this.sigueSeleccionada(idPersonaMayor)) return;
        this.cargandoMedicamentos.set(false);
        this.errorMedicamentos.set(this.mensajeError(error, 'los medicamentos'));
      }
    });
  }

  private cargarCitas(idPersonaMayor: number, mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargandoCitas.set(true);
    }
    this.errorCitas.set('');

    this.acompananteService.obtenerCitasMedicasSeguimiento(idPersonaMayor).subscribe({
      next: (data) => {
        if (!this.sigueSeleccionada(idPersonaMayor)) return;
        this.citas.set(data);
        this.cargandoCitas.set(false);
      },
      error: (error) => {
        if (!this.sigueSeleccionada(idPersonaMayor)) return;
        this.cargandoCitas.set(false);
        this.errorCitas.set(this.mensajeError(error, 'las citas médicas'));
      }
    });
  }

  private cargarSignos(idPersonaMayor: number, mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargandoSignos.set(true);
    }
    this.errorSignos.set(null);

    this.acompananteService.obtenerSignosVitalesSeguimiento(idPersonaMayor).subscribe({
      next: (data) => {
        if (!this.sigueSeleccionada(idPersonaMayor)) return;
        this.signos.set(data);
        this.cargandoSignos.set(false);
      },
      error: (error) => {
        if (!this.sigueSeleccionada(idPersonaMayor)) return;
        this.cargandoSignos.set(false);
        this.errorSignos.set(this.mensajeError(error, 'los signos vitales'));
      }
    });
  }

  private cargarActividades(idPersonaMayor: number, mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargandoActividades.set(true);
    }
    this.errorActividades.set('');

    this.actividadService.listarDisponiblesDeAcompanada(idPersonaMayor).subscribe({
      next: (data) => {
        if (!this.sigueSeleccionada(idPersonaMayor)) return;
        this.actividades.set(data);
        this.cargandoActividades.set(false);
      },
      error: (error) => {
        if (!this.sigueSeleccionada(idPersonaMayor)) return;
        this.cargandoActividades.set(false);
        this.errorActividades.set(this.mensajeError(error, 'las actividades'));
      }
    });
  }

  /** Inscribe a la persona seleccionada o cancela su inscripción, según cómo esté. */
  protected cambiarInscripcion(actividad: ActividadDisponible): void {
    const persona = this.personaSeleccionada();
    if (!persona) {
      return;
    }

    this.procesandoActividad.set(actividad.idActividad);
    this.errorInscripcion.set('');

    const peticion = actividad.inscrito
      ? this.actividadService.cancelarInscripcionDeAcompanada(actividad.idActividad, persona.idUsuario)
      : this.actividadService.inscribirAcompanada(actividad.idActividad, persona.idUsuario);

    peticion.subscribe({
      next: () => {
        this.procesandoActividad.set(null);
        this.cargarActividades(persona.idUsuario, false);
      },
      error: (error) => {
        this.procesandoActividad.set(null);
        this.errorInscripcion.set(mensajeDeError(
          error,
          actividad.inscrito ? 'No se pudo cancelar la inscripción.' : 'No se pudo completar la inscripción.'
        ));
      }
    });
  }

  // ---- Ayudas para la vista ----

  protected readonly iniciales = iniciales;

  /** Color fijo por persona, para reconocerla en las tarjetas. */
  protected colorAvatar(persona: PersonaMayorAcompanada): string {
    return COLORES_AVATAR[persona.idUsuario % COLORES_AVATAR.length];
  }

  /** "Mañana, 3:00 p. m." */
  protected cuandoEsCita(cita: CitaMedica): string {
    return `${formatearFechaCita(cita.fecha, this.ahora())}, ${formatearHora(cita.hora)}`;
  }

  protected faltaParaCita(cita: CitaMedica): string | null {
    return tiempoParaCita(cita, this.ahora());
  }

  /** "hoy", "ayer" o "hace N días" (lo usa la plantilla). */
  protected hace(fechaHora: string): string {
    return haceDias(fechaHora, this.ahora());
  }
}

