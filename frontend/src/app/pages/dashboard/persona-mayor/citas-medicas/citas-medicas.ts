import { Component, OnDestroy, OnInit, computed, effect, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CitaMedicaService,
  CitaMedica,
  CitaMedicaRequest,
  TEXTO_AVISOS_CITA,
  fechaLocal,
  formatearConsultorio,
  momentoDeCita,
  separarCitas,
  tiempoParaCita
} from '../../../../core/citas-medicas/cita-medica.service';
import { formatearHora } from '../../../../core/medicamentos/medicamento.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../../../../shared/icon/icon';

/** Citas pasadas que se muestran antes de pulsar "Ver todas". */
const PASADAS_VISIBLES = 5;

/** Partes de la fecha para la "hoja de calendario" de cada tarjeta. */
interface PartesFecha {
  dia: number;
  mes: string;      // "oct"
  semana: string;   // "lunes"
  anio: number;
}

/**
 * Citas médicas de la persona mayor: las próximas, el historial de las que
 * ya pasaron y un formulario para registrarlas o editarlas. Solo se
 * registran citas futuras, y las pasadas no se editan (sí se pueden
 * borrar). salud-service envía los recordatorios por SMS un día antes y
 * una hora antes.
 *
 * El acompañante usa esta misma vista dentro de "Gestionar cuidado": con
 * idPersonaMayor gestiona las citas de esa persona.
 */
@Component({
  selector: 'app-citas-medicas',
  standalone: true,
  imports: [FormsModule, Icon],
  templateUrl: './citas-medicas.html',
  styleUrl: './citas-medicas.css'
})
export class CitasMedicas implements OnInit, OnDestroy {

  /** Persona mayor que gestiona el acompañante; null si es la persona mayor autenticada. */
  readonly idPersonaMayor = input<number | null>(null);
  /** Nombre de esa persona, para los textos del acompañante. */
  readonly nombrePersona = input('');

  protected readonly paraAcompanante = computed(() => this.idPersonaMayor() !== null);

  protected readonly textoAvisos = TEXTO_AVISOS_CITA;
  protected readonly formatearHora = formatearHora;
  protected readonly formatearConsultorio = formatearConsultorio;

  /** Fecha mínima del formulario: no se registran citas en días pasados. */
  protected readonly hoy = computed(() => fechaLocal(this.ahora()));

  /** Hora actual; se refresca cada minuto para que las citas pasen solas al historial. */
  protected readonly ahora = signal(new Date());
  private intervaloReloj?: ReturnType<typeof setInterval>;

  protected readonly citas = signal<CitaMedica[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly proximas = computed(() => separarCitas(this.citas(), this.ahora()).proximas);
  protected readonly pasadas = computed(() => separarCitas(this.citas(), this.ahora()).pasadas);

  protected readonly verTodasLasPasadas = signal(false);
  protected readonly pasadasVisibles = computed(() =>
    this.verTodasLasPasadas() ? this.pasadas() : this.pasadas().slice(0, PASADAS_VISIBLES)
  );

  protected readonly mostrandoFormulario = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);
  protected readonly idEditando = signal<number | null>(null);

  /** Tras un intento de guardar incompleto, se marcan en rojo los campos obligatorios vacíos. */
  protected readonly intentoGuardar = signal(false);

  // Campos del formulario
  titulo = '';
  lugar = '';
  consultorio = '';
  fecha = '';
  hora = '';
  observaciones = '';

  constructor(private citaMedicaService: CitaMedicaService) {
    alCambiar(['citas-medicas'], () => this.cargar(false));

    // Carga al iniciar y cada vez que el acompañante cambia de persona.
    effect(() => {
      this.idPersonaMayor();
      untracked(() => this.cargar());
    });
  }

  ngOnInit(): void {
    this.intervaloReloj = setInterval(() => this.ahora.set(new Date()), 60_000);
  }

  ngOnDestroy(): void {
    clearInterval(this.intervaloReloj);
  }

  /** Con mostrarCargando en false, la lista se actualiza sin parpadear. */
  private cargar(mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargando.set(true);
    }
    this.error.set(null);

    const idPersonaMayor = this.idPersonaMayor();

    this.citaMedicaService.listar(idPersonaMayor).subscribe({
      next: (citas) => {
        // Si el acompañante cambió de persona mientras llegaba la respuesta, ya no sirve.
        if (idPersonaMayor !== this.idPersonaMayor()) {
          return;
        }
        this.citas.set(citas);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set(this.paraAcompanante()
          ? 'No se pudieron cargar las citas médicas.'
          : 'No se pudieron cargar tus citas médicas.');
        this.cargando.set(false);
      }
    });
  }

  protected faltaPara(cita: CitaMedica): string | null {
    return tiempoParaCita(cita, this.ahora());
  }

  /** Día, mes y día de la semana de la cita, para la hoja de calendario. */
  protected partesFecha(cita: CitaMedica): PartesFecha {
    const momento = momentoDeCita(cita);
    return {
      dia: momento.getDate(),
      mes: momento.toLocaleDateString('es-CO', { month: 'short' }).replace('.', ''),
      semana: momento.toLocaleDateString('es-CO', { weekday: 'long' }),
      anio: momento.getFullYear()
    };
  }

  /** Si un campo obligatorio está vacío después de intentar guardar. */
  protected faltaCampo(valor: string): boolean {
    return this.intentoGuardar() && !valor.trim();
  }

  /** Si la fecha y hora escritas en el formulario ya pasaron (no se puede guardar). */
  protected fechaFormularioYaPaso(): boolean {
    if (!this.fecha || !this.hora) {
      return false;
    }
    const cita = { fecha: this.fecha, hora: this.hora } as CitaMedica;
    return momentoDeCita(cita) <= this.ahora();
  }

  /** Abre el selector de fecha u hora al hacer clic en cualquier parte del campo. */
  protected abrirSelector(event: Event): void {
    const input = event.target as HTMLInputElement;
    try {
      input.showPicker();
    } catch {
      // Navegadores sin showPicker(): se deja el comportamiento normal.
    }
  }

  abrirFormularioNuevo(): void {
    this.idEditando.set(null);
    this.limpiarFormulario();
    this.errorFormulario.set(null);
    this.intentoGuardar.set(false);
    this.mostrandoFormulario.set(true);
  }

  abrirFormularioEditar(cita: CitaMedica): void {
    this.idEditando.set(cita.idCita);
    this.titulo = cita.titulo;
    this.lugar = cita.lugar;
    this.consultorio = cita.consultorio ?? '';
    this.fecha = cita.fecha;
    this.hora = cita.hora;
    this.observaciones = cita.observaciones ?? '';
    this.errorFormulario.set(null);
    this.intentoGuardar.set(false);
    this.mostrandoFormulario.set(true);
  }

  cancelarFormulario(): void {
    this.mostrandoFormulario.set(false);
    this.errorFormulario.set(null);
    this.intentoGuardar.set(false);
    this.limpiarFormulario();
  }

  private limpiarFormulario(): void {
    this.titulo = '';
    this.lugar = '';
    this.consultorio = '';
    this.fecha = '';
    this.hora = '';
    this.observaciones = '';
  }

  /** Crea o actualiza la cita, según si se está editando. */
  guardar(): void {
    this.intentoGuardar.set(true);

    if (!this.titulo.trim() || !this.lugar.trim() || !this.fecha || !this.hora) {
      this.errorFormulario.set('Completa los campos marcados con *.');
      return;
    }

    // Por si la página lleva un rato abierta, se compara con la hora real.
    this.ahora.set(new Date());
    if (this.fechaFormularioYaPaso()) {
      this.errorFormulario.set('La fecha y la hora de la cita deben ser posteriores a este momento.');
      return;
    }

    this.guardando.set(true);
    this.errorFormulario.set(null);

    const request: CitaMedicaRequest = {
      titulo: this.titulo,
      lugar: this.lugar,
      consultorio: this.consultorio || undefined,
      fecha: this.fecha,
      hora: this.hora,
      observaciones: this.observaciones || undefined
    };

    const idEditando = this.idEditando();
    const peticion = idEditando
      ? this.citaMedicaService.actualizar(idEditando, request, this.idPersonaMayor())
      : this.citaMedicaService.crear(request, this.idPersonaMayor());

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrandoFormulario.set(false);
        this.intentoGuardar.set(false);
        this.limpiarFormulario();
        this.cargar(false);
      },
      error: (err) => {
        this.guardando.set(false);
        this.errorFormulario.set(
          typeof err?.error === 'string' && err.error ? err.error : 'No se pudo guardar la cita.'
        );
      }
    });
  }

  eliminar(cita: CitaMedica): void {
    const confirmado = confirm(`¿Eliminar la cita "${cita.titulo}"?`);
    if (!confirmado) {
      return;
    }

    this.citaMedicaService.eliminar(cita.idCita, this.idPersonaMayor()).subscribe({
      next: () => this.cargar(false),
      error: () => this.error.set('No se pudo eliminar la cita.')
    });
  }
}
