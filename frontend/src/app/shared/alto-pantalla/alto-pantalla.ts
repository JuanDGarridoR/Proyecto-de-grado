import { AfterViewInit, Directive, ElementRef, HostListener, inject } from '@angular/core';

/**
 * Para los inicios que en pantallas grandes ocupan justo el alto de la
 * ventana (sin scroll): calcula cuánto queda debajo de la barra superior y
 * lo pasa al CSS en --alto-disponible. Se recalcula al cambiar el tamaño de
 * la ventana. Cada página decide en su CSS desde qué tamaño la usa; en
 * celulares no la usa y la página se desplaza normalmente.
 *
 * Se aplica con hostDirectives: [AltoPantalla] en el componente.
 */
@Directive({ selector: '[appAltoPantalla]', standalone: true })
export class AltoPantalla implements AfterViewInit {
  private elemento = inject<ElementRef<HTMLElement>>(ElementRef);

  ngAfterViewInit(): void {
    this.ajustar();
  }

  @HostListener('window:resize')
  ajustar(): void {
    const host = this.elemento.nativeElement;
    const contenedor = host.parentElement;
    const inicio = host.getBoundingClientRect().top + window.scrollY;
    const margenInferior = contenedor ? parseFloat(getComputedStyle(contenedor).paddingBottom) || 0 : 0;

    host.style.setProperty('--alto-disponible', `${Math.floor(window.innerHeight - inicio - margenInferior)}px`);
  }
}
