import { Component, AfterViewInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { LectorVoz } from './shared/lector-voz/lector-voz';

/**
 * Componente raíz: contiene el router y el botón del lector de voz, que así
 * aparece en todas las páginas. Además desactiva el
 * autocompletado del navegador en todos los campos, también en los que
 * aparecen después al cambiar de página o abrir un modal.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, LectorVoz],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements AfterViewInit {

  ngAfterViewInit(): void {
    const aplicarAutocompleteOff = () => {
      document.querySelectorAll('input, textarea, select, form').forEach((element) => {
        element.setAttribute('autocomplete', 'off');
      });
    };

    // Campos que ya están en pantalla.
    aplicarAutocompleteOff();

    // Y los que se agreguen después al DOM.
    const observer = new MutationObserver(() => {
      aplicarAutocompleteOff();
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true
    });
  }
}