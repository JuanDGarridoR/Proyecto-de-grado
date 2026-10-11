import { Component } from '@angular/core';

/**
 * Llamado a la acción: invita a contactar al equipo. Está desactivada en la
 * landing y las cifras son de ejemplo.
 */
@Component({
  selector: 'app-llamado-accion',
  imports: [],
  templateUrl: './llamado-accion.html',
  styleUrl: './llamado-accion.css'
})
export class LlamadoAccion {
  protected readonly stats = [
    { number: '12', label: 'Módulos integrados' },
    { number: '38+', label: 'Organizaciones' },
    { number: '100%', label: 'Datos seguros' }
  ];
}
