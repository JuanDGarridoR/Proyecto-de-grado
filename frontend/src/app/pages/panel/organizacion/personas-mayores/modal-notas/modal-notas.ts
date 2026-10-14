import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { NotaPersonaMayor, OrganizacionService } from '../../../../../core/organizacion/organizacion.service';
import { alCambiar } from '../../../../../core/tiempo-real/tiempo-real.service';
import { mensajeDeError } from '../../../../../core/formato/formato';
import { Icono } from '../../../../../shared/icono/icono';

const MAX_TEXTO = 2000;

/**
 * Notas de la organización sobre una persona mayor: agregar, editar y
 * eliminar. Son privadas: solo las ve la organización. Las demás cuentas
 * de la misma organización las ven al instante (tiempo real).
 */
@Component({
  selector: 'app-modal-notas',
  imports: [FormsModule, Icono],
  templateUrl: './modal-notas.html',
  styleUrls: ['../../../../../shared/modal-acompanantes/modal-acompanantes.css', './modal-notas.css']
})
export class ModalNotas implements OnInit {
  private readonly organizacionService = inject(OrganizacionService);

  readonly idPersonaMayor = input.required<number>();
  readonly nombre = input<string | null | undefined>(null);
  readonly cerrar = output<void>();

  protected readonly notas = signal<NotaPersonaMayor[]>([]);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);

  // Nota nueva
  protected textoNuevo = '';
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  // Edición y eliminación (una nota a la vez)
  protected readonly editando = signal<number | null>(null);
  protected textoEditado = '';
  protected readonly eliminando = signal<number | null>(null);
  /** Nota sobre la que hay una petición en curso. */
  protected readonly procesando = signal<number | null>(null);

  protected readonly MAX_TEXTO = MAX_TEXTO;

  constructor() {
    // Otra cuenta de la organización agregó, editó o borró una nota.
    alCambiar(['notas'], () => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected guardar(): void {
    const texto = this.textoNuevo.trim();
    if (!texto || this.guardando()) return;

    this.guardando.set(true);
    this.error.set(null);

    this.organizacionService.crearNota(this.idPersonaMayor(), texto).subscribe({
      next: (nota) => {
        this.guardando.set(false);
        this.textoNuevo = '';
        this.notas.update((notas) => [nota, ...notas]);
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(err, 'No se pudo guardar la nota. Intenta de nuevo.'));
      }
    });
  }

  protected empezarEdicion(nota: NotaPersonaMayor): void {
    this.eliminando.set(null);
    this.error.set(null);
    this.textoEditado = nota.texto;
    this.editando.set(nota.idNota);
  }

  protected guardarEdicion(nota: NotaPersonaMayor): void {
    const texto = this.textoEditado.trim();
    if (!texto || texto === nota.texto) {
      this.editando.set(null);
      return;
    }

    this.ejecutar(nota, this.organizacionService.editarNota(this.idPersonaMayor(), nota.idNota, texto),
      'No se pudo editar la nota.', (editada) => {
        this.notas.update((notas) => notas.map((n) => (n.idNota === nota.idNota ? editada : n)));
        this.editando.set(null);
      });
  }

  protected confirmarEliminacion(nota: NotaPersonaMayor): void {
    this.ejecutar(nota, this.organizacionService.eliminarNota(this.idPersonaMayor(), nota.idNota),
      'No se pudo eliminar la nota.', () => {
        this.notas.update((notas) => notas.filter((n) => n.idNota !== nota.idNota));
        this.eliminando.set(null);
      });
  }

  /** "14 de octubre de 2026, 3:20 p. m." */
  protected fecha(fecha: string): string {
    return new Date(fecha).toLocaleString('es-CO', {
      day: 'numeric', month: 'long', year: 'numeric', hour: 'numeric', minute: '2-digit'
    });
  }

  private ejecutar<T>(nota: NotaPersonaMayor, peticion: Observable<T>, porDefecto: string,
                      alTerminar: (respuesta: T) => void): void {
    this.procesando.set(nota.idNota);
    this.error.set(null);

    peticion.subscribe({
      next: (respuesta) => {
        this.procesando.set(null);
        alTerminar(respuesta);
      },
      error: (err) => {
        this.procesando.set(null);
        this.error.set(mensajeDeError(err, porDefecto));
      }
    });
  }

  private cargar(): void {
    this.organizacionService.obtenerNotas(this.idPersonaMayor()).subscribe({
      next: (notas) => {
        this.notas.set(notas);
        this.errorCarga.set(null);
        this.cargando.set(false);
      },
      error: (err) => {
        this.errorCarga.set(mensajeDeError(err, 'No se pudieron cargar las notas.'));
        this.cargando.set(false);
      }
    });
  }
}
