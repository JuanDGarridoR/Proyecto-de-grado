import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  GustoService,
  Gusto,
  CategoriaGusto
} from '../../core/gustos/gusto.service';
import { VoluntarioService } from '../../core/voluntario/voluntario.service';
import { alCambiar } from '../../core/tiempo-real/tiempo-real.service';
import { Icono } from '../icono/icono';

/** Pestaña de categoría. */
interface CategoriaTab {
  valor: CategoriaGusto;
  label: string;
  icon: string;
}

/**
 * Icono de cada gusto, por su nombre exacto en la base de datos. Un gusto
 * que no esté aquí usa el icono por defecto de su categoría
 * (ver ICONO_POR_DEFECTO).
 */
const NOMBRE_A_ICONO: Record<string, string> = {
  // GUSTO
  'Leer': 'book',
  'Escuchar música': 'music',
  'Ver televisión o películas': 'film',
  'Cocinar': 'chef-hat',
  'Pasear al aire libre': 'sun',
  'Fotografía': 'camera',
  'Viajar': 'plane',
  'Compartir con mascotas': 'paw',
  'Ver deportes': 'activity',
  'Conversar con amigos': 'message-circle',

  // TALENTO
  'Tocar un instrumento': 'guitar',
  'Cantar': 'mic',
  'Pintar o dibujar': 'palette',
  'Escribir': 'pen',
  'Manualidades y artesanías': 'scissors',
  'Costura o tejido': 'yarn',
  'Baile': 'dance',
  'Carpintería': 'hammer',
  'Repostería': 'cake',
  'Actuación o teatro': 'drama',

  // HOBBY
  'Ejercicio físico': 'dumbbell',
  'Caminar': 'footprints',
  'Yoga o estiramiento': 'stretch',
  'Jardinería': 'sprout',
  'Pesca': 'fish',
  'Juegos de mesa': 'dice',
  'Rompecabezas': 'puzzle',
  'Ciclismo': 'bike',
  'Natación': 'swim',
  'Voluntariado': 'users',

  // PROFESION (ver CatalogoProfesiones en persona-mayor-service)
  'Labores del hogar': 'home',
  'Agricultura o trabajo del campo': 'sprout',
  'Construcción': 'hammer',
  'Comercio o ventas': 'tag',
  'Conducción o transporte': 'map',
  'Modistería o sastrería': 'scissors',
  'Docencia': 'book',
  'Salud (enfermería o medicina)': 'stethoscope',
  'Cocina': 'chef-hat',
  'Servicios generales o aseo': 'sparkles',
  'Oficina o administración': 'clipboard',
  'Vigilancia o seguridad': 'eye',
  'Mecánica': 'wrench',
  'Carpintería o ebanistería': 'hammer',
  'Peluquería o belleza': 'scissors',
  'Artesanía': 'palette',
  'Fuerza pública (militar o policía)': 'star',
  'Ingeniería o trabajo técnico': 'settings',
  'Trabajo social o psicología': 'users',
  'Estudiante': 'book'
};

/** Largo máximo del nombre de un interés nuevo (igual que en GustoController). */
const LARGO_MAXIMO_INTERES = 60;

/**
 * Nombre para comparar si un interés ya existe: sin tildes, en minúscula y
 * sin espacios de más ("  Música  " y "musica" son el mismo).
 */
function claveNombre(nombre: string): string {
  return nombre
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLocaleLowerCase('es')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Icono de los gustos que no están en NOMBRE_A_ICONO. */
const ICONO_POR_DEFECTO: Record<CategoriaGusto, string> = {
  GUSTO: 'heart',
  TALENTO: 'sparkles',
  HOBBY: 'target',
  PROFESION: 'wrench'
};

/**
 * Intereses de la persona mayor o del voluntario (data.rol de la ruta):
 * marca sus gustos, talentos, pasatiempos y su profesión u oficio del mismo
 * catálogo, en cuatro pestañas, y los guarda todos de una vez. Con ellos se
 * le recomiendan organizaciones (la profesión pesa menos que los gustos).
 * Si el suyo no está en la lista, lo agrega al catálogo en la categoría
 * abierta y queda marcado.
 */
@Component({
  selector: 'app-intereses',
  standalone: true,
  imports: [FormsModule, Icono],
  templateUrl: './intereses.html',
  styleUrl: './intereses.css'
})
export class Intereses implements OnInit {

  protected readonly categorias: CategoriaTab[] = [
    { valor: 'GUSTO', label: 'Gustos', icon: 'heart' },
    { valor: 'TALENTO', label: 'Talentos', icon: 'sparkles' },
    { valor: 'HOBBY', label: 'Pasatiempos', icon: 'target' },
    { valor: 'PROFESION', label: 'Profesión u oficio', icon: 'wrench' }
  ];

  protected readonly categoriaActiva = signal<CategoriaGusto>('GUSTO');
  protected readonly gustosDisponibles = signal<Gusto[]>([]);
  /** Ids marcados en pantalla; se guardan al pulsar "Guardar". */
  protected readonly gustosSeleccionados = signal<Set<number>>(new Set());
  protected readonly guardandoGustos = signal(false);
  protected readonly cargando = signal(true);
  protected readonly errorGustos = signal<string | null>(null);

  protected readonly largoMaximoInteres = LARGO_MAXIMO_INTERES;
  /** Texto del campo para agregar un interés que no está en la lista. */
  protected readonly nuevoInteres = signal('');
  protected readonly agregandoInteres = signal(false);
  /** Resultado de agregar un interés, debajo del campo. */
  protected readonly mensajeNuevoInteres = signal<{ texto: string; error: boolean } | null>(null);

  protected readonly gustosDeCategoriaActiva = computed(() =>
    this.gustosDisponibles().filter(
      gusto => gusto.categoria === this.categoriaActiva()
    )
  );

  private voluntarioService = inject(VoluntarioService);

  /** El voluntario guarda sus gustos en voluntario-service; la persona mayor, en persona-mayor-service. */
  private readonly esVoluntario = inject(ActivatedRoute).snapshot.data['rol'] === 'VOLUNTARIO';

  constructor(
    private authService: AuthService,
    private gustoService: GustoService
  ) {
    // Solo se refresca el catálogo: la selección puede tener cambios
    // sin guardar que no se deben perder.
    alCambiar(['gustos'], () =>
      this.gustoService.listar().subscribe((gustos) => this.gustosDisponibles.set(gustos))
    );
  }

  ngOnInit(): void {
    const idUsuario = this.authService.getIdUsuario();

    if (idUsuario === null) {
      this.errorGustos.set('No se pudo identificar al usuario.');
      this.cargando.set(false);
      return;
    }

    this.cargarIntereses(idUsuario);
  }

  /** Gustos que ya tiene marcados el usuario. */
  private gustosAsignados(idUsuario: number): Observable<Gusto[]> {
    return this.esVoluntario
      ? this.voluntarioService.listarGustos()
      : this.gustoService.listarAsignados(idUsuario);
  }

  /** Reemplaza los gustos del usuario por los de la lista. */
  private guardarAsignados(idUsuario: number, idsGustos: number[]): Observable<Gusto[]> {
    return this.esVoluntario
      ? this.voluntarioService.guardarGustos(idsGustos)
      : this.gustoService.asignar(idUsuario, idsGustos);
  }

  /** Carga el catálogo y después los gustos que ya tenía marcados. */
  private cargarIntereses(idUsuario: number): void {
    this.cargando.set(true);
    this.errorGustos.set(null);

    this.gustoService.listar().subscribe({
      next: (gustos) => {
        this.gustosDisponibles.set(gustos);

        this.gustosAsignados(idUsuario).subscribe({
          next: (gustosAsignados) => {
            this.gustosSeleccionados.set(
              new Set(gustosAsignados.map(gusto => gusto.idGusto))
            );
            this.cargando.set(false);
          },
          error: () => {
            this.errorGustos.set('No se pudieron cargar tus intereses guardados.');
            this.cargando.set(false);
          }
        });
      },
      error: () => {
        this.errorGustos.set('No se pudieron cargar los intereses disponibles.');
        this.cargando.set(false);
      }
    });
  }

  cambiarCategoria(categoria: CategoriaGusto): void {
    this.categoriaActiva.set(categoria);
    this.mensajeNuevoInteres.set(null);
  }

  contarSeleccionados(categoria: CategoriaGusto): number {
    const seleccionados = this.gustosSeleccionados();

    return this.gustosDisponibles()
      .filter(gusto => gusto.categoria === categoria && seleccionados.has(gusto.idGusto))
      .length;
  }

  estaSeleccionado(idGusto: number): boolean {
    return this.gustosSeleccionados().has(idGusto);
  }

  alternarGusto(idGusto: number): void {
    const seleccionados = new Set(this.gustosSeleccionados());

    if (seleccionados.has(idGusto)) {
      seleccionados.delete(idGusto);
    } else {
      seleccionados.add(idGusto);
    }

    this.gustosSeleccionados.set(seleccionados);
  }

  /**
   * Agrega al catálogo el interés escrito, en la categoría abierta, y lo
   * marca. Si ya existe (aunque cambien tildes o mayúsculas), solo lo marca.
   * Como el resto de la selección, queda guardado al pulsar "Guardar".
   */
  agregarInteres(): void {
    const nombre = this.nuevoInteres().replace(/\s+/g, ' ').trim();
    if (!nombre || this.agregandoInteres()) {
      return;
    }

    this.mensajeNuevoInteres.set(null);

    if (this.marcarExistente(nombre)) {
      return;
    }

    this.agregandoInteres.set(true);

    this.gustoService.crear({ nombre, categoria: this.categoriaActiva() }).subscribe({
      next: (gusto) => {
        this.agregandoInteres.set(false);
        // El aviso en tiempo real pudo haberlo traído ya al catálogo.
        if (!this.gustosDisponibles().some(g => g.idGusto === gusto.idGusto)) {
          this.gustosDisponibles.update(gustos => [...gustos, gusto]);
        }
        this.marcar(gusto.idGusto);
        this.nuevoInteres.set('');
        this.mensajeNuevoInteres.set({
          texto: `Agregamos «${gusto.nombre}» y quedó marcado. Recuerda guardar tus intereses.`,
          error: false
        });
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 409) {
          // Alguien más lo agregó hace un momento: se trae el catálogo y se marca.
          this.gustoService.listar().subscribe({
            next: (gustos) => {
              this.agregandoInteres.set(false);
              this.gustosDisponibles.set(gustos);
              if (!this.marcarExistente(nombre)) {
                this.mensajeNuevoInteres.set({ texto: 'No se pudo agregar el interés.', error: true });
              }
            },
            error: () => {
              this.agregandoInteres.set(false);
              this.mensajeNuevoInteres.set({ texto: 'No se pudo agregar el interés.', error: true });
            }
          });
          return;
        }

        this.agregandoInteres.set(false);
        this.mensajeNuevoInteres.set({
          texto: error.status === 400 && typeof error.error === 'string'
            ? error.error
            : 'No se pudo agregar el interés.',
          error: true
        });
      }
    });
  }

  /**
   * Si el catálogo ya tiene un interés con ese nombre, lo marca, abre su
   * categoría y avisa. Devuelve si lo encontró.
   */
  private marcarExistente(nombre: string): boolean {
    const clave = claveNombre(nombre);
    const existente = this.gustosDisponibles().find(g => claveNombre(g.nombre) === clave);
    if (!existente) {
      return false;
    }

    this.marcar(existente.idGusto);
    this.nuevoInteres.set('');

    const categoria = this.categorias.find(c => c.valor === existente.categoria);
    const otraCategoria = categoria && existente.categoria !== this.categoriaActiva();
    if (otraCategoria) {
      this.categoriaActiva.set(existente.categoria);
    }

    this.mensajeNuevoInteres.set({
      texto: otraCategoria
        ? `«${existente.nombre}» ya estaba en ${categoria.label}; lo marcamos por ti.`
        : `«${existente.nombre}» ya estaba en la lista; lo marcamos por ti.`,
      error: false
    });
    return true;
  }

  private marcar(idGusto: number): void {
    const seleccionados = new Set(this.gustosSeleccionados());
    seleccionados.add(idGusto);
    this.gustosSeleccionados.set(seleccionados);
  }

  /** Reemplaza en el backend todos los gustos marcados por la selección actual. */
  guardarGustos(): void {
    const idUsuario = this.authService.getIdUsuario();
    if (idUsuario === null) {
      return;
    }

    this.guardandoGustos.set(true);
    this.errorGustos.set(null);

    const idsGustos = Array.from(this.gustosSeleccionados());

    this.guardarAsignados(idUsuario, idsGustos).subscribe({
      next: () => {
        this.guardandoGustos.set(false);
      },
      error: () => {
        this.guardandoGustos.set(false);
        this.errorGustos.set('No se pudieron guardar tus intereses.');
      }
    });
  }

  /**
   * Nombre del icono (para <app-icono [name]="...">) que corresponde al
   * gusto, según su nombre exacto en la base de datos. Si no está en la
   * lista, usa el icono por defecto de su categoría.
   */
  obtenerIcono(gusto: Gusto): string {
    return NOMBRE_A_ICONO[gusto.nombre] ?? ICONO_POR_DEFECTO[gusto.categoria];
  }
}