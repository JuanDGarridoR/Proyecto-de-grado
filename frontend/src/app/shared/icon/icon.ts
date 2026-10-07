import { Component, Input, OnChanges, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Set de iconos de línea (estilo consistente, trazo de 1.75) usados en toda la
 * aplicación en reemplazo de los emojis. Cada entrada es el contenido interno de
 * un <svg viewBox="0 0 24 24">. El color se hereda con `currentColor` y el
 * tamaño con `1em`, de modo que basta con ajustar `font-size` / `color` en el
 * contenedor.
 */
const ICONS: Record<string, string> = {
  home: '<path d="M3 10.75 12 4l9 6.75"/><path d="M5.5 9.5V20h13V9.5"/><path d="M10 20v-5h4v5"/>',
  activity: '<path d="M3 12h4l3 8 4-16 3 8h4"/>',
  heart:
    '<path d="M12 20s-7-4.35-9.5-8.5A5 5 0 0 1 12 6a5 5 0 0 1 9.5 5.5C19 15.65 12 20 12 20Z"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/>',
  users:
    '<circle cx="8.5" cy="8.5" r="3"/><path d="M2.5 19.5a6 6 0 0 1 12 0"/><path d="M15.5 6a3 3 0 0 1 0 6"/><path d="M16.5 14c2.4.7 4 2.6 4 5.5"/>',
  building:
  '<path d="M4 21V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v16"/><path d="M8 8h2M14 8h2M8 12h2M14 12h2M8 16h2M14 16h2"/><path d="M2.5 21h19"/>',
  phone:
    '<path d="M6.5 3h3l1.5 5-2 1.5a12 12 0 0 0 5.5 5.5l1.5-2 5 1.5v3a2 2 0 0 1-2 2A16 16 0 0 1 4.5 5a2 2 0 0 1 2-2Z"/>',
  mail:
  '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m4 7 8 6 8-6"/>',
  star: '<path d="m12 3.5 2.6 5.27 5.82.85-4.21 4.1.99 5.8L12 16.9l-5.2 2.72.99-5.8-4.21-4.1 5.82-.85Z"/>',
  stethoscope:
    '<path d="M5 3H4a1.5 1.5 0 0 0-1.5 1.5V9a5.5 5.5 0 0 0 11 0V4.5A1.5 1.5 0 0 0 12 3h-1"/><path d="M8 14.5v1a5.5 5.5 0 0 0 11 0V13"/><circle cx="19" cy="10.5" r="2.25"/>',
  pill: '<path d="M10.5 3.5 3.5 10.5a4.95 4.95 0 0 0 7 7l7-7a4.95 4.95 0 0 0-7-7Z"/><path d="m7 7 7 7"/>',
  gift:
    '<path d="M20 12v8a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-8"/><rect x="2.5" y="7.5" width="19" height="4.5" rx="1"/><path d="M12 7.5V21"/><path d="M12 7.5S10.5 3 8 3a2.25 2.25 0 0 0 0 4.5Z"/><path d="M12 7.5S13.5 3 16 3a2.25 2.25 0 0 1 0 4.5Z"/>',
  bell: '<path d="M6 9a6 6 0 0 1 12 0c0 4.5 1.5 6 2 6.5H4c.5-.5 2-2 2-6.5Z"/><path d="M10 19a2 2 0 0 0 4 0"/>',
  'bar-chart':
    '<path d="M4 20V4"/><path d="M4 20h17"/><rect x="6.5" y="11" width="3" height="6" rx="0.5"/><rect x="11.5" y="7" width="3" height="10" rx="0.5"/><rect x="16.5" y="13.5" width="3" height="3.5" rx="0.5"/>',
  map: '<path d="m9 4-5.5 2v14L9 18l6 2 5.5-2V4L15 6 9 4Z"/><path d="M9 4v14"/><path d="M15 6v14"/>',
  settings:
    '<circle cx="12" cy="12" r="3.25"/><path d="M19.4 12c0 .4 0 .8-.1 1.2l2 1.6-2 3.4-2.4-1a7.3 7.3 0 0 1-2 1.2L14.4 21H9.6l-.5-2.6a7.3 7.3 0 0 1-2-1.2l-2.4 1-2-3.4 2-1.6a7.4 7.4 0 0 1 0-2.4l-2-1.6 2-3.4 2.4 1a7.3 7.3 0 0 1 2-1.2L9.6 3h4.8l.5 2.6a7.3 7.3 0 0 1 2 1.2l2.4-1 2 3.4-2 1.6c.1.4.1.8.1 1.2Z"/>',
  tag: '<path d="M3.5 12.5V5A1.5 1.5 0 0 1 5 3.5h7.5L21 12l-8.5 8.5Z"/><circle cx="7.5" cy="7.5" r="1.4"/>',
  clipboard:
    '<rect x="8" y="3" width="8" height="4" rx="1"/><path d="M9 5H6.5A1.5 1.5 0 0 0 5 6.5v13A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5v-13A1.5 1.5 0 0 0 17.5 5H15"/><path d="m9 13.5 2 2 4.5-5"/>',
  calendar:
    '<rect x="3.5" y="5" width="17" height="15.5" rx="1.5"/><path d="M8 3v4M16 3v4M3.5 10h17"/>',
  'alert-triangle':
    '<path d="M12 4 2.7 19a1.5 1.5 0 0 0 1.3 2.2h16a1.5 1.5 0 0 0 1.3-2.2L12 4Z"/><path d="M12 10v4"/><path d="M12 17.5h.01"/>',
  'alert-circle':
    '<circle cx="12" cy="12" r="8.5"/><path d="M12 8v4.5"/><path d="M12 16h.01"/>',
  power: '<path d="M12 3.5v8"/><path d="M6.4 7.4a8 8 0 1 0 11.2 0"/>',
  check: '<path d="M20 6.5 9.5 17 4 11.5"/>',
  'check-circle':
    '<circle cx="12" cy="12" r="8.5"/><path d="m8.5 12 2.5 2.5 4.5-5"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
  sparkles:
    '<path d="M12 3.5 13.9 9 19.5 11 13.9 13 12 18.5 10.1 13 4.5 11 10.1 9 12 3.5Z"/><path d="M18.5 15.5 19.3 18l2.5.8-2.5.8-.8 2.5-.8-2.5-2.5-.8 2.5-.8.8-2.5Z"/>',
  target:
    '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.75"/><circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none"/>',
  hourglass:
    '<path d="M6 3h12"/><path d="M6 21h12"/><path d="M7 3c0 4.5 4.5 6 4.5 9S7 16.5 7 21"/><path d="M17 3c0 4.5-4.5 6-4.5 9S17 16.5 17 21"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5"/><path d="M12 8h.01"/>',
  sprout:
    '<path d="M12 21v-9"/><path d="M12 12c-2.8 0-5-2-5-5.5 2.8 0 5 2 5 5.5Z"/><path d="M12 12c0-3 2.2-5.5 5-5.5 0 3.5-2.2 5.5-5 5.5Z"/>',
  thermometer:
    '<path d="M14 14.76V5a2 2 0 0 0-4 0v9.76a4 4 0 1 0 4 0Z"/><path d="M12 9v7.5"/><circle cx="12" cy="17.5" r="1.2" fill="currentColor" stroke="none"/>',
  'arrow-left': '<path d="M20 12H5"/><path d="m11 19-7-7 7-7"/>',
  wrench:
    '<path d="M15.5 4.5a4.2 4.2 0 0 0-5.4 5.4l-6.1 6.1a1.9 1.9 0 0 0 2.7 2.7l6.1-6.1a4.2 4.2 0 0 0 5.4-5.4l-2.6 2.6-2.3-.6-.6-2.3 2.6-2.6Z"/>',
  dot: '<circle cx="12" cy="12" r="2.5" fill="currentColor" stroke="none"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/>',
  'eye-off':
    '<path d="M10.6 5.6A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.4 3.2"/><path d="M6.6 6.6C3.9 8.3 2.5 12 2.5 12S6 18.5 12 18.5a9.3 9.3 0 0 0 5.4-1.7"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/><path d="M3 3l18 18"/>',
  'map-pin':
    '<path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z"/><circle cx="12" cy="10" r="2.3"/>',

  // Iconos de gustos, talentos y pasatiempos
  book: '<path d="M3.5 5.5c2-1 5-1 7 .5v13c-2-1.5-5-1.5-7-.5Z"/><path d="M20.5 5.5c-2-1-5-1-7 .5v13c2-1.5 5-1.5 7-.5Z"/>',
  music:
    '<circle cx="7" cy="17.5" r="2.2"/><circle cx="17" cy="15.5" r="2.2"/><path d="M9.2 17.5V5.5L19.2 3.5v12"/>',
  film: '<rect x="3" y="5.5" width="18" height="12" rx="1.5"/><path d="M8 20.5h8"/><path d="M12 17.5v3"/>',
  'chef-hat':
    '<path d="M7 21h10"/><path d="M8 21v-6.2a4 4 0 0 1-2-7.6 4 4 0 0 1 4-3 3.5 3.5 0 0 1 4 0 4 4 0 0 1 4 3 4 4 0 0 1-2 7.6V21"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2.5M12 19v2.5M4.2 4.2l1.8 1.8M18 18l1.8 1.8M2.5 12H5M19 12h2.5M4.2 19.8 6 18M18 6l1.8-1.8"/>',
  camera:
    '<rect x="3" y="7.5" width="18" height="12" rx="1.5"/><circle cx="12" cy="13.5" r="3.2"/><path d="M8.5 7.5 10 5h4l1.5 2.5"/>',
  plane: '<path d="M12 3.5v17"/><path d="M4 10.5 12 8l8 2.5"/><path d="M9 19l3-1.5 3 1.5"/>',
  paw: '<circle cx="7" cy="9" r="1.6"/><circle cx="12" cy="7" r="1.6"/><circle cx="17" cy="9" r="1.6"/><path d="M12 12.5c-3 0-5.5 2-5.5 4.3 0 1.6 1.5 2.7 3 2.2.9-.3 1.7-.3 2.5-.3s1.6 0 2.5.3c1.5.5 3-.6 3-2.2 0-2.3-2.5-4.3-5.5-4.3Z"/>',
  'message-circle': '<path d="M4 12a8 8 0 1 1 3.2 6.4L4 20l1-3.4A7.9 7.9 0 0 1 4 12Z"/>',
  guitar: '<circle cx="8" cy="16" r="4"/><path d="M11 13 18 4"/><path d="M16.5 6.5l2 2"/>',
  mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0 0 12 0"/><path d="M12 17v4"/><path d="M9 21h6"/>',
  palette:
    '<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1 0 1.8-.8 1.8-1.8 0-.5-.2-.9-.5-1.2-.3-.3-.5-.7-.5-1.2 0-1 .8-1.8 1.8-1.8h2a3.5 3.5 0 0 0 3.5-3.5c0-4.1-3.9-7.5-8.1-7.5Z"/><circle cx="7.5" cy="11" r="1" fill="currentColor" stroke="none"/><circle cx="9.5" cy="7.5" r="1" fill="currentColor" stroke="none"/><circle cx="14" cy="7" r="1" fill="currentColor" stroke="none"/>',
  pen: '<path d="M4 20l1-4.5L15.5 5 19 8.5 8.5 19 4 20Z"/><path d="M13 7 17 11"/>',
  scissors:
    '<circle cx="6" cy="6.5" r="2.2"/><circle cx="6" cy="17.5" r="2.2"/><path d="M20 5 7.8 15"/><path d="M20 19 7.8 9"/>',
  yarn: '<circle cx="12" cy="12" r="8"/><path d="M6 9c3 1 9 1 12 0"/><path d="M5 14c4 1.5 10 1.5 14 0"/><path d="M9 5c1.5 3 1.5 11 0 14"/>',
  dance:
    '<circle cx="12" cy="4.5" r="1.8"/><path d="M12 6.5v6"/><path d="M12 8l-4 3"/><path d="M12 8l4 3"/><path d="M12 12.5 9 20"/><path d="M12 12.5 16 19"/>',
  hammer:
    '<path d="m14 6 4 4"/><path d="M12.5 7.5 17 3l4 4-4.5 4.5"/><path d="M3 21l7.5-7.5"/><path d="M9 12.5l2.5 2.5"/>',
  cake: '<path d="M4 21v-7.5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2V21"/><path d="M4 17h16"/><path d="M9 11.5V8"/><path d="M12 11.5V8"/><path d="M15 11.5V8"/><path d="M9 5.5h.01M12 4.5h.01M15 5.5h.01"/>',
  drama:
    '<circle cx="8.5" cy="12" r="5"/><circle cx="16" cy="9" r="4"/><path d="M6.5 11h4"/><path d="M14.5 8h3"/>',
  dumbbell:
    '<path d="M3.5 12h17"/><rect x="1.5" y="9" width="3" height="6" rx="1"/><rect x="19.5" y="9" width="3" height="6" rx="1"/><rect x="5.5" y="7.5" width="2.5" height="9" rx="1"/><rect x="16" y="7.5" width="2.5" height="9" rx="1"/>',
  footprints:
    '<ellipse cx="8" cy="7" rx="2" ry="2.8"/><ellipse cx="16" cy="14" rx="2" ry="2.8"/><path d="M6.5 12c0 2 1.5 2.5 1.5 4.5S6.5 20 5 19"/><path d="M17.5 19c0-2-1.5-2.5-1.5-4.5S17.5 11 19 12"/>',
  stretch:
    '<circle cx="12" cy="5" r="2"/><path d="M6 19c1.5-3 3-4 6-4s4.5 1 6 4"/><path d="M8 13l4-2 4 2"/>',
  fish: '<path d="M3 12c3-4 7-5 11-3.5 2 .8 4 2.3 5.5 3.5-1.5 1.2-3.5 2.7-5.5 3.5C10 17.5 6 16.5 3 12Z"/><path d="M17 10.5 20 8"/><path d="M17 13.5 20 16"/><circle cx="8" cy="11.5" r="0.8" fill="currentColor" stroke="none"/>',
  dice: '<rect x="4" y="4" width="16" height="16" rx="3"/><circle cx="8.5" cy="8.5" r="1" fill="currentColor" stroke="none"/><circle cx="15.5" cy="8.5" r="1" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="8.5" cy="15.5" r="1" fill="currentColor" stroke="none"/><circle cx="15.5" cy="15.5" r="1" fill="currentColor" stroke="none"/>',
  puzzle:
    '<path d="M9 4.5h4v2a1.5 1.5 0 0 0 3 0v-2H19v4.5h-2a1.5 1.5 0 0 0 0 3h2V19h-4.5v-2a1.5 1.5 0 0 0-3 0v2H9v-4.5h2a1.5 1.5 0 0 0 0-3H9Z"/>',
  bike: '<circle cx="6" cy="17" r="3.2"/><circle cx="18" cy="17" r="3.2"/><path d="M6 17 10 9h5l3 8"/><path d="M10 9 9 6h-2"/><path d="M13 9l3 4h-8"/>',
  swim: '<path d="M3 17c1.5-1.5 3-1.5 4.5 0s3 1.5 4.5 0 3-1.5 4.5 0 3 1.5 4.5 0"/><circle cx="17" cy="7" r="1.6"/><path d="M4 12.5 13 9l2 3-4 2"/>',
};

/** Icono SVG del set ICONS. Si el nombre no existe, muestra un punto. */
@Component({
  selector: 'app-icon',
  standalone: true,
  template: `<span class="app-icon" [innerHTML]="svg"></span>`,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        line-height: 0;
        vertical-align: -0.125em;
        flex-shrink: 0;
      }
      .app-icon {
        display: inline-flex;
      }
    `,
  ],
})
export class Icon implements OnChanges {
  private readonly sanitizer = inject(DomSanitizer);

  /** Nombre del icono. Ver claves de ICONS. */
  @Input() name = '';
  /** Grosor del trazo. */
  @Input() strokeWidth: number | string = 1.75;

  protected svg: SafeHtml = '';

  ngOnChanges(): void {
    const inner = ICONS[this.name] ?? ICONS['dot'];
    // Saltarse la sanitización es seguro: el SVG sale de ICONS, que es texto
    // fijo del código, y no de datos que escriba el usuario.
    this.svg = this.sanitizer.bypassSecurityTrustHtml(
      `<svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" ` +
        `stroke="currentColor" stroke-width="${this.strokeWidth}" ` +
        `stroke-linecap="round" stroke-linejoin="round" ` +
        `style="display:block;overflow:visible" aria-hidden="true">${inner}</svg>`
    );
  }
}