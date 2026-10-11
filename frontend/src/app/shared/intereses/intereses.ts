import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
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
  'Voluntariado': 'users'
};

/** Icono de los gustos que no están en NOMBRE_A_ICONO. */
const ICONO_POR_DEFECTO: Record<CategoriaGusto, string> = {
  GUSTO: 'heart',
  TALENTO: 'sparkles',
  HOBBY: 'target'
};

/**
 * Intereses de la persona mayor o del voluntario (data.rol de la ruta):
 * marca sus gustos, talentos y pasatiempos del mismo catálogo, en tres
 * pestañas, y los guarda todos de una vez. Con ellos se le recomiendan
 * organizaciones.
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
    { valor: 'HOBBY', label: 'Pasatiempos', icon: 'target' }
  ];

  protected readonly categoriaActiva = signal<CategoriaGusto>('GUSTO');
  protected readonly gustosDisponibles = signal<Gusto[]>([]);
  /** Ids marcados en pantalla; se guardan al pulsar "Guardar". */
  protected readonly gustosSeleccionados = signal<Set<number>>(new Set());
  protected readonly guardandoGustos = signal(false);
  protected readonly cargando = signal(true);
  protected readonly errorGustos = signal<string | null>(null);

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