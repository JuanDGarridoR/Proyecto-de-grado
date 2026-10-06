import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Una actividad del período con sus inscritos y asistentes. */
export interface ActividadAnalitica {
  idActividad: number;
  nombre: string;
  tipo: string | null;
  fecha: string;          // YYYY-MM-DD
  cupos: number | null;
  inscritos: number;
  asistentes: number;     // asistio = true
  conRegistro: number;    // se tomó asistencia (asistio no es null)
}

/** Persona mayor vinculada a la organización. */
export interface PersonaAnalitica {
  idUsuario: number;
  nombre: string;
}

/** Una medición de signos vitales. */
export interface MedicionAnalitica {
  idPersonaMayor: number;
  fechaHora: string;      // YYYY-MM-DDTHH:mm
  presionSistolica: number | null;
  presionDiastolica: number | null;
  frecuenciaCardiaca: number | null;
  temperatura: number | null;
  saturacionOxigeno: number | null;
  frecuenciaRespiratoria: number | null;
  peso: number | null;
}

/** Respuesta de /salud: las personas y todas sus mediciones. */
export interface SaludAnalitica {
  personas: PersonaAnalitica[];
  mediciones: MedicionAnalitica[];
}

/** Datos de una persona para el perfil de la población. */
export interface PersonaPoblacion {
  idUsuario: number;
  nombre: string;
  fechaNacimiento: string | null;
  genero: string | null;
  eps: string | null;
}

/** Cuántas personas tienen marcado un gusto. */
export interface InteresConteo {
  nombre: string;
  categoria: string;
  personas: number;
}

/** Respuesta de /poblacion. */
export interface PoblacionAnalitica {
  personas: PersonaPoblacion[];
  intereses: InteresConteo[];
  personasConIntereses: number;
}

/** Organización recomendada a la persona mayor (puntaje de 0 a 100). */
export interface OrganizacionRecomendada {
  idOrganizacion: number;
  nombre: string;
  direccion: string | null;
  barrio: string | null;
  distanciaKm: number | null;
  celular: string | null;
  correo: string | null;
  puntaje: number;
  razones: string[];
  gustosCoincidentes: string[];
  actividadesCoincidentes: string[];
  personasAfines: number;
}

/** Respuesta de /recomendaciones/organizaciones. */
export interface RecomendacionesOrganizaciones {
  conGustos: boolean;            // si la persona registró intereses
  direccionReconocida: boolean;  // si se pudo ubicar su dirección (para la cercanía)
  recomendaciones: OrganizacionRecomendada[];
}

/** Contenido de un reporte para el PDF (ver ReportePdfRequest en el backend). */
export interface ReportePdf {
  titulo: string;
  descripcion: string;
  periodo: string | null;
  indicadores: { etiqueta: string; valor: string; detalle: string; tono: string }[];
  secciones: {
    titulo: string;
    descripcion: string;
    imagen: string | null;   // PNG en base64 (data URL)
    explicacion: string | null;
    tabla: { columnas: string[]; filas: string[][] };
  }[];
  nota: string | null;
}

/**
 * Datos de la analítica de la organización. analitica-service devuelve
 * datos "crudos" (una fila por actividad, medición o persona); los
 * indicadores y las gráficas se calculan en cada reporte.
 */
@Injectable({ providedIn: 'root' })
export class AnaliticaService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/analitica';

  /** Actividades con fecha entre desde y hasta (YYYY-MM-DD; null = sin límite). */
  actividades(desde: string | null, hasta: string | null): Observable<ActividadAnalitica[]> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);
    return this.http.get<ActividadAnalitica[]>(`${this.apiUrl}/actividades`, { params });
  }

  /** Personas vinculadas y todas sus mediciones de signos vitales. */
  salud(): Observable<SaludAnalitica> {
    return this.http.get<SaludAnalitica>(`${this.apiUrl}/salud`);
  }

  /** Edad, género, EPS e intereses de las personas vinculadas. */
  poblacion(): Observable<PoblacionAnalitica> {
    return this.http.get<PoblacionAnalitica>(`${this.apiUrl}/poblacion`);
  }

  /** Persona mayor: organizaciones recomendadas según sus gustos y la cercanía. */
  recomendacionesOrganizaciones(): Observable<RecomendacionesOrganizaciones> {
    return this.http.get<RecomendacionesOrganizaciones>(`${this.apiUrl}/recomendaciones/organizaciones`);
  }

  /** Arma el PDF de un reporte en analitica-service y lo devuelve como archivo. */
  descargarPdf(reporte: ReportePdf): Observable<Blob> {
    return this.http.post(`${this.apiUrl}/reportes/pdf`, reporte, { responseType: 'blob' });
  }
}
