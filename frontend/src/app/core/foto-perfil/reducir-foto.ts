/** Lado mayor de la foto de perfil, en píxeles: de sobra para los avatares. */
const LADO_MAXIMO = 512;

/**
 * Reduce la foto a 512 px por su lado mayor y la convierte a JPEG antes de
 * subirla. Las fotos de celular pesan varios MB; así quedan en decenas de KB,
 * suben rápido con mala señal y nunca pasan el tope de 2 MB del backend.
 * createImageBitmap ya respeta la orientación de la cámara (EXIF).
 */
export async function reducirFoto(archivo: File): Promise<Blob> {
  const imagen = await createImageBitmap(archivo);

  const escala = Math.min(1, LADO_MAXIMO / Math.max(imagen.width, imagen.height));
  const ancho = Math.round(imagen.width * escala);
  const alto = Math.round(imagen.height * escala);

  const canvas = document.createElement('canvas');
  canvas.width = ancho;
  canvas.height = alto;

  const contexto = canvas.getContext('2d')!;
  // Fondo blanco: un PNG con transparencia quedaría negro en JPEG.
  contexto.fillStyle = '#ffffff';
  contexto.fillRect(0, 0, ancho, alto);
  contexto.drawImage(imagen, 0, 0, ancho, alto);
  imagen.close();

  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('No se pudo procesar la imagen'))),
      'image/jpeg',
      0.85,
    ),
  );
}
