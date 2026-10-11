import { Component } from '@angular/core';

/** Cifra del panel de ejemplo. */
interface DashboardStat {
  icon: string;
  value: string;
  delta: string;
  label: string;
}

/** Vista previa del panel con cifras de ejemplo. Está desactivada en la landing. */
@Component({
  selector: 'app-vista-previa-panel',
  imports: [],
  templateUrl: './vista-previa-panel.html',
  styleUrl: './vista-previa-panel.css'
})
export class VistaPreviaPanel {
  protected readonly stats: DashboardStat[] = [
    {
      icon: '👥',
      value: '2,210',
      delta: '+12%',
      label: 'Personas mayores registradas'
    },
    {
      icon: '🤝',
      value: '740',
      delta: '+8%',
      label: 'Acompañantes activos'
    },
    {
      icon: '⭐',
      value: '460',
      delta: '+15%',
      label: 'Voluntarios en programa'
    },
    {
      icon: '🏥',
      value: '38',
      delta: '+3%',
      label: 'Instituciones aliadas'
    }
  ];
}
