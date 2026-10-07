import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import {
  AcompananteService,
  PersonaMayorAcompanada
} from '../../../../core/acompanantes/acompanante.service';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { Icon } from '../../../../shared/icon/icon';
import { DatosSalud } from '../../../../shared/datos-salud/datos-salud';
import { Recordatorios } from '../../persona-mayor/recordatorios/recordatorios';
import { CitasMedicas } from '../../persona-mayor/citas-medicas/citas-medicas';
import { PerfilPersona } from './perfil-persona/perfil-persona';
import { iniciales } from '../../../../core/formato/formato';
import { Cargando } from '../../../../shared/cargando/cargando';

/** Pestañas de la persona seleccionada. */
type Pestana = 'perfil' | 'salud' | 'medicamentos' | 'citas';

const PESTANAS: { id: Pestana; texto: string; icono: string }[] = [
  { id: 'perfil', texto: 'Perfil', icono: 'user' },
  { id: 'salud', texto: 'Datos de salud', icono: 'heart' },
  { id: 'medicamentos', texto: 'Medicamentos', icono: 'pill' },
  { id: 'citas', texto: 'Citas médicas', icono: 'calendar' }
];

/** Colores de los avatares; los mismos de Seguimiento para reconocer a cada persona. */
const COLORES_AVATAR = ['#12355b', '#2ec4b6', '#8e7cc3', '#e0a526', '#d1665a', '#3a86c8'];

/**
 * "Gestionar cuidado": el acompañante elige a una de las personas mayores
 * que acompaña y registra por ella su perfil, sus datos de salud, sus
 * medicamentos y sus citas médicas. Las pestañas reutilizan las mismas
 * vistas que usa la persona mayor, en modo acompañante.
 *
 * Se puede abrir con ?persona=<id> para llegar con alguien ya elegido
 * (por ejemplo, desde "Personas mayores").
 */
@Component({
  selector: 'app-gestion-cuidado',
  imports: [Icon, PerfilPersona, DatosSalud, Recordatorios, CitasMedicas, Cargando],
  templateUrl: './gestion.html',
  styleUrl: './gestion.css'
})
export class GestionCuidado implements OnInit {

  private acompananteService = inject(AcompananteService);
  private route = inject(ActivatedRoute);

  protected readonly pestanas = PESTANAS;

  protected readonly personasMayores = signal<PersonaMayorAcompanada[]>([]);
  protected readonly personaSeleccionada = signal<PersonaMayorAcompanada | null>(null);
  protected readonly pestana = signal<Pestana>('perfil');

  protected readonly cargando = signal(true);
  protected readonly error = signal('');

  constructor() {
    // Si cambia un vínculo o el nombre de alguien, se actualiza la lista.
    alCambiar(['acompanamientos', 'usuarios'], () => this.cargarPersonas(false));
  }

  ngOnInit(): void {
    this.cargarPersonas();
  }

  /** Conserva la persona elegida si sigue en la lista; si no, toma la de la URL o la primera. */
  private cargarPersonas(mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargando.set(true);
    }
    this.error.set('');

    this.acompananteService.obtenerPersonasMayores().subscribe({
      next: (personas) => {
        this.personasMayores.set(personas);
        this.cargando.set(false);

        const idPedido = this.personaSeleccionada()?.idUsuario
          ?? Number(this.route.snapshot.queryParamMap.get('persona'));
        this.personaSeleccionada.set(personas.find((p) => p.idUsuario === idPedido) ?? personas[0] ?? null);
      },
      error: () => {
        this.cargando.set(false);
        this.error.set('No se pudieron cargar las personas mayores que acompañas.');
      }
    });
  }

  protected seleccionar(persona: PersonaMayorAcompanada): void {
    this.personaSeleccionada.set(persona);
  }

  protected readonly iniciales = iniciales;

  protected colorAvatar(persona: PersonaMayorAcompanada): string {
    return COLORES_AVATAR[persona.idUsuario % COLORES_AVATAR.length];
  }

  /** "Rosa Elvira Díaz" -> "Rosa", para los textos de las pestañas. */
  protected primerNombre(persona: PersonaMayorAcompanada): string {
    return persona.nombre.trim().split(/\s+/)[0] ?? persona.nombre;
  }
}
