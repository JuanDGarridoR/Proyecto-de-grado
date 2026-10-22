import { Component, OnInit, WritableSignal, computed, inject, signal, viewChild } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';

import {
  ActividadAnalitica,
  DonacionesAnalitica,
  InventarioAnalitica,
  AnaliticaService,
  PerfilesAnalitica,
  PoblacionAnalitica,
  ReportePdf,
  RetirosAnalitica,
  SaludAnalitica
} from '../../../../core/analitica/analitica.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { imagenGrafica } from '../../../../shared/echart/echart';
import { Icono } from '../../../../shared/icono/icono';
import { ReporteExportable } from './contenido-reporte';
import { ReporteActividades } from './reportes/reporte-actividades';
import { ReportePoblacion } from './reportes/reporte-poblacion';
import { ReporteSalud } from './reportes/reporte-salud';
import { ReporteRetiros } from './reportes/reporte-retiros';
import { ReportePerfiles } from './reportes/reporte-perfiles';
import { ReporteRecursos } from './reportes/reporte-recursos';
import { Cargando } from '../../../../shared/cargando/cargando';

/** Pestañas del informe. */
type Reporte = 'actividades' | 'perfiles' | 'salud' | 'poblacion' | 'recursos' | 'retiros';
/** Período de los filtros, en días; "todo" no tiene límite. */
type Periodo = '30' | '90' | '365' | 'todo';

/** Estado de carga de un reporte. */
interface Estado<T> {
  datos: T | null;
  cargando: boolean;
  error: string | null;
  actualizado: Date | null;
}

/** Las dos fuentes del reporte de donaciones e inventario. */
interface RecursosAnalitica {
  donaciones: DonacionesAnalitica;
  inventario: InventarioAnalitica;
}

/** Estado inicial de un reporte: sin datos. */
const vacio = <T>(): Estado<T> => ({ datos: null, cargando: false, error: null, actualizado: null });

/**
 * Analítica de la organización: seis reportes (pestañas) con indicadores y
 * gráficas de Apache ECharts. Cada reporte se carga la primera vez que se
 * abre y se recarga solo cuando cambian sus datos (tiempo real).
 */
@Component({
  selector: 'app-analitica',
  imports: [Icono, ReporteActividades, ReporteSalud, ReportePoblacion, ReporteRecursos, ReporteRetiros,
    ReportePerfiles, Cargando],
  templateUrl: './analitica.html',
  styleUrl: './analitica.css'
})
export class Analitica implements OnInit {

  private analiticaService = inject(AnaliticaService);

  protected readonly reportes: { id: Reporte; nombre: string; icono: string; descripcion: string }[] = [
    { id: 'actividades', nombre: 'Actividades y participación', icono: 'activity',
      descripcion: 'Cuántas actividades haces, cuántas personas se inscriben y cuántas asisten.' },
    // Segundo, después del reporte que abre por defecto: es el que agrupa con K-means
    { id: 'perfiles', nombre: 'Grupos de atención', icono: 'target',
      descripcion: 'Grupos de personas mayores con necesidades parecidas (salud, signos vitales, edad y '
        + 'acompañamiento), encontrados con K-means, con recomendaciones de atención y de actividades para cada uno.' },
    { id: 'salud', nombre: 'Salud de la población', icono: 'heart',
      descripcion: 'Estado de los signos vitales y personas que requieren atención.' },
    { id: 'poblacion', nombre: 'Perfil de la población', icono: 'users',
      descripcion: 'Quiénes son tus personas mayores: edad, género, si viven solas, EPS, intereses y profesión.' },
    { id: 'recursos', nombre: 'Donaciones e inventario', icono: 'gift',
      descripcion: 'Cuántas donaciones llegan, qué se recibe y quién dona, y qué se está agotando o venciendo en el '
        + 'inventario.' },
    { id: 'retiros', nombre: 'Retiros', icono: 'power',
      descripcion: 'Cuántas personas mayores eliminaron su cuenta y por qué.' }
  ];

  protected readonly periodos: { id: Periodo; nombre: string }[] = [
    { id: '30', nombre: 'Últimos 30 días' },
    { id: '90', nombre: 'Últimos 90 días' },
    { id: '365', nombre: 'Último año' },
    { id: 'todo', nombre: 'Todo' }
  ];

  protected readonly reporteActivo = signal<Reporte>('actividades');
  protected readonly periodo = signal<Periodo>('90');

  protected readonly actividades = signal<Estado<ActividadAnalitica[]>>(vacio());
  protected readonly salud = signal<Estado<SaludAnalitica>>(vacio());
  protected readonly poblacion = signal<Estado<PoblacionAnalitica>>(vacio());
  protected readonly recursos = signal<Estado<RecursosAnalitica>>(vacio());
  protected readonly retiros = signal<Estado<RetirosAnalitica>>(vacio());
  protected readonly perfiles = signal<Estado<PerfilesAnalitica>>(vacio());
  /** Número de grupos elegido en el reporte de perfiles (null = el sugerido). */
  private readonly kPerfiles = signal<number | null>(null);

  /** Los reportes de población y perfiles no dependen del período. */
  protected readonly usaPeriodo = computed(() => !['poblacion', 'perfiles'].includes(this.reporteActivo()));

  // Reporte visible (solo uno existe a la vez), para exportarlo a PDF
  private readonly reporteActividades = viewChild(ReporteActividades);
  private readonly reporteSalud = viewChild(ReporteSalud);
  private readonly reportePoblacion = viewChild(ReportePoblacion);
  private readonly reporteRecursos = viewChild(ReporteRecursos);
  private readonly reporteRetiros = viewChild(ReporteRetiros);
  private readonly reportePerfiles = viewChild(ReportePerfiles);

  protected readonly descargandoPdf = signal(false);
  protected readonly errorPdf = signal<string | null>(null);

  protected readonly descripcionActiva = computed(() =>
    this.reportes.find((r) => r.id === this.reporteActivo())!.descripcion);

  /** Inicio del período (YYYY-MM-DD) o null si es "Todo". */
  protected readonly desde = computed(() => {
    const periodo = this.periodo();
    if (periodo === 'todo') return null;
    const fecha = new Date();
    fecha.setDate(fecha.getDate() - Number(periodo));
    return fecha.toLocaleDateString('en-CA');
  });

  protected readonly estadoActivo = computed<Estado<unknown>>(() => {
    switch (this.reporteActivo()) {
      case 'actividades': return this.actividades();
      case 'salud': return this.salud();
      case 'poblacion': return this.poblacion();
      case 'recursos': return this.recursos();
      case 'retiros': return this.retiros();
      case 'perfiles': return this.perfiles();
    }
  });

  constructor() {
    // Solo se recarga el reporte si ya se había abierto.
    alCambiar(['actividades'], () => this.actividades().datos && this.cargarActividades());
    alCambiar(['signos-vitales', 'organizaciones', 'usuarios'], () => this.salud().datos && this.cargarSalud());
    alCambiar(['organizaciones', 'usuarios', 'gustos'], () => this.poblacion().datos && this.cargarPoblacion());
    // Al eliminar una cuenta llega un aviso de "todo cambió" (también lo escucha alCambiar).
    alCambiar(['organizaciones', 'usuarios'], () => this.retiros().datos && this.cargarRetiros());
    // Recibir una donación también mueve el inventario.
    alCambiar(['inventario', 'donaciones'], () => this.recursos().datos && this.cargarRecursos());
    alCambiar(['signos-vitales', 'condiciones-salud', 'medicamentos', 'citas-medicas', 'acompanamientos',
      'organizaciones', 'usuarios'], () => this.perfiles().datos && this.cargarPerfiles());
  }

  ngOnInit(): void {
    this.cargarActividades();
  }

  protected elegirReporte(reporte: Reporte): void {
    this.reporteActivo.set(reporte);
    if (reporte === 'salud' && !this.salud().datos) this.cargarSalud();
    if (reporte === 'poblacion' && !this.poblacion().datos) this.cargarPoblacion();
    if (reporte === 'recursos' && !this.recursos().datos) this.cargarRecursos();
    if (reporte === 'retiros' && !this.retiros().datos) this.cargarRetiros();
    if (reporte === 'perfiles' && !this.perfiles().datos) this.cargarPerfiles();
  }

  /** El reporte de perfiles pide otro número de grupos (null = el sugerido). */
  protected elegirKPerfiles(k: number | null): void {
    this.kPerfiles.set(k);
    this.cargarPerfiles();
  }

  protected elegirPeriodo(periodo: Periodo): void {
    this.periodo.set(periodo);
    // Actividades se filtra en el servidor; salud filtra en el navegador.
    this.cargarActividades();
  }

  protected recargar(): void {
    switch (this.reporteActivo()) {
      case 'actividades': this.cargarActividades(); break;
      case 'salud': this.cargarSalud(); break;
      case 'poblacion': this.cargarPoblacion(); break;
      case 'recursos': this.cargarRecursos(); break;
      case 'retiros': this.cargarRetiros(); break;
      case 'perfiles': this.cargarPerfiles(); break;
    }
  }

  // Carga de cada reporte

  private cargarActividades(): void {
    const hasta = this.periodo() === 'todo' ? null : new Date().toLocaleDateString('en-CA');
    this.cargar(this.actividades, this.analiticaService.actividades(this.desde(), hasta));
  }

  private cargarSalud(): void {
    this.cargar(this.salud, this.analiticaService.salud());
  }

  private cargarPoblacion(): void {
    this.cargar(this.poblacion, this.analiticaService.poblacion());
  }

  private cargarRecursos(): void {
    this.cargar(this.recursos, forkJoin({
      donaciones: this.analiticaService.donaciones(),
      inventario: this.analiticaService.inventario()
    }));
  }

  private cargarRetiros(): void {
    this.cargar(this.retiros, this.analiticaService.retiros());
  }

  private cargarPerfiles(): void {
    this.cargar(this.perfiles, this.analiticaService.perfiles(this.kPerfiles()));
  }

  /** Mantiene los datos anteriores mientras recarga (sin parpadeo). */
  private cargar<T>(estado: WritableSignal<Estado<T>>, peticion: Observable<T>): void {
    estado.update((e) => ({ ...e, cargando: true, error: null }));
    peticion.subscribe({
      next: (datos) => estado.set({ datos, cargando: false, error: null, actualizado: new Date() }),
      error: (error) => {
        console.error('Error al cargar la analítica:', error);
        estado.update((e) => ({ ...e, cargando: false, error: 'No se pudieron cargar los datos del reporte.' }));
      }
    });
  }

  /** Hora de la última actualización, para mostrarla junto al reporte. */
  // ---------- PDF ----------

  /**
   * Convierte las gráficas del reporte visible en imágenes y le pide a
   * analitica-service que arme el PDF; luego lo descarga.
   */
  protected descargarPdf(): void {
    const reporte: ReporteExportable | undefined = {
      actividades: this.reporteActividades(),
      salud: this.reporteSalud(),
      poblacion: this.reportePoblacion(),
      recursos: this.reporteRecursos(),
      retiros: this.reporteRetiros(),
      perfiles: this.reportePerfiles()
    }[this.reporteActivo()];

    if (!reporte || this.descargandoPdf()) {
      return;
    }

    this.descargandoPdf.set(true);
    this.errorPdf.set(null);

    const info = this.reportes.find((r) => r.id === this.reporteActivo())!;
    const contenido = reporte.contenidoPdf();

    const solicitud: ReportePdf = {
      titulo: info.nombre,
      descripcion: info.descripcion,
      periodo: this.usaPeriodo() ? this.periodos.find((p) => p.id === this.periodo())!.nombre : null,
      indicadores: contenido.indicadores.map(({ etiqueta, valor, detalle, tono }) => ({ etiqueta, valor, detalle, tono })),
      secciones: contenido.secciones.map((s) => ({
        titulo: s.titulo,
        descripcion: s.descripcion,
        imagen: s.opciones ? imagenGrafica(s.opciones) : null,
        explicacion: s.explicacion ?? null,
        tabla: { columnas: s.tabla.columnas, filas: s.tabla.filas.map((f) => f.map(String)) }
      })),
      nota: contenido.nota ?? null
    };

    this.analiticaService.descargarPdf(solicitud).subscribe({
      next: (archivo) => {
        const url = URL.createObjectURL(archivo);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `reporte-${this.reporteActivo()}-${new Date().toLocaleDateString('en-CA')}.pdf`;
        enlace.click();
        URL.revokeObjectURL(url);
        this.descargandoPdf.set(false);
      },
      error: (error) => {
        console.error('Error al generar el PDF:', error);
        this.errorPdf.set('No se pudo generar el PDF del reporte. Intenta de nuevo.');
        this.descargandoPdf.set(false);
      }
    });
  }

  protected hora(fecha: Date | null): string {
    return fecha ? fecha.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' }) : '';
  }
}
