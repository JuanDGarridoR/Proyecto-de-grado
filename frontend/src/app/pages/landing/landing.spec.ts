import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Landing } from './landing';

// Página pública de inicio (RF-57): presenta la plataforma y sus módulos y
// lleva al registro y al inicio de sesión.
describe('Landing', () => {
  it('presenta los módulos de la plataforma y lleva al registro y al inicio de sesión', async () => {
    TestBed.configureTestingModule({ imports: [Landing], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Landing);
    fixture.detectChanges();
    await fixture.whenStable();

    const pagina: HTMLElement = fixture.nativeElement;
    for (const modulo of ['Personas Mayores', 'Acompañantes', 'Voluntarios', 'Organizaciones', 'Analítica']) {
      expect(pagina.textContent).toContain(modulo);
    }

    const enlaces = [...pagina.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(enlaces).toContain('/registro');
    expect(enlaces).toContain('/iniciar-sesion');
  });
});
