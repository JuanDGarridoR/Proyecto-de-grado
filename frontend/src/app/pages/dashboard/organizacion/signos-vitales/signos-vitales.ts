import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { timeout } from 'rxjs';

import {
  SignosVitalesService,
  SignoVitalRequest,
  PersonaMayor,
} from '../../../../core/signos-vitales/signos-vitales.services';
import { alCambiar } from '../../../../core/tiempo-real/tiempo-real.service';
import { validarMedicion } from '../../../../core/signos-vitales/rangos';
import { Imc, calcularImc } from '../../../../core/signos-vitales/imc';
import { Icon } from '../../../../shared/icon/icon';

/**
 * Registro de signos vitales por parte de la organización. Antes de
 * guardar se validan los valores (ver validarMedicion) y se pide
 * confirmación; el backend vuelve a validar.
 */
@Component({
  selector: 'app-signos-vitales',
  standalone: true,
  imports: [FormsModule, Icon],
  templateUrl: './signos-vitales.html',
  styleUrl: './signos-vitales.css',
})
export class SignosVitales implements OnInit {
  private signosVitalesService = inject(SignosVitalesService);
  private cdr = inject(ChangeDetectorRef);

  personasMayores: PersonaMayor[] = [];

  idPersonaMayor: number | null = null;

  presionSistolica: number | null = null;
  presionDiastolica: number | null = null;
  frecuenciaCardiaca: number | null = null;
  temperatura: number | null = null;
  saturacionOxigeno: number | null = null;
  frecuenciaRespiratoria: number | null = null;
  peso: number | null = null;
  estatura: number | null = null;

  observaciones = '';

  cargandoPersonas = false;
  guardando = false;

  mensajeExito = '';
  mensajeError = '';

  mostrarConfirmacion = false;
  personaSeleccionada: PersonaMayor | null = null;

  constructor() {
    // Personas vinculadas o desvinculadas, o que cambiaron de nombre.
    alCambiar(['organizaciones', 'usuarios'], () => this.cargarPersonasMayores(false));
  }

  ngOnInit(): void {
    this.cargarPersonasMayores();
  }

  cargarPersonasMayores(mostrarCargando = true): void {
    if (mostrarCargando) {
      this.cargandoPersonas = true;
    }
    this.mensajeError = '';

    this.signosVitalesService.listarPersonasMayores().subscribe({
      next: (personas) => {
        this.personasMayores = personas;
        this.cargandoPersonas = false;
        this.cdr.markForCheck();
      },

      error: (error) => {
        console.error('Error cargando personas mayores:', error);

        this.mensajeError = 'No se pudieron cargar las personas mayores.';

        this.cargandoPersonas = false;
        this.cdr.markForCheck();
      },
    });
  }

/** Valida el formulario y abre la confirmación; todavía no guarda nada. */
registrarSignosVitales(): void {
  this.mensajeExito = '';
  this.mensajeError = '';

  if (this.idPersonaMayor === null) {
    this.mensajeError = 'Debes seleccionar una persona mayor.';
    return;
  }

  const persona = this.personasMayores.find(
    p => p.idUsuario === this.idPersonaMayor
  );

  if (!persona) {
    this.mensajeError =
      'No se pudo identificar la persona mayor seleccionada.';
    return;
  }

  // Valores posibles y presión completa (sistólica y diastólica).
  const errorMedicion = validarMedicion({
    presionSistolica: this.presionSistolica,
    presionDiastolica: this.presionDiastolica,
    frecuenciaCardiaca: this.frecuenciaCardiaca,
    temperatura: this.temperatura,
    saturacionOxigeno: this.saturacionOxigeno,
    frecuenciaRespiratoria: this.frecuenciaRespiratoria,
    peso: this.peso,
    estatura: this.estatura
  });

  if (errorMedicion) {
    this.mensajeError = errorMedicion;
    return;
  }

  this.personaSeleccionada = persona;

  this.guardando = false;
  this.mostrarConfirmacion = true;
}

/** Guarda la medición. Si el servidor tarda más de 20 segundos, se avisa sin reintentar. */
confirmarRegistro(): void {
  if (this.guardando) return;

  if (this.idPersonaMayor === null) {
    this.mensajeError =
      'No se pudo identificar la persona mayor.';
    this.mostrarConfirmacion = false;
    return;
  }

  this.guardando = true;

  const datos: SignoVitalRequest = {
    presionSistolica: this.presionSistolica,
    presionDiastolica: this.presionDiastolica,
    frecuenciaCardiaca: this.frecuenciaCardiaca,
    temperatura: this.temperatura,
    saturacionOxigeno: this.saturacionOxigeno,
    frecuenciaRespiratoria: this.frecuenciaRespiratoria,
    peso: this.peso,
    estatura: this.estatura,
    observaciones: this.observaciones
  };

  this.signosVitalesService
    .registrar(
      this.idPersonaMayor,
      datos
    )
    .pipe(timeout(20000))
    .subscribe({
      next: () => {
        this.guardando = false;
        this.mostrarConfirmacion = false;

        this.mensajeExito =
          'Los signos vitales se registraron correctamente.';

        this.limpiarFormulario();
        this.cdr.markForCheck();
      },

      error: (error) => {
        console.error(
          'Error registrando signos vitales:',
          error
        );

        console.error(
          'Respuesta del servidor:',
          error.error
        );

        this.guardando = false;
        this.mostrarConfirmacion = false;

        if (error.status === 400 && typeof error.error === 'string') {
          // Valores imposibles o presión incompleta (validación del servidor).
          this.mensajeError = error.error;
        } else if (error.status === 403) {
          this.mensajeError =
            'La persona mayor no está asociada a esta organización.';
        } else if (error.status === 404) {
          this.mensajeError =
            'No se encontró la organización asociada al usuario.';
        } else if (error.status === 0 || error.status >= 500 || error.name === 'TimeoutError') {
          // Puede que sí se haya guardado: mejor revisar antes de repetir.
          this.mensajeError =
            'El servidor no confirmó el registro. Revisa el historial antes de volver a intentarlo para no duplicarlo.';
        } else {
          this.mensajeError =
            'No se pudieron registrar los signos vitales.';
        }

        this.cdr.markForCheck();
      }
    });
}

cancelarConfirmacion(): void {
  this.mostrarConfirmacion = false;
  this.guardando = false;
}

  /** IMC con el peso y la estatura ingresados, para el resumen. */
  protected imc(): Imc | null {
    return calcularImc(this.peso, this.estatura);
  }

  limpiarFormulario(): void {
    this.idPersonaMayor = null;

    this.presionSistolica = null;
    this.presionDiastolica = null;
    this.frecuenciaCardiaca = null;
    this.temperatura = null;
    this.saturacionOxigeno = null;
    this.frecuenciaRespiratoria = null;
    this.peso = null;
    this.estatura = null;

    this.observaciones = '';
  }
}