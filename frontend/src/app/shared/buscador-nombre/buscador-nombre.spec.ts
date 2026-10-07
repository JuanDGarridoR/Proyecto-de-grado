import { filtrarPorNombre, normalizar } from './buscador-nombre';

// Búsqueda rápida por nombre en las listas de cosas asociadas.
describe('búsqueda por nombre', () => {
  const lista = [
    { nombre: 'José Martínez' },
    { nombre: 'Rosa Elvira Gómez' },
    { nombre: 'Fundación Entrenubes Usme' }
  ];

  it('ignora mayúsculas y tildes', () => {
    expect(normalizar('  José ÁLVAREZ ')).toBe('jose alvarez');
    expect(filtrarPorNombre(lista, 'jose').map((p) => p.nombre)).toEqual(['José Martínez']);
    expect(filtrarPorNombre(lista, 'GOMEZ').map((p) => p.nombre)).toEqual(['Rosa Elvira Gómez']);
  });

  it('busca en cualquier parte del nombre', () => {
    expect(filtrarPorNombre(lista, 'entrenubes').length).toBe(1);
  });

  it('sin texto devuelve la lista completa', () => {
    expect(filtrarPorNombre(lista, '   ')).toEqual(lista);
  });
});
