import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';
import { API_URL } from '../api';

/** Login con correo y contraseña. */
export interface LoginRequest {
  correo: string;
  contrasena: string;
}

/** Datos del formulario de registro (ver RegistroRequest en auth-service). */
export interface RegistroRequest {
  nombreUsuario: string;
  correo?: string;
  contrasena?: string;
  rol: string;
  direccion?: string;
  celular?: string;
  fechaNacimiento?: string;
  genero?: string;
  codigo?: string;
  /** El backend no crea la cuenta sin esto y guarda la constancia de aceptación. */
  aceptaTerminos?: boolean;
}

/** Celular al que se envía el código OTP. */
export interface EnviarOtpRequest {
  celular: string;
}

/** Respuesta de mensajeria-service al pedir un código. */
export interface OtpEnviarResponse {
  success: boolean;
  message: string;
}

/** Login con celular y código OTP. */
export interface OtpLoginRequest {
  celular: string;
  codigo: string;
}

/** Registro con código OTP (ver registroOtp). */
export interface OtpRegistroRequest {
  celular: string;
  codigo: string;
  nombreUsuario: string;
  rol: string;
  correo?: string;
  fechaNacimiento?: string;
  genero?: string;
  direccion?: string;
}

/** Respuesta del login y del registro: el token y los datos que se guardan en la sesión. */
export interface LoginResponse {
  token: string;
  rol: string;
  mensaje: string;
  idUsuario: number;
  nombreUsuario: string;
}

/** Recuperación de contraseña con el código OTP enviado al celular. */
export interface RestablecerContrasenaRequest {
  celular: string;
  codigo: string;
  contrasena: string;
  confirmarContrasena: string;
}

/**
 * Cada pestaña guarda su sesión en sessionStorage (así se pueden tener
 * varias cuentas abiertas a la vez). Además se guarda una copia de la
 * última sesión iniciada en localStorage, que sobrevive al cerrar el
 * navegador: al abrir una ventana nueva sin sesión se restaura esa copia.
 */
const CLAVES_SESION = ['token', 'rol', 'idUsuario', 'nombreUsuario'] as const;
const CLAVE_SESION_RECORDADA = 'sesionRecordada';

type Sesion = Record<(typeof CLAVES_SESION)[number], string>;

/** Revisa solo la fecha de vencimiento del token; la firma la valida el backend. */
function tokenVigente(token: string): boolean {
  try {
    const payload = JSON.parse(
      atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))
    );
    return typeof payload.exp !== 'number' || payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

function leerSesionRecordada(): Sesion | null {
  try {
    const guardada = localStorage.getItem(CLAVE_SESION_RECORDADA);
    return guardada ? (JSON.parse(guardada) as Sesion) : null;
  } catch {
    return null;
  }
}

function escribirSesionRecordada(sesion: Sesion | null): void {
  try {
    if (sesion) {
      localStorage.setItem(CLAVE_SESION_RECORDADA, JSON.stringify(sesion));
    } else {
      localStorage.removeItem(CLAVE_SESION_RECORDADA);
    }
  } catch {
    // Sin acceso a localStorage (p. ej. modo privado estricto): la sesión
    // simplemente no se recuerda al cerrar la ventana.
  }
}

/**
 * Si esta pestaña no tiene sesión, intenta recuperar la última recordada.
 * Devuelve si la pestaña quedó con sesión.
 */
function restaurarSesion(): boolean {
  if (sessionStorage.getItem('token')) {
    return true;
  }

  const recordada = leerSesionRecordada();

  if (!recordada?.token) {
    return false;
  }

  if (!tokenVigente(recordada.token)) {
    escribirSesionRecordada(null);
    return false;
  }

  for (const clave of CLAVES_SESION) {
    sessionStorage.setItem(clave, recordada[clave]);
  }

  return true;
}

/** Panel al que se envía a cada rol después de iniciar sesión. */
const RUTAS_POR_ROL: Record<string, string> = {
  ORGANIZACION: '/panel/organizacion',
  VOLUNTARIO: '/panel/voluntario',
  ACOMPANANTE: '/panel/acompanante',
  PERSONA_MAYOR: '/panel/persona-mayor'
};

/**
 * Sesión del usuario: login (con contraseña o con OTP), registro, cierre de
 * sesión y los datos de la sesión guardados en el navegador.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly apiUrl = `${API_URL}/auth`;
  private readonly otpApiUrl = `${API_URL}/otp`;

  private readonly autenticadoSignal = signal(restaurarSesion());

  /**
   * Nombre que se muestra en el panel (arriba a la derecha). Es un signal
   * para que al editarlo en "Mi información" se vea al instante.
   */
  private readonly nombreUsuarioSignal = signal(
    sessionStorage.getItem('nombreUsuario') ?? 'Usuario'
  );

  /**
   * URL de la foto de perfil del usuario, o null si no tiene (entonces se
   * muestra la inicial). Se pide una vez por sesión (ver cargarFotoPerfil).
   */
  private readonly fotoPerfilSignal = signal<string | null>(null);
  private fotoPerfilCargada = false;

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiUrl}/login`, request)
      .pipe(
        tap((response) => this.guardarSesion(response))
      );
  }

  registro(request: RegistroRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiUrl}/registro`, request)
      .pipe(
        tap((response) => this.guardarSesion(response))
      );
  }

  celularExiste(celular: string): Observable<boolean> {
  return this.http.get<boolean>(
    `${this.apiUrl}/celular-existe`,
    {
      params: { celular }
    }
  );
}

restablecerContrasena(
  request: RestablecerContrasenaRequest
) {
  return this.http.post(
    `${this.apiUrl}/restablecer-contrasena`,
    request
  );
}

  /**
   * El envío del código está en mensajeria-service (/api/otp/send), no en
   * auth-service. Espera "phoneNumber" en lugar de "celular" y responde
   * { success, message }, no un LoginResponse.
   */
  enviarOtp(celular: string): Observable<OtpEnviarResponse> {
    return this.http.post<OtpEnviarResponse>(
      `${this.otpApiUrl}/send`,
      { phoneNumber: celular }
    );
  }

  loginOtp(request: OtpLoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(
        `${this.apiUrl}/login-otp`,
        request
      )
      .pipe(
        tap((response) => this.guardarSesion(response))
      );
  }

  /**
   * Registro con código OTP. Usa el mismo /api/auth/registro que el
   * registro normal; el backend no recibe ni verifica el código. Hoy
   * ninguna página lo usa.
   */
  registroOtp(request: OtpRegistroRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(
        `${this.apiUrl}/registro`,
        request
      )
      .pipe(
        tap((response) => this.guardarSesion(response))
      );
  }

  /**
   * Borra la cuenta del usuario autenticado, sea cual sea su rol. El backend
   * toma el id del token y guarda la razón (ver razones-retiro.ts).
   */
  eliminarCuenta(razon: string, comentario: string | null): Observable<string> {
    return this.http.delete(`${this.apiUrl}/cuenta`, {
      body: { razon, comentario },
      responseType: 'text'
    });
  }

  /** false si el usuario inactivó su cuenta (nadie más la ve hasta que la reactive). */
  cuentaActiva(): Observable<boolean> {
    return this.http
      .get<{ activo: boolean }>(`${this.apiUrl}/informacion`)
      .pipe(map((informacion) => informacion.activo !== false));
  }

  /** La cuenta deja de verse para sus organizaciones, acompañantes y demás vínculos. */
  inactivarCuenta(): Observable<string> {
    return this.http.put(`${this.apiUrl}/cuenta/inactivar`, {}, { responseType: 'text' });
  }

  reactivarCuenta(): Observable<string> {
    return this.http.put(`${this.apiUrl}/cuenta/reactivar`, {}, { responseType: 'text' });
  }

  /** Roles de la cuenta: una persona mayor también puede ser acompañante y al revés (RF-76). */
  roles(): Observable<string[]> {
    return this.http
      .get<{ roles: string[] }>(`${this.apiUrl}/roles`)
      .pipe(map((respuesta) => respuesta.roles));
  }

  /**
   * Pasa la misma cuenta a otro rol (persona mayor ↔ acompañante) con un
   * token nuevo. Si aún no tenía ese rol, el backend se lo activa con su
   * misma información. Después hay que llevarla a su panel (redirigirSegunRol).
   */
  cambiarRol(rol: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiUrl}/roles/cambiar`, { rol })
      .pipe(tap((response) => this.guardarSesion(response)));
  }

  /** Guarda la sesión en la pestaña y una copia "recordada" para las ventanas nuevas. */
  private guardarSesion(response: LoginResponse): void {
    sessionStorage.setItem('token', response.token);
    sessionStorage.setItem('rol', response.rol);
    sessionStorage.setItem('idUsuario', String(response.idUsuario));
    sessionStorage.setItem('nombreUsuario', response.nombreUsuario);
    this.nombreUsuarioSignal.set(response.nombreUsuario);
    this.olvidarFotoPerfil();

    escribirSesionRecordada({
      token: response.token,
      rol: response.rol,
      idUsuario: String(response.idUsuario),
      nombreUsuario: response.nombreUsuario
    });

    // Avisa a toda la aplicación que hay una sesión.
    this.autenticadoSignal.set(true);
  }

  redirigirSegunRol(rol: string): void {
    const ruta = RUTAS_POR_ROL[rol] ?? '/';
    this.router.navigateByUrl(ruta);
  }

  logout(): void {
    // Solo se olvida la sesión recordada si es la de esta pestaña; así,
    // cerrar sesión aquí no afecta a otra cuenta abierta en otra pestaña.
    if (leerSesionRecordada()?.token === sessionStorage.getItem('token')) {
      escribirSesionRecordada(null);
    }

    sessionStorage.removeItem('token');
    sessionStorage.removeItem('rol');
    sessionStorage.removeItem('idUsuario');
    sessionStorage.removeItem('nombreUsuario');
    this.nombreUsuarioSignal.set('Usuario');
    this.olvidarFotoPerfil();

    // Avisa a toda la aplicación que la sesión terminó.
    this.autenticadoSignal.set(false);

    this.router.navigateByUrl('/');
  }

  getToken(): string | null {
    return sessionStorage.getItem('token');
  }

  getIdUsuario(): number | null {
    const idUsuario = sessionStorage.getItem('idUsuario');
    return idUsuario ? Number(idUsuario) : null;
  }

  /** Lee un signal, así que dentro de computed() o de una plantilla se actualiza solo. */
  getNombreUsuario(): string {
    return this.nombreUsuarioSignal();
  }

  /** Se llama después de guardar un nombre nuevo en "Mi información". */
  actualizarNombreUsuario(nombre: string): void {
    sessionStorage.setItem('nombreUsuario', nombre);
    this.nombreUsuarioSignal.set(nombre);

    const recordada = leerSesionRecordada();
    if (recordada && recordada.token === sessionStorage.getItem('token')) {
      escribirSesionRecordada({ ...recordada, nombreUsuario: nombre });
    }
  }

  /** Lee un signal: la foto cambia sola en el panel al subirla o quitarla. */
  getFotoPerfil(): string | null {
    return this.fotoPerfilSignal();
  }

  /**
   * Pide la foto a "Mi información" si aún no se tiene. La llaman el panel y
   * la barra de navegación; solo la primera vez hace la petición.
   */
  cargarFotoPerfil(): void {
    if (this.fotoPerfilCargada || !this.getToken()) {
      return;
    }

    this.fotoPerfilCargada = true;

    this.http.get<{ fotoPerfil?: string | null }>(`${this.apiUrl}/informacion`).subscribe({
      next: (informacion) => this.fotoPerfilSignal.set(informacion.fotoPerfil ?? null),
      // Sin foto se ve la inicial; se reintenta en la próxima carga del panel.
      error: () => (this.fotoPerfilCargada = false),
    });
  }

  /** Se llama después de subir o quitar la foto en "Mi información". */
  actualizarFotoPerfil(url: string | null): void {
    this.fotoPerfilCargada = true;
    this.fotoPerfilSignal.set(url);
  }

  /** Al entrar con otra cuenta o salir, la foto anterior ya no aplica. */
  private olvidarFotoPerfil(): void {
    this.fotoPerfilCargada = false;
    this.fotoPerfilSignal.set(null);
  }

  getRol(): string | null {
    return sessionStorage.getItem('rol');
  }

  estaAutenticado(): boolean {
    return this.autenticadoSignal();
  }

  /** El signal de la sesión, para reaccionar con effect() (ver TiempoRealService). */
  estaAutenticadoSignal() {
    return this.autenticadoSignal;
  }
}