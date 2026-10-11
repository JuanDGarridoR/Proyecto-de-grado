import { OpcionMenuPanel } from '../marco-panel/marco-panel';

/** Lo que cambia entre los paneles: nombre del rol, color de acento y menú lateral. */
export interface ConfiguracionPanel {
  roleLabel: string;
  roleAccent: string;
  navItems: OpcionMenuPanel[];
}

/**
 * Configuración del panel de cada rol. Convención de la barra lateral:
 * "Inicio" siempre va primero y "Perfil" siempre al final; en medio, las
 * secciones de más a menos importantes. Los nombres son cortos y sin
 * posesivos ("Recordatorios", no "Mis recordatorios"), y una misma sección
 * se llama igual y usa el mismo ícono en todos los roles.
 */
export const CONFIGURACION_PANEL: Record<string, ConfiguracionPanel> = {
  PERSONA_MAYOR: {
    roleLabel: 'Persona mayor',
    roleAccent: 'var(--vita-green)',
    navItems: [
      { icon: 'home', label: 'Inicio', path: '/panel/persona-mayor' },
      { icon: 'clock', label: 'Recordatorios', path: '/panel/persona-mayor/recordatorios' },
      { icon: 'phone', label: 'Contactos', path: '/panel/persona-mayor/contactos' },
      { icon: 'activity', label: 'Signos vitales', path: '/panel/persona-mayor/signos-vitales' },
      { icon: 'stethoscope', label: 'Citas médicas', path: '/panel/persona-mayor/citas-medicas' },
      { icon: 'calendar', label: 'Actividades', path: '/panel/persona-mayor/actividades' },
      { icon: 'building', label: 'Organizaciones', path: '/panel/persona-mayor/organizaciones' },
      { icon: 'heart', label: 'Intereses', path: '/panel/persona-mayor/intereses' },
      { icon: 'user', label: 'Perfil', path: '/panel/persona-mayor/perfil' }
    ]
  },

  ORGANIZACION: {
    roleLabel: 'Organización',
    roleAccent: 'var(--vita-navy)',
    navItems: [
      { icon: 'home', label: 'Inicio', path: '/panel/organizacion' },
      { icon: 'users', label: 'Personas mayores', path: '/panel/organizacion/personas-mayores' },
      { icon: 'activity', label: 'Signos vitales', path: '/panel/organizacion/signos-vitales' },
      { icon: 'calendar', label: 'Actividades', path: '/panel/organizacion/actividades' },
      { icon: 'star', label: 'Voluntarios', path: '/panel/organizacion/voluntarios' },
      { icon: 'bar-chart', label: 'Analítica', path: '/panel/organizacion/analitica' },
      // Acompañantes, alertas, medicamentos, donaciones y mapa irán aquí, en
      // ese orden, cuando existan. Mientras tanto no aparecen en el menú y sus
      // rutas muestran EnConstruccion.
      { icon: 'user', label: 'Perfil', path: '/panel/organizacion/perfil' }
    ]
  },

  ACOMPANANTE: {
    roleLabel: 'Acompañante',
    roleAccent: 'var(--vita-complemento)',
    navItems: [
      { icon: 'home', label: 'Inicio', path: '/panel/acompanante' },
      { icon: 'users', label: 'Personas mayores', path: '/panel/acompanante/personas-mayores' },
      { icon: 'clipboard', label: 'Seguimiento', path: '/panel/acompanante/seguimiento' },
      { icon: 'stethoscope', label: 'Gestionar cuidado', path: '/panel/acompanante/gestion' },
      { icon: 'calendar', label: 'Actividades', path: '/panel/acompanante/actividades' },
      { icon: 'user', label: 'Perfil', path: '/panel/acompanante/perfil' }
    ]
  },

  VOLUNTARIO: {
    roleLabel: 'Voluntario',
    roleAccent: 'var(--vita-gold-dark)',
    navItems: [
      { icon: 'home', label: 'Inicio', path: '/panel/voluntario' },
      { icon: 'building', label: 'Organizaciones', path: '/panel/voluntario/organizaciones' },
      { icon: 'calendar', label: 'Actividades', path: '/panel/voluntario/actividades' },
      { icon: 'heart', label: 'Intereses', path: '/panel/voluntario/intereses' },
      { icon: 'user', label: 'Perfil', path: '/panel/voluntario/perfil' }
    ]
  }
};
