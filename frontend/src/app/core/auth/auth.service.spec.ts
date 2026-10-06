import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from './auth.service';

/** Token con la fecha de vencimiento indicada (la firma la revisa el backend, no el frontend). */
function token(vence: Date): string {
  const payload = btoa(JSON.stringify({ idUsuario: 25, rol: 'PERSONA_MAYOR', exp: Math.floor(vence.getTime() / 1000) }));
  return `encabezado.${payload}.firma`;
}

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

const EN_UNA_HORA = new Date(Date.now() + 60 * 60 * 1000);
const HACE_UNA_HORA = new Date(Date.now() - 60 * 60 * 1000);

// Sesión en el navegador (RNF-09): se crea al iniciar sesión, se borra al
// cerrarla y una sesión recordada vencida no se restaura.
describe('AuthService', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', almacenamientoEnMemoria());
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function crearServicio(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    return TestBed.inject(AuthService);
  }

  it('al iniciar sesión guarda la sesión de la pestaña', () => {
    const servicio = crearServicio();
    const vigente = token(EN_UNA_HORA);

    servicio.login({ correo: 'ana.rojas@vitamas.co', contrasena: 'secreta1' }).subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('http://localhost:8080/api/auth/login')
      .flush({ token: vigente, rol: 'PERSONA_MAYOR', mensaje: 'Inicio de sesión exitoso', idUsuario: 25, nombreUsuario: 'Ana Rojas' });

    expect(servicio.estaAutenticado()).toBe(true);
    expect(servicio.getToken()).toBe(vigente);
    expect(servicio.getRol()).toBe('PERSONA_MAYOR');
    expect(servicio.getIdUsuario()).toBe(25);
  });

  it('al cerrar sesión borra la sesión y vuelve al inicio', () => {
    sessionStorage.setItem('token', token(EN_UNA_HORA));
    sessionStorage.setItem('rol', 'PERSONA_MAYOR');
    const servicio = crearServicio();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    servicio.logout();

    expect(servicio.estaAutenticado()).toBe(false);
    expect(servicio.getToken()).toBeNull();
    expect(servicio.getRol()).toBeNull();
    expect(navegar).toHaveBeenCalledWith('/');
  });

  it('una sesión recordada vigente se restaura al abrir otra ventana', () => {
    const vigente = token(EN_UNA_HORA);
    localStorage.setItem('sesionRecordada', JSON.stringify(
      { token: vigente, rol: 'ACOMPANANTE', idUsuario: '20', nombreUsuario: 'Carlos Díaz' }));

    const servicio = crearServicio();

    expect(servicio.estaAutenticado()).toBe(true);
    expect(servicio.getRol()).toBe('ACOMPANANTE');
  });

  it('una sesión recordada vencida no se restaura y se olvida', () => {
    localStorage.setItem('sesionRecordada', JSON.stringify(
      { token: token(HACE_UNA_HORA), rol: 'ACOMPANANTE', idUsuario: '20', nombreUsuario: 'Carlos Díaz' }));

    const servicio = crearServicio();

    expect(servicio.estaAutenticado()).toBe(false);
    expect(localStorage.getItem('sesionRecordada')).toBeNull();
  });

  it('envía a cada rol a su propio panel', () => {
    const servicio = crearServicio();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    servicio.redirigirSegunRol('ORGANIZACION');
    servicio.redirigirSegunRol('PERSONA_MAYOR');

    expect(navegar).toHaveBeenNthCalledWith(1, '/panel/organizacion');
    expect(navegar).toHaveBeenNthCalledWith(2, '/panel/persona-mayor');
  });
});
