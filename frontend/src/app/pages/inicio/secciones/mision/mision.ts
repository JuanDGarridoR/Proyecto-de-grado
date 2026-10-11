import { Component } from '@angular/core';

/**
 * Sección "Nuestra misión" de la landing: introducción y los tres pilares
 * del proyecto.
 */
@Component({
  selector: 'app-mision',
  imports: [],
  templateUrl: './mision.html',
  styleUrl: './mision.css'
})
export class Mision {
  protected readonly pillars = [
    {
      icon: '🤝',
      title: 'Colaboración real',
      text: 'Todos los actores conectados en tiempo real, coordinados para el mismo objetivo.'
    },
    {
      icon: '📱',
      title: 'Tecnología accesible',
      text: 'Diseñada para que cualquier persona, con o sin conocimiento técnico, pueda usarla.'
    },
    {
      icon: '💡',
      title: 'Impacto medible',
      text: 'Cada acción queda registrada y se convierte en datos para mejorar continuamente.'
    }
  ];
}
