import { Component, input, signal } from '@angular/core';
import { Icono } from '../icono/icono';

/**
 * Foto de perfil de una persona u organización, o su ícono si no tiene
 * (o si la foto no carga). Ocupa todo el contenedor en el que se pone: el
 * tamaño, el fondo y la forma (círculo o esquinas redondeadas) los define
 * ese contenedor, que debe tener overflow: hidden para recortar la foto.
 */
@Component({
  selector: 'app-avatar',
  imports: [Icono],
  template: `
    @if (foto() && foto() !== fotoRota()) {
      <img [src]="foto()" alt="" loading="lazy" (error)="fotoRota.set(foto() ?? null)" />
    } @else {
      <app-icono [name]="icono()" />
    }
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
    }

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  `,
})
export class Avatar {
  /** URL de la foto de perfil; null o vacío muestra el ícono. */
  readonly foto = input<string | null | undefined>(null);
  readonly icono = input('user');

  /** Foto que falló al cargar, para no reintentarla. */
  protected readonly fotoRota = signal<string | null>(null);
}
