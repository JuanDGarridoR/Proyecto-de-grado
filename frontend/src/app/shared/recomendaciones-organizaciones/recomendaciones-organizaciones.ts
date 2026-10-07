import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import {
  AnaliticaService,
  OrganizacionRecomendada,
  RecomendacionesOrganizaciones
} from '../../core/analitica/analitica.service';
import { OrganizacionService } from '../../core/organizacion/organizacion.service';
import { VoluntarioService } from '../../core/voluntario/voluntario.service';
import { alCambiar } from '../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../icon/icon';

/**
 * "Organizaciones que te pueden interesar": recomendaciones de
 * analitica-service según los gustos de la persona mayor o del voluntario
 * (actividades y personas mayores con gustos parecidos) y la cercanía de
 * su dirección. Desde cada tarjeta se puede solicitar la vinculación; la
 * organización la acepta o la rechaza. Al enviarla, la organización sale
 * de la lista (el recomendador no muestra las que tienen un vínculo
 * pendiente) y pasa a las solicitudes de la página.
 */
@Component({
  selector: 'app-recomendaciones-organizaciones',
  imports: [Icon, RouterLink],
  templateUrl: './recomendaciones-organizaciones.html',
  styleUrl: './recomendaciones-organizaciones.css'
})
export class RecomendacionesOrganizacionesComponent implements OnInit {

  private analiticaService = inject(AnaliticaService);
  private organizacionService = inject(OrganizacionService);
  private voluntarioService = inject(VoluntarioService);

  /** Quién ve las recomendaciones: cambia los enlaces y a dónde va la solicitud. */
  readonly rol = input<'PERSONA_MAYOR' | 'VOLUNTARIO'>('PERSONA_MAYOR');

  /** Se emite al enviar una solicitud, para que la página recargue sus solicitudes. */
  readonly solicitada = output<void>();

  protected readonly datos = signal<RecomendacionesOrganizaciones | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal(false);

  /** Organización cuya solicitud se está enviando; deshabilita su botón. */
  protected readonly enviando = signal<number | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly errorSolicitud = signal<string | null>(null);

  constructor() {
    // Cambian si la persona edita sus gustos, se une a una organización o
    // una organización crea actividades.
    alCambiar(['gustos', 'organizaciones', 'voluntarios', 'actividades', 'usuarios'], () => this.cargar());
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

  /** Envía la solicitud de vinculación a la organización. */
  protected solicitar(org: OrganizacionRecomendada): void {
    this.enviando.set(org.idOrganizacion);
    this.mensaje.set(null);
    this.errorSolicitud.set(null);

    const peticion: Observable<string> = this.rol() === 'VOLUNTARIO'
      ? this.voluntarioService.solicitarVinculacion(org.idOrganizacion)
      : this.organizacionService.solicitarVinculacionOrganizacion(org.idOrganizacion);

    peticion.subscribe({
      next: (respuesta) => {
        this.enviando.set(null);
        this.mensaje.set(`${org.nombre}: ${respuesta}`);
        this.solicitada.emit();
        this.cargar();
      },
      error: (error) => {
        this.enviando.set(null);
        this.errorSolicitud.set(
          typeof error?.error === 'string' && error.error ? error.error : 'No se pudo enviar la solicitud.'
        );
      }
    });
  }

  /** Ruta base del panel del rol, para los enlaces a intereses y perfil. */
  protected panel(): string {
    return this.rol() === 'VOLUNTARIO' ? '/panel/voluntario' : '/panel/persona-mayor';
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
