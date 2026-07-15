#!/usr/bin/env bash
# Restaura un respaldo generado por backup.sh.
# Uso:   ./scripts/restore.sh backups/optisaas_YYYYMMDD_HHMMSS.sql.gz
# ⚠️  Sobrescribe los datos actuales de la base. Úsalo con cuidado.
set -euo pipefail

FILE="${1:?Uso: ./scripts/restore.sh <archivo.sql.gz>}"
DB_CONTAINER="${DB_CONTAINER:-optisaas-db}"
DB_USER="${DB_USER:-opti_admin}"
DB_NAME="${DB_NAME:-optisaas_core}"

[ -f "$FILE" ] || { echo "No existe el archivo: $FILE" >&2; exit 1; }

echo "[restore] Restaurando $FILE en $DB_NAME ..."
gunzip -c "$FILE" | docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME"
echo "[restore] Restauración completada."
