import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { AnaliticaService, RedOrganizacion } from '../../../../core/analitica/analitica.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { mensajeDeError } from '../../../../core/formato/formato';
import { Cargando } from '../../../../shared/cargando/cargando';
import { Icono } from '../../../../shared/icono/icono';
import { DatoKpi, Kpi } from '../analitica/componentes/kpi';
import { TarjetaGrafica } from '../analitica/componentes/tarjeta-grafica';
import { MapaRed } from './mapa-red';
import {
  COLOR,
  datosMapa,
  grafoGustos,
  grafoRedApoyo,
  personasConGusto,
  sinAcompanante,
  tablaGustos,
  tablaMapa,
  tablaRedApoyo
} from './red-datos';

/**
 * «Red y mapa» de la organización: un panorama general de su red de apoyo.
 *
 *  - Grafo: quién está conectado con quién (organización, personas
 *    mayores, acompañantes y voluntarios), o qué gustos comparten.
 *    Resalta a las personas mayores sin acompañante (posible aislamiento).
 *  - Mapa: dónde viven (por barrio) y a qué lugares van a las
 *    actividades, con un filtro por gusto.
 *
 * Solo informa (RNF-13): no cambia nada ni decide por la organización.
 */
@Component({
  selector: 'app-red',
  imports: [Cargando, Icono, Kpi, TarjetaGrafica, MapaRed],
  templateUrl: './red.html',
  styleUrl: './red.css'
})
export class Red implements OnInit {

  private readonly analitica = inject(AnaliticaService);

  protected readonly red = signal<RedOrganizacion | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Vista del grafo: la red de apoyo o los gustos en común. */
  protected readonly vistaGrafo = signal<'red' | 'gustos'>('red');
  /** En la red de apoyo: deja al frente solo a las personas sin acompañante. */
  protected readonly resaltarAisladas = signal(false);
  /** Filtro del mapa (null = todas las personas). */
  protected readonly gusto = signal<number | null>(null);
  protected readonly verTablaMapa = signal(false);

  protected readonly color = COLOR;

  /** La red de apoyo es lo principal de la página: ocupa casi toda la pantalla de alto. */
  protected readonly altoRed = Math.max(820, (typeof window === 'undefined' ? 0 : window.innerHeight) - 80);

  protected readonly tarjetas = computed<DatoKpi[]>(() => {
    const red = this.red();
    if (!red) {
      return [];
    }
    const aisladas = sinAcompanante(red).length;
    return [
      {
        etiqueta: 'Personas mayores', valor: String(red.personasMayores.length),
        detalle: 'Con vínculo aceptado', icono: 'users', tono: 'neutro'
      },
      {
        etiqueta: 'Sin acompañante', valor: String(aisladas),
        detalle: aisladas > 0 ? 'Posible aislamiento: revisa su red' : 'Todas tienen acompañante',
        icono: 'alert-triangle', tono: aisladas > 0 ? 'alerta' : 'bueno'
      },
      {
        etiqueta: 'Acompañantes', valor: String(red.acompanantes.length),
        detalle: 'De tus personas mayores', icono: 'heart', tono: 'neutro'
      },
      {
        etiqueta: 'Voluntarios', valor: String(red.voluntarios.length),
        detalle: 'Vinculados a la organización', icono: 'star', tono: 'neutro'
      }
    ];
  });

  protected readonly grafo = computed(() => {
    const red = this.red();
    if (!red) {
      return null;
    }
    return this.vistaGrafo() === 'red'
      ? { opciones: grafoRedApoyo(red, this.resaltarAisladas()), tabla: tablaRedApoyo(red) }
      : { opciones: grafoGustos(red), tabla: tablaGustos(red) };
  });

  protected readonly mapa = computed(() => {
    const red = this.red();
    return red ? datosMapa(red, this.gusto()) : null;
  });

  protected readonly tablaDelMapa = computed(() => {
    const mapa = this.mapa();
    return mapa ? tablaMapa(mapa) : null;
  });

  /** Personas que entran en el filtro del mapa. */
  protected readonly personasFiltradas = computed(() => {
    const red = this.red();
    return red ? personasConGusto(red, this.gusto()).length : 0;
  });

  /** Actividades de la ventana que aún no tienen punto en el mapa. */
  protected readonly actividadesSinUbicar = computed(() =>
    this.red()?.actividades.filter((a) => a.latitud === null).length ?? 0
  );

  constructor() {
    // Cambian vínculos, cuentas, gustos o actividades: se vuelve a armar la red.
    alCambiar(['organizaciones', 'usuarios', 'gustos', 'actividades'], () => this.red() && this.cargar(false));
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargando.set(true);
    }
    this.error.set(null);

    this.analitica.red().subscribe({
      next: (red) => {
        this.red.set(red);
        this.cargando.set(false);
      },
      error: (error) => {
        this.error.set(mensajeDeError(error, 'No se pudo cargar la red de tu organización.'));
        this.cargando.set(false);
      }
    });
  }

  protected elegirGusto(valor: string): void {
    this.gusto.set(valor === '' ? null : Number(valor));
  }
}
