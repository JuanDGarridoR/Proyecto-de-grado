import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  AcompananteService,
  PerfilPersonaMayor
} from '../../../../../core/acompanantes/acompanante.service';
import { Icono } from '../../../../../shared/icono/icono';
import { mensajeDeError } from '../../../../../core/formato/formato';

/** Campos que edita el acompañante; el celular y el correo no se tocan. */
interface FormularioPerfil {
  nombre: string;
  fechaNacimiento: string;
  genero: string;
  direccion: string;
  eps: string;
  ips: string;
  direccionIps: string;
  viveSolo: boolean | null;
}

/**
 * Pestaña "Perfil" de "Gestionar cuidado": datos personales de la persona
 * mayor que el acompañante puede consultar y completar (nombre, fecha de
 * nacimiento, género, dirección, EPS, IPS y si vive sola). El celular y el correo solo
 * los ve, porque con ellos la persona inicia sesión.
 */
@Component({
  selector: 'app-perfil-persona',
  imports: [FormsModule, Icono],
  templateUrl: './perfil-persona.html',
  styleUrl: './perfil-persona.css'
})
export class PerfilPersona {

  private acompananteService = inject(AcompananteService);

  readonly idPersonaMayor = input.required<number>();

  protected readonly perfil = signal<PerfilPersonaMayor | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal('');

  protected readonly editando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGuardado = signal('');
  protected readonly guardadoExitoso = signal(false);

  formulario: FormularioPerfil = this.formularioVacio();

  constructor() {
    // Carga al iniciar y cada vez que el acompañante cambia de persona.
    effect(() => {
      const id = this.idPersonaMayor();
      untracked(() => this.cargar(id));
    });
  }

  private cargar(idPersonaMayor: number): void {
    this.cargando.set(true);
    this.errorCarga.set('');
    this.editando.set(false);
    this.guardadoExitoso.set(false);

    this.acompananteService.obtenerPerfilPersonaMayor(idPersonaMayor).subscribe({
      next: (perfil) => {
        if (idPersonaMayor !== this.idPersonaMayor()) {
          return;
        }
        this.perfil.set(perfil);
        this.cargando.set(false);
      },
      error: (error) => {
        if (idPersonaMayor !== this.idPersonaMayor()) {
          return;
        }
        console.error('Error al cargar el perfil de la persona mayor:', error);
        this.cargando.set(false);
        this.errorCarga.set(error?.status === 403
          ? 'No tienes autorización para ver este perfil.'
          : 'No se pudo cargar el perfil.');
      }
    });
  }

  protected reintentar(): void {
    this.cargar(this.idPersonaMayor());
  }

  protected abrirEdicion(): void {
    const perfil = this.perfil();
    if (!perfil) {
      return;
    }

    this.formulario = {
      nombre: perfil.nombre ?? '',
      fechaNacimiento: perfil.fechaNacimiento ?? '',
      genero: perfil.genero ?? '',
      direccion: perfil.direccion ?? '',
      eps: perfil.eps ?? '',
      ips: perfil.ips ?? '',
      direccionIps: perfil.direccionIps ?? '',
      viveSolo: perfil.viveSolo ?? null
    };
    this.errorGuardado.set('');
    this.guardadoExitoso.set(false);
    this.editando.set(true);
  }

  protected cancelarEdicion(): void {
    if (this.guardando()) {
      return;
    }
    this.editando.set(false);
    this.errorGuardado.set('');
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    if (!this.formulario.nombre.trim()) {
      this.errorGuardado.set('El nombre es obligatorio.');
      return;
    }

    const idPersonaMayor = this.idPersonaMayor();
    const f = this.formulario;

    this.guardando.set(true);
    this.errorGuardado.set('');

    this.acompananteService.actualizarPerfilPersonaMayor(idPersonaMayor, {
      nombre: f.nombre.trim(),
      fechaNacimiento: f.fechaNacimiento || null,
      genero: f.genero || null,
      direccion: f.direccion.trim() || null,
      eps: f.eps.trim() || null,
      ips: f.ips.trim() || null,
      direccionIps: f.direccionIps.trim() || null,
      viveSolo: f.viveSolo
    }).subscribe({
      next: (perfil) => {
        this.guardando.set(false);
        if (idPersonaMayor !== this.idPersonaMayor()) {
          return;
        }
        this.perfil.set(perfil);
        this.editando.set(false);
        this.guardadoExitoso.set(true);
      },
      error: (error) => {
        console.error('Error al guardar el perfil de la persona mayor:', error);
        this.guardando.set(false);
        this.errorGuardado.set(
          mensajeDeError(error, 'No se pudo guardar. Intenta de nuevo.')
        );
      }
    });
  }

  /** "1948-07-02" -> "2 de julio de 1948 (78 años)". */
  protected fechaConEdad(fecha: string | null): string {
    if (!fecha) {
      return '';
    }

    const [anio, mes, dia] = fecha.split('-').map(Number);
    const nacimiento = new Date(anio, mes - 1, dia);
    const hoy = new Date();

    let edad = hoy.getFullYear() - anio;
    if (hoy.getMonth() < mes - 1 || (hoy.getMonth() === mes - 1 && hoy.getDate() < dia)) {
      edad--;
    }

    const texto = nacimiento.toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' });
    return `${texto} (${edad} años)`;
  }

  private formularioVacio(): FormularioPerfil {
    return {
      nombre: '', fechaNacimiento: '', genero: '', direccion: '', eps: '', ips: '', direccionIps: '', viveSolo: null
    };
  }

  /** true -> "Sí", false -> "No, vive con otras personas", null -> "". */
  protected textoViveSolo(viveSolo: boolean | null): string {
    return viveSolo === true ? 'Sí' : viveSolo === false ? 'No, vive con otras personas' : '';
  }
}
