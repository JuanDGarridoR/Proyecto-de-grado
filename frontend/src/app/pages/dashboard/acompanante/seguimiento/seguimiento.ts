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
  separarCitas,
  tiempoParaCita
} from '../../../../core/citas-medicas/cita-medica.service';
import { SignoVitalResponse } from '../../../../core/signos-vitales/signos-vitales.services';
import {
  INDICADORES,
  Indicador,
  NOMBRE_INDICADOR,
  evaluarIndicador,
  indicadoresFueraDeRango
} from '../../../../core/signos-vitales/rangos';
import { Icon } from '../../../../shared/icon/icon';
import { SignosVitalesLista } from '../../../../shared/signos-vitales-lista/signos-vitales-lista';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';

/** Pestañas con la información de la persona seleccionada. */
type Pestana = 'medicamentos' | 'citas' | 'signos';

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
 * detalle de medicamentos, citas médicas y signos vitales.
 */
@Component({
  selector: 'app-seguimiento',
  standalone: true,
  imports: [Icon, SignosVitalesLista],
  templateUrl: './seguimiento.html',
  styleUrl: './seguimiento.css'
})
export class Seguimiento implements OnInit, OnDestroy {

  protected readonly formatearHora = formatearHora;
  protected readonly formatearProximaToma = formatearProximaToma;
  protected readonly formatearConsultorio = formatearConsultorio;

  /** Hora actual; se refresca cada minuto para "en 3 horas", "hace 2 días", etc. */
  protected readonly ahora = signal(new Date());
  private intervaloReloj?: ReturnType<typeof setInterval>;

  protected readonly personasMayores = signal<PersonaMayorAcompanada[]>([]);
  protected readonly personaSeleccionada = signal<PersonaMayorAcompanada | null>(null);
  protected readonly pestana = signal<Pestana>('medicamentos');

  protected readonly medicamentos = signal<MedicamentoSeguimiento[]>([]);
  protected readonly citas = signal<CitaMedica[]>([]);
  protected readonly signos = signal<SignoVitalResponse[]>([]);

  protected readonly cargandoPersonas = signal(true);
  protected readonly cargandoMedicamentos = signal(false);
  protected readonly cargandoCitas = signal(false);
  protected readonly cargandoSignos = signal(false);

  protected readonly errorPersonas = signal('');
  protected readonly errorMedicamentos = signal('');
  protected readonly errorCitas = signal('');
  protected readonly errorSignos = signal<string | null>(null);

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

  constructor(
    private acompananteService: AcompananteService,
    private route: ActivatedRoute
  ) {
    alCambiar(['acompanamientos', 'usuarios'], () => this.cargarPersonasMayores(false));
    alCambiar(['medicamentos'], () => this.recargarDetalle('medicamentos'));
    alCambiar(['citas-medicas'], () => this.recargarDetalle('citas'));
    alCambiar(['signos-vitales'], () => this.recargarDetalle('signos'));
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

    if (!persona) {
      return;
    }

    this.cargarMedicamentos(persona.idUsuario);
    this.cargarCitas(persona.idUsuario);
    this.cargarSignos(persona.idUsuario);
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

  // ---- Ayudas para la vista ----

  /** Iniciales del nombre: "María Pérez" -> "MP". */
  protected iniciales(nombre: string): string {
    return nombre
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((parte) => parte.charAt(0).toUpperCase())
      .join('');
  }

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

  /** Día y mes de la cita para la hoja de calendario. */
  protected partesFecha(cita: CitaMedica): { dia: number; mes: string } {
    const momento = momentoDeCita(cita);
    return {
      dia: momento.getDate(),
      mes: momento.toLocaleDateString('es-CO', { month: 'short' }).replace('.', '')
    };
  }

  /** "hoy", "ayer" o "hace N días". */
  protected hace(fechaHora: string): string {
    const inicioDia = (fecha: Date) => new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate()).getTime();
    const dias = Math.round((inicioDia(this.ahora()) - inicioDia(new Date(fechaHora))) / 86_400_000);

    if (dias <= 0) return 'hoy';
    if (dias === 1) return 'ayer';
    return `hace ${dias} días`;
  }
}
