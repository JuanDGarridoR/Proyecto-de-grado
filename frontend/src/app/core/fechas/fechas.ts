const DIA = 86_400_000;

/** Fecha YYYY-MM-DD en hora local (toISOString() usaría UTC). */
export function fechaLocal(fecha: Date): string {
  return fecha.toLocaleDateString('en-CA');
}

/** Medianoche (hora local) del día de esa fecha, en milisegundos. */
function inicioDelDia(fecha: Date): number {
  return new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate()).getTime();
}

/** Días de calendario entre esa fecha y hoy (0 = hoy, 1 = ayer...). */
export function diasDesde(fechaHora: string, ahora: Date): number {
  return Math.round((inicioDelDia(ahora) - inicioDelDia(new Date(fechaHora))) / DIA);
}

/** "hoy", "ayer" o "hace N días". */
export function haceDias(fechaHora: string, ahora: Date): string {
  const dias = diasDesde(fechaHora, ahora);
  if (dias <= 0) return 'hoy';
  if (dias === 1) return 'ayer';
  return `hace ${dias} días`;
}

/** Próximo cumpleaños de una fecha de nacimiento (YYYY-MM-DD). */
export interface ProximoCumpleanos {
  /** Días que faltan (0 = hoy). */
  dias: number;
  /** Edad que cumple. */
  edad: number;
  /** Fecha del próximo cumpleaños, YYYY-MM-DD. */
  fecha: string;
}

export function proximoCumpleanos(fechaNacimiento: string, ahora: Date): ProximoCumpleanos {
  const [anioNacimiento, mes, dia] = fechaNacimiento.split('-').map(Number);
  const hoy = new Date(inicioDelDia(ahora));

  let anio = hoy.getFullYear();
  let proximo = new Date(anio, mes - 1, dia);
  if (proximo < hoy) {
    anio++;
    proximo = new Date(anio, mes - 1, dia);
  }

  return {
    dias: Math.round((proximo.getTime() - hoy.getTime()) / DIA),
    edad: anio - anioNacimiento,
    fecha: fechaLocal(proximo)
  };
}
