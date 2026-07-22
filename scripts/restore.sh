#!/usr/bin/env bash
# Restaura un respaldo generado por backup.sh.
# Uso:   ./scripts/restore.sh backups/optisaas_YYYYMMDD_HHMMSS.sql.gz
#        FORCE=1 ./scripts/restore.sh <archivo>   # sin confirmación (para automatizar)
#
# ⚠️  DESTRUCTIVO: la base queda EXACTAMENTE como el respaldo. Todo lo posterior se pierde.
#
# Por qué recrea la base en vez de volcar encima:
#   El dump de pg_dump no lleva DROP, así que cargarlo sobre una base con datos hacía que
#   cada CREATE TABLE fallara ("already exists") y, peor, que el COPY de una tabla vacía SÍ
#   entrara mientras el de una tabla con datos no. El resultado era una base con unas tablas
#   del respaldo y otras del presente: se midió un caso con ventas cobradas y CERO pagos
#   registrados (descuadre de 1050 MXN) y el script terminando con "completada" y exit 0.
#   Recrear la base es la única forma de garantizar que el resultado es el respaldo y no una
#   mezcla de dos fechas.
set -euo pipefail

FILE="${1:?Uso: ./scripts/restore.sh <archivo.sql.gz>}"
DB_CONTAINER="${DB_CONTAINER:-optisaas-db}"
DB_USER="${DB_USER:-opti_admin}"
DB_NAME="${DB_NAME:-optisaas_core}"

[ -f "$FILE" ] || { echo "No existe el archivo: $FILE" >&2; exit 1; }
docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1 \
  || { echo "El contenedor '$DB_CONTAINER' no responde. ¿Está levantado?" >&2; exit 1; }

# Se comprueba el archivo ANTES de borrar la base: un volcado truncado es SQL válido hasta
# donde llega y se cargaría sin errores, dejando la base a medias. Los respaldos hechos por
# backup.sh ya vienen verificados; esto cubre los de otras manos y los copiados a mano.
gzip -t "$FILE" 2>/dev/null \
  || { echo "❌ El archivo está corrupto (gzip incompleto). No se toca la base." >&2; exit 1; }
gunzip -c "$FILE" | tail -5 | grep -q "PostgreSQL database dump complete" \
  || { echo "❌ El volcado está incompleto (sin marcador de cierre). No se toca la base." >&2; exit 1; }

# psql conectado a 'postgres': no se puede borrar la base a la que estás conectado.
psql_admin() { docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -q -U "$DB_USER" -d postgres "$@"; }

if [ "${FORCE:-0}" != "1" ]; then
  echo "⚠️  Se va a BORRAR y recrear '$DB_NAME' en el contenedor '$DB_CONTAINER'."
  echo "    Todos los datos actuales se perderán y quedarán los de:"
  echo "    $FILE"
  read -r -p "Escribe 'restaurar' para continuar: " answer
  [ "$answer" = "restaurar" ] || { echo "Cancelado."; exit 1; }
fi

# Red de seguridad: antes de destruir, respaldar lo que hay. Si el archivo venía corrupto,
# esto es lo único que permite volver atrás.
SAFETY="${BACKUP_DIR:-./backups}/pre-restore_$(date +%Y%m%d_%H%M%S).sql.gz"
mkdir -p "$(dirname "$SAFETY")"
echo "[restore] Respaldo de seguridad del estado actual -> $SAFETY"
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" | gzip > "$SAFETY"

echo "[restore] Cerrando conexiones abiertas a $DB_NAME ..."
psql_admin -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
               WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" >/dev/null

echo "[restore] Recreando la base $DB_NAME ..."
psql_admin -c "DROP DATABASE IF EXISTS \"$DB_NAME\";"
psql_admin -c "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USER\";"

# ON_ERROR_STOP=1: aborta al primer error en vez de seguir y mentir con exit 0.
# --single-transaction: o entra todo, o no entra nada (nunca una base a medias).
echo "[restore] Cargando $FILE ..."
if ! gunzip -c "$FILE" \
      | docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 --single-transaction \
          -q -o /dev/null -U "$DB_USER" -d "$DB_NAME"; then
  echo "[restore] ❌ FALLÓ la carga. La base quedó VACÍA (la transacción se revirtió)." >&2
  echo "[restore]    Para volver al estado previo:  ./scripts/restore.sh $SAFETY" >&2
  exit 1
fi

echo "[restore] ✅ Restauración completada y verificada."
echo "[restore]    Respaldo del estado anterior: $SAFETY"
echo "[restore]    Reinicia la aplicación:  docker compose restart app"
