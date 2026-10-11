import { Component, Signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { Icono } from '../icono/icono';

/**
 * Página temporal de las secciones que todavía no existen. Muestra el
 * título que llega en data.titulo de la ruta.
 */
@Component({
  selector: 'app-en-construccion',
  standalone: true,
  imports: [Icono],
  template: `
    <div class="en-construccion">
      <span class="en-construccion__icon" aria-hidden="true"><app-icono name="wrench" /></span>
      <h2>{{ titulo() }}</h2>
      <p>Esta sección está en construcción. Muy pronto vas a poder usarla desde aquí.</p>
    </div>
  `,
  styles: [`
    .en-construccion {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: 60px 20px;
      color: var(--vita-text-gray);
    }
    .en-construccion__icon {
      font-size: 2.5rem;
      margin-bottom: 12px;
    }
    .en-construccion h2 {
      color: var(--vita-navy);
      margin-bottom: 8px;
    }
  `]
})
export class EnConstruccion {
  protected readonly titulo: Signal<string>;

  constructor(private route: ActivatedRoute) {
    this.titulo = toSignal(
      this.route.data.pipe(map((data) => data['titulo'] ?? 'Próximamente')),
      { initialValue: 'Próximamente' }
    );
  }
}