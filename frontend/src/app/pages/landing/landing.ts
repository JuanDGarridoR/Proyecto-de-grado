import { Component } from '@angular/core';

import { BarraNavegacion } from '../../shared/barra-navegacion/barra-navegacion';
import { PiePagina } from '../../shared/pie-pagina/pie-pagina';
import { Portada } from './secciones/portada/portada';
import { Modulos } from './secciones/modulos/modulos';
import { Mision } from './secciones/mision/mision';
import { QuienesSomosComponent } from "./secciones/quienes-somos/quienes-somos";
import { Reto } from "./secciones/reto/reto";

/** Página de inicio pública: reúne las secciones de la landing en orden. */
@Component({
  selector: 'app-landing',
  imports: [BarraNavegacion, Portada, Modulos, Mision, PiePagina, QuienesSomosComponent, Reto],
  templateUrl: './landing.html',
  styleUrl: './landing.css'
})
export class Landing {}
