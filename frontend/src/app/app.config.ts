import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';

// Fechas y números en español de Colombia (pipes date, number, etc.).
registerLocaleData(localeEsCo);

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { reintentosInterceptor } from './core/reintentos/reintentos.interceptor';

/**
 * Configuración global: rutas, cliente HTTP con los interceptores (el que
 * agrega el token y el que repite las consultas que fallan por un corte
 * pasajero), e idioma es-CO.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, reintentosInterceptor])),
    {provide: LOCALE_ID, 
    useValue: 'es-CO' }
  ]
};