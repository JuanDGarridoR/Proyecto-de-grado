import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { FotoPerfilService } from './foto-perfil';

// Fotos de perfil desde el bucket privado: se piden con la sesión, una sola
// vez, y se muestran desde memoria.
describe('FotoPerfilService', () => {
  const RUTA = '40/0f8fad5b-d9cb-469f-a165-70867728950e.jpeg';

  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('token', 'token-de-prueba');
    vi.stubGlobal('URL', Object.assign(URL, {
      createObjectURL: vi.fn(() => 'blob:foto-1'),
      revokeObjectURL: vi.fn()
    }));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('pide la foto a auth-service y la muestra desde memoria', () => {
    const servicio = TestBed.inject(FotoPerfilService);
    let url: string | null = null;

    servicio.url(RUTA).subscribe((u) => (url = u));
    const peticion = TestBed.inject(HttpTestingController).expectOne(`http://localhost:8080/api/auth/fotos/${RUTA}`);
    expect(peticion.request.responseType).toBe('blob');
    peticion.flush(new Blob(['foto'], { type: 'image/jpeg' }));

    expect(url).toBe('blob:foto-1');
  });

  it('la misma foto se pide una sola vez', () => {
    const servicio = TestBed.inject(FotoPerfilService);

    servicio.url(RUTA).subscribe();
    servicio.url(RUTA).subscribe();

    TestBed.inject(HttpTestingController).expectOne(`http://localhost:8080/api/auth/fotos/${RUTA}`)
      .flush(new Blob(['foto']));
  });

  it('sin foto, o si no carga, devuelve null', () => {
    const servicio = TestBed.inject(FotoPerfilService);
    let sinFoto: string | null = 'x';
    let rota: string | null = 'x';

    servicio.url(null).subscribe((u) => (sinFoto = u));
    servicio.url(RUTA).subscribe((u) => (rota = u));
    TestBed.inject(HttpTestingController).expectOne(`http://localhost:8080/api/auth/fotos/${RUTA}`)
      .flush(null, { status: 404, statusText: 'Not Found' });

    expect(sinFoto).toBeNull();
    expect(rota).toBeNull();
  });

  it('una URL que el navegador ya puede mostrar se usa tal cual', () => {
    const servicio = TestBed.inject(FotoPerfilService);
    let url: string | null = null;

    servicio.url('blob:vista-previa').subscribe((u) => (url = u));

    TestBed.inject(HttpTestingController).expectNone(() => true);
    expect(url).toBe('blob:vista-previa');
  });
});
