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
- **Fase 2:** ✅ terminada.
  - ✅ **Bitácora de auditoría**: entidad `AuditLog` (append-only, `branch_id` nullable para acciones de Hub),
    migración `V3__add_audit_log.sql`, `AuditService` (escritura, en la transacción del llamador) y
    `AuditQueryService` (lectura, acotada a las sucursales del dueño + sus acciones de Hub).
    Endpoint `GET /api/audit` (solo OWNER) con filtros de acción y fechas, paginado y con tope de 200.
    Cableada en: cambio de estado de venta, pagos, apertura/cierre de caja (esperado vs contado y diferencia),
    movimientos de caja, cambio de operador por PIN, acceso al Hub y alta/edición/baja/reset de empleados.
    Verificada end-to-end (registro, filtros, aislamiento, 403 sin sesión). Tests: 20 en verde.
  - ✅ **Idempotencia** en crear venta, agregar pago y devolver: header `Idempotency-Key` (opcional
    por ahora; el frontend aún no lo manda), tabla `idempotency_key` con único
    `(branch_id, scope, key_value)` y migración `V4__add_idempotency_key.sql`.
    `IdempotencyService` reserva la llave en transacción propia ANTES de operar, repite la
    respuesta original en un reintento (409 si la operación sigue en curso o si la llave se reusó
    con otro contenido) y la libera si la operación falla, para no quemarla. Purga diaria a los 7 días.
  - ✅ **Devoluciones/reembolsos** por partida (parciales o totales), solo Dueño/Gerente:
    `POST /api/sales/{id}/refunds` y `GET /api/sales/{id}/refunds`, entidades `Refund`/`RefundItem`,
    migración `V5__add_refunds.sql`. Reingreso de stock (solo armazones/accesorios, con opción
    `restock` para mercancía dañada), reversa en el arqueo de caja (`cashRefunds` resta de
    `expectedCash`) y estados `PARTIALLY_RETURNED`/`RETURNED`, que solo puede fijar una devolución real.
    Modelo: `total_amount`/`paid_amount` quedan como historia bruta; lo devuelto vive en
    `returned_amount` (mercancía, con el descuento prorrateado) y `refunded_amount` (dinero).
    Solo se reembolsa lo que el cliente pagó de más sobre lo que se queda: con saldo pendiente,
    la devolución baja la deuda sin mover la caja. Auditada con `SALE_REFUNDED`.
    Verificada end-to-end en Docker (reintento devuelve la misma venta sin duplicar stock,
    devolución parcial y total, tope de piezas, arqueo y bitácora). Tests: 36 en verde.
- **Fase 3:** 🚧 backend terminado y verificado; falta el frontend.
  - ✅ **Proveedores**: `Supplier` (por sucursal, con baja lógica para no perder el historial de
    compras), CRUD en `/api/suppliers`, solo Dueño/Gerente.
  - ✅ **Órdenes de compra**: `PurchaseOrder`/`PurchaseOrderItem`, migración `V6`, estados
    `DRAFT → ORDERED → PARTIALLY_RECEIVED → RECEIVED / CANCELLED`. **Pedir no es tener**: crear o
    confirmar no toca inventario. `POST /api/purchase-orders/{id}/receive` (idempotente, scope
    `PURCHASE_RECEIPT`) es el ÚNICO punto que suma stock, admite recepciones parciales y
    recalcula el **costo promedio ponderado** del producto. Cancelar no retira lo ya recibido.
  - ✅ **Ajustes de inventario**: `InventoryAdjustment` con motivo de lista cerrada
    (`AdjustmentReason`: merma, robo, corrección de conteo, caducidad, devolución a proveedor),
    nota, responsable y **costo congelado** al momento (valuar la pérdida no debe depender del
    costo de mañana). Se ajusta por conteo físico (`newQuantity`) o por movimiento (`delta`),
    exactamente uno de los dos. Historial filtrable en `GET /api/inventory-adjustments`.
  - Auditado: `PURCHASE_ORDER_CREATED/CONFIRMED/CANCELLED/RECEIVED`, `INVENTORY_ADJUSTED`,
    `SUPPLIER_CREATED/UPDATED`. Verificado end-to-end en Docker. Tests: 52 en verde.

#### Decisiones de la Fase 3 que conviene recordar
- **Costo unitario vs. lo que se debe**: `purchase_order_items.received_cost_total` acumula el
  costo de cada recepción a SU precio. Calcular la deuda como `piezas recibidas × último costo`
  repreciaba hacia atrás lo ya recibido cuando la segunda parte del pedido llegaba más cara
  (se detectó en el smoke test: daba 1800 en vez de 1680). Hay test de regresión.
- **Producto con stock y costo 0**: el costo 0 significa "no capturado" (ver comentario en
  `Product.cost`), no "gratis". Al recibir, ese producto ADOPTA el costo de la compra en lugar
  de promediar contra cero, que dejaría el inventario valuado en casi nada. Efecto a tener en
  cuenta: la primera recepción de un producto histórico cambia su valuación de golpe.

### Frontend pendiente (Fase 3)
- Vista de Compras: catálogo de proveedores y órdenes (crear, confirmar, recibir con captura de
  lo que realmente llegó y su costo de factura, cancelar). Mandar `Idempotency-Key` al recibir.
- Ajustes desde Inventario: botón por producto con motivo, cantidad/conteo y nota, más el
  historial de mermas.

- Fases 4–7: pendientes.

### Frontend de la Fase 2 — ✅ hecho
- `src/features/utils/api.js`: `newIdempotencyKey()` (UUID; con respaldo para contextos no seguros,
  p. ej. el POS abierto por IP de la red local, donde `crypto.randomUUID` no existe) y
  `readApiError()`, que saca el `message` del backend en vez de enseñar el JSON crudo.
- `Idempotency-Key` en crear venta y cotizar (POSView), abonar (OrdersView) y devolver (RefundModal).
  La llave se genera con la INTENCIÓN (al abrir el modal de cobro / al primer intento de cotizar),
  no en el clic, y se limpia al concretarse: así el doble clic y el reintento comparten llave.
- Devoluciones: `RefundModal` (elegir piezas y cantidades, marcar reingreso al anaquel, motivo,
  medio y monto opcional, con estimación del máximo a reembolsar), botón en el tablero de órdenes
  solo para Dueño/Gerente (`App.jsx` ya le pasa `role` a `OrdersView`), badges de
  `PARTIALLY_RETURNED`/`RETURNED` y lo devuelto en el detalle de la orden.
- Corte de caja: `cashRefunds` en el desglose del efectivo esperado y en el resumen del corte.

**Pendiente:** volver `Idempotency-Key` obligatorio en el backend. Ya se puede hacer, pero solo
después de confirmar que el frontend nuevo está desplegado y sin bundles viejos en caché: si un
cliente sin actualizar sigue cobrando sin el header, empezaría a recibir 400 en cada venta.
