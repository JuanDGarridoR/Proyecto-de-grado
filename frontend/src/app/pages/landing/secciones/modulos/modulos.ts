import { Component } from '@angular/core';

/** Sección con los módulos de la plataforma, en tarjetas. */
@Component({
  selector: 'app-modulos',
  imports: [],
  templateUrl: './modulos.html',
  styleUrl: './modulos.css'
})
export class Modulos {
  protected readonly modules: { icon: string; title: string; subtitle: string; badge?: string }[] = [
    { icon: '🧓', title: 'Personas Mayores', subtitle: 'Expedientes digitales', badge: 'Nuevo' },
    { icon: '🤝', title: 'Acompañantes', subtitle: 'Gestión y asignación', badge: 'Nuevo' },
    { icon: '⭐', title: 'Voluntarios', subtitle: 'Coordinación y tareas', badge: 'Nuevo' },
    { icon: '🏥', title: 'Organizaciones', subtitle: 'Alianzas y redes' },
    { icon: '💊', title: 'Medicamentos', subtitle: 'Control y alertas' },
    { icon: '🏃', title: 'Actividades', subtitle: 'Programación y seguimiento' },
    { icon: '🔔', title: 'Alertas', subtitle: 'Notificaciones críticas' },
    { icon: '🔬', title: 'Analítica', subtitle: 'Inteligencia de datos' }
  ];
}
