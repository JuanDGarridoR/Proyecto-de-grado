import { Component } from '@angular/core';

/** Pie de página de la landing: marca, contacto y enlace al repositorio. */
@Component({
  selector: 'app-pie-pagina',
  imports: [],
  templateUrl: './pie-pagina.html',
  styleUrl: './pie-pagina.css'
})
export class PiePagina {
  protected readonly anioActual = 2026;

  protected readonly redesSociales = [
    { texto: 'GH', etiqueta: 'GitHub Repository', url: 'https://github.com/JuanDGarridoR/Proyecto-de-grado.git' },
  ];

  // Textos para futuras columnas de enlaces; hoy la plantilla no los muestra.
  protected readonly enlacesPlataforma = [
    'Módulos',
    'Analítica',
    'Integraciones',
    'API pública',
    'Seguridad'
  ];

  protected readonly enlacesOrganizacion = [
    'Nuestra misión',
    'Equipo',
    'Alianzas',
    'Blog',
    'Prensa'
  ];
}