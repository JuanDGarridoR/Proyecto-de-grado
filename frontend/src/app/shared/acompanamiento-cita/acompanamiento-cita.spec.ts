import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AcompananteService } from '../../core/acompanantes/acompanante.service';
import { AuthService } from '../../core/auth/auth.service';
import { CitaMedica } from '../../core/citas-medicas/cita-medica.service';
import { AcompanamientoCita } from './acompanamiento-cita';

const YO = 20;

function cita(extra: Partial<CitaMedica> = {}): CitaMedica {
  return {
    idCita: 5, titulo: 'Colonoscopia', lugar: 'USS Usme', consultorio: null,
    fecha: '2026-10-22', hora: '08:00', observaciones: null,
    acompanamiento: 'REQUERIDO', idAcompananteCita: null, nombreAcompananteCita: null, puntoEncuentro: null,
    ...extra
  };
}

// Acompañamiento a citas médicas (RF-75): el acompañante se ofrece, escribe
// el punto de encuentro y puede cambiarlo o decir que ya no puede ir.
describe('AcompanamientoCita', () => {
  const servicio = { aceptarAcompanamientoCita: vi.fn(), cancelarAcompanamientoCita: vi.fn() };
  let componente: any;
  let fixture: any;
  let emitidas: CitaMedica[];

  function crear(datos: CitaMedica): void {
    fixture = TestBed.createComponent(AcompanamientoCita);
    fixture.componentRef.setInput('cita', datos);
    fixture.componentRef.setInput('idPersonaMayor', 10);
    fixture.componentRef.setInput('nombrePersona', 'Rosa Díaz');
    componente = fixture.componentInstance;
    emitidas = [];
    componente.actualizada.subscribe((c: CitaMedica) => emitidas.push(c));
  }

  beforeEach(() => {
    servicio.aceptarAcompanamientoCita.mockReset().mockImplementation((_p: number, _c: number, punto: string) =>
      of(cita({ idAcompananteCita: YO, nombreAcompananteCita: 'Laura Pérez', puntoEncuentro: punto })));
    servicio.cancelarAcompanamientoCita.mockReset().mockReturnValue(of(cita()));

    TestBed.configureTestingModule({
      imports: [AcompanamientoCita],
      providers: [
        { provide: AcompananteService, useValue: servicio },
        { provide: AuthService, useValue: { getIdUsuario: () => YO } }
      ]
    });
    TestBed.overrideComponent(AcompanamientoCita, { set: { template: '', imports: [] } });
  });

  it('sin punto de encuentro no se confirma', () => {
    crear(cita());
    componente.abrirFormulario();
    componente.punto = '   ';
    componente.confirmar();

    expect(servicio.aceptarAcompanamientoCita).not.toHaveBeenCalled();
    expect(componente.error()).toBe('Escribe dónde se van a encontrar para salir a la cita.');
  });

  it('al confirmar envía el punto de encuentro y avisa la cita actualizada', () => {
    crear(cita());
    componente.abrirFormulario();
    componente.punto = '  Portería del conjunto  ';
    componente.confirmar();

    expect(servicio.aceptarAcompanamientoCita).toHaveBeenCalledWith(10, 5, 'Portería del conjunto');
    expect(emitidas[0].idAcompananteCita).toBe(YO);
    expect(componente.formularioAbierto()).toBe(false);
  });

  it('distingue si la acompaña quien ve la cita u otro acompañante', () => {
    crear(cita({ idAcompananteCita: YO }));
    expect(componente.esMia()).toBe(true);
    expect(componente.tomada()).toBe(false);

    crear(cita({ idAcompananteCita: 21, nombreAcompananteCita: 'Pedro León' }));
    expect(componente.esMia()).toBe(false);
    expect(componente.tomada()).toBe(true);
  });

  it('quien la acompaña puede decir que ya no puede ir', () => {
    crear(cita({ idAcompananteCita: YO, puntoEncuentro: 'Portería' }));
    componente.cancelar();

    expect(servicio.cancelarAcompanamientoCita).toHaveBeenCalledWith(10, 5);
    expect(emitidas[0].idAcompananteCita).toBeNull();
  });
});
