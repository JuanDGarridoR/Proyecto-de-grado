import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewEncapsulation,
  effect,
  input,
  viewChild
} from '@angular/core';

import * as L from 'leaflet';

import { CENTRO_USME } from '../../../../core/mapa/mapa';
import { COLOR, DatosMapaRed } from './red-datos';

/**
 * Mapa de la red de la organización (Leaflet + OpenStreetMap):
 *  - la sede (círculo azul con anillo),
 *  - las personas mayores agrupadas por barrio (círculo naranja; más grande
 *    cuanta más gente vive ahí; nunca la casa de nadie),
 *  - los lugares de actividad (cuadro violeta),
 *  - y una línea de cada barrio a cada lugar al que fue su gente, más gruesa
 *    cuantas más inscripciones.
 * Al tocar cualquier elemento se ve su detalle.
 */
@Component({
  selector: 'app-mapa-red',
  template: `<div #contenedor class="mapa-red" role="region" [attr.aria-label]="descripcion()"></div>`,
  styles: [`
    .mapa-red {
      height: 460px; border: 1px solid var(--vita-border); border-radius: var(--radius-md);
      overflow: hidden; position: relative; z-index: 0;
    }
    @media (max-width: 640px) { .mapa-red { height: 340px; } }
    .mapa-red-etiqueta {
      background: #ffffff; border: 1px solid var(--vita-border); border-radius: 6px;
      box-shadow: none; color: #12355b; font-weight: 600; font-size: 12px; padding: 2px 6px;
    }
    .mapa-red-etiqueta::before { display: none; }
    .mapa-red-lugar span {
      display: block; width: 14px; height: 14px; margin: 2px;
      /* El violeta de COLOR.lugar (SERIE[6] en tema.ts) */
      background: #4a3aa7; border: 2px solid #ffffff; border-radius: 3px;
      box-shadow: 0 1px 4px rgba(18, 53, 91, 0.4);
    }
    .mapa-red-ventana { display: flex; flex-direction: column; gap: 3px; font-size: 0.92rem; color: #12355b; }
    .mapa-red-ventana strong { font-size: 1rem; }
  `],
  // Leaflet crea el contenido fuera de la plantilla de Angular.
  encapsulation: ViewEncapsulation.None
})
export class MapaRed implements AfterViewInit, OnDestroy {

  readonly datos = input.required<DatosMapaRed>();
  readonly descripcion = input('Mapa de la red de la organización');

  private readonly contenedor = viewChild.required<ElementRef<HTMLDivElement>>('contenedor');
  private mapa?: L.Map;
  private capa?: L.LayerGroup;
  private observador?: ResizeObserver;
  private encuadrado = false;

  constructor() {
    effect(() => {
      const datos = this.datos();
      if (this.mapa) {
        this.dibujar(datos);
      }
    });
  }

  ngAfterViewInit(): void {
    const elemento = this.contenedor().nativeElement;

    this.mapa = L.map(elemento, {
      center: [CENTRO_USME.latitud, CENTRO_USME.longitud],
      zoom: 13,
      scrollWheelZoom: false
    });

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(this.mapa);

    this.capa = L.layerGroup().addTo(this.mapa);
    this.dibujar(this.datos());

    this.observador = new ResizeObserver(() => this.mapa?.invalidateSize());
    this.observador.observe(elemento);
  }

  ngOnDestroy(): void {
    this.observador?.disconnect();
    this.mapa?.remove();
  }

  private dibujar(datos: DatosMapaRed): void {
    if (!this.mapa || !this.capa) {
      return;
    }
    this.capa.clearLayers();

    const barrio = new Map(datos.barrios.map((b) => [b.nombre, b]));
    const lugar = new Map(datos.lugares.map((l) => [l.clave, l]));

    // Primero las líneas, para que queden debajo de los puntos.
    for (const f of datos.flujos) {
      const desde = barrio.get(f.barrio);
      const hasta = lugar.get(f.lugar);
      if (!desde || !hasta) {
        continue;
      }
      L.polyline([[desde.latitud, desde.longitud], [hasta.latitud, hasta.longitud]], {
        color: '#586576',
        weight: 1.5 + Math.min(f.inscripciones, 12) * 0.5,
        opacity: 0.45
      })
        .bindTooltip(`${f.barrio} → ${hasta.nombre}: ${f.inscripciones} inscripción(es)`, { sticky: true })
        .addTo(this.capa);
    }

    for (const b of datos.barrios) {
      L.circleMarker([b.latitud, b.longitud], {
        radius: 7 + 3.5 * Math.sqrt(b.personas),
        color: '#ffffff',
        weight: 2,
        fillColor: COLOR.persona,
        fillOpacity: 0.8
      })
        .bindTooltip(`${b.nombre} · ${b.personas}`, {
          permanent: true, direction: 'right', offset: [8, 0], className: 'mapa-red-etiqueta'
        })
        .bindPopup(ventana(b.nombre, [
          `${b.personas} persona(s) mayor(es)`,
          b.sinAcompanante > 0 ? `${b.sinAcompanante} sin acompañante` : 'Todas con acompañante',
          b.gustosComunes.length > 0 ? `Gustos más comunes: ${b.gustosComunes.join(', ')}` : ''
        ]))
        .addTo(this.capa);
    }

    for (const l of datos.lugares) {
      L.marker([l.latitud, l.longitud], {
        icon: L.divIcon({ className: 'mapa-red-lugar', html: '<span></span>', iconSize: [22, 22], iconAnchor: [11, 11] }),
        title: l.nombre,
        alt: l.nombre
      })
        .bindPopup(ventana(l.nombre, l.actividades.map((a) => `· ${a}`)))
        .addTo(this.capa);
    }

    if (datos.sede) {
      L.circleMarker([datos.sede.latitud, datos.sede.longitud], {
        radius: 11,
        color: COLOR.organizacion,
        weight: 4,
        fillColor: '#ffffff',
        fillOpacity: 1
      })
        .bindTooltip('Sede', { permanent: true, direction: 'top', offset: [0, -10], className: 'mapa-red-etiqueta' })
        .bindPopup(ventana(datos.sede.nombre, ['Sede de la organización']))
        .addTo(this.capa);
    }

    // Se encuadra una sola vez: al cambiar el filtro, el mapa no salta.
    if (!this.encuadrado) {
      const puntos: L.LatLngTuple[] = [
        ...datos.barrios.map((b) => [b.latitud, b.longitud] as L.LatLngTuple),
        ...datos.lugares.map((l) => [l.latitud, l.longitud] as L.LatLngTuple),
        ...(datos.sede ? [[datos.sede.latitud, datos.sede.longitud] as L.LatLngTuple] : [])
      ];
      if (puntos.length > 1) {
        this.mapa.fitBounds(L.latLngBounds(puntos), { padding: [50, 50], maxZoom: 15 });
        this.encuadrado = true;
      }
    }
  }
}

/** Contenido de la ventana de un elemento (con textContent: los nombres los escriben los usuarios). */
function ventana(titulo: string, lineas: string[]): HTMLElement {
  const caja = document.createElement('div');
  caja.className = 'mapa-red-ventana';
  const fuerte = document.createElement('strong');
  fuerte.textContent = titulo;
  caja.append(fuerte);
  for (const linea of lineas.filter(Boolean)) {
    const texto = document.createElement('span');
    texto.textContent = linea;
    caja.append(texto);
  }
  return caja;
}
