import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { TiempoRealService } from '../../core/tiempo-real/tiempo-real.service';
import { MarcoPanel } from './marco-panel';

/** Acceso a las señales protegidas del marco. */
interface VistaMarco {
  noLeidas(): number;
  cuentaInactiva(): boolean;
}

// Marco común de los paneles: el mensaje de privacidad abre la política de
// datos, la campanita deja leídas las notificaciones (RF-50) y una cuenta
// inactiva ve el aviso para reactivarla (RF-10).
describe('MarcoPanel', () => {
  const marcarLeidas = vi.fn(() => of(undefined));
  const reactivarCuenta = vi.fn(() => of('Cuenta reactivada'));
  let cuentaActiva: boolean;
  let fixture: ComponentFixture<MarcoPanel>;

  function crearMarco(): VistaMarco {
    TestBed.configureTestingModule({
      imports: [MarcoPanel],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            cuentaActiva: () => of(cuentaActiva),
            reactivarCuenta,
            logout: vi.fn(),
            getRol: () => 'PERSONA_MAYOR',
            roles: () => of(['PERSONA_MAYOR'])
          }
        },
        {
          provide: NotificacionService,
          useValue: {
            listar: () => of({
              notificaciones: [
                { id: 1, mensaje: 'Es hora de tomar Losartán (50 mg).', fechaEnvio: new Date().toISOString(), leida: false }
              ],
              noLeidas: 1
            }),
            marcarLeidas
          }
        },
        { provide: TiempoRealService, useValue: { cambios: () => NEVER } }
      ]
    });
    fixture = TestBed.createComponent(MarcoPanel);
    fixture.detectChanges();
    return fixture.componentInstance as unknown as VistaMarco;
  }

  beforeEach(() => {
    cuentaActiva = true;
    marcarLeidas.mockClear();
    reactivarCuenta.mockClear();
  });

  it('el mensaje de privacidad abre la política de datos y «Entendido» la cierra', () => {
    crearMarco();
    const pagina: HTMLElement = fixture.nativeElement;
    expect(pagina.querySelector('[role="dialog"]')).toBeNull();

    (pagina.querySelector('.shell__privacy-message') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(pagina.querySelector('#politicaDatosTitulo')?.textContent).toContain('Cómo cuidamos tus datos');

    const entendido = [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Entendido');
    entendido!.click();
    fixture.detectChanges();
    expect(pagina.querySelector('[role="dialog"]')).toBeNull();
  });

  it('al abrir la campanita las notificaciones quedan leídas', () => {
    const marco = crearMarco();
    expect(marco.noLeidas()).toBe(1);

    fixture.componentInstance.toggleNotificaciones();

    expect(marcarLeidas).toHaveBeenCalledTimes(1);
    expect(marco.noLeidas()).toBe(0);
  });

  it('una cuenta inactiva ve el aviso y la puede reactivar desde ahí', () => {
    cuentaActiva = false;
    const marco = crearMarco();
    expect(marco.cuentaInactiva()).toBe(true);

    fixture.componentInstance.reactivarCuenta();

    expect(reactivarCuenta).toHaveBeenCalledTimes(1);
    expect(marco.cuentaInactiva()).toBe(false);
  });
});
