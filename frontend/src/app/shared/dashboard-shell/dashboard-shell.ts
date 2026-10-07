import { Component, ElementRef, HostListener, Input, OnDestroy, OnInit, signal } from '@angular/core';
import { IsActiveMatchOptions, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Icon } from '../icon/icon';
import { alCambiar } from '../../core/tiempo-real/tiempo-real.service';
import { Notificacion, NotificacionService } from '../../core/notificaciones/notificacion.service';

/** Opción del menú lateral. */
export interface ShellNavItem {
  /** Nombre de icono (ver set en shared/icon/icon.ts). */
  icon: string;
  label: string;
  active?: boolean;
  path?: string;
}

/** Página de "Mi información" de cada rol. */
const RUTA_MI_INFORMACION: Record<string, string> = {
  PERSONA_MAYOR: '/panel/persona-mayor/perfil',
  ACOMPANANTE: '/panel/acompanante/perfil',
  ORGANIZACION: '/panel/organizacion/perfil',
  VOLUNTARIO: '/panel/voluntario/perfil'
};

/**
 * Estructura común de los paneles: barra lateral con el menú del rol y barra
 * superior con la campanita de notificaciones y el menú del usuario.
 */
@Component({
  selector: 'app-dashboard-shell',
  imports: [RouterLink, RouterLinkActive, Icon],
  templateUrl: './dashboard-shell.html',
  styleUrl: './dashboard-shell.css',
  host: {
    '[style.--role-accent]': 'roleAccent',
    '[class.shell-host--accessible]': 'accessible'
  }
})
export class DashboardShell implements OnInit, OnDestroy {

  @Input() roleLabel = '';
  /** Color de acento del rol; llega al CSS como --role-accent. */
  @Input() roleAccent = 'var(--vita-navy)';
  @Input() userName = '';
  @Input() userInitials = '';
  @Input() navItems: ShellNavItem[] = [];
  /** Menú con letra e iconos más grandes. PanelShellLayout lo activa en todos los paneles. */
  @Input() accessible = false;

  menuUsuarioAbierto = false;

  /**
   * La opción del menú se marca solo con la ruta exacta, sin mirar los
   * parámetros: "Gestionar cuidado" se abre a veces con ?persona=<id>.
   */
  protected readonly opcionesEnlaceActivo: IsActiveMatchOptions = {
    paths: 'exact',
    queryParams: 'ignored',
    matrixParams: 'ignored',
    fragment: 'ignored'
  };

  // Notificaciones (campanita)
  protected readonly notificaciones = signal<Notificacion[]>([]);
  protected readonly noLeidas = signal(0);
  protected readonly panelNotificacionesAbierto = signal(false);
  protected readonly errorNotificaciones = signal(false);
  private intervaloNotificaciones?: ReturnType<typeof setInterval>;

  constructor(
    private authService: AuthService,
    private notificacionService: NotificacionService,
    private router: Router,
    private elementRef: ElementRef<HTMLElement>
  ) {
    // Las emergencias y las notificaciones leídas en otra pestaña llegan al instante.
    alCambiar(['notificaciones'], () => this.cargarNotificaciones());
  }

  ngOnInit(): void {
    this.cargarNotificaciones();

    // Los recordatorios los envían tareas programadas del backend, que no
    // generan aviso en tiempo real: se consultan cada minuto.
    this.intervaloNotificaciones = setInterval(() => this.cargarNotificaciones(), 60_000);
  }

  ngOnDestroy(): void {
    clearInterval(this.intervaloNotificaciones);
  }

  private cargarNotificaciones(): void {
    this.notificacionService.listar().subscribe({
      next: (respuesta) => {
        this.notificaciones.set(respuesta.notificaciones);
        this.noLeidas.set(respuesta.noLeidas);
        this.errorNotificaciones.set(false);
      },
      error: () => this.errorNotificaciones.set(true)
    });
  }

  /** Abre o cierra el panel de la campanita. */
  toggleNotificaciones(): void {
    const abrir = !this.panelNotificacionesAbierto();
    this.panelNotificacionesAbierto.set(abrir);
    this.menuUsuarioAbierto = false;

    // Al abrir, todas quedan leídas. La lista conserva su marca de "nueva"
    // mientras el panel siga abierto, para que se vea cuáles eran.
    if (abrir && this.noLeidas() > 0) {
      this.noLeidas.set(0);
      this.notificacionService.marcarLeidas().subscribe({
        error: () => this.cargarNotificaciones()
      });
    }

    if (!abrir) {
      this.notificaciones.update((lista) => lista.map((n) => ({ ...n, leida: true })));
    }
  }

  /** Cierra el panel de la campanita o el menú del usuario al hacer clic por fuera. */
  @HostListener('document:click', ['$event'])
  protected cerrarPanelSiClicFuera(evento: MouseEvent): void {
    if (this.menuUsuarioAbierto) {
      const usuario = this.elementRef.nativeElement.querySelector('.shell__user');
      if (usuario && !usuario.contains(evento.target as Node)) {
        this.menuUsuarioAbierto = false;
      }
    }

    if (!this.panelNotificacionesAbierto()) {
      return;
    }

    const campanita = this.elementRef.nativeElement.querySelector('.shell__notif');
    if (campanita && !campanita.contains(evento.target as Node)) {
      this.toggleNotificaciones();
    }
  }

  /** Fecha corta para la campanita: "Hoy, 3:05 p. m.", "Ayer, 8:00 a. m." o "12 sep, 8:00 a. m.". */
  protected formatearFecha(fechaIso: string): string {
    const fecha = new Date(fechaIso);
    const hora = fecha.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' });

    const hoy = new Date();
    const ayer = new Date(hoy.getTime() - 24 * 60 * 60 * 1000);

    if (fecha.toDateString() === hoy.toDateString()) return `Hoy, ${hora}`;
    if (fecha.toDateString() === ayer.toDateString()) return `Ayer, ${hora}`;

    return `${fecha.toLocaleDateString('es-CO', { day: 'numeric', month: 'short' })}, ${hora}`;
  }

  toggleMenuUsuario(): void {
    this.menuUsuarioAbierto = !this.menuUsuarioAbierto;
    this.panelNotificacionesAbierto.set(false);
  }

  cerrarSesion(): void {
    this.authService.logout();
  }

  irAMiInformacion(): void {
    this.menuUsuarioAbierto = false;
    const rol = this.authService.getRol();
    const ruta = rol ? RUTA_MI_INFORMACION[rol] : undefined;

    if (ruta) {
      this.router.navigateByUrl(ruta);
    }
  }
}