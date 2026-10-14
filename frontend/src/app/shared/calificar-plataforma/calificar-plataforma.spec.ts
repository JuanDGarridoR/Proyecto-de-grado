import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { CalificarPlataforma } from './calificar-plataforma';

// Calificación de la plataforma desde el perfil (RF-70): de 0 a 5
// estrellas y una observación opcional; si ya calificó, se actualiza.
describe('CalificarPlataforma', () => {
  const get = vi.fn();
  const put = vi.fn();

  function crear(guardada: unknown = null): any {
    get.mockReset().mockReturnValue(of(guardada));
    put.mockReset().mockImplementation((_url: string, cuerpo: any) =>
      of({ ...cuerpo, fechaActualizacion: '2026-10-14T10:00:00' }),
    );

    TestBed.configureTestingModule({
      imports: [CalificarPlataforma],
      providers: [{ provide: HttpClient, useValue: { get, put } }],
    });
    TestBed.overrideComponent(CalificarPlataforma, { set: { template: '', imports: [] } });

    const componente = TestBed.createComponent(CalificarPlataforma).componentInstance as any;
    componente.ngOnInit();
    return componente;
  }

  it('sin estrellas elegidas no se puede enviar', () => {
    const c = crear();

    expect(c.textoActual()).toBe('Sin calificar');
    expect(c.hayCambios).toBe(false);
  });

  it('tocar la misma estrella otra vez la quita, hasta llegar a 0', () => {
    const c = crear();

    c.elegir(1);
    expect(c.estrellas()).toBe(1);

    c.elegir(1);
    expect(c.estrellas()).toBe(0);
    expect(c.textoActual()).toBe('0 de 5 · Muy mala');
    // 0 estrellas también es una calificación válida.
    expect(c.hayCambios).toBe(true);
  });

  it('envía las estrellas y la observación sin espacios sobrantes', () => {
    const c = crear();
    c.elegir(4);
    c.comentario = '  Me gustaría letra más grande  ';

    c.guardar();

    expect(put).toHaveBeenCalledWith(expect.stringContaining('/auth/calificacion'), {
      estrellas: 4,
      comentario: 'Me gustaría letra más grande',
    });
    expect(c.mensaje()).toBe('¡Gracias por calificar VITA+!');
    expect(c.hayCambios).toBe(false);
  });

  it('muestra la calificación que ya dejó y solo deja guardar si cambia algo', () => {
    const c = crear({ estrellas: 5, comentario: 'Muy útil', fechaActualizacion: '2026-10-01T09:00:00' });

    expect(c.estrellas()).toBe(5);
    expect(c.comentario).toBe('Muy útil');
    expect(c.hayCambios).toBe(false);

    c.elegir(3);
    expect(c.hayCambios).toBe(true);

    c.guardar();
    expect(c.mensaje()).toBe('Tu calificación quedó actualizada.');
  });
});
