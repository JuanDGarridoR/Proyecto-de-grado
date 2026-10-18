#!/usr/bin/env bash
# Copia de seguridad propia de la base de datos de Vita+ (esquema public de
# Supabase), aparte de las que dé el plan de Supabase. Así, si algo se borra
# por error o el proyecto de Supabase se pierde, hay una copia que no depende
# del proveedor.
#
# Uso (en el servidor, desde la raíz del proyecto, con el .env cargado):
#   set -a; . ./.env; set +a
#   backend/scripts/respaldo.sh
#
# Para hacerlo cada día a las 3 a. m., con crontab -e:
#   0 3 * * * cd /ruta/a/Proyecto-de-grado && set -a && . ./.env && set +a && backend/scripts/respaldo.sh >> respaldos/respaldo.log 2>&1
#
# Usa pg_dump de la imagen oficial de PostgreSQL, así que solo hace falta
# Docker. Se conecta por el pooler de Supabase en modo sesión (puerto 5432):
# el modo transacción (6543, el de los servicios) no sirve para pg_dump.
#
# La copia tiene datos personales y de salud: queda en respaldos/ (que no
# se sube a git) solo con permisos para el dueño. Conviene copiarla también
# fuera del servidor, a un lugar cifrado: si se pierde el servidor, se
# pierden las copias que estén solo en él.

set -euo pipefail

: "${DB_PASSWORD:?Define DB_PASSWORD (por ejemplo, cargando el .env)}"

HOST="${DB_HOST:-aws-0-us-east-1.pooler.supabase.com}"
PUERTO="${DB_PORT:-5432}"
USUARIO="${DB_USER:-postgres.lrcpnehwvywypgppxhjr}"
CARPETA="${CARPETA_RESPALDOS:-$(cd "$(dirname "$0")/../.." && pwd)/respaldos}"
DIAS_QUE_SE_GUARDAN="${DIAS_RESPALDOS:-30}"
IMAGEN="postgres:17.11-alpine"

mkdir -p "$CARPETA"
chmod 700 "$CARPETA"

ARCHIVO="vitaplus-$(date +%Y%m%d-%H%M).dump"

echo "$(date '+%F %T') Respaldando la base en $CARPETA/$ARCHIVO"

# La contraseña va por variable de entorno (-e PGPASSWORD sin valor toma la
# de este proceso), no en la línea de comandos, donde la vería cualquiera
# que liste los procesos.
PGPASSWORD="$DB_PASSWORD" docker run --rm \
    -e PGPASSWORD \
    -e PGSSLMODE=require \
    -v "$CARPETA:/respaldos" \
    "$IMAGEN" \
    pg_dump \
        --host "$HOST" --port "$PUERTO" --username "$USUARIO" --dbname postgres \
        --schema public --no-owner --no-privileges \
        --format custom --file "/respaldos/$ARCHIVO"

chmod 600 "$CARPETA/$ARCHIVO"
echo "$(date '+%F %T') Listo: $(du -h "$CARPETA/$ARCHIVO" | cut -f1)"

# Se borran las copias de más de DIAS_QUE_SE_GUARDAN días.
find "$CARPETA" -name 'vitaplus-*.dump' -mtime +"$DIAS_QUE_SE_GUARDAN" -print -delete
