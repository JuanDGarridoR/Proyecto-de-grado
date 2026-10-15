import { Component, inject } from '@angular/core';

import { LectorService } from '../../core/lector/lector.service';
import { Icono } from '../icono/icono';

/**
 * Círculo flotante, siempre visible en una esquina, que prende y apaga el
 * lector de voz. Va una sola vez en app.html, así aparece en toda la
 * plataforma (también encima de los modales).
 */
@Component({
  selector: 'app-lector-voz',
  imports: [Icono],
  templateUrl: './lector-voz.html',
  styleUrl: './lector-voz.css'
})
export class LectorVoz {
  protected readonly lector = inject(LectorService);
}
