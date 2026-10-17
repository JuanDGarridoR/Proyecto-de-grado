import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';

import { Intereses } from './intereses';
import { AuthService } from '../../core/auth/auth.service';
import { Gusto, GustoService } from '../../core/gustos/gusto.service';
import { VoluntarioService } from '../../core/voluntario/voluntario.service';
import { TiempoRealService } from '../../core/tiempo-real/tiempo-real.service';

// Intereses (RF-16): si el suyo no está en la lista, la persona lo escribe,
// se agrega al catálogo en la categoría abierta y queda marcado.
describe('Intereses: agregar un interés que no está en la lista', () => {
  const catalogo: Gusto[] = [
    { idGusto: 1, nombre: 'Música ranchera', categoria: 'GUSTO' },
    { idGusto: 2, nombre: 'Caminar', categoria: 'HOBBY' },
  ];

  const gustoService = {
    listar: vi.fn(),
    listarAsignados: vi.fn(),
    crear: vi.fn(),
    asignar: vi.fn(),
  };

  function crear(): any {
    gustoService.listar.mockReset().mockReturnValue(of(catalogo));
    gustoService.listarAsignados.mockReset().mockReturnValue(of([]));
    gustoService.crear.mockReset().mockImplementation((request: any) =>
      of({ idGusto: 9, ...request }),
    );

    TestBed.configureTestingModule({
      imports: [Intereses],
      providers: [
        { provide: GustoService, useValue: gustoService },
        { provide: AuthService, useValue: { getIdUsuario: () => 10 } },
        { provide: VoluntarioService, useValue: {} },
        { provide: TiempoRealService, useValue: { cambios: () => NEVER } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { rol: 'PERSONA_MAYOR' } } } },
      ],
    });
    TestBed.overrideComponent(Intereses, { set: { template: '', imports: [] } });

    const componente = TestBed.createComponent(Intereses).componentInstance as any;
    componente.ngOnInit();
    return componente;
  }

  it('crea el interés en la categoría abierta, sin espacios de más, y lo marca', () => {
    const c = crear();
    c.cambiarCategoria('HOBBY');
    c.nuevoInteres.set('  Jugar   tejo ');

    c.agregarInteres();

    expect(gustoService.crear).toHaveBeenCalledWith({ nombre: 'Jugar tejo', categoria: 'HOBBY' });
    expect(c.gustosDeCategoriaActiva().map((g: Gusto) => g.nombre)).toContain('Jugar tejo');
    expect(c.estaSeleccionado(9)).toBe(true);
    expect(c.nuevoInteres()).toBe('');
    expect(c.mensajeNuevoInteres().error).toBe(false);
  });

  it('si ya existe, aunque cambien tildes o mayúsculas, solo lo marca y abre su categoría', () => {
    const c = crear();
    c.cambiarCategoria('TALENTO');
    c.nuevoInteres.set('MUSICA ranchera');

    c.agregarInteres();

    expect(gustoService.crear).not.toHaveBeenCalled();
    expect(c.estaSeleccionado(1)).toBe(true);
    expect(c.categoriaActiva()).toBe('GUSTO');
    expect(c.mensajeNuevoInteres().texto).toContain('ya estaba en Gustos');
  });

  it('un texto vacío no se envía', () => {
    const c = crear();
    c.nuevoInteres.set('   ');

    c.agregarInteres();

    expect(gustoService.crear).not.toHaveBeenCalled();
    expect(c.mensajeNuevoInteres()).toBeNull();
  });

  it('si otra persona lo agregó hace un momento, trae el catálogo y lo marca', () => {
    const c = crear();
    gustoService.crear.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    gustoService.listar.mockReturnValue(
      of([...catalogo, { idGusto: 7, nombre: 'Jugar tejo', categoria: 'HOBBY' }]),
    );
    c.cambiarCategoria('HOBBY');
    c.nuevoInteres.set('Jugar tejo');

    c.agregarInteres();

    expect(c.estaSeleccionado(7)).toBe(true);
    expect(c.agregandoInteres()).toBe(false);
  });

  it('muestra el motivo cuando el servidor rechaza el nombre', () => {
    const c = crear();
    gustoService.crear.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: 'La categoría no es válida' })),
    );
    c.nuevoInteres.set('Algo');

    c.agregarInteres();

    expect(c.mensajeNuevoInteres()).toEqual({ texto: 'La categoría no es válida', error: true });
  });
});
