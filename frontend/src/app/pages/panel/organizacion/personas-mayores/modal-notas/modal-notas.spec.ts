import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { NotaPersonaMayor, OrganizacionService } from '../../../../../core/organizacion/organizacion.service';
import { TiempoRealService } from '../../../../../core/tiempo-real/tiempo-real.service';
import { ModalNotas } from './modal-notas';

function nota(idNota: number, texto: string): NotaPersonaMayor {
  return {
    idNota, texto, autor: 'Fundación Entrenubes',
    fechaCreacion: '2026-10-14T10:00:00', fechaActualizacion: '2026-10-14T10:00:00', editada: false
  };
}

// Notas de la organización sobre una persona mayor (RF-74): agregar,
// editar y eliminar desde el modal.
describe('ModalNotas', () => {
  const servicio = {
    obtenerNotas: vi.fn(),
    crearNota: vi.fn(),
    editarNota: vi.fn(),
    eliminarNota: vi.fn()
  };
  let modal: any;

  beforeEach(() => {
    servicio.obtenerNotas.mockReset().mockReturnValue(of([nota(1, 'Primera visita')]));
    servicio.crearNota.mockReset().mockImplementation((_id: number, texto: string) => of(nota(2, texto)));
    servicio.editarNota.mockReset().mockImplementation((_id: number, idNota: number, texto: string) =>
      of({ ...nota(idNota, texto), editada: true }));
    servicio.eliminarNota.mockReset().mockReturnValue(of('Nota eliminada'));

    TestBed.configureTestingModule({
      imports: [ModalNotas],
      providers: [
        { provide: OrganizacionService, useValue: servicio },
        { provide: TiempoRealService, useValue: { cambios: () => of() } }
      ]
    });
    TestBed.overrideComponent(ModalNotas, { set: { template: '', imports: [] } });

    const fixture = TestBed.createComponent(ModalNotas);
    fixture.componentRef.setInput('idPersonaMayor', 10);
    modal = fixture.componentInstance;
    modal.ngOnInit();
  });

  it('carga las notas de la persona', () => {
    expect(servicio.obtenerNotas).toHaveBeenCalledWith(10);
    expect(modal.notas().map((n: NotaPersonaMayor) => n.texto)).toEqual(['Primera visita']);
  });

  it('agrega la nota nueva arriba, sin espacios sobrantes, y limpia el campo', () => {
    modal.textoNuevo = '  Llamada de seguimiento  ';
    modal.guardar();

    expect(servicio.crearNota).toHaveBeenCalledWith(10, 'Llamada de seguimiento');
    expect(modal.notas().map((n: NotaPersonaMayor) => n.texto)).toEqual(['Llamada de seguimiento', 'Primera visita']);
    expect(modal.textoNuevo).toBe('');
  });

  it('una nota vacía no se envía', () => {
    modal.textoNuevo = '   ';
    modal.guardar();

    expect(servicio.crearNota).not.toHaveBeenCalled();
  });

  it('edita y elimina una nota', () => {
    const primera = modal.notas()[0];

    modal.empezarEdicion(primera);
    modal.textoEditado = 'Primera visita: todo bien';
    modal.guardarEdicion(primera);

    expect(servicio.editarNota).toHaveBeenCalledWith(10, 1, 'Primera visita: todo bien');
    expect(modal.notas()[0].editada).toBe(true);
    expect(modal.editando()).toBeNull();

    modal.confirmarEliminacion(modal.notas()[0]);
    expect(servicio.eliminarNota).toHaveBeenCalledWith(10, 1);
    expect(modal.notas()).toEqual([]);
  });
});
