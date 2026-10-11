import { Component } from '@angular/core';

/** Portada de la landing: título, tarjetas flotantes y los roles de la plataforma. */
@Component({
  selector: 'app-portada',
  imports: [],
  templateUrl: './portada.html',
  styleUrl: './portada.css'
})
export class Portada {
  // Hoy la plantilla tiene estos textos escritos directamente y no usa
  // estos arreglos.
  protected readonly stats = [
    { value: '2,200+', label: 'Personas mayores' },
    { value: '840+', label: 'Acompañantes activos' },
    { value: '95%', label: 'Satisfacción' }
  ];

  protected readonly floatCards = [
    { icon: '🔔', label: 'Recordatorio', text: 'Medicación 14:00', accent: false },
    { icon: '🏃', label: 'Actividad', text: 'Fisioterapia', accent: true },
    { icon: '📊', label: 'Indicador', text: 'Todo en orden', accent: false },
    { icon: '🖥️', label: 'Dashboard', text: 'Ver en vivo', accent: false },
    { icon: '⚠️', label: 'Alerta', text: 'Revisar visita', accent: true }
  ];

  protected readonly rolePills = [
    { label: 'Institución', className: 'pill-institution' },
    { label: 'Persona Mayor', className: 'pill-elder' },
    { label: 'Acompañante', className: 'pill-companion' },
    { label: 'Voluntario', className: 'pill-volunteer' }
  ];
}
