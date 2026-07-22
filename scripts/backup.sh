#!/usr/bin/env bash
# Respaldo de la base de datos OptiSaaS: pg_dump comprimido con retención.
# Uso:   ./scripts/backup.sh
# Cron:  0 2 * * *  cd /ruta/optisaas-backend && ./scripts/backup.sh >> backups/backup.log 2>&1
#
# Por qué escribe a un temporal y verifica antes de renombrar:
#   Un respaldo cortado a la mitad (disco lleno, contenedor reiniciado) es SQL válido hasta
#   donde llega: se restaura sin un solo error y deja la base con la mitad de las tablas.
#   Se midió: 10 de 27 tablas y el restore reportando éxito. Como no hay error que detectar,
#   la única defensa es comprobar que el volcado llegó hasta el final ANTES de darle el
#   nombre bueno. Así, todo archivo optisaas_*.sql.gz de backups/ está completo por
#   construcción, y el de un intento fallido nunca se confunde con uno bueno.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
# Mínimo de respaldos a conservar aunque superen la retención: si los respaldos se rompen
# y nadie lo nota, a los 15 días la retención habría borrado hasta el último bueno.
KEEP_MIN="${KEEP_MIN:-3}"
DB_CONTAINER="${DB_CONTAINER:-optisaas-db}"
DB_USER="${DB_USER:-opti_admin}"
DB_NAME="${DB_NAME:-optisaas_core}"

mkdir -p "$BACKUP_DIR"
TS="$(date +%Y%m%d_%H%M%S)"
FILE="$BACKUP_DIR/optisaas_${TS}.sql.gz"
TMP="$FILE.tmp"

# Si algo falla a medio camino, el temporal no se queda ocupando disco ni confundiendo.
trap 'rm -f "$TMP"' EXIT

docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" >/dev/null 2>&1 \
  || { echo "[backup] ❌ El contenedor '$DB_CONTAINER' no responde. ¿Está levantado?" >&2; exit 1; }

echo "[backup] Generando $FILE ..."
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" | gzip > "$TMP"

# 1) El gzip está íntegro (no cortado).
if ! gzip -t "$TMP" 2>/dev/null; then
  echo "[backup] ❌ El archivo comprimido salió corrupto. Respaldo descartado." >&2
  exit 1
fi
# 2) pg_dump llegó al final: la última línea del volcado es su marcador de cierre.
if ! gunzip -c "$TMP" | tail -5 | grep -q "PostgreSQL database dump complete"; then
  echo "[backup] ❌ El volcado está incompleto (sin marcador de cierre). Respaldo descartado." >&2
  echo "[backup]    Suele ser disco lleno o el contenedor de la base reiniciándose." >&2
  exit 1
fi
# 3) La base tenía algo que respaldar. El volcado de una base vacía es válido y completo:
#    sin esta comprobación, una base perdida se respaldaría "con éxito" cada noche y la
#    retención acabaría borrando los respaldos buenos que aún la contenían.
TABLES=$(gunzip -c "$TMP" | grep -c "^CREATE TABLE" || true)
if [ "$TABLES" -lt "${MIN_TABLES:-20}" ]; then
  echo "[backup] ❌ El volcado solo tiene $TABLES tablas (se esperaban ${MIN_TABLES:-20} o más)." >&2
  echo "[backup]    ¿Se conectó a la base correcta? ¿Se perdieron datos? Respaldo descartado" >&2
  echo "[backup]    para no rotar los respaldos buenos. Revísalo antes de seguir." >&2
  exit 1
fi

mv "$TMP" "$FILE"
echo "[backup] ✅ Listo y verificado: $FILE ($(du -h "$FILE" | cut -f1))"

# Retención: elimina respaldos más viejos que RETENTION_DAYS, pero nunca deja menos de KEEP_MIN.
TOTAL=$(find "$BACKUP_DIR" -name 'optisaas_*.sql.gz' -type f | wc -l)
if [ "$TOTAL" -gt "$KEEP_MIN" ]; then
  # Los más recientes quedan protegidos; solo los sobrantes entran al criterio de antigüedad.
  find "$BACKUP_DIR" -name 'optisaas_*.sql.gz' -type f -printf '%T@ %p\n' \
    | sort -rn | tail -n +$((KEEP_MIN + 1)) | cut -d' ' -f2- \
    | while read -r old; do
        if [ -n "$(find "$old" -mtime +"$RETENTION_DAYS")" ]; then rm -f "$old"; fi
      done
fi
# Los respaldos de seguridad que deja restore.sh antes de destruir (pre-restore_*) no
# coinciden con el patrón de arriba, así que se acumularían sin límite. Se conservan los 2
# más recientes: son la red para deshacer una restauración equivocada, no historial.
find "$BACKUP_DIR" -name 'pre-restore_*.sql.gz' -type f -printf '%T@ %p\n' 2>/dev/null \
  | sort -rn | tail -n +3 | cut -d' ' -f2- | xargs -r rm -f

echo "[backup] Retención aplicada (> ${RETENTION_DAYS} días, conservando mínimo ${KEEP_MIN})."
