import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
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

// El token viaja en cada petición al backend, menos en las públicas (RNF-02), y
// si vence (dura 15 minutos) se renueva una vez y se repite la petición.
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

  it('con un 401 renueva el token y repite la petición con el nuevo', () => {
    sessionStorage.setItem('refreshToken', 'renovacion-1');
    let respuesta: unknown;

    http.get('http://localhost:8080/api/persona-mayor/medicamentos').subscribe((r) => (respuesta = r));

    backend.expectOne('http://localhost:8080/api/persona-mayor/medicamentos')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('http://localhost:8080/api/auth/sesion/renovar')
      .flush({ token: 'token-nuevo', rol: 'PERSONA_MAYOR', mensaje: 'ok', idUsuario: 25, nombreUsuario: 'Ana' });

    const repetida = backend.expectOne('http://localhost:8080/api/persona-mayor/medicamentos');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer token-nuevo');
    repetida.flush([{ id: 1 }]);

    expect(respuesta).toEqual([{ id: 1 }]);
  });

  it('si con el token nuevo vuelve a dar 401, no renueva otra vez y deja pasar el error', () => {
    sessionStorage.setItem('refreshToken', 'renovacion-1');
    let error: HttpErrorResponse | undefined;

    http.put('http://localhost:8080/api/auth/contrasena', {}).subscribe({ error: (e) => (error = e) });

    backend.expectOne('http://localhost:8080/api/auth/contrasena')
      .flush('La contraseña actual es incorrecta', { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('http://localhost:8080/api/auth/sesion/renovar')
      .flush({ token: 'token-nuevo', rol: 'PERSONA_MAYOR', mensaje: 'ok', idUsuario: 25, nombreUsuario: 'Ana' });
    backend.expectOne('http://localhost:8080/api/auth/contrasena')
      .flush('La contraseña actual es incorrecta', { status: 401, statusText: 'Unauthorized' });

    backend.expectNone('http://localhost:8080/api/auth/sesion/renovar');
    expect(error?.error).toBe('La contraseña actual es incorrecta');
  });

  it('un 401 del inicio de sesión no intenta renovar', () => {
    sessionStorage.setItem('refreshToken', 'renovacion-1');

    http.post('http://localhost:8080/api/auth/login', {}).subscribe({ error: () => undefined });
    backend.expectOne('http://localhost:8080/api/auth/login')
      .flush({ mensaje: 'Correo o contraseña incorrectos' }, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone('http://localhost:8080/api/auth/sesion/renovar');
  });

  it('sin token de renovación (sesiones de antes) un 401 no intenta renovar', () => {
    http.get('http://localhost:8080/api/persona-mayor/medicamentos').subscribe({ error: () => undefined });
    backend.expectOne('http://localhost:8080/api/persona-mayor/medicamentos')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone('http://localhost:8080/api/auth/sesion/renovar');
  });
});
