import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of } from 'rxjs';

import { ActividadService } from '../../../core/actividades/actividad.service';
import { AcompananteService, MedicamentoSeguimiento } from '../../../core/acompanantes/acompanante.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CitaMedica } from '../../../core/citas-medicas/cita-medica.service';
import { SignoVitalResponse } from '../../../core/signos-vitales/signos-vitales.services';
import { TiempoRealService } from '../../../core/tiempo-real/tiempo-real.service';
import { PanelAcompanante } from './acompanante';

/** Acceso a los computed protegidos del inicio. */
interface VistaInicio {
  resumenes(): Record<number, {
    tomasHechas: number;
    tomasTotal: number;
    sinTomar: number;
    ultimaMedicion: { normal: boolean } | null;
    proximaCita: string | null;
  }>;
  alertas(): { prioridad: string; persona: { nombre: string }; texto: string }[];
}

const ROSA = { idUsuario: 10, nombre: 'Rosa Díaz', celular: '+573001112233' };
const LUIS = { idUsuario: 11, nombre: 'Luis Gómez', celular: '+573004445566' };

const medicamentos: Record<number, MedicamentoSeguimiento[]> = {
  // Rosa tomó el de las 8:00 y le falta el de las 8:00 p. m.
  10: [{ idMedicamento: 1, nombre: 'Losartán', dosis: '50 mg', intervaloHoras: 12, activo: true, fechaFin: null,
    ultimaToma: '2026-10-13T08:00', proximaToma: '2026-10-13T20:00' } as MedicamentoSeguimiento],
  // A Luis le tocaba a las 9:00 y no lo ha tomado.
  11: [{ idMedicamento: 2, nombre: 'Metformina', dosis: '850 mg', intervaloHoras: 24, activo: true, fechaFin: null,
    ultimaToma: null, proximaToma: '2026-10-13T09:00' } as MedicamentoSeguimiento]
};

const citas: Record<number, CitaMedica[]> = {
  10: [{ idCita: 1, titulo: 'Control de cardiología', lugar: 'USS Usme', consultorio: null,
    fecha: '2026-10-14', hora: '09:00', observaciones: null }],
  11: []
};

/** Medición con solo la presión; los demás valores no se tomaron. */
function presion(fechaHora: string, sistolica: number, diastolica: number): SignoVitalResponse {
  return {
    idSignoVital: 1, fechaHora, presionSistolica: sistolica, presionDiastolica: diastolica,
    frecuenciaCardiaca: null, temperatura: null, saturacionOxigeno: null, frecuenciaRespiratoria: null,
    peso: null, estatura: null, observaciones: null
  };
}

// Rosa se midió hace dos días con la presión alta; Luis no tiene mediciones.
const signos: Record<number, SignoVitalResponse[]> = {
  10: [presion('2026-10-11T09:00', 165, 95)],
  11: []
};

// Inicio del acompañante (RF-54): el resumen de cada persona mayor y las
// alertas, con la emergencia primero. Es el 13 de octubre de 2026 a las
// 10:00 a. m. y Rosa activó el botón de emergencia a las 9:30.
describe('PanelAcompanante', () => {
  let fixture: ComponentFixture<PanelAcompanante>;
  let inicio: VistaInicio;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 13, 10, 0));

    TestBed.configureTestingModule({
      imports: [PanelAcompanante],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { getNombreUsuario: () => 'Pedro Díaz' } },
        { provide: ActividadService, useValue: { listar: () => of([]) } },
        {
          provide: AcompananteService,
          useValue: {
            obtenerPersonasMayores: () => of([ROSA, LUIS]),
            obtenerSolicitudes: () => of([]),
            obtenerEmergenciasRecientes: () => of([
              { idEmergencia: 3, idPersonaMayor: 10, nombre: 'Rosa Díaz', fechaHora: '2026-10-13T09:30' }
            ]),
            obtenerMedicamentosSeguimiento: (id: number) => of(medicamentos[id]),
            obtenerCitasMedicasSeguimiento: (id: number) => of(citas[id]),
            obtenerSignosVitalesSeguimiento: (id: number) => of(signos[id])
          }
        },
        { provide: TiempoRealService, useValue: { cambios: () => NEVER } }
      ]
    });
    fixture = TestBed.createComponent(PanelAcompanante);
    fixture.detectChanges();
    inicio = fixture.componentInstance as unknown as VistaInicio;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('resume a cada persona mayor: tomas de hoy, última medición y próxima cita', () => {
    const rosa = inicio.resumenes()[ROSA.idUsuario];
    expect(rosa).toMatchObject({ tomasHechas: 1, tomasTotal: 2, sinTomar: 0, ultimaMedicion: { normal: false } });
    expect(rosa.proximaCita).toContain('Mañana');

    expect(inicio.resumenes()[LUIS.idUsuario]).toMatchObject({
      tomasHechas: 0, tomasTotal: 1, sinTomar: 1, ultimaMedicion: null, proximaCita: null
    });
  });

  it('pone primero la emergencia y después lo urgente de cada persona', () => {
    expect(inicio.alertas().map((a) => [a.prioridad, a.persona.nombre])).toEqual([
      ['Emergencia', 'Rosa Díaz'],
      ['Alta', 'Rosa Díaz'],   // presión fuera de rango
      ['Alta', 'Luis Gómez'],  // medicamento sin tomar
      ['Media', 'Rosa Díaz'],  // cita mañana
      ['Baja', 'Luis Gómez']   // sin mediciones
    ]);
    expect(inicio.alertas()[0].texto).toContain('hace 30 minutos');
    expect(inicio.alertas()[2].texto).toBe('No ha tomado: Metformina.');

    const alertas = fixture.nativeElement.querySelectorAll('.alerta__texto');
    expect(alertas.length).toBe(5);
    expect(alertas[0].textContent).toContain('Activó el botón de emergencia');
  });
});
