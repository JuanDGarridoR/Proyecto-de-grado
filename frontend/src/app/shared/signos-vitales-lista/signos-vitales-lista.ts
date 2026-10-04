import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';

import { SignoVitalResponse } from '../../core/signos-vitales/signos-vitales.services';
import { imcPorRegistro } from '../../core/signos-vitales/imc';
import { Icon } from '../icon/icon';

/**
 * Lista de registros de signos vitales, con sus estados de cargando, error y
 * vacío. La usan el modal de la organización y del acompañante, y el
 * historial de la persona mayor; cada página carga los registros con el
 * endpoint de su rol.
 */
@Component({
  selector: 'app-signos-vitales-lista',
  imports: [Icon, DatePipe],
  templateUrl: './signos-vitales-lista.html',
  styleUrl: './signos-vitales-lista.css'
})
export class SignosVitalesLista {

  readonly registros = input<SignoVitalResponse[]>([]);
  readonly cargando = input(false);
  readonly error = input<string | null>(null);

  /** En el modal la lista tiene altura máxima con scroll; en una página, no. */
  readonly compacta = input(true);

  readonly tituloVacio = input('No tiene signos vitales registrados');
  readonly textoVacio = input(
    'Cuando se registren signos vitales de esta persona mayor, aparecerán aquí.'
  );

  /** IMC de cada registro, en el mismo orden que registros(). */
  protected readonly imcs = computed(() => imcPorRegistro(this.registros()));
}
