import { Component, OnInit, WritableSignal, computed, inject, signal, viewChild } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ActividadAnalitica,
  AnaliticaService,
  PoblacionAnalitica,
  ReportePdf,
  SaludAnalitica
} from '../../../../core/analitica/analitica.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { imagenGrafica } from '../../../../shared/echart/echart';
import { Icon } from '../../../../shared/icon/icon';
import { ReporteExportable } from './contenido-reporte';
import { ReporteActividades } from './reportes/reporte-actividades';
import { ReportePoblacion } from './reportes/reporte-poblacion';
import { ReporteSalud } from './reportes/reporte-salud';

/** Pestañas del informe. */
type Reporte = 'actividades' | 'salud' | 'poblacion';
/** Período de los filtros, en días; "todo" no tiene límite. */
type Periodo = '30' | '90' | '365' | 'todo';

/** Estado de carga de un reporte. */
interface Estado<T> {
  datos: T | null;
  cargando: boolean;
  error: string | null;
  actualizado: Date | null;
}

/** Estado inicial de un reporte: sin datos. */
const vacio = <T>(): Estado<T> => ({ datos: null, cargando: false, error: null, actualizado: null });

/**
 * Analítica de la organización: tres reportes (pestañas) con indicadores y
 * gráficas de Apache ECharts. Cada reporte se carga la primera vez que se
 * abre y se recarga solo cuando cambian sus datos (tiempo real).
 */
@Component({
  selector: 'app-analitica',
  imports: [Icon, ReporteActividades, ReporteSalud, ReportePoblacion],
  templateUrl: './analitica.html',
  styleUrl: './analitica.css'
})
export class Analitica implements OnInit {

  private analiticaService = inject(AnaliticaService);

  protected readonly reportes: { id: Reporte; nombre: string; icono: string; descripcion: string }[] = [
    { id: 'actividades', nombre: 'Actividades y participación', icono: 'activity',
      descripcion: 'Cuántas actividades haces, cuántas personas se inscriben y cuántas asisten.' },
    { id: 'salud', nombre: 'Salud de la población', icono: 'heart',
      descripcion: 'Estado de los signos vitales y personas que requieren atención.' },
    { id: 'poblacion', nombre: 'Perfil de la población', icono: 'users',
      descripcion: 'Quiénes son tus personas mayores: edad, género, EPS e intereses.' }
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

  /** El reporte de población no depende del período. */
  protected readonly usaPeriodo = computed(() => this.reporteActivo() !== 'poblacion');

  // Reporte visible (solo uno existe a la vez), para exportarlo a PDF
  private readonly reporteActividades = viewChild(ReporteActividades);
  private readonly reporteSalud = viewChild(ReporteSalud);
  private readonly reportePoblacion = viewChild(ReportePoblacion);

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
    }
  });

  constructor() {
    // Solo se recarga el reporte si ya se había abierto.
    alCambiar(['actividades'], () => this.actividades().datos && this.cargarActividades());
    alCambiar(['signos-vitales', 'organizaciones', 'usuarios'], () => this.salud().datos && this.cargarSalud());
    alCambiar(['organizaciones', 'usuarios', 'gustos'], () => this.poblacion().datos && this.cargarPoblacion());
  }

  ngOnInit(): void {
    this.cargarActividades();
  }

  protected elegirReporte(reporte: Reporte): void {
    this.reporteActivo.set(reporte);
    if (reporte === 'salud' && !this.salud().datos) this.cargarSalud();
    if (reporte === 'poblacion' && !this.poblacion().datos) this.cargarPoblacion();
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
      poblacion: this.reportePoblacion()
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
