import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  AnaliticaService,
  OrganizacionRecomendada,
  RecomendacionesOrganizaciones
} from '../../../../../core/analitica/analitica.service';
import { alCambiar } from '../../../../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../../../../../shared/icon/icon';

/**
 * "Organizaciones que te pueden interesar": recomendaciones de
 * analitica-service según los gustos de la persona mayor (actividades y
 * personas con gustos parecidos) y la cercanía de su barrio.
 */
@Component({
  selector: 'app-recomendaciones-organizaciones',
  imports: [Icon, RouterLink],
  templateUrl: './recomendaciones.html',
  styleUrl: './recomendaciones.css'
})
export class RecomendacionesOrganizacionesComponent implements OnInit {

  private analiticaService = inject(AnaliticaService);

  protected readonly datos = signal<RecomendacionesOrganizaciones | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal(false);

  constructor() {
    // Cambian si la persona edita sus gustos, se une a una organización o
    // una organización crea actividades.
    alCambiar(['gustos', 'organizaciones', 'actividades', 'usuarios'], () => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.analiticaService.recomendacionesOrganizaciones().subscribe({
      next: (datos) => {
        this.datos.set(datos);
        this.error.set(false);
        this.cargando.set(false);
      },
      error: (error) => {
        console.error('Error al cargar las recomendaciones:', error);
        this.error.set(true);
        this.cargando.set(false);
      }
    });
  }

  /** Texto del nivel de coincidencia (además del porcentaje). */
  protected nivel(puntaje: number): string {
    if (puntaje >= 70) return 'Muy recomendada para ti';
    if (puntaje >= 40) return 'Recomendada para ti';
    return 'Podría interesarte';
  }

  /** Solo se muestran las que tienen alguna razón para recomendarse. */
  protected conRazones(lista: OrganizacionRecomendada[]): OrganizacionRecomendada[] {
    return lista.filter((o) => o.puntaje > 0 && o.razones.length > 0);
  }
}
