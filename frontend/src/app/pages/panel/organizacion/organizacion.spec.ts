import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of } from 'rxjs';

import { Actividad, ActividadRequest, ActividadService } from '../../../core/actividades/actividad.service';
import { AnaliticaService, MedicionAnalitica } from '../../../core/analitica/analitica.service';
import { AuthService } from '../../../core/auth/auth.service';
import { OrganizacionService } from '../../../core/organizacion/organizacion.service';
import { TiempoRealService } from '../../../core/tiempo-real/tiempo-real.service';
import { PanelOrganizacion } from './organizacion';

/** Acceso a los miembros protegidos del registro rápido. */
interface VistaPanel {
  nuevaActividad: ActividadRequest;
  actividadEditando: ActividadRequest;
  crearActividad(): void;
  editarActividad(actividad: Actividad): void;
  guardarActividad(): void;
}

/** Actividad con los datos obligatorios. */
function completa(): ActividadRequest {
  return {
    nombre: 'Bingo comunitario', descripcion: null, fecha: '2026-10-20', hora: '15:00', horaFin: '17:00',
    lugar: 'Salón comunal', tipo: null, cupos: null, responsable: null, frecuenciaDias: null
  };
}

// Registro rápido de actividades desde el inicio de la organización (RF-39):
// sin nombre, fecha, horario o lugar no se envía nada, ni al crear ni al editar.
describe('PanelOrganizacion: registro rápido de actividades', () => {
  const crear = vi.fn(() => of({}));
  const actualizar = vi.fn(() => of({}));
  let panel: VistaPanel;

  beforeEach(() => {
    crear.mockClear();
    actualizar.mockClear();

    TestBed.configureTestingModule({
      imports: [PanelOrganizacion],
      providers: [
        {
          provide: ActividadService,
          useValue: {
            crear, actualizar, eliminar: vi.fn(() => of({})),
            listarMias: () => of([]), listarPropuestasPendientes: () => of([])
          }
        },
        { provide: AnaliticaService, useValue: { actividades: () => of([]), poblacion: () => NEVER, salud: () => NEVER } },
        { provide: AuthService, useValue: { getNombreUsuario: () => 'Fundación Entrenubes' } },
        {
          provide: OrganizacionService,
          useValue: {
            obtenerInformacion: () => of({ nombre: 'Fundación Entrenubes' }),
            obtenerSolicitudesPersonasMayores: () => of([]),
            obtenerSolicitudesVoluntarios: () => of([]),
            obtenerVoluntarios: () => of([])
          }
        },
        { provide: TiempoRealService, useValue: { cambios: () => NEVER } }
      ]
    });
    TestBed.overrideComponent(PanelOrganizacion, { set: { template: '', imports: [] } });
    const fixture = TestBed.createComponent(PanelOrganizacion);
    fixture.detectChanges();
    panel = fixture.componentInstance as unknown as VistaPanel;
  });

  it('no registra una actividad a la que le falta el nombre, la fecha, el horario o el lugar', () => {
    panel.nuevaActividad = { ...completa(), nombre: '   ' };
    panel.crearActividad();
    panel.nuevaActividad = { ...completa(), fecha: null };
    panel.crearActividad();
    panel.nuevaActividad = { ...completa(), hora: null };
    panel.crearActividad();
    panel.nuevaActividad = { ...completa(), horaFin: null };
    panel.crearActividad();
    // Termina antes de empezar
    panel.nuevaActividad = { ...completa(), horaFin: '14:00' };
    panel.crearActividad();
    panel.nuevaActividad = { ...completa(), lugar: '' };
    panel.crearActividad();

    expect(crear).not.toHaveBeenCalled();
  });

  it('con los datos completos registra la actividad y limpia el formulario', () => {
    panel.nuevaActividad = completa();
    panel.crearActividad();

    expect(crear).toHaveBeenCalledWith(expect.objectContaining({
      nombre: 'Bingo comunitario', fecha: '2026-10-20', hora: '15:00', horaFin: '17:00', lugar: 'Salón comunal'
    }));
    expect(panel.nuevaActividad.nombre).toBe('');
  });

  it('al editar una actividad desde el inicio también exige los cuatro datos', () => {
    panel.editarActividad({ idActividad: 7, ...completa() } as unknown as Actividad);
    panel.actividadEditando.lugar = '  ';
    panel.guardarActividad();
    expect(actualizar).not.toHaveBeenCalled();

    panel.actividadEditando.lugar = 'Parque La Flora';
    panel.guardarActividad();
    expect(actualizar).toHaveBeenCalledWith(7, expect.objectContaining({ lugar: 'Parque La Flora' }));
  });
});

// Resumen del inicio (RF-55): personas mayores, acompañantes, voluntarios y
// alertas, y los accesos rápidos para registrar signos vitales y actividades.
// Rosa tiene la presión alta en su última medición, Luis no tiene mediciones
// y hay una solicitud de voluntario por responder.
describe('PanelOrganizacion: resumen del inicio', () => {
  const presionAlta = {
    idPersonaMayor: 10, fechaHora: '2026-10-12T09:00', presionSistolica: 170, presionDiastolica: 100,
    frecuenciaCardiaca: null, temperatura: null, saturacionOxigeno: null, frecuenciaRespiratoria: null, peso: null
  } as MedicionAnalitica;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 13, 10, 0));

    TestBed.configureTestingModule({
      imports: [PanelOrganizacion],
      providers: [
        provideRouter([]),
        {
          provide: ActividadService,
          useValue: { listarMias: () => of([]), listarPropuestasPendientes: () => of([]) }
        },
        {
          provide: AnaliticaService,
          useValue: {
            actividades: () => of([]),
            poblacion: () => of({
              personas: [
                ...['Rosa Díaz', 'Luis Gómez', 'Marta Ruiz'].map((nombre, i) => ({
                  idUsuario: 10 + i, nombre, fechaNacimiento: null, genero: null, eps: null,
                  acompanantes: 1, ultimaParticipacion: '2026-10-01'
                }))
              ],
              intereses: [],
              personasConIntereses: 2,
              acompanantesActivos: 4
            }),
            salud: () => of({
              personas: [{ idUsuario: 10, nombre: 'Rosa Díaz' }, { idUsuario: 11, nombre: 'Luis Gómez' }],
              mediciones: [presionAlta]
            })
          }
        },
        { provide: AuthService, useValue: { getNombreUsuario: () => 'Fundación Entrenubes' } },
        {
          provide: OrganizacionService,
          useValue: {
            obtenerInformacion: () => of({ nombre: 'Fundación Entrenubes' }),
            obtenerSolicitudesPersonasMayores: () => of([]),
            obtenerSolicitudesVoluntarios: () => of([{ idUsuario: 42, nombre: 'Andrea Mora', celular: null, correo: null }]),
            obtenerVoluntarios: () => of([
              { idUsuario: 40, nombre: 'Laura Peña', celular: null, correo: null },
              { idUsuario: 41, nombre: 'Jorge Ríos', celular: null, correo: null }
            ])
          }
        },
        { provide: TiempoRealService, useValue: { cambios: () => NEVER } }
      ]
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('muestra cuántas personas mayores, acompañantes, voluntarios y alertas tiene', () => {
    const fixture = TestBed.createComponent(PanelOrganizacion);
    fixture.detectChanges();
    const pagina: HTMLElement = fixture.nativeElement;

    const indicadores = [...pagina.querySelectorAll('.stat')].map((s) => [
      s.querySelector('.stat__etiqueta')?.textContent?.trim(),
      s.querySelector('.stat__valor')?.textContent?.trim()
    ]);
    expect(indicadores).toEqual([
      ['Personas mayores registradas', '3'],
      ['Acompañantes activos', '4'],
      ['Voluntarios en el programa', '2'],
      // presión alta de Rosa, Luis sin mediciones y la solicitud de Andrea
      ['Alertas activas', '3']
    ]);
    expect(pagina.textContent).toContain('1 solicitud nueva');
    expect(pagina.textContent).toContain('1 urgente');
  });

  // RF-60: posible aislamiento. Ana tiene las tres señales; Pedro, dos; Sara
  // solo no tiene acompañantes y Juan solo no participa: van agrupadas. A
  // Lucía, que solo vive sola, no se le avisa.
  it('avisa de posible aislamiento según las señales de cada persona', () => {
    const persona = (idUsuario: number, nombre: string, acompanantes: number,
                     ultimaParticipacion: string | null, viveSolo: boolean) =>
      ({ idUsuario, nombre, fechaNacimiento: null, genero: null, eps: null,
         acompanantes, ultimaParticipacion, viveSolo });

    TestBed.overrideProvider(AnaliticaService, {
      useValue: {
        actividades: () => of([]),
        salud: () => of(null),
        poblacion: () => of({
          personas: [
            persona(20, 'Ana Ruiz', 0, null, true),
            persona(21, 'Pedro León', 0, '2026-07-01', false),
            persona(22, 'Sara Gil', 0, '2026-10-01', false),
            persona(23, 'Juan Mora', 2, '2026-08-01', false),
            persona(24, 'Lucía Paz', 1, '2026-10-20', true)
          ],
          intereses: [],
          personasConIntereses: 0,
          acompanantesActivos: 3
        })
      }
    });
    const fixture = TestBed.createComponent(PanelOrganizacion);
    fixture.detectChanges();

    const alertas = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.alerta')]
      .map((a) => [a.querySelector('.alerta__tag')?.textContent?.trim(), a.querySelector('strong')?.textContent?.trim()]);

    expect(alertas).toContainEqual(['Alta', 'Posible aislamiento: Ana Ruiz']);
    expect(alertas).toContainEqual(['Media', 'Posible aislamiento: Pedro León']);
    expect(alertas).toContainEqual(['Baja', 'Sara Gil no tiene acompañantes']);
    expect(alertas).toContainEqual(['Baja', 'Juan Mora no participa en actividades']);
    expect(alertas.some(([, titulo]) => titulo?.includes('Lucía'))).toBe(false);
    expect(fixture.nativeElement.textContent)
      .toContain('No tiene acompañantes, no ha participado en actividades en los últimos 60 días y vive sola.');
  });

  it('tiene accesos rápidos para registrar signos vitales y actividades', () => {
    const fixture = TestBed.createComponent(PanelOrganizacion);
    fixture.detectChanges();

    const accesos = [...(fixture.nativeElement as HTMLElement).querySelectorAll('a.quick-action')]
      .map((a) => [a.textContent?.trim(), a.getAttribute('href')]);
    expect(accesos).toContainEqual(['Registrar signos vitales', '/panel/organizacion/signos-vitales']);
    expect(accesos).toContainEqual(['Registrar actividad', '/panel/organizacion/actividades?abrir=registrar']);
  });
});
