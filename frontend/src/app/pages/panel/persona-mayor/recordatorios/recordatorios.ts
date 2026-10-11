import { Component, OnDestroy, OnInit, computed, effect, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  MedicamentoService,
  Medicamento,
  MedicamentoRequest,
  MINUTOS_AVISO_PREVIO,
  formatearHora,
  formatearProximaToma
} from '../../../../core/medicamentos/medicamento.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icono } from '../../../../shared/icono/icono';

/** Opciones de "Cada cuántas horas": de 1 a 12, más una vez al día. */
const INTERVALOS_HORAS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 24];

/**
 * Medicamentos de la persona mayor: lista con la próxima toma y un
 * formulario para agregarlos o editarlos. salud-service envía los
 * recordatorios por SMS antes de cada toma.
 *
 * El acompañante usa esta misma vista dentro de "Gestionar cuidado": con
 * idPersonaMayor gestiona los medicamentos de esa persona y los textos le
 * hablan de ella en lugar de "tus medicamentos".
 */
@Component({
  selector: 'app-recordatorios',
  standalone: true,
  imports: [FormsModule, Icono],
  templateUrl: './recordatorios.html',
  styleUrl: './recordatorios.css'
})
export class Recordatorios implements OnInit, OnDestroy {

  /** Persona mayor que gestiona el acompañante; null si es la persona mayor autenticada. */
  readonly idPersonaMayor = input<number | null>(null);
  /** Nombre de esa persona, para los textos del acompañante. */
  readonly nombrePersona = input('');

  protected readonly paraAcompanante = computed(() => this.idPersonaMayor() !== null);

  protected readonly minutosAvisoPrevio = MINUTOS_AVISO_PREVIO;
  protected readonly formatearHora = formatearHora;
  protected readonly formatearProximaToma = formatearProximaToma;

  private intervaloReloj?: ReturnType<typeof setInterval>;

  protected readonly medicamentos = signal<Medicamento[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly mostrandoFormulario = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);
  protected readonly idEditando = signal<number | null>(null);

  // Campos del formulario
  nombre = '';
  dosis = '';
  frecuencia = '';
  intervaloHoras: number | null = null;
  hora = '';
  fechaInicio = '';
  fechaFin = '';

  constructor(private medicamentoService: MedicamentoService) {
    alCambiar(['medicamentos'], () => this.cargar(false));

    // Carga al iniciar y cada vez que el acompañante cambia de persona.
    effect(() => {
      this.idPersonaMayor();
      untracked(() => this.cargar());
    });
  }

  /**
   * Si un medicamento ya guardado tiene un intervalo fuera de la lista, se
   * agrega para no perderlo al editar.
   */
  protected opcionesIntervalo(): number[] {
    const actual = this.intervaloHoras;
    if (actual && !INTERVALOS_HORAS.includes(actual)) {
      return [...INTERVALOS_HORAS, actual].sort((a, b) => a - b);
    }
    return INTERVALOS_HORAS;
  }

  /** Abre el selector de hora al hacer clic en cualquier parte del campo, no solo en el reloj. */
  protected abrirSelectorHora(event: Event): void {
    const input = event.target as HTMLInputElement;
    try {
      input.showPicker();
    } catch {
      // Navegadores sin showPicker(): se deja el comportamiento normal.
    }
  }

  ngOnInit(): void {
    // Cuando pasa la hora de una toma, el backend la avanza sola a la
    // siguiente; se recarga la lista para mostrar la nueva "Próxima toma".
    this.intervaloReloj = setInterval(() => {
      const ahora = Date.now();
      const hayTomaPasada = this.medicamentos().some(
        (m) => new Date(m.proximaToma).getTime() <= ahora
      );
      if (hayTomaPasada) {
        this.cargar(false);
      }
    }, 60_000);
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

    this.medicamentoService.listar(idPersonaMayor).subscribe({
      next: (medicamentos) => {
        // Si el acompañante cambió de persona mientras llegaba la respuesta, ya no sirve.
        if (idPersonaMayor !== this.idPersonaMayor()) {
          return;
        }
        this.medicamentos.set(medicamentos);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set(this.paraAcompanante()
          ? 'No se pudieron cargar los medicamentos.'
          : 'No se pudieron cargar tus medicamentos.');
        this.cargando.set(false);
      }
    });
  }

  /**
   * Horas del día en que toca el medicamento; por ejemplo, cada 8 horas
   * desde las 8:00 da "8:00 a. m. · 4:00 p. m. · 12:00 a. m.". Solo cuando
   * el intervalo cabe exacto en el día y no son demasiadas horas para leer.
   */
  protected horarioDelDia(medicamento: Medicamento): string | null {
    const intervalo = medicamento.intervaloHoras;
    if (!medicamento.hora || !intervalo || 24 % intervalo !== 0 || 24 / intervalo > 6) {
      return null;
    }

    const [h, m] = medicamento.hora.split(':').map(Number);
    const tomas: string[] = [];
    for (let i = 0; i < 24 / intervalo; i++) {
      const hora = (h + i * intervalo) % 24;
      tomas.push(formatearHora(`${hora}:${m}`));
    }
    return tomas.join(' · ');
  }

  abrirFormularioNuevo(): void {
    this.idEditando.set(null);
    this.limpiarFormulario();
    this.mostrandoFormulario.set(true);
  }

  abrirFormularioEditar(medicamento: Medicamento): void {
    this.idEditando.set(medicamento.idMedicamento);
    this.nombre = medicamento.nombre;
    this.dosis = medicamento.dosis;
    this.frecuencia = medicamento.frecuencia;
    this.intervaloHoras = medicamento.intervaloHoras;
    this.hora = medicamento.hora;
    this.fechaInicio = medicamento.fechaInicio ?? '';
    this.fechaFin = medicamento.fechaFin ?? '';
    this.mostrandoFormulario.set(true);
  }

  cancelarFormulario(): void {
    this.mostrandoFormulario.set(false);
    this.errorFormulario.set(null);
    this.limpiarFormulario();
  }

  private limpiarFormulario(): void {
    this.nombre = '';
    this.dosis = '';
    this.frecuencia = '';
    this.intervaloHoras = null;
    this.hora = '';
    this.fechaInicio = '';
    this.fechaFin = '';
  }

  /** Crea o actualiza el medicamento, según si se está editando. */
  guardar(): void {
    if (!this.nombre.trim() || !this.hora.trim() || !this.intervaloHoras) {
      this.errorFormulario.set(this.paraAcompanante()
        ? 'Escribe el nombre, cada cuántas horas y a qué hora se lo toma.'
        : 'Escribe el nombre, cada cuántas horas y a qué hora te lo tomas.');
      return;
    }

    this.guardando.set(true);
    this.errorFormulario.set(null);

    const request: MedicamentoRequest = {
      nombre: this.nombre,
      dosis: this.dosis,
      frecuencia: this.frecuencia,
      intervaloHoras: this.intervaloHoras,
      hora: this.hora,
      fechaInicio: this.fechaInicio || undefined,
      fechaFin: this.fechaFin || undefined
    };

    const idEditando = this.idEditando();
    const peticion = idEditando
      ? this.medicamentoService.actualizar(idEditando, request, this.idPersonaMayor())
      : this.medicamentoService.crear(request, this.idPersonaMayor());

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrandoFormulario.set(false);
        this.limpiarFormulario();
        this.cargar();
      },
      error: () => {
        this.guardando.set(false);
        this.errorFormulario.set('No se pudo guardar el medicamento.');
      }
    });
  }

  eliminar(medicamento: Medicamento): void {
    const deQuien = this.paraAcompanante() ? `los medicamentos de ${this.nombrePersona()}` : 'tus medicamentos';
    const confirmado = confirm(`¿Eliminar ${medicamento.nombre} de ${deQuien}?`);
    if (!confirmado) {
      return;
    }

    this.medicamentoService.eliminar(medicamento.idMedicamento, this.idPersonaMayor()).subscribe({
      next: () => this.cargar(),
      error: () => this.error.set('No se pudo eliminar el medicamento.')
    });
  }
}