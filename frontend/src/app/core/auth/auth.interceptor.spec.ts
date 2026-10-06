import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';

/** localStorage en memoria: con Node 25 o superior el de jsdom queda sin definir. */
function almacenamientoEnMemoria(): Storage {
  const datos = new Map<string, string>();
  return {
    get length() {
      return datos.size;
    },
    clear: () => datos.clear(),
    getItem: (clave: string) => datos.get(clave) ?? null,
    key: (indice: number) => [...datos.keys()][indice] ?? null,
    removeItem: (clave: string) => {
      datos.delete(clave);
    },
    setItem: (clave: string, valor: string) => {
      datos.set(clave, String(valor));
    }
  };
}

// El token viaja en cada petición al backend, menos en las públicas (RNF-07).
describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    vi.stubGlobal('localStorage', almacenamientoEnMemoria());
    sessionStorage.clear();
    sessionStorage.setItem('token', 'token-de-prueba');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });

    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('agrega el token a las peticiones protegidas', () => {
    http.get('http://localhost:8080/api/persona-mayor/medicamentos').subscribe();

    const peticion = backend.expectOne('http://localhost:8080/api/persona-mayor/medicamentos');
    expect(peticion.request.headers.get('Authorization')).toBe('Bearer token-de-prueba');
    peticion.flush([]);
  });

  it('no agrega el token al inicio de sesión ni al envío del código OTP', () => {
    http.post('http://localhost:8080/api/auth/login', {}).subscribe();
    http.post('http://localhost:8080/api/otp/send', {}).subscribe();

    expect(backend.expectOne('http://localhost:8080/api/auth/login').request.headers.has('Authorization')).toBe(false);
    expect(backend.expectOne('http://localhost:8080/api/otp/send').request.headers.has('Authorization')).toBe(false);
  });
});
