-- Amplía users.quick_pin de varchar(4) a varchar(100).
-- Motivo: el PIN dejó de guardarse en texto plano (4 dígitos) y ahora es un hash
-- BCrypt (~60 chars). En DBs previas a Flyway (baseline V1) esta migración aplica el
-- cambio automáticamente; en DBs frescas la columna ya nace como varchar(100) y esto
-- es un no-op seguro.
ALTER TABLE users ALTER COLUMN quick_pin TYPE varchar(100);
