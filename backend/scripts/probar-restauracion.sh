#!/usr/bin/env bash
# Prueba que una copia de seguridad (de respaldo.sh) se pueda restaurar: la
# carga en una base PostgreSQL desechable, en un contenedor, y muestra
# cuántas filas tiene cada tabla. Si además está DB_PASSWORD, compara esas
# cantidades con la base real de Supabase. Al terminar borra el contenedor.
#
# Uso:
#   backend/scripts/probar-restauracion.sh respaldos/vitaplus-20261018-0300.dump
#
# Una copia que nunca se ha restaurado no está comprobada: conviene hacerlo
# al menos una vez al mes y cada vez que cambie el modelo de datos.

set -euo pipefail

COPIA="${1:?Indica el archivo .dump que quieres probar}"
[ -f "$COPIA" ] || { echo "No existe $COPIA" >&2; exit 1; }

IMAGEN="postgres:17.11-alpine"
CONTENEDOR="vita-prueba-restauracion"
CLAVE_TEMPORAL="prueba-$RANDOM$RANDOM"

limpiar() { docker rm -f "$CONTENEDOR" > /dev/null 2>&1 || true; }
trap limpiar EXIT
limpiar

echo "Levantando una base desechable..."
docker run -d --name "$CONTENEDOR" -e POSTGRES_PASSWORD="$CLAVE_TEMPORAL" "$IMAGEN" > /dev/null
until docker exec "$CONTENEDOR" pg_isready -U postgres > /dev/null 2>&1; do sleep 1; done

echo "Restaurando $COPIA..."
docker cp "$COPIA" "$CONTENEDOR:/tmp/copia.dump"
docker exec "$CONTENEDOR" pg_restore --username postgres --dbname postgres \
    --no-owner --no-privileges --exit-on-error /tmp/copia.dump

# Cantidad exacta de filas de cada tabla del esquema public.
CONTAR="SELECT table_name, (xpath('/row/n/text()', query_to_xml(format('SELECT count(*) AS n FROM public.%I', table_name), false, true, '')))[1]::text
        FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name"

docker exec "$CONTENEDOR" psql -U postgres -d postgres -AtF ' ' -c "$CONTAR" > /tmp/vita-restaurada.txt

if [ -n "${DB_PASSWORD:-}" ]; then
    PGPASSWORD="$DB_PASSWORD" docker exec -e PGPASSWORD -e PGSSLMODE=require "$CONTENEDOR" \
        psql -h "${DB_HOST:-aws-0-us-east-1.pooler.supabase.com}" -p "${DB_PORT:-5432}" \
        -U "${DB_USER:-postgres.lrcpnehwvywypgppxhjr}" -d postgres -AtF ' ' -c "$CONTAR" > /tmp/vita-real.txt
    # join necesita las dos listas ordenadas de la misma forma.
    LC_ALL=C sort -o /tmp/vita-restaurada.txt /tmp/vita-restaurada.txt
    LC_ALL=C sort -o /tmp/vita-real.txt /tmp/vita-real.txt
    echo
    printf '%-40s %12s %12s\n' "Tabla" "Restaurada" "Real hoy"
    LC_ALL=C join -a1 -a2 -e '-' -o 0,1.2,2.2 /tmp/vita-restaurada.txt /tmp/vita-real.txt \
        | while read -r tabla restaurada real; do printf '%-40s %12s %12s\n' "$tabla" "$restaurada" "$real"; done
    echo
    echo "La copia es de antes, así que la base real puede tener algunas filas más."
else
    echo
    printf '%-40s %12s\n' "Tabla" "Filas"
    while read -r tabla filas; do printf '%-40s %12s\n' "$tabla" "$filas"; done < /tmp/vita-restaurada.txt
fi

rm -f /tmp/vita-restaurada.txt /tmp/vita-real.txt
echo
echo "La copia se restauró sin errores."
