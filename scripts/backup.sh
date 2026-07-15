#!/usr/bin/env bash
# Respaldo de la base de datos OptiSaaS: pg_dump comprimido con retención.
# Uso:   ./scripts/backup.sh
# Cron:  0 2 * * *  cd /ruta/optisaas-backend && ./scripts/backup.sh >> backups/backup.log 2>&1
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
DB_CONTAINER="${DB_CONTAINER:-optisaas-db}"
DB_USER="${DB_USER:-opti_admin}"
DB_NAME="${DB_NAME:-optisaas_core}"

mkdir -p "$BACKUP_DIR"
TS="$(date +%Y%m%d_%H%M%S)"
FILE="$BACKUP_DIR/optisaas_${TS}.sql.gz"

echo "[backup] Generando $FILE ..."
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" | gzip > "$FILE"
echo "[backup] Listo: $FILE ($(du -h "$FILE" | cut -f1))"

# Retención: elimina respaldos más viejos que RETENTION_DAYS.
find "$BACKUP_DIR" -name 'optisaas_*.sql.gz' -type f -mtime +"$RETENTION_DAYS" -delete
echo "[backup] Retención aplicada (> ${RETENTION_DAYS} días eliminados)."
