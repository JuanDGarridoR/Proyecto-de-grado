import { Component, computed } from '@angular/core';
import { ActivatedRoute, RouterOutlet } from '@angular/router';
import { MarcoPanel, OpcionMenuPanel } from '../marco-panel/marco-panel';
import { AuthService } from '../../core/auth/auth.service';
import { CONFIGURACION_PANEL } from '../configuracion-panel/configuracion-panel';

/**
 * Plantilla de los paneles: arma MarcoPanel con la configuración del rol
 * de la ruta (data.rol) y muestra la página hija adentro.
 */
@Component({
  selector: 'app-plantilla-panel',
  standalone: true,
  imports: [MarcoPanel, RouterOutlet],
  template: `
    <app-marco-panel
      [roleLabel]="roleLabel"
      [roleAccent]="roleAccent"
      [userName]="nombreUsuario()"
      [userInitials]="iniciales()"
      [navItems]="navItems"
      [accessible]="true"
    >
      <router-outlet></router-outlet>
    </app-marco-panel>
  `
})
export class PlantillaPanel {
  /** Se recalcula solo cuando cambia el nombre en AuthService (por ejemplo, en "Mi información"). */
  protected readonly nombreUsuario = computed(() => this.authService.getNombreUsuario());
  protected readonly iniciales = computed(() => this.nombreUsuario().charAt(0).toUpperCase());

  protected readonly roleLabel: string;
  protected readonly roleAccent: string;
  protected readonly navItems: OpcionMenuPanel[];

  constructor(
      private route: ActivatedRoute,
      private authService: AuthService
  ) {
      const rol = this.route.snapshot.data['rol'] as string;
      const config = CONFIGURACION_PANEL[rol];

      this.roleLabel = config?.roleLabel ?? '';
      this.roleAccent = config?.roleAccent ?? 'var(--vita-navy)';
      this.navItems = config?.navItems ?? [];
  }
}
