import { Component, HostListener } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Barra de navegación de la landing: enlaces a cada sección y, si hay sesión,
 * el menú del usuario.
 */
@Component({
  selector: 'app-barra-navegacion',
  imports: [RouterLink],
  templateUrl: './barra-navegacion.html',
  styleUrl: './barra-navegacion.css'
})
export class BarraNavegacion {

  /** Secciones de la landing (anclas dentro de la misma página). */
  protected readonly navLinks = [
    { label: 'Inicio', href: '#hero' },
    { label: 'El Reto', href: '#reto' },
    { label: 'Quiénes Somos', href: '#quienes-somos' },
    { label: 'Misión', href: '#mission' },
    { label: 'Módulos', href: '#modules' },
    { label: 'Contacto', href: '#contacto' }
  ];

  protected readonly autenticado;
  protected menuUsuarioAbierto = false;

  constructor(
    protected authService: AuthService
  ) {
    this.autenticado = this.authService.estaAutenticadoSignal();
  }

  get nombreUsuario(): string {
    return this.authService.getNombreUsuario();
  }

  get inicialUsuario(): string {
    return this.nombreUsuario
      .trim()
      .charAt(0)
      .toUpperCase();
  }

  irAlDashboard(): void {
    const rol = this.authService.getRol();

    if (rol) {
      this.authService.redirigirSegunRol(rol);
    }
  }

  toggleMenuUsuario(): void {
    this.menuUsuarioAbierto = !this.menuUsuarioAbierto;
    console.log('Menú:', this.menuUsuarioAbierto);
  }

  /** Por ahora lleva al inicio del panel del rol. */
  irAMiInformacion(): void {
    this.menuUsuarioAbierto = false;

    const rol = this.authService.getRol();

    if (rol) {
      this.authService.redirigirSegunRol(rol);
    }
  }

  /** Cierra el menú del usuario al hacer clic por fuera. */
  @HostListener('document:click', ['$event'])
  cerrarMenuAlHacerClickAfuera(event: MouseEvent): void {
    const target = event.target as HTMLElement;

    if (!target.closest('.navbar__user')) {
      this.menuUsuarioAbierto = false;
    }
  }

  cerrarSesion(): void {
    this.authService.logout();
  }
}