import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PerfilesAnalitica } from '../../../../../core/analitica/analitica.service';
import { DatoKpi } from '../componentes/kpi';
import { TablaGrafica } from '../componentes/tarjeta-grafica';
import { ContenidoReporte } from '../contenido-reporte';
import { ReportePerfiles } from './reporte-perfiles';

/** Dos perfiles con 3 personas en total, una excluida y una variable descartada. */
function datos(): PerfilesAnalitica {
  return {
    estado: 'LISTO',
    generado: '2026-10-15',
    calidad: {
      personasAsociadas: 4, personasIncluidas: 3, personasExcluidas: 1, minimoPersonas: 12,
      porcentajeImputado: 8.5, registrosDePrueba: 3, nivel: 'LIMITADA'
    },
    variables: [
      { clave: 'edad', nombre: 'Edad', dimension: 'Edad', unidad: 'años', completitud: 100,
        media: 76, desviacion: 8, usada: true, motivoDescarte: null },
      { clave: 'imc', nombre: 'Índice de masa corporal', dimension: 'Signos vitales', unidad: 'kg/m²',
        completitud: 20, media: null, desviacion: null, usada: false, motivoDescarte: 'Falta en el 80 % de las personas incluidas' }
    ],
    modelo: {
      k: 2, kSugerido: 2, kElegidoPorUsuario: false, kMaximo: 3, silueta: 0.42, estructura: 'RAZONABLE',
      inercia: 12.5, iteraciones: 4, reinicios: 20, semilla: 42, varianzaExplicada: [0.6, 0.25],
      evaluaciones: [{ k: 2, inercia: 12.5, silueta: 0.42 }, { k: 3, inercia: 8.1, silueta: 0.3 }]
    },
    grupos: [
      { numero: 1, nombre: 'Experiencia de vida', descripcion: 'Mayor edad que el promedio.', personas: 1, porcentaje: 33.3,
        rasgos: ['Edad más alto que el promedio: 88 años frente a 76 años'],
        centroide: [{ clave: 'edad', valor: 88, z: 1.5 }], dimensiones: [{ dimension: 'Edad', puntaje: 1.5 }],
        recomendacionesAtencion: ['Verificar la movilidad.'], recomendacionesActividades: ['Actividades suaves.'],
        interesesFrecuentes: ['Tejido'], asistencia: null },
      { numero: 2, nombre: 'Activos e independientes', descripcion: 'Igual o menos necesidad.', personas: 2, porcentaje: 66.7,
        rasgos: [], centroide: [{ clave: 'edad', valor: 70, z: -0.75 }], dimensiones: [{ dimension: 'Edad', puntaje: -0.75 }],
        recomendacionesAtencion: ['Seguimiento preventivo.'], recomendacionesActividades: ['Caminatas.'],
        interesesFrecuentes: [], asistencia: 80 }
    ],
    personas: [
      { id: 1, nombre: 'Rosa', grupo: 1, x: 1.2, y: 0.1 },
      { id: 2, nombre: 'Pedro', grupo: 2, x: -0.6, y: 0.4 },
      { id: 3, nombre: 'Ana', grupo: 2, x: -0.6, y: -0.5 }
    ],
    excluidas: [{ id: 4, nombre: 'Luis', faltantes: ['Signos vitales de los últimos 180 días'] }],
    advertencias: ['3 de las 3 personas analizadas son datos ficticios del script de pruebas.']
  };
}

/** Acceso a los computed protegidos del reporte. */
interface VistaReporte {
  tarjetas(): DatoKpi[];
  opcionesK(): number[];
  tablaPerfil(): TablaGrafica;
  personasPorGrupo(): { grupo: { numero: number }; personas: string[] }[];
}

// Reporte de perfiles (K-means) de la analítica de la organización.
describe('ReportePerfiles', () => {
  let fixture: ComponentFixture<ReportePerfiles>;
  let reporte: VistaReporte;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ReportePerfiles] });
    TestBed.overrideComponent(ReportePerfiles, { set: { template: '', imports: [] } });
    fixture = TestBed.createComponent(ReportePerfiles);
    fixture.componentRef.setInput('datos', datos());
    reporte = fixture.componentInstance as unknown as VistaReporte;
  });

  it('muestra la calidad de los datos, los perfiles y la separación', () => {
    const [personas, perfiles, separacion, calidad] = reporte.tarjetas();
    expect(personas.valor).toBe('3');
    expect(personas.detalle).toContain('1 sin datos suficientes');
    expect(perfiles.valor).toBe('2');
    expect(separacion.tono).toBe('bueno');
    expect(calidad.valor).toBe('Limitada');
    expect(calidad.tono).toBe('alerta');
  });

  it('ofrece elegir entre 2 y kMaximo grupos', () => {
    expect(reporte.opcionesK()).toEqual([2, 3]);
  });

  it('compara cada grupo con el promedio usando solo las variables del modelo', () => {
    const tabla = reporte.tablaPerfil();
    expect(tabla.columnas).toEqual(['Variable', 'Promedio organización', 'Grupo 1', 'Grupo 2']);
    expect(tabla.filas).toEqual([['Edad', '76 años', '88 años', '70 años']]);
  });

  it('lista las personas de cada grupo en orden alfabético', () => {
    expect(reporte.personasPorGrupo().map((g) => g.personas)).toEqual([['Rosa'], ['Ana', 'Pedro']]);
  });

  it('sin datos suficientes solo exporta la calidad y las personas excluidas', () => {
    fixture.componentRef.setInput('datos', { ...datos(), estado: 'INSUFICIENTE', modelo: null, grupos: [], personas: [] });

    const contenido: ContenidoReporte = fixture.componentInstance.contenidoPdf();
    expect(contenido.secciones.map((s) => s.titulo)).toEqual(['Calidad de los datos', 'Personas sin datos suficientes']);
    expect(contenido.nota).toContain('datos ficticios');
    expect(reporte.tarjetas().map((t) => t.etiqueta)).toEqual(['Personas analizadas', 'Calidad de los datos']);
  });
});
