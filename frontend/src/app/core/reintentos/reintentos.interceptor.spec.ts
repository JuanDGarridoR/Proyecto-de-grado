import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ESPERA_BASE_MS, reintentosInterceptor } from './reintentos.interceptor';

// Un corte pasajero no le muestra un error a la persona: las consultas se
// repiten solas; lo que cambia datos no se repite nunca.
describe('reintentosInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([reintentosInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('repite un GET que falló por un servicio caído y entrega la respuesta', () => {
    let respuesta: unknown;
    http.get('/api/gustos').subscribe((r) => (respuesta = r));

    backend.expectOne('/api/gustos').flush('caído', { status: 503, statusText: 'Service Unavailable' });
    vi.advanceTimersByTime(ESPERA_BASE_MS);
    backend.expectOne('/api/gustos').flush(['Ajedrez']);

    expect(respuesta).toEqual(['Ajedrez']);
  });

  it('después de dos reintentos muestra el error', () => {
    let estado = 0;
    http.get('/api/gustos').subscribe({ error: (e) => (estado = e.status) });

    backend.expectOne('/api/gustos').error(new ProgressEvent('error'));
    vi.advanceTimersByTime(ESPERA_BASE_MS);
    backend.expectOne('/api/gustos').error(new ProgressEvent('error'));
    vi.advanceTimersByTime(ESPERA_BASE_MS * 2);
    backend.expectOne('/api/gustos').error(new ProgressEvent('error'));

    expect(estado).toBe(0);
  });

  it('no repite un error que no es pasajero, como un 404', () => {
    let estado = 0;
    http.get('/api/gustos/9').subscribe({ error: (e) => (estado = e.status) });

    backend.expectOne('/api/gustos/9').flush('No existe', { status: 404, statusText: 'Not Found' });

    expect(estado).toBe(404);
  });

  it('nunca repite un POST, aunque el servicio esté caído', () => {
    let estado = 0;
    http.post('/api/persona-mayor/emergencia', {}).subscribe({ error: (e) => (estado = e.status) });

    backend.expectOne('/api/persona-mayor/emergencia').flush('caído', { status: 503, statusText: 'Service Unavailable' });
    vi.advanceTimersByTime(ESPERA_BASE_MS * 5);

    expect(estado).toBe(503);
  });
});
