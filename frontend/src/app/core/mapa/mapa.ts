/** Un punto en el mapa, en grados (WGS84), igual que en el backend. */
export interface Coordenadas {
  latitud: number;
  longitud: number;
}

/**
 * Lugar que se pinta en el mapa. lineas son los datos que se ven al
 * tocarlo (fecha, lugar...); destacado lo pinta de otro color (p. ej. las
 * actividades en las que la persona ya está inscrita).
 */
export interface PuntoMapa extends Coordenadas {
  id: number | string;
  titulo: string;
  lineas: string[];
  destacado?: boolean;
}

/**
 * Centro de Usme (UPL Entrenubes), donde están la mayoría de usuarios. El
 * mapa arranca aquí cuando no hay puntos que mostrar.
 */
export const CENTRO_USME: Coordenadas = { latitud: 4.505, longitud: -74.112 };

const RADIO_TIERRA_KM = 6371;

/** Distancia en línea recta en km (fórmula del haversine). */
export function distanciaKm(a: Coordenadas, b: Coordenadas): number {
  const rad = (grados: number) => (grados * Math.PI) / 180;
  const dLat = rad(b.latitud - a.latitud);
  const dLon = rad(b.longitud - a.longitud);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.latitud)) * Math.cos(rad(b.latitud)) * Math.sin(dLon / 2) ** 2;
  return 2 * RADIO_TIERRA_KM * Math.asin(Math.sqrt(h));
}

/** "A unos 350 m de ti", "A unos 1,2 km de ti". */
export function textoDistancia(km: number): string {
  if (km < 1) {
    return `A unos ${Math.max(50, Math.round((km * 1000) / 50) * 50)} m de ti`;
  }
  return `A unos ${km.toLocaleString('es-CO', { maximumFractionDigits: 1 })} km de ti`;
}

/**
 * Enlace a Google Maps con la ruta hasta el punto. En el celular abre la
 * aplicación de mapas y arranca desde donde esté la persona, así que no
 * hace falta pedirle su ubicación.
 */
export function enlaceComoLlegar(punto: Coordenadas): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${punto.latitud},${punto.longitud}`;
}

/** Coordenadas de un objeto que puede no tenerlas (actividad sin punto de encuentro). */
export function coordenadasDe(
  objeto: { latitud?: number | null; longitud?: number | null }
): Coordenadas | null {
  return objeto.latitud != null && objeto.longitud != null
    ? { latitud: objeto.latitud, longitud: objeto.longitud }
    : null;
}
