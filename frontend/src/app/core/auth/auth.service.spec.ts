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

// Sesión en el navegador (RF-09 y RF-11): se crea al iniciar sesión, se borra al
// cerrarla, una sesión recordada vencida no se restaura, y el token de acceso
// se renueva con el de renovación hasta que la sesión se cierra o se revoca.
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

  it('al iniciar sesión guarda también el token de renovación', () => {
    const servicio = crearServicio();

    servicio.login({ correo: 'ana.rojas@vitamas.co', contrasena: 'secreta1' }).subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('http://localhost:8080/api/auth/login')
      .flush({ token: token(EN_UNA_HORA), rol: 'PERSONA_MAYOR', mensaje: 'ok', idUsuario: 25,
        nombreUsuario: 'Ana Rojas', refreshToken: 'renovacion-1' });

    expect(servicio.getRefreshToken()).toBe('renovacion-1');
    expect(JSON.parse(localStorage.getItem('sesionRecordada')!).refreshToken).toBe('renovacion-1');
  });

  it('al cambiar de rol conserva el token de renovación de la sesión', () => {
    sessionStorage.setItem('token', token(EN_UNA_HORA));
    sessionStorage.setItem('refreshToken', 'renovacion-1');
    const servicio = crearServicio();

    servicio.cambiarRol('ACOMPANANTE').subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('http://localhost:8080/api/auth/roles/cambiar')
      .flush({ token: token(EN_UNA_HORA), rol: 'ACOMPANANTE', mensaje: 'ok', idUsuario: 25, nombreUsuario: 'Ana Rojas' });

    expect(servicio.getRefreshToken()).toBe('renovacion-1');
    expect(servicio.getRol()).toBe('ACOMPANANTE');
  });

  it('una sesión recordada con token de renovación se restaura aunque el de acceso haya vencido', () => {
    localStorage.setItem('sesionRecordada', JSON.stringify({ token: token(HACE_UNA_HORA), rol: 'ACOMPANANTE',
      idUsuario: '20', nombreUsuario: 'Carlos Díaz', refreshToken: 'renovacion-1' }));

    const servicio = crearServicio();

    expect(servicio.estaAutenticado()).toBe(true);
    expect(servicio.getRefreshToken()).toBe('renovacion-1');
  });

  it('renueva el token de acceso con el rol de la pestaña y actualiza la copia recordada', () => {
    sessionStorage.setItem('token', 'acceso-viejo');
    sessionStorage.setItem('rol', 'ACOMPANANTE');
    sessionStorage.setItem('refreshToken', 'renovacion-1');
    localStorage.setItem('sesionRecordada', JSON.stringify({ token: 'acceso-viejo', rol: 'ACOMPANANTE',
      idUsuario: '20', nombreUsuario: 'Carlos Díaz', refreshToken: 'renovacion-1' }));
    const servicio = crearServicio();
    let renovado = '';

    servicio.renovarToken().subscribe((nuevo) => (renovado = nuevo));
    const peticion = TestBed.inject(HttpTestingController).expectOne('http://localhost:8080/api/auth/sesion/renovar');
    expect(peticion.request.body).toEqual({ refreshToken: 'renovacion-1', rol: 'ACOMPANANTE' });
    peticion.flush({ token: 'acceso-nuevo', rol: 'ACOMPANANTE', mensaje: 'Sesión renovada', idUsuario: 20,
      nombreUsuario: 'Carlos Díaz' });

    expect(renovado).toBe('acceso-nuevo');
    expect(servicio.getToken()).toBe('acceso-nuevo');
    expect(servicio.getRefreshToken()).toBe('renovacion-1');
    expect(JSON.parse(localStorage.getItem('sesionRecordada')!).token).toBe('acceso-nuevo');
  });

  it('varias renovaciones a la vez hacen una sola petición', () => {
    sessionStorage.setItem('token', 'acceso-viejo');
    sessionStorage.setItem('refreshToken', 'renovacion-1');
    const servicio = crearServicio();

    servicio.renovarToken().subscribe();
    servicio.renovarToken().subscribe();

    TestBed.inject(HttpTestingController).expectOne('http://localhost:8080/api/auth/sesion/renovar')
      .flush({ token: 'acceso-nuevo', rol: 'PERSONA_MAYOR', mensaje: 'ok', idUsuario: 25, nombreUsuario: 'Ana' });
  });

  it('si la sesión ya no se puede renovar, la olvida y lleva a iniciar sesión', () => {
    sessionStorage.setItem('token', 'acceso-viejo');
    sessionStorage.setItem('refreshToken', 'renovacion-revocada');
    const servicio = crearServicio();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    servicio.renovarToken().subscribe({ error: () => undefined });
    TestBed.inject(HttpTestingController).expectOne('http://localhost:8080/api/auth/sesion/renovar')
      .flush({ mensaje: 'Tu sesión terminó. Vuelve a iniciar sesión.' }, { status: 401, statusText: 'Unauthorized' });

    expect(servicio.estaAutenticado()).toBe(false);
    expect(servicio.getRefreshToken()).toBeNull();
    expect(navegar).toHaveBeenCalledWith('/iniciar-sesion');
  });

  it('al cerrar sesión le pide al backend que revoque el token de renovación', () => {
    sessionStorage.setItem('token', token(EN_UNA_HORA));
    sessionStorage.setItem('refreshToken', 'renovacion-1');
    const servicio = crearServicio();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    servicio.logout();

    const peticion = TestBed.inject(HttpTestingController).expectOne('http://localhost:8080/api/auth/sesion/cerrar');
    expect(peticion.request.body).toEqual({ refreshToken: 'renovacion-1' });
    expect(servicio.getRefreshToken()).toBeNull();
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
