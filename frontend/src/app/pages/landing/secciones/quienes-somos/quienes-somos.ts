import { Component, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Sección "¿Quiénes somos?": el equipo y las tarjetas de propósito, visión e
 * impacto social.
 */
@Component({
  selector: 'app-quienes-somos',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './quienes-somos.html',
  styleUrls: ['./quienes-somos.css'],
  encapsulation: ViewEncapsulation.None 
})
export class QuienesSomosComponent {
  // Hoy la plantilla tiene las tarjetas escritas directamente y no usa este arreglo.
  public cards = [
    {
      title: 'Tecnología con propósito',
      category: 'NUESTRO PROPÓSITO',
      iconPath: 'assets/icons/target-icon.svg',
      description: 'Buscamos conectar la tecnología con las necesidades reales de las organizaciones que acompañan a personas mayores, facilitando la gestión de información y aprovechando la analítica de datos para generar conocimiento que contribuya a fortalecer sus acciones.',
    },
    {
      title: 'Nuestra misión',
      category: 'NUESTRO MISIÓN',
      iconPath: 'assets/icons/rocket-icon.svg',
      description: 'Desarrollar una plataforma web que permita a las organizaciones que acompañan a personas mayores gestionar, organizar y comprender mejor su información, integrando tecnología y analítica de datos como herramientas de apoyo para el seguimiento y la toma de decisiones.',
    },
    {
      title: 'Nuestra visión',
      category: 'NUESTRO VISIÓN',
      iconPath: 'assets/icons/sun-icon.svg',
      description: 'Contribuir a que las organizaciones que trabajan con personas mayores cuenten con herramientas tecnológicas accesibles que les permitan transformar sus datos en información para fortalecer sus procesos, comprender mejor las necesidades de su comunidad y ampliar el impacto de sus acciones.',
    },
  ];
}