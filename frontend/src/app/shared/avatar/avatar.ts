import { Component, input, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { Icono } from '../icono/icono';
import { FotoPerfilPipe } from '../../core/foto-perfil/foto-perfil';

/**
 * Foto de perfil de una persona u organización, o su ícono si no tiene
 * (o si la foto no carga). Ocupa todo el contenedor en el que se pone: el
 * tamaño, el fondo y la forma (círculo o esquinas redondeadas) los define
 * ese contenedor, que debe tener overflow: hidden para recortar la foto.
 */
@Component({
  selector: 'app-avatar',
  imports: [Icono, AsyncPipe, FotoPerfilPipe],
  template: `
    @let src = foto() | fotoPerfil | async;
    @if (src && src !== fotoRota()) {
      <img [src]="src" alt="" (error)="fotoRota.set(src)" />
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
  /** Ruta de la foto de perfil (ver FotoPerfilService); null o vacío muestra el ícono. */
  readonly foto = input<string | null | undefined>(null);
  readonly icono = input('user');

  /** Foto que falló al cargar, para no reintentarla. */
  protected readonly fotoRota = signal<string | null>(null);
}
