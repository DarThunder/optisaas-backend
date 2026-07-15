# OptiSaaS — Roadmap a producto profesional

> Complemento de [security-audit.md](security-audit.md). La seguridad ya quedó cerrada;
> este documento planifica el resto (operación, integridad financiera, features y SaaS).

## Principios
- **Cada fase entrega valor coherente** y deja el sistema estable y desplegable.
- **Esquema versionado** (Flyway) desde la Fase 1: ninguna feature nueva usa `ddl-auto`.
- **Todo cambio con pruebas** y pasando por CI.
- **Dinero y clínica = trazables e íntegros**: auditoría, idempotencia, respaldos.

---

## Fase 1 — Cimientos de ingeniería y operación  *(base para todo lo demás)*
**Objetivo:** que el proyecto sea desplegable, versionado y verificable de forma profesional.
- Flyway: baseline del esquema actual + `ddl-auto=validate` (fin del auto-DDL).
- CI/CD: GitHub Actions (build + tests en cada push/PR).
- Respaldos automatizados de Postgres (dump programado + retención) y guía de restore.
- Hardening de despliegue: perfiles `dev`/`prod`, no exponer el puerto 5432 en prod, logging.
- Ampliar cobertura de tests (regresión de la seguridad ya implementada).

## Fase 2 — Integridad financiera y trazabilidad
**Objetivo:** que ninguna operación de dinero se pierda, duplique o quede sin rastro.
- Bitácora de auditoría (quién, qué, cuándo) de acciones sensibles: cancelar venta,
  abrir/cerrar caja, cambiar precios/roles, escalar por PIN.
- Idempotencia en creación de ventas y registro de pagos (evita duplicados por reintentos).
- Devoluciones/reembolsos: reingreso de stock + reversa en caja + estado de venta.

## Fase 3 — Ciclo completo de inventario
**Objetivo:** que el stock y su valuación reflejen la realidad.
- Entradas/compras: órdenes de compra, recepción de mercancía, costo (promedio).
- Ajustes de inventario (mermas, correcciones) con motivo y responsable.

## Fase 4 — Comunicaciones y gestión de cuentas
**Objetivo:** operar sin dictar códigos a mano y comunicarse con el cliente.
- Infraestructura de correo (SMTP/proveedor).
- Activación por correo + "olvidé mi contraseña" self-service.
- Envío de ticket/comprobante por correo.
- Avisos al cliente (p. ej. "su trabajo está listo").

## Fase 5 — Facturación electrónica CFDI (SAT)
**Objetivo:** emitir comprobantes fiscales válidos en México.
- Selección e integración de PAC (Facturama/Finkok/etc.).
- Desglose de IVA, emisión de CFDI 4.0, cancelaciones y descarga de XML/PDF.
- *Requiere credenciales del PAC y definición fiscal del cliente.*

## Fase 6 — Monetización SaaS (suscripciones)
**Objetivo:** convertir la plataforma en un producto de paga sostenible.
- Planes y límites (sucursales/usuarios), periodo de prueba.
- Integración de cobro (Stripe/MercadoPago).
- Enforcement: bloqueo por falta de pago, dunning; onboarding self-service de dueños.
- *Requiere cuenta del proveedor de pagos y definición de planes/precios.*

## Fase 7 — Escalabilidad y observabilidad
**Objetivo:** soportar crecimiento y operar con visibilidad.
- Rate limiter distribuido (Redis) — hoy es en memoria.
- Refresh tokens + revocación de sesión (hoy JWT válido hasta expirar).
- Métricas, alertas y APM; preparación multi-instancia.

---

## Dependencias y orden
1 habilita todo (esquema versionado + CI + respaldos). 2 y 3 son el core operativo/financiero.
4 habilita 5 (correo para comprobantes) y 6 (correo para dunning/onboarding). 7 es para escala.
Fases 5 y 6 requieren decisiones/credenciales externas del cliente.

## Estado
- **Seguridad:** ✅ terminada (ver security-audit.md).
- **Fase 1:** ✅ terminada.
  - Flyway: `V1__baseline.sql` (esquema adoptado) + `V2__widen_quick_pin.sql`; `ddl-auto=validate`. Verificado en Docker (baseline en DB existente y creación en DB fresca).
  - CI/CD: `.github/workflows/ci.yml` (build + tests con Postgres de servicio).
  - Respaldos: `scripts/backup.sh` (dump comprimido + retención) y `scripts/restore.sh`.
  - Hardening: DB solo en `127.0.0.1`, `docker-compose.prod.yml` (sin puerto de DB, `restart: always`), `show-sql=false` por defecto.
  - Tests: +9 de regresión de seguridad (`AttemptLimiter`, `PinEncoder`). Total 17, en verde.
- **Fase 2:** ⏭️ siguiente.
- Fases 3–7: pendientes.
