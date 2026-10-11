import {
  Component,
  OnDestroy,
  OnInit,
  computed,
  signal
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es-CO';

import { Icono } from '../../../shared/icono/icono';
import {
  ActividadService,
  Actividad,
  EstadoPropuesta,
  PropuestaActividad,
  separarPorFecha
} from '../../../core/actividades/actividad.service';
import { VoluntarioService, OrganizacionVoluntario } from '../../../core/voluntario/voluntario.service';
import { AuthService } from '../../../core/auth/auth.service';
import { alCambiar } from '../../../core/tiempo-real/tiempo-real.service';
import { formatearHora } from '../../../core/medicamentos/medicamento.service';
import { formatearFechaCita } from '../../../core/citas-medicas/cita-medica.service';
import { AltoPantalla } from '../../../shared/alto-pantalla/alto-pantalla';
import { fechaLocal } from '../../../core/fechas/fechas';

registerLocaleData(localeEs);

/** Tarjeta de indicador. */
interface StatCard {
  icon: string;
  value: number;
  label: string;
}

/** Algo que el voluntario debería saber o hacer (aparece arriba). */
interface Aviso {
  clave: string;
  icono: string;
  texto: string;
  accion: string;
  enlace: string;
}

const MINUTO = 60_000;

/** Cuántas propuestas se muestran en el inicio. */
const PROPUESTAS_EN_INICIO = 4;

/**
 * Inicio del panel del voluntario:
 *  1. Saludo y acceso para proponer una actividad.
 *  2. Avisos: sin organización, solicitudes en espera o rechazadas.
 *  3. Indicadores: organizaciones y actividades.
 *  4. Columna principal: actividades de sus organizaciones (la próxima
 *     destacada y las siguientes).
 *  5. Columna lateral: el estado de sus propuestas y sus organizaciones.
 * Todo sale del backend (organizaciones del voluntario, actividades de esas
 * organizaciones y propuestas que ha enviado).
 */
@Component({
  selector: 'app-panel-voluntario',
  imports: [Icono, DatePipe, RouterLink],
  templateUrl: './voluntario.html',
  styleUrl: './voluntario.css',
  hostDirectives: [AltoPantalla]
})
export class PanelVoluntario implements OnInit, OnDestroy {

  protected readonly nombreUsuario: string;
  protected readonly ruta = '/panel/voluntario';

  /** Hora actual; se refresca cada minuto (cambio de día, "Hoy"/"Mañana"). */
  protected readonly ahora = signal(new Date());
  private intervaloReloj?: ReturnType<typeof setInterval>;

  /** Todas las actividades de sus organizaciones (próximas e historial). */
  private readonly actividades = signal<Actividad[]>([]);
  protected readonly organizaciones = signal<OrganizacionVoluntario[]>([]);
  protected readonly propuestas = signal<PropuestaActividad[]>([]);

  protected readonly cargandoActividades = signal(true);
  protected readonly cargandoOrganizaciones = signal(true);
  protected readonly cargandoPropuestas = signal(true);

  protected readonly formatearHora = formatearHora;

  protected readonly textoEstado: Record<EstadoPropuesta, string> = {
    PENDIENTE: 'En revisión',
    ACEPTADA: 'Aceptada',
    RECHAZADA: 'Rechazada'
  };

  constructor(
    private actividadService: ActividadService,
    private voluntarioService: VoluntarioService,
    private authService: AuthService
  ) {
    this.nombreUsuario = this.authService.getNombreUsuario();

    // Las propuestas también son actividades: al responderlas cambia "actividades".
    alCambiar(['actividades'], () => {
      this.cargarActividades();
      this.cargarPropuestas();
    });
    alCambiar(['voluntarios', 'usuarios'], () => {
      this.cargarOrganizaciones();
      this.cargarActividades();
    });
  }

  ngOnInit(): void {
    this.intervaloReloj = setInterval(() => this.ahora.set(new Date()), MINUTO);

    this.cargarActividades();
    this.cargarOrganizaciones();
    this.cargarPropuestas();
  }

  ngOnDestroy(): void {
    clearInterval(this.intervaloReloj);
  }

  /**
   * En pantallas grandes la página ocupa justo el alto de la ventana (sin
   * scroll): se calcula cuánto queda debajo de la barra superior y se pasa al
   * CSS en --alto-disponible. En celulares el CSS no la usa.
   */
  private cargarActividades(): void {
    this.actividadService.listar().subscribe({
      next: (actividades) => this.actividades.set(actividades),
      complete: () => this.cargandoActividades.set(false),
      error: () => this.cargandoActividades.set(false)
    });
  }

  private cargarOrganizaciones(): void {
    this.voluntarioService.listarOrganizaciones().subscribe({
      next: (organizaciones) => this.organizaciones.set(organizaciones),
      complete: () => this.cargandoOrganizaciones.set(false),
      error: () => this.cargandoOrganizaciones.set(false)
    });
  }

  /** Llegan de la más nueva a la más vieja. */
  private cargarPropuestas(): void {
    this.actividadService.listarPropuestasMias().subscribe({
      next: (propuestas) => this.propuestas.set(propuestas),
      complete: () => this.cargandoPropuestas.set(false),
      error: () => this.cargandoPropuestas.set(false)
    });
  }

  /** Organizaciones donde ya ayuda (vínculo aceptado). */
  protected readonly vinculadas = computed(() =>
    this.organizaciones().filter((o) => o.estado === 'ACEPTADA')
  );

  /** Solicitudes de vinculación que la organización aún no responde. */
  protected readonly enEspera = computed(() =>
    this.organizaciones().filter((o) => o.estado === 'PENDIENTE')
  );

  /** Próximas actividades de sus organizaciones, de la más cercana a la más lejana. */
  protected readonly proximas = computed(() => {
    this.ahora(); // se recalcula al cambiar de día
    return separarPorFecha(this.actividades()).proximas;
  });

  protected readonly proximaActividad = computed(() => this.proximas()[0] ?? null);

  /** Las siguientes después de la destacada. */
  protected readonly siguientes = computed(() => this.proximas().slice(1, 6));

  protected readonly ultimasPropuestas = computed(() =>
    this.propuestas().slice(0, PROPUESTAS_EN_INICIO)
  );

  /** Cuántas propuestas hay en cada estado. */
  protected readonly conteoPropuestas = computed<Record<EstadoPropuesta, number>>(() => {
    const conteo: Record<EstadoPropuesta, number> = { PENDIENTE: 0, ACEPTADA: 0, RECHAZADA: 0 };
    for (const p of this.propuestas()) {
      conteo[p.estado]++;
    }
    return conteo;
  });

  /**
   * Ids de sus propuestas aceptadas: una propuesta aceptada se vuelve una
   * actividad más de la organización, y en la lista se marca como suya.
   */
  private readonly idsPropias = computed(() =>
    new Set(this.propuestas().filter((p) => p.estado === 'ACEPTADA').map((p) => p.idActividad))
  );

  protected esPropia(actividad: Actividad): boolean {
    return this.idsPropias().has(actividad.idActividad);
  }

  /** Las propuestas tienen su propia tarjeta, así que aquí solo van organizaciones y actividades. */
  protected readonly stats = computed<StatCard[]>(() => {
    const hoy = fechaLocal(this.ahora());
    const mes = hoy.slice(0, 7); // "2026-10"
    const enUnaSemana = fechaLocal(new Date(this.ahora().getTime() + 7 * 24 * 60 * MINUTO));

    return [
      { icon: 'building', value: this.vinculadas().length, label: 'Organizaciones donde ayudas' },
      {
        icon: 'calendar',
        value: this.actividades().filter((a) => a.fecha?.startsWith(mes)).length,
        label: 'Actividades este mes'
      },
      {
        icon: 'clock',
        value: this.proximas().filter((a) => a.fecha && a.fecha <= enUnaSemana).length,
        label: 'Actividades en los próximos 7 días'
      }
    ];
  });

  /** Avisos según el estado de sus vínculos. */
  protected readonly avisos = computed<Aviso[]>(() => {
    if (this.cargandoOrganizaciones()) {
      return [];
    }

    const avisos: Aviso[] = [];
    const enEspera = this.enEspera();

    if (this.vinculadas().length === 0) {
      avisos.push({
        clave: 'sin-organizacion',
        icono: 'building',
        texto: enEspera.length > 0
          ? 'Aún no estás vinculado a ninguna organización. Cuando acepten tu solicitud verás aquí sus actividades.'
          : 'Aún no estás vinculado a ninguna organización. Envía una solicitud para empezar a ayudar.',
        accion: 'Buscar organizaciones',
        enlace: `${this.ruta}/organizaciones`
      });
    } else if (enEspera.length > 0) {
      avisos.push({
        clave: 'en-espera',
        icono: 'hourglass',
        texto: enEspera.length === 1
          ? `Tu solicitud a ${enEspera[0].nombre} está esperando respuesta.`
          : `Tienes ${enEspera.length} solicitudes esperando respuesta.`,
        accion: 'Ver organizaciones',
        enlace: `${this.ruta}/organizaciones`
      });
    }

    const rechazadas = this.organizaciones().filter((o) => o.estado === 'RECHAZADA');
    if (rechazadas.length > 0) {
      avisos.push({
        clave: 'rechazadas',
        icono: 'info',
        texto: rechazadas.length === 1
          ? `${rechazadas[0].nombre} no aceptó tu solicitud.`
          : `${rechazadas.length} organizaciones no aceptaron tu solicitud.`,
        accion: 'Ver organizaciones',
        enlace: `${this.ruta}/organizaciones`
      });
    }

    return avisos;
  });

  /** Nombre de la organización de una actividad (de las que conoce el voluntario). */
  protected nombreOrganizacion(idOrganizacion: number): string | null {
    return this.organizaciones().find((o) => o.idOrganizacion === idOrganizacion)?.nombre ?? null;
  }

  /** "Hoy", "Mañana" o "jueves 2 de octubre" con la hora si la tiene. */
  protected cuando(fecha: string | null, hora: string | null): string {
    if (!fecha) {
      return 'Fecha por definir';
    }
    const dia = formatearFechaCita(fecha, this.ahora());
    return hora ? `${dia}, ${formatearHora(hora)}` : dia;
  }
}
