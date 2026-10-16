import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../api';

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
  /** cm; con el peso da el IMC. */
  estatura: number | null;
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
  /** null si no lo ha indicado. */
  viveSolo: boolean | null;
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
  /** Acompañantes distintos con vínculo aceptado a alguna persona de la organización. */
  acompanantesActivos: number;
  /** Profesiones u oficios (categoría PROFESION), aparte de los intereses. */
  profesiones: InteresConteo[];
  personasConProfesion: number;
}

/** Persona mayor que eliminó su cuenta. razon es un código de RAZONES_RETIRO. */
export interface RetiroAnalitica {
  nombre: string | null;
  razon: string;
  comentario: string | null;
  fecha: string;          // YYYY-MM-DDTHH:mm
}

/** Respuesta de /retiros: todos los retiros y cuántas personas tiene hoy la organización. */
export interface RetirosAnalitica {
  retiros: RetiroAnalitica[];
  personasActuales: number;
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

/**
 * Red de apoyo de la organización (/red), para el grafo y el mapa de
 * «Red y mapa». De cada persona mayor solo llega el barrio, nunca su
 * dirección.
 */
export interface RedOrganizacion {
  organizacion: { id: number; nombre: string; latitud: number | null; longitud: number | null };
  personasMayores: {
    id: number;
    nombre: string;
    barrio: string | null;
    gustos: number[];
    /** Actividades (de las de esta respuesta) en las que se inscribió. */
    actividades: number[];
  }[];
  acompanantes: { id: number; nombre: string }[];
  /** Vínculos aceptados persona mayor - acompañante, con la relación ("Hija"). */
  vinculos: { idPersonaMayor: number; idAcompanante: number; relacion: string | null }[];
  voluntarios: { id: number; nombre: string; gustos: number[] }[];
  gustos: { id: number; nombre: string; categoria: string }[];
  /** Centro de cada barrio donde vive al menos una persona mayor. */
  barrios: { nombre: string; latitud: number; longitud: number }[];
  /** Actividades de los últimos 90 días y los próximos 60. */
  actividades: {
    id: number;
    nombre: string;
    fecha: string;
    lugar: string | null;
    latitud: number | null;
    longitud: number | null;
  }[];
}

/** Variable del modelo de perfiles: completitud (%), promedio y si entró al modelo. */
export interface VariablePerfil {
  clave: string;
  nombre: string;
  dimension: string;
  unidad: string;
  completitud: number;
  media: number | null;
  desviacion: number | null;
  usada: boolean;
  motivoDescarte: string | null;
}

/** Un perfil (grupo) encontrado por K-means, con su interpretación. */
export interface GrupoPerfil {
  numero: number;
  nombre: string;
  descripcion: string;
  personas: number;
  porcentaje: number;
  rasgos: string[];
  /** Promedio del grupo en cada variable usada: en sus unidades y en desviaciones (z). */
  centroide: { clave: string; valor: number; z: number }[];
  dimensiones: { dimension: string; puntaje: number }[];
  recomendacionesAtencion: string[];
  recomendacionesActividades: string[];
  interesesFrecuentes: string[];
  /** % de asistencia a actividades; null si nadie tiene asistencia registrada. */
  asistencia: number | null;
}

/**
 * Respuesta de /perfiles: segmentación de las personas mayores con K-means
 * (ver KMEANS_PERFILES.md). Con estado INSUFICIENTE no hay modelo ni grupos.
 */
export interface PerfilesAnalitica {
  estado: 'LISTO' | 'INSUFICIENTE';
  generado: string;
  calidad: {
    personasAsociadas: number;
    personasIncluidas: number;
    personasExcluidas: number;
    minimoPersonas: number;
    porcentajeImputado: number;
    registrosDePrueba: number;
    nivel: 'BUENA' | 'ACEPTABLE' | 'LIMITADA' | 'INSUFICIENTE';
  };
  variables: VariablePerfil[];
  modelo: {
    k: number;
    kSugerido: number;
    kElegidoPorUsuario: boolean;
    kMaximo: number;
    silueta: number;
    estructura: 'FUERTE' | 'RAZONABLE' | 'DEBIL' | 'SIN_ESTRUCTURA';
    inercia: number;
    iteraciones: number;
    reinicios: number;
    semilla: number;
    /** Varianza que explica cada eje del mapa (0 a 1). */
    varianzaExplicada: number[];
    evaluaciones: { k: number; inercia: number; silueta: number }[];
  } | null;
  grupos: GrupoPerfil[];
  /** Personas incluidas, su grupo y su punto en el mapa. */
  personas: { id: number; nombre: string; grupo: number; x: number; y: number }[];
  /** Personas que no entraron por falta de datos y lo que les falta. */
  excluidas: { id: number; nombre: string; faltantes: string[] }[];
  advertencias: string[];
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
  private apiUrl = `${API_URL}/analitica`;

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

  /** Por qué las personas mayores eliminaron su cuenta (se filtra por período en el navegador). */
  retiros(): Observable<RetirosAnalitica> {
    return this.http.get<RetirosAnalitica>(`${this.apiUrl}/retiros`);
  }

  /** Perfiles de las personas mayores (K-means); sin k, el número de grupos lo sugiere la silueta. */
  perfiles(k: number | null): Observable<PerfilesAnalitica> {
    const params = k ? new HttpParams().set('k', k) : new HttpParams();
    return this.http.get<PerfilesAnalitica>(`${this.apiUrl}/perfiles`, { params });
  }

  /** Red de apoyo de la organización: vínculos, gustos, barrios y actividades. */
  red(): Observable<RedOrganizacion> {
    return this.http.get<RedOrganizacion>(`${this.apiUrl}/red`);
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
