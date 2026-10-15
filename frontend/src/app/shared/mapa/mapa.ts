import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewEncapsulation,
  effect,
  input,
  signal,
  viewChild
} from '@angular/core';

import * as L from 'leaflet';

import {
  CENTRO_USME,
  Coordenadas,
  PuntoMapa,
  distanciaKm,
  enlaceComoLlegar,
  textoDistancia
} from '../../core/mapa/mapa';
import { Icono } from '../icono/icono';

/** Zoom con el que se ve un barrio completo. */
const ZOOM_BARRIO = 15;
/** Zoom con el que se ve Usme completo. */
const ZOOM_LOCALIDAD = 13;

/**
 * Mapa de OpenStreetMap (con Leaflet, sin claves ni costo) que pinta
 * lugares: al tocar uno se ven sus datos y un botón "Cómo llegar" que abre
 * la ruta en Google Maps. Los puntos los calcula analitica-service a partir
 * de la dirección o el lugar que escribieron (nadie marca nada a mano).
 *
 * El botón "Mostrar dónde estoy" usa la ubicación del navegador (con
 * permiso de la persona) solo en esta pantalla: no se guarda en ningún lado.
 * Con la ubicación, cada punto muestra a qué distancia está.
 */
@Component({
  selector: 'app-mapa',
  imports: [Icono],
  templateUrl: './mapa.html',
  styleUrl: './mapa.css',
  // Leaflet crea los marcadores y las ventanas fuera de la plantilla de
  // Angular: los estilos van sin encapsular, con prefijo "mapa-".
  encapsulation: ViewEncapsulation.None
})
export class Mapa implements AfterViewInit, OnDestroy {

  readonly puntos = input<PuntoMapa[]>([]);
  /** Texto para lectores de pantalla. */
  readonly descripcion = input('Mapa');

  protected readonly ubicacion = signal<Coordenadas | null>(null);
  protected readonly buscandoUbicacion = signal(false);
  protected readonly errorUbicacion = signal<string | null>(null);

  private readonly contenedor = viewChild.required<ElementRef<HTMLDivElement>>('contenedor');
  private mapa?: L.Map;
  private capaPuntos?: L.LayerGroup;
  private marcadorUbicacion?: L.CircleMarker;
  private observador?: ResizeObserver;
  /** Últimos puntos encuadrados: solo se vuelve a encuadrar si cambian. */
  private puntosEncuadrados?: PuntoMapa[];

  constructor() {
    // Las señales se leen antes de mirar si el mapa existe, para que el
    // efecto quede pendiente de ellas aunque la primera vez no dibuje.
    effect(() => {
      const puntos = this.puntos();
      const ubicacion = this.ubicacion();
      if (this.mapa) {
        this.dibujarPuntos(puntos, ubicacion);
      }
    });
  }

  ngAfterViewInit(): void {
    const elemento = this.contenedor().nativeElement;

    this.mapa = L.map(elemento, {
      center: [CENTRO_USME.latitud, CENTRO_USME.longitud],
      zoom: ZOOM_LOCALIDAD,
      // Que la rueda del ratón no mueva el mapa sin querer al bajar por la página.
      scrollWheelZoom: false
    });

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(this.mapa);

    this.capaPuntos = L.layerGroup().addTo(this.mapa);

    this.dibujarPuntos(this.puntos(), this.ubicacion());

    // Si el mapa aparece dentro de algo que cambia de tamaño, Leaflet
    // tiene que volver a medir el contenedor.
    this.observador = new ResizeObserver(() => this.mapa?.invalidateSize());
    this.observador.observe(elemento);
  }

  ngOnDestroy(): void {
    this.observador?.disconnect();
    this.mapa?.remove();
  }

  /** Pide la ubicación al navegador para mostrar a la persona y las distancias. */
  protected mostrarDondeEstoy(): void {
    if (!navigator.geolocation) {
      this.errorUbicacion.set('Este navegador no puede mostrar tu ubicación.');
      return;
    }

    this.buscandoUbicacion.set(true);
    this.errorUbicacion.set(null);

    navigator.geolocation.getCurrentPosition(
      (posicion) => {
        const aqui = { latitud: posicion.coords.latitude, longitud: posicion.coords.longitude };
        this.buscandoUbicacion.set(false);
        this.ubicacion.set(aqui);
        this.mostrarUbicacion(aqui);
      },
      () => {
        this.buscandoUbicacion.set(false);
        this.errorUbicacion.set(
          'No pudimos saber dónde estás. Revisa que el navegador tenga permiso de ubicación.'
        );
      },
      { enableHighAccuracy: true, timeout: 15000 }
    );
  }

  private dibujarPuntos(puntos: PuntoMapa[], ubicacion: Coordenadas | null): void {
    if (!this.mapa || !this.capaPuntos) {
      return;
    }

    this.capaPuntos.clearLayers();

    for (const punto of puntos) {
      L.marker([punto.latitud, punto.longitud], {
        icon: pin(punto.destacado ? 'mapa-pin mapa-pin--destacado' : 'mapa-pin'),
        title: punto.titulo,
        alt: punto.titulo
      })
        .bindPopup(contenidoPunto(punto, ubicacion), { minWidth: 220 })
        .addTo(this.capaPuntos);
    }

    if (puntos !== this.puntosEncuadrados && puntos.length > 0) {
      this.puntosEncuadrados = puntos;
      this.encuadrar(puntos);
    }
  }

  private mostrarUbicacion(aqui: Coordenadas): void {
    if (!this.mapa) {
      return;
    }

    const lugar: L.LatLngExpression = [aqui.latitud, aqui.longitud];

    this.marcadorUbicacion?.remove();
    this.marcadorUbicacion = L.circleMarker(lugar, {
      radius: 10,
      weight: 4,
      color: '#ffffff',
      fillColor: '#2563eb',
      fillOpacity: 1
    })
      .bindTooltip('Estás aquí', { permanent: true, direction: 'top', offset: [0, -10] })
      .addTo(this.mapa);

    // Que se vean a la vez la persona y los lugares.
    const puntos = this.puntos();
    if (puntos.length > 0) {
      this.encuadrar([...puntos, aqui]);
    } else {
      this.mapa.setView(lugar, ZOOM_BARRIO);
    }
  }

  private encuadrar(puntos: Coordenadas[]): void {
    if (puntos.length === 1) {
      this.mapa?.setView([puntos[0].latitud, puntos[0].longitud], ZOOM_BARRIO);
      return;
    }

    this.mapa?.fitBounds(
      L.latLngBounds(puntos.map((p) => [p.latitud, p.longitud] as L.LatLngTuple)),
      { padding: [40, 40], maxZoom: ZOOM_BARRIO }
    );
  }
}

/** Marcador en forma de gota, grande para tocarlo fácil con el dedo. */
function pin(clase: string): L.DivIcon {
  return L.divIcon({
    className: clase,
    html: '<span></span>',
    iconSize: [36, 36],
    iconAnchor: [18, 36],
    popupAnchor: [0, -34]
  });
}

/**
 * Contenido de la ventana de un punto. Se arma con textContent (no con
 * HTML) porque los nombres y lugares los escriben los usuarios.
 */
function contenidoPunto(punto: PuntoMapa, ubicacion: Coordenadas | null): HTMLElement {
  const caja = document.createElement('div');
  caja.className = 'mapa-ventana';

  const titulo = document.createElement('strong');
  titulo.textContent = punto.titulo;
  caja.append(titulo);

  for (const linea of punto.lineas) {
    const texto = document.createElement('span');
    texto.textContent = linea;
    caja.append(texto);
  }

  if (ubicacion) {
    const distancia = document.createElement('span');
    distancia.className = 'mapa-ventana__distancia';
    distancia.textContent = textoDistancia(distanciaKm(ubicacion, punto));
    caja.append(distancia);
  }

  const ruta = document.createElement('a');
  ruta.className = 'mapa-ventana__ruta';
  ruta.href = enlaceComoLlegar(punto);
  ruta.target = '_blank';
  ruta.rel = 'noopener';
  ruta.textContent = 'Cómo llegar';
  caja.append(ruta);

  return caja;
}
