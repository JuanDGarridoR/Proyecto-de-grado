import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of } from 'rxjs';

import { ActividadDisponible, ActividadService } from '../../../core/actividades/actividad.service';
import { AcompananteService } from '../../../core/acompanantes/acompanante.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CitaMedica, CitaMedicaService } from '../../../core/citas-medicas/cita-medica.service';
import { CondicionSaludService } from '../../../core/condiciones-salud/condicion-salud.service';
import { EmergenciaService } from '../../../core/emergencia/emergencia.service';
import { Medicamento, MedicamentoService } from '../../../core/medicamentos/medicamento.service';
import { OrganizacionService } from '../../../core/organizacion/organizacion.service';
import { PersonaMayorService } from '../../../core/persona-mayor/persona-mayor.service';
import { SignosVitalesService } from '../../../core/signos-vitales/signos-vitales.services';
import { TiempoRealService } from '../../../core/tiempo-real/tiempo-real.service';
import { PanelPersonaMayor } from './persona-mayor';

/** Acceso a los computed protegidos del inicio. */
interface VistaInicio {
  agendaHoy(): { titulo: string; tipo: string; estado: string }[];
  tomasHoy(): { hechas: number; total: number };
  pendientes(): { texto: string }[];
}

const medicamentos = [
  // Tomado a las 8:00; la siguiente toma es a las 8:00 p. m.
  { idMedicamento: 1, nombre: 'Losartán', dosis: '50 mg', intervaloHoras: 12, activo: true, fechaFin: null,
    ultimaToma: '2026-10-13T08:00', proximaToma: '2026-10-13T20:00' },
  // Le tocaba a las 9:00 y no lo ha tomado.
  { idMedicamento: 2, nombre: 'Metformina', dosis: '850 mg', intervaloHoras: 24, activo: true, fechaFin: null,
    ultimaToma: null, proximaToma: '2026-10-13T09:00' }
] as Medicamento[];

const actividades = [
  { idActividad: 1, nombre: 'Taller de tejido', fecha: '2026-10-13', hora: '15:00', lugar: 'Salón comunal', inscrito: true },
  { idActividad: 2, nombre: 'Caminata', fecha: '2026-10-13', hora: null, lugar: 'Parque Entrenubes', inscrito: true },
  // No es de hoy o no está inscrita: no van en la agenda de hoy.
  { idActividad: 3, nombre: 'Bingo', fecha: '2026-10-14', hora: '10:00', lugar: 'Salón comunal', inscrito: true },
  { idActividad: 4, nombre: 'Clase de baile', fecha: '2026-10-13', hora: '16:00', lugar: 'Salón comunal', inscrito: false }
] as ActividadDisponible[];

const citas = [
  { idCita: 1, titulo: 'Toma de muestras', lugar: 'USS Usme', consultorio: null, fecha: '2026-10-13', hora: '07:00' },
  { idCita: 2, titulo: 'Control de cardiología', lugar: 'USS Usme', consultorio: null, fecha: '2026-10-13', hora: '11:30' },
  { idCita: 3, titulo: 'Odontología', lugar: 'USS Usme', consultorio: null, fecha: '2026-10-20', hora: '09:00' }
] as CitaMedica[];

// Inicio de la persona mayor (RF-53): la agenda de hoy junta las tomas de
// medicamentos, las actividades en las que está inscrita y las citas médicas,
// en orden de hora y con su estado. Es el 13 de octubre de 2026 a las 10:00 a. m.
describe('PanelPersonaMayor', () => {
  let fixture: ComponentFixture<PanelPersonaMayor>;
  let inicio: VistaInicio;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 13, 10, 0));

    TestBed.configureTestingModule({
      imports: [PanelPersonaMayor],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { getNombreUsuario: () => 'Rosa Díaz' } },
        { provide: MedicamentoService, useValue: { listar: () => of(medicamentos) } },
        { provide: ActividadService, useValue: { listarDisponibles: () => of(actividades) } },
        { provide: CitaMedicaService, useValue: { listar: () => of(citas) } },
        {
          provide: AcompananteService,
          useValue: { obtenerAcompanantes: () => of([]), obtenerSolicitudesDeAcompanantes: () => of([]) }
        },
        {
          provide: OrganizacionService,
          useValue: { obtenerOrganizaciones: () => of([]), obtenerSolicitudesOrganizaciones: () => of([]) }
        },
        { provide: CondicionSaludService, useValue: { listar: () => of([]) } },
        { provide: PersonaMayorService, useValue: { obtenerInformacion: () => of({ fechaNacimiento: '1948-07-02' }) } },
        { provide: SignosVitalesService, useValue: { listarPropios: () => of([]) } },
        { provide: EmergenciaService, useValue: { activarEmergencia: vi.fn() } },
        { provide: TiempoRealService, useValue: { cambios: () => NEVER } }
      ]
    });
    fixture = TestBed.createComponent(PanelPersonaMayor);
    fixture.detectChanges();
    inicio = fixture.componentInstance as unknown as VistaInicio;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('arma la agenda de hoy en orden de hora, con lo que ya pasó y lo siguiente', () => {
    expect(inicio.agendaHoy().map((e) => [e.titulo, e.estado])).toEqual([
      ['Toma de muestras', 'pasado'],
      ['Losartán', 'hecho'],
      ['Metformina', 'atrasado'],
      ['Control de cardiología', 'siguiente'],
      ['Taller de tejido', 'pendiente'],
      ['Losartán', 'pendiente'],
      // Las actividades sin hora van al final
      ['Caminata', 'pendiente']
    ]);

    const filas = fixture.nativeElement.querySelectorAll('.agenda__lista li');
    expect(filas.length).toBe(7);
    expect(filas[3].textContent).toContain('Siguiente');
  });

  it('cuenta las tomas del día y avisa del medicamento sin tomar', () => {
    expect(inicio.tomasHoy()).toMatchObject({ hechas: 1, total: 3 });
    expect(fixture.nativeElement.textContent).toContain('1 de 3 tomados');
    expect(inicio.pendientes().map((p) => p.texto)).toEqual(['Tienes 1 medicamento sin tomar.']);
  });
});
