import { AfterContentInit, Component, ElementRef, inject } from '@angular/core';
import { Icono } from '../icono/icono';

/**
 * Envuelve un <input type="password"> y le agrega un botón con un ojo para
 * mostrar u ocultar la contraseña. El input se proyecta tal cual, así que
 * conserva su id, clases y [(ngModel)].
 *
 * Uso:
 *   <app-campo-contrasena>
 *     <input type="password" [(ngModel)]="contrasena" />
 *   </app-campo-contrasena>
 */
@Component({
  selector: 'app-campo-contrasena',
  standalone: true,
  imports: [Icono],
  template: `
    <ng-content />
    <button
      type="button"
      class="campo-contrasena__toggle"
      [attr.aria-label]="visible ? 'Ocultar contraseña' : 'Mostrar contraseña'"
      [attr.aria-pressed]="visible"
      [title]="visible ? 'Ocultar contraseña' : 'Mostrar contraseña'"
      (click)="alternar()"
    >
      <app-icono [name]="visible ? 'eye-off' : 'eye'" />
    </button>
  `,
  styles: [
    `
      :host {
        position: relative;
        display: block;
        width: 100%;
      }
      :host ::ng-deep input {
        width: 100%;
        box-sizing: border-box;
        padding-right: 44px;
      }
      .campo-contrasena__toggle {
        position: absolute;
        top: 50%;
        right: 6px;
        transform: translateY(-50%);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 34px;
        height: 34px;
        padding: 0;
        border: none;
        border-radius: 8px;
        background: transparent;
        color: var(--vita-text-muted, #6b7280);
        font-size: 1.15rem;
        cursor: pointer;
        transition: color 0.2s ease, background 0.2s ease;
      }
      .campo-contrasena__toggle:hover,
      .campo-contrasena__toggle:focus-visible {
        color: var(--vita-orange);
        background: color-mix(in srgb, var(--vita-orange) 10%, transparent);
        outline: none;
      }
    `,
  ],
})
export class CampoContrasena implements AfterContentInit {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private input: HTMLInputElement | null = null;

  protected visible = false;

  ngAfterContentInit(): void {
    this.input = this.host.nativeElement.querySelector('input');
  }

  protected alternar(): void {
    if (!this.input) return;
    this.visible = !this.visible;
    this.input.type = this.visible ? 'text' : 'password';
  }
}
