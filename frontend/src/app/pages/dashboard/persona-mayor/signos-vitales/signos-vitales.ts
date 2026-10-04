import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';

import {
  SignosVitalesService,
  SignoVitalResponse
} from '../../../../core/signos-vitales/signos-vitales.services';
import { SignosVitalesLista } from '../../../../shared/signos-vitales-lista/signos-vitales-lista';
import { RegistrarSignosModal } from './registrar-signos-modal/registrar-signos-modal';
import { imcPorRegistro } from '../../../../core/signos-vitales/imc';
import { Icon } from '../../../../shared/icon/icon';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';

/**
 * Historial de signos vitales de la persona mayor. Usa la misma lista que el
 * modal de "Últimos signos vitales" de la organización y del acompañante.
 * La persona mayor también puede registrar sus propios signos vitales.
 */
@Component({
  selector: 'app-persona-mayor-signos-vitales',
  imports: [SignosVitalesLista, RegistrarSignosModal, Icon, DatePipe],
  templateUrl: './signos-vitales.html',
  styleUrl: './signos-vitales.css'
})
export class SignosVitalesPersonaMayor implements OnInit {

  private signosVitalesService = inject(SignosVitalesService);

  protected readonly registros = signal<SignoVitalResponse[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /**
   * IMC del último registro. Si ese registro no trae la estatura se usa la
   * más reciente del historial.
   */
  protected readonly imcUltimo = computed(() => imcPorRegistro(this.registros())[0] ?? null);

  protected readonly mostrarRegistro = signal(false);
  protected readonly mensajeExito = signal('');
  private temporizadorExito?: ReturnType<typeof setTimeout>;

  constructor() {
    alCambiar(['signos-vitales'], () => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected abrirRegistro(): void {
    this.mensajeExito.set('');
    this.mostrarRegistro.set(true);
  }

  protected alRegistrar(): void {
    this.mostrarRegistro.set(false);
    this.cargar();

    this.mensajeExito.set('Tus signos vitales se registraron correctamente.');
    clearTimeout(this.temporizadorExito);
    this.temporizadorExito = setTimeout(() => this.mensajeExito.set(''), 5000);
  }

  private cargar(): void {
    this.signosVitalesService.listarPropios().subscribe({
      next: (registros) => {
        this.registros.set(registros);
        this.error.set(null);
        this.cargando.set(false);
      },
      error: (error) => {
        console.error('Error al cargar los signos vitales:', error);
        this.error.set('No se pudo cargar tu historial de signos vitales.');
        this.cargando.set(false);
      }
    });
  }
}
