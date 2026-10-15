import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of } from 'rxjs';

import { Actividad, ActividadService } from '../../../core/actividades/actividad.service';
import { AuthService } from '../../../core/auth/auth.service';
import { TiempoRealService } from '../../../core/tiempo-real/tiempo-real.service';
import { VoluntarioService } from '../../../core/voluntario/voluntario.service';
import { PanelVoluntario } from './voluntario';

/** Acceso a los computed protegidos del inicio. */
interface VistaInicio {
  stats(): { label: string; value: number }[];
  proximaActividad(): Actividad | null;
  avisos(): { texto: string }[];
}

function actividad(idActividad: number, nombre: string, fecha: string, hora: string | null): Actividad {
  return {
    idActividad, idOrganizacion: 5, nombre, descripcion: null, fecha, hora,
    lugar: 'Salón comunal', latitud: null, longitud: null, tipo: null, cupos: null, responsable: null, frecuenciaDias: null
  };
}

// Inicio del voluntario (RF-56): sus organizaciones y las actividades de
// ellas. Ayuda en la Fundación Entrenubes y espera respuesta del Centro Día
// Renacer. Es el 13 de octubre de 2026 a las 10:00 a. m.
describe('PanelVoluntario', () => {
  let fixture: ComponentFixture<PanelVoluntario>;
  let inicio: VistaInicio;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 13, 10, 0));

    TestBed.configureTestingModule({
      imports: [PanelVoluntario],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { getNombreUsuario: () => 'Laura Peña' } },
        {
          provide: ActividadService,
          useValue: {
            listar: () => of([
              actividad(1, 'Bingo', '2026-10-01', '15:00'),
              actividad(2, 'Caminata', '2026-10-16', '08:00'),
              actividad(3, 'Taller de tejido', '2026-10-13', '15:00'),
              actividad(4, 'Novena de aguinaldos', '2026-12-16', '17:00')
            ]),
            listarPropuestasMias: () => of([])
          }
        },
        {
          provide: VoluntarioService,
          useValue: {
            listarOrganizaciones: () => of([
              { idOrganizacion: 5, nombre: 'Fundación Entrenubes', direccion: null, celular: null, correo: null, estado: 'ACEPTADA' },
              { idOrganizacion: 6, nombre: 'Centro Día Renacer', direccion: null, celular: null, correo: null, estado: 'PENDIENTE' }
            ])
          }
        },
        { provide: TiempoRealService, useValue: { cambios: () => NEVER } }
      ]
    });
    fixture = TestBed.createComponent(PanelVoluntario);
    fixture.detectChanges();
    inicio = fixture.componentInstance as unknown as VistaInicio;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('cuenta sus organizaciones y las actividades del mes y de los próximos siete días', () => {
    expect(inicio.stats().map((s) => [s.label, s.value])).toEqual([
      ['Organizaciones donde ayudas', 1],
      ['Actividades este mes', 3],
      ['Actividades en los próximos 7 días', 2]
    ]);
  });

  it('destaca la próxima actividad y avisa de la solicitud que espera respuesta', () => {
    expect(inicio.proximaActividad()?.nombre).toBe('Taller de tejido');
    expect(inicio.avisos().map((a) => a.texto)).toEqual([
      'Tu solicitud a Centro Día Renacer está esperando respuesta.'
    ]);

    const pagina: HTMLElement = fixture.nativeElement;
    expect(pagina.textContent).toContain('Taller de tejido');
    expect(pagina.textContent).toContain('Fundación Entrenubes');
  });
});
