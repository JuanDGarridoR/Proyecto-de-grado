import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  CondicionSalud,
  CondicionSaludRegistrada,
  CondicionSaludService,
  SeveridadCondicion,
  TipoCondicionSalud
} from '../../core/condiciones-salud/condicion-salud.service';
import { alCambiar } from '../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../icon/icon';

/** Cómo se presenta cada tipo de dato de salud. */
interface SeccionSalud {
  tipo: TipoCondicionSalud;
  titulo: string;
  singular: string;
  /** Nombre del campo en el formulario. */
  etiqueta: string;
  icono: string;
  vacio: string;
  /** "Severidad" en enfermedades y alergias; "Grado" en discapacidades. */
  etiquetaSeveridad: string;
}

const SECCIONES: SeccionSalud[] = [
  { tipo: 'ENFERMEDAD', titulo: 'Enfermedades', singular: 'enfermedad', etiqueta: 'Enfermedad', icono: 'stethoscope',
    vacio: 'No has registrado enfermedades.', etiquetaSeveridad: 'Severidad' },
  { tipo: 'ALERGIA', titulo: 'Alergias', singular: 'alergia', etiqueta: 'Alergia', icono: 'alert-circle',
    vacio: 'No has registrado alergias.', etiquetaSeveridad: 'Severidad' },
  { tipo: 'DISCAPACIDAD', titulo: 'Discapacidades', singular: 'discapacidad', etiqueta: 'Discapacidad', icono: 'user',
    vacio: 'No has registrado discapacidades.', etiquetaSeveridad: 'Grado' }
];

const SEVERIDADES: { valor: SeveridadCondicion; texto: string }[] = [
  { valor: 'LEVE', texto: 'Leve' },
  { valor: 'MODERADA', texto: 'Moderada' },
  { valor: 'SEVERA', texto: 'Severa' }
];

/** Opciones del catálogo de una misma categoría, para el <optgroup>. */
interface GrupoOpciones {
  categoria: string;
  opciones: CondicionSalud[];
}

/**
 * Sección "Datos de salud" del perfil de la persona mayor: sus
 * enfermedades, alergias y discapacidades. Todas son opcionales y se
 * eligen de un catálogo de salud-service, para que la analítica pueda
 * agruparlas; la opción "Otra" permite escribir lo que no esté en la lista.
 */
@Component({
  selector: 'app-datos-salud',
  imports: [FormsModule, Icon],
  templateUrl: './datos-salud.html',
  styleUrl: './datos-salud.css'
})
export class DatosSalud implements OnInit {

  private condicionSaludService = inject(CondicionSaludService);

  protected readonly secciones = SECCIONES;
  protected readonly severidades = SEVERIDADES;

  protected readonly catalogo = signal<CondicionSalud[]>([]);
  protected readonly registros = signal<CondicionSaludRegistrada[]>([]);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal(false);

  /** Registro que espera confirmación para quitarse. */
  protected readonly idConfirmandoQuitar = signal<number | null>(null);
  protected readonly quitando = signal(false);
  protected readonly errorLista = signal('');

  // Formulario (modal)
  protected readonly seccionFormulario = signal<SeccionSalud | null>(null);
  protected readonly editando = signal<CondicionSaludRegistrada | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal('');

  idCondicionSalud: number | null = null;
  severidad: SeveridadCondicion | null = null;
  detalle = '';

  constructor() {
    alCambiar(['condiciones-salud'], () => this.cargarRegistros());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(false);

    this.condicionSaludService.catalogo().subscribe({
      next: (catalogo) => {
        this.catalogo.set(catalogo);
        this.cargarRegistros();
      },
      error: (error) => this.manejarErrorCarga(error)
    });
  }

  private cargarRegistros(): void {
    this.condicionSaludService.listar().subscribe({
      next: (registros) => {
        this.registros.set(registros);
        this.cargando.set(false);
      },
      error: (error) => this.manejarErrorCarga(error)
    });
  }

  private manejarErrorCarga(error: unknown): void {
    console.error('Error al cargar los datos de salud:', error);
    this.errorCarga.set(true);
    this.cargando.set(false);
  }

  protected registrosDe(tipo: TipoCondicionSalud): CondicionSaludRegistrada[] {
    return this.registros().filter((r) => r.tipo === tipo);
  }

  protected textoSeveridad(severidad: SeveridadCondicion | null): string {
    return SEVERIDADES.find((s) => s.valor === severidad)?.texto ?? '';
  }

  /** Lo que se muestra como nombre: en "Otra" es lo que escribió la persona. */
  protected nombreDe(registro: CondicionSaludRegistrada): string {
    return registro.esOtra && registro.detalle ? registro.detalle : registro.nombre;
  }

  /**
   * Opciones del catálogo para el tipo del formulario, agrupadas por
   * categoría. No aparecen las que la persona ya registró, salvo "Otra".
   */
  protected readonly gruposOpciones = computed<GrupoOpciones[]>(() => {
    const seccion = this.seccionFormulario();
    if (!seccion) {
      return [];
    }

    const yaRegistradas = new Set(this.registros().map((r) => r.idCondicionSalud));
    const grupos = new Map<string, CondicionSalud[]>();

    for (const opcion of this.catalogo()) {
      if (opcion.tipo !== seccion.tipo || opcion.esOtra || yaRegistradas.has(opcion.idCondicionSalud)) {
        continue;
      }
      grupos.set(opcion.categoria, [...(grupos.get(opcion.categoria) ?? []), opcion]);
    }

    return [...grupos].map(([categoria, opciones]) => ({ categoria, opciones }));
  });

  /** La opción "Otra" del tipo del formulario; va al final de la lista. */
  protected readonly opcionOtra = computed(() =>
    this.catalogo().find((c) => c.tipo === this.seccionFormulario()?.tipo && c.esOtra) ?? null
  );

  /** Si lo elegido (o lo que se edita) es "Otra", el detalle pasa a ser "¿Cuál?". */
  protected get esOtraSeleccionada(): boolean {
    const editando = this.editando();
    if (editando) {
      return editando.esOtra;
    }
    return this.idCondicionSalud !== null && this.idCondicionSalud === this.opcionOtra()?.idCondicionSalud;
  }

  protected abrirNuevo(seccion: SeccionSalud): void {
    this.editando.set(null);
    this.idCondicionSalud = null;
    this.severidad = null;
    this.detalle = '';
    this.errorFormulario.set('');
    this.seccionFormulario.set(seccion);
  }

  protected abrirEdicion(seccion: SeccionSalud, registro: CondicionSaludRegistrada): void {
    this.editando.set(registro);
    this.idCondicionSalud = registro.idCondicionSalud;
    this.severidad = registro.severidad;
    this.detalle = registro.detalle ?? '';
    this.errorFormulario.set('');
    this.seccionFormulario.set(seccion);
  }

  protected cerrarFormulario(): void {
    if (this.guardando()) {
      return;
    }
    this.seccionFormulario.set(null);
    this.editando.set(null);
  }

  /** Un segundo clic sobre la severidad elegida la quita (es opcional). */
  protected elegirSeveridad(valor: SeveridadCondicion): void {
    this.severidad = this.severidad === valor ? null : valor;
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }

    const editando = this.editando();
    if (!editando && this.idCondicionSalud === null) {
      this.errorFormulario.set('Selecciona una opción de la lista.');
      return;
    }

    const detalle = this.detalle.trim();
    if (this.esOtraSeleccionada && !detalle) {
      this.errorFormulario.set('Escribe cuál es.');
      return;
    }

    const request = {
      idCondicionSalud: this.idCondicionSalud ?? undefined,
      severidad: this.severidad,
      detalle: detalle || null
    };

    this.guardando.set(true);
    this.errorFormulario.set('');

    const peticion = editando
      ? this.condicionSaludService.actualizar(editando.idPersonaMayorCondicionSalud, request)
      : this.condicionSaludService.crear(request);

    peticion.subscribe({
      next: (guardado) => {
        this.registros.update((registros) => editando
          ? registros.map((r) => r.idPersonaMayorCondicionSalud === guardado.idPersonaMayorCondicionSalud ? guardado : r)
          : [...registros, guardado]);
        this.guardando.set(false);
        this.cerrarFormulario();
      },
      error: (error) => {
        console.error('Error al guardar el dato de salud:', error);
        this.guardando.set(false);
        this.errorFormulario.set(
          typeof error.error === 'string' && error.error
            ? error.error
            : 'No se pudo guardar. Intenta de nuevo.'
        );
      }
    });
  }

  protected quitar(registro: CondicionSaludRegistrada): void {
    if (this.quitando()) {
      return;
    }

    this.quitando.set(true);
    this.errorLista.set('');

    this.condicionSaludService.eliminar(registro.idPersonaMayorCondicionSalud).subscribe({
      next: () => {
        this.registros.update((registros) =>
          registros.filter((r) => r.idPersonaMayorCondicionSalud !== registro.idPersonaMayorCondicionSalud));
        this.idConfirmandoQuitar.set(null);
        this.quitando.set(false);
      },
      error: (error) => {
        console.error('Error al quitar el dato de salud:', error);
        this.quitando.set(false);
        this.errorLista.set('No se pudo quitar. Intenta de nuevo.');
      }
    });
  }
}
