# Migraciones de base de datos (Flyway)

El esquema lo gestiona **Flyway**. Hibernate está en modo `validate` (no aplica DDL).

## Convención de nombres
`V<n>__descripcion_en_snake_case.sql` — versión incremental, doble guion bajo.
Ej.: `V3__add_audit_log.sql`.

## Reglas
- **Nunca** edites una migración ya aplicada (Flyway valida su checksum y fallará).
  Para corregir algo, crea una migración nueva.
- Cada cambio de esquema para una feature nueva va como migración aquí (no con `ddl-auto`).
- `V1__baseline.sql` es el esquema adoptado al introducir Flyway (dump del estado previo).
  En DBs que ya existían se registra como *baseline* (no se re-ejecuta); en DBs frescas crea todo.
- Mantén las migraciones idempotentes/seguras cuando aplique (`IF NOT EXISTS`, etc.).

## Verificar
Al arrancar, el log muestra `Current version of schema "public": <n>` y las migraciones aplicadas.
