import { Injectable, signal } from '@angular/core';

import { describir } from './descripcion';

const CLAVE_ACTIVO = 'vita.lector.activo';

/** Clase del elemento que se está leyendo (estilo en styles.css). */
const CLASE_RESALTADO = 'lector-resaltado';

/**
 * Voces en español que suenan más naturales, en orden de preferencia. Las
 * "Online (Natural)" son las neuronales de Edge; las de Google, las de Chrome.
 */
const VOCES_PREFERIDAS = [/natural/i, /online/i, /google/i, /sabina|salome|paloma|dalia|elvira|helena|laura/i];
const IDIOMAS_PREFERIDOS = ['es-co', 'es-us', 'es-mx', 'es-419', 'es-es'];

/**
 * Lector de voz de toda la plataforma: cuando está activo, cada vez que la
 * persona toca algo le dice en voz alta para qué sirve (ver descripcion.ts).
 * El toque sigue funcionando normal; el lector solo explica.
 */
@Injectable({ providedIn: 'root' })
export class LectorService {

  /** El navegador sabe hablar (casi todos; si no, el botón no aparece). */
  readonly disponible = typeof window !== 'undefined' && 'speechSynthesis' in window;

  readonly activo = signal(false);
  readonly hablando = signal(false);

  private voz: SpeechSynthesisVoice | null = null;
  private resaltado: Element | null = null;

  constructor() {
    if (!this.disponible) {
      return;
    }

    this.elegirVoz();
    // En Chrome las voces llegan después de cargar la página.
    speechSynthesis.addEventListener('voiceschanged', () => this.elegirVoz());

    // En captura: se escucha antes que los botones, para leer aunque el
    // botón detenga el evento o abra un modal.
    document.addEventListener('click', (evento) => this.alTocar(evento), true);

    if (leerPreferencia()) {
      this.activo.set(true);
      document.body.classList.add('lector-activo');
    }
  }

  alternar(): void {
    this.activo() ? this.desactivar() : this.activar();
  }

  activar(): void {
    this.activo.set(true);
    document.body.classList.add('lector-activo');
    guardarPreferencia(true);
    this.decir('Lector de voz activado. Toca cualquier botón, campo o texto y te diré para qué sirve.');
  }

  desactivar(): void {
    this.activo.set(false);
    document.body.classList.remove('lector-activo');
    guardarPreferencia(false);
    this.decir('Lector de voz desactivado.');
  }

  /** Dice el texto en voz alta, cortando lo que estuviera diciendo antes. */
  decir(texto: string, elemento?: Element): void {
    if (!this.disponible || !texto) {
      return;
    }

    speechSynthesis.cancel();
    this.quitarResaltado();

    const frase = new SpeechSynthesisUtterance(texto);
    frase.lang = this.voz?.lang ?? 'es-CO';
    frase.voice = this.voz;
    // Un poco más lento y cálido que lo normal: más fácil de entender.
    frase.rate = 0.9;
    frase.pitch = 1.05;

    frase.onstart = () => this.hablando.set(true);
    frase.onend = frase.onerror = () => {
      // Si ya empezó otra frase, esta no le quita el resaltado a la nueva.
      if (this.resaltado === elemento) {
        this.quitarResaltado();
      }
      this.hablando.set(speechSynthesis.speaking);
    };

    if (elemento) {
      elemento.classList.add(CLASE_RESALTADO);
      this.resaltado = elemento;
    }
    speechSynthesis.speak(frase);
  }

  private alTocar(evento: MouseEvent): void {
    const objetivo = evento.target;
    if (!this.activo() || !(objetivo instanceof Element)) {
      return;
    }
    // El propio botón del lector anuncia por su cuenta.
    if (objetivo.closest('[data-lector-ignorar]')) {
      return;
    }

    const texto = describir(objetivo);
    if (texto) {
      this.decir(texto, objetivo.closest('[data-lector], button, a, input, textarea, select, label') ?? objetivo);
    }
  }

  private quitarResaltado(): void {
    this.resaltado?.classList.remove(CLASE_RESALTADO);
    this.resaltado = null;
  }

  private elegirVoz(): void {
    const espanol = speechSynthesis.getVoices().filter((v) => v.lang.toLowerCase().startsWith('es'));
    if (!espanol.length) {
      return;
    }

    const puntaje = (v: SpeechSynthesisVoice) => {
      const nombre = VOCES_PREFERIDAS.findIndex((p) => p.test(v.name));
      const idioma = IDIOMAS_PREFERIDOS.indexOf(v.lang.toLowerCase());
      // Primero la calidad de la voz, luego el acento más cercano.
      return (nombre < 0 ? 100 : nombre * 10) + (idioma < 0 ? 9 : idioma);
    };

    this.voz = [...espanol].sort((a, b) => puntaje(a) - puntaje(b))[0];
  }
}

function leerPreferencia(): boolean {
  try {
    return localStorage.getItem(CLAVE_ACTIVO) === '1';
  } catch {
    return false;
  }
}

function guardarPreferencia(activo: boolean): void {
  try {
    localStorage.setItem(CLAVE_ACTIVO, activo ? '1' : '0');
  } catch {
    // Sin almacenamiento solo se pierde el recordar la preferencia.
  }
}
