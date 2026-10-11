import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';

/**
 * Rutas de la aplicación. Cada panel carga PlantillaPanel (barra lateral
 * y superior) y authGuard, que exige sesión con el rol de data.rol. Las
 * secciones que aún no existen muestran EnConstruccion con data.titulo.
 */
export const routes: Routes = [
  // Páginas públicas
  {
    path: '',
    loadComponent: () => import('./pages/inicio/inicio').then((m) => m.Inicio),
  },

  {
    path: 'iniciar-sesion',
    loadComponent: () => import('./pages/iniciar-sesion/iniciar-sesion').then((m) => m.IniciarSesion),
  },

  // Dirección anterior, por si alguien la tiene guardada.
  { path: 'login', redirectTo: 'iniciar-sesion', pathMatch: 'full' },

  {
    path: 'registro',
    loadComponent: () => import('./pages/registro/registro').then((m) => m.Registro),
  },

  // Panel de la organización
  {
    path: 'panel/organizacion',
    loadComponent: () =>
      import('./shared/plantilla-panel/plantilla-panel').then((m) => m.PlantillaPanel),

    canActivate: [authGuard],
    data: { rol: 'ORGANIZACION' },

    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/panel/organizacion/organizacion').then(
            (m) => m.PanelOrganizacion,
          ),
      },

      {
        path: 'personas-mayores',
        loadComponent: () =>
          import('./pages/panel/organizacion/personas-mayores/personas-mayores').then(
            (m) => m.PersonasMayores,
          ),
        data: { titulo: 'Personas mayores' },
      },

      {
        path: 'acompanantes',
        loadComponent: () =>
          import('./shared/en-construccion/en-construccion').then((m) => m.EnConstruccion),
        data: { titulo: 'Acompañantes' },
      },

      {
        path: 'voluntarios',
        loadComponent: () =>
          import('./pages/panel/organizacion/voluntarios/voluntarios').then(
            (m) => m.Voluntarios,
          ),
      },

      {
        path: 'actividades',
        loadComponent: () =>
          import('./pages/panel/organizacion/actividades/actividades').then(
            (m) => m.Actividades,
          ),
        data: { titulo: 'Actividades' },
      },

      {
        path: 'signos-vitales',
        loadComponent: () =>
          import('./pages/panel/organizacion/signos-vitales/signos-vitales').then(
            (m) => m.SignosVitales,
          ),
        data: { titulo: 'Signos vitales' },
      },

      {
        path: 'medicamentos',
        loadComponent: () =>
          import('./shared/en-construccion/en-construccion').then((m) => m.EnConstruccion),
        data: { titulo: 'Medicamentos' },
      },

      {
        path: 'donaciones',
        loadComponent: () =>
          import('./shared/en-construccion/en-construccion').then((m) => m.EnConstruccion),
        data: { titulo: 'Donaciones' },
      },

      {
        path: 'alertas',
        loadComponent: () =>
          import('./shared/en-construccion/en-construccion').then((m) => m.EnConstruccion),
        data: { titulo: 'Alertas' },
      },

      {
        path: 'analitica',
        loadComponent: () =>
          import('./pages/panel/organizacion/analitica/analitica').then((m) => m.Analitica),
      },

      {
        path: 'mapa',
        loadComponent: () =>
          import('./shared/en-construccion/en-construccion').then((m) => m.EnConstruccion),
        data: { titulo: 'Mapa' },
      },

      {
        path: 'perfil',
        loadComponent: () => import('./shared/perfil/perfil').then((m) => m.Perfil),
        data: {
          titulo: 'Perfil',
          tipoPerfil: 'ORGANIZACION',
        },
      },
    ],
  },

  // Panel del voluntario
  {
    path: 'panel/voluntario',
    loadComponent: () =>
      import('./shared/plantilla-panel/plantilla-panel').then((m) => m.PlantillaPanel),

    canActivate: [authGuard],
    data: { rol: 'VOLUNTARIO' },

    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/panel/voluntario/voluntario').then((m) => m.PanelVoluntario),
      },

      {
        path: 'organizaciones',
        loadComponent: () =>
          import('./pages/panel/voluntario/organizaciones/organizaciones').then(
            (m) => m.VoluntarioOrganizaciones,
          ),
      },

      {
        path: 'actividades',
        loadComponent: () =>
          import('./pages/panel/voluntario/actividades/actividades').then(
            (m) => m.VoluntarioActividades,
          ),
      },

      {
        path: 'intereses',
        loadComponent: () =>
          import('./shared/intereses/intereses').then((m) => m.Intereses),
        data: { rol: 'VOLUNTARIO' },
      },

      {
        path: 'perfil',
        loadComponent: () => import('./shared/perfil/perfil').then((m) => m.Perfil),
        data: {
          titulo: 'Perfil',
          tipoPerfil: 'VOLUNTARIO',
        },
      },
    ],
  },

  // Panel del acompañante
  {
    path: 'panel/acompanante',
    loadComponent: () =>
      import('./shared/plantilla-panel/plantilla-panel').then((m) => m.PlantillaPanel),

    canActivate: [authGuard],
    data: { rol: 'ACOMPANANTE' },

    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/panel/acompanante/acompanante').then((m) => m.PanelAcompanante),
      },

      {
        path: 'personas-mayores',
        loadComponent: () =>
          import('./pages/panel/acompanante/mis-personas-mayores/mis-personas-mayores').then(
            (m) => m.MisPersonasMayores,
          ),
      },

      {
        path: 'seguimiento',
        loadComponent: () =>
          import('./pages/panel/acompanante/seguimiento/seguimiento').then(
            (m) => m.Seguimiento,
          ),
      },

      {
        path: 'gestion',
        loadComponent: () =>
          import('./pages/panel/acompanante/gestion/gestion').then((m) => m.GestionCuidado),
      },

      {
        path: 'actividades',
        loadComponent: () =>
          import('./pages/panel/acompanante/actividades/actividades').then(
            (m) => m.ActividadesComponent,
          ),
      },

      {
        path: 'perfil',
        loadComponent: () => import('./shared/perfil/perfil').then((m) => m.Perfil),
        data: { tipoPerfil: 'ACOMPANANTE' },
      },
    ],
  },

  // Panel de la persona mayor
  {
    path: 'panel/persona-mayor',

    loadComponent: () =>
      import('./shared/plantilla-panel/plantilla-panel').then((m) => m.PlantillaPanel),

    canActivate: [authGuard],
    data: { rol: 'PERSONA_MAYOR' },

    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/panel/persona-mayor/persona-mayor').then(
            (m) => m.PanelPersonaMayor,
          ),
      },

      {
        path: 'actividades',
        loadComponent: () =>
          import('./pages/panel/persona-mayor/actividades/actividades').then(
            (m) => m.Actividades,
          ),
      },

      {
        path: 'intereses',
        loadComponent: () =>
          import('./shared/intereses/intereses').then((m) => m.Intereses),
        data: { rol: 'PERSONA_MAYOR' },
      },

      {
        path: 'recordatorios',
        loadComponent: () =>
          import('./pages/panel/persona-mayor/recordatorios/recordatorios').then(
            (m) => m.Recordatorios,
          ),
      },

      {
        path: 'signos-vitales',
        loadComponent: () =>
          import('./pages/panel/persona-mayor/signos-vitales/signos-vitales').then(
            (m) => m.SignosVitalesPersonaMayor,
          ),
      },

      {
        path: 'citas-medicas',
        loadComponent: () =>
          import('./pages/panel/persona-mayor/citas-medicas/citas-medicas').then(
            (m) => m.CitasMedicas,
          ),
      },

      {
        path: 'perfil',
        loadComponent: () => import('./shared/perfil/perfil').then((m) => m.Perfil),
        data: { tipoPerfil: 'PERSONA_MAYOR' },
      },
      {
        path: 'contactos',
        loadComponent: () =>
          import('./pages/panel/persona-mayor/contactos/contactos').then((m) => m.Contactos),
      },
      {
        path: 'organizaciones',
        loadComponent: () =>
          import('./pages/panel/persona-mayor/organizaciones/organizaciones').then(
            (m) => m.Organizaciones,
          ),
      },
    ],
  },
];
