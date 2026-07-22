# Fóvea — Roadmap a producto profesional

> **Nombre:** el producto es **Fóvea** (dominio `fovea.com.mx`); **VLK** es el estudio que lo
> desarrolla y aparece solo en el pie de los correos y en "acerca de" — la relación del cliente es
> con el producto. El nombre vive en `app.brand.name` (backend) y `src/features/utils/brand.js`
> (frontend): cambiarlo es editar esos dos valores, no perseguirlo por el código.
> Los paquetes Java siguen siendo `com.idar.optisaas` a propósito: renombrarlos toca todo el
> proyecto, tiene riesgo real y el cliente nunca los ve.

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
  - ⚠️ Ambas cosas estuvieron rotas entre el 15 y el 16 de julio de 2026 y se corrigieron
    (ver "Trampas de la Fase 1"). El CI estuvo en rojo desde su creación hasta entonces.
  - Respaldos: `scripts/backup.sh` (dump comprimido + retención) y `scripts/restore.sh`.
  - Hardening: DB solo en `127.0.0.1`, `docker-compose.prod.yml` (sin puerto de DB, `restart: always`), `show-sql=false` por defecto.
  - Tests: +9 de regresión de seguridad (`AttemptLimiter`, `PinEncoder`). Total 17, en verde.

#### Trampas de la Fase 1 que conviene recordar
- **`pg_dump` >= 17 envuelve el volcado en `\restrict` / `\unrestrict`**, meta-comandos que solo
  entiende el cliente `psql`. Flyway ejecuta el script por JDBC, donde un `\` inicial es un error
  de sintaxis: `V1__baseline.sql` reventaba en cualquier BD vacía (CI, clon nuevo, `postgres-data`
  borrado). No se notaba en las BD existentes porque ahí V1 es de tipo BASELINE —
  `baseline-on-migrate=true` lo marca como adoptado sin ejecutarlo, y las filas BASELINE no
  guardan checksum. Por eso editar V1 no rompió ninguna BD viva. Al regenerar el dump, borrar
  esas dos líneas.
- **`src/test/resources/application.properties` ocultaba al de `src/main`**: Maven pone
  `target/test-classes` antes en el classpath, así que ese archivo NO complementa al de
  producción, lo REEMPLAZA. El contexto quedaba sin `spring.datasource.url`, `contextLoads`
  moría con "Failed to determine a suitable driver class" y las variables `DB_*` del workflow no
  las leía nadie. Ahora es `application-test.properties` (perfil `test`, se carga encima del de
  `src/main`) y `OptisaasApplicationTests` lleva `@ActiveProfiles("test")`. **Todo `@SpringBootTest`
  nuevo debe llevarlo.**
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
- **Fase 3:** ✅ terminada (backend y frontend verificados end-to-end).
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

### Frontend de la Fase 3 — ✅ hecho
- **Vista de Compras** (`src/features/purchases/`): pestaña nueva `purchases` en `ROLE_TABS`, solo
  OWNER/MANAGER — es lo que exige `SecurityConfig` para `/api/suppliers`, `/api/purchase-orders` e
  `/api/inventory-adjustments`.
  - `SuppliersSection` + `SupplierModal`: alta, edición y baja lógica (los inactivos se ocultan
    por defecto y se pueden reactivar; el aviso deja claro que no se pierde el historial).
  - `PurchaseOrdersSection`: filtros por estado, detalle desplegable con lo pedido vs. lo recibido,
    y las acciones según estado (confirmar en DRAFT, recibir en ORDERED/PARTIALLY_RECEIVED,
    cancelar mientras no esté RECEIVED/CANCELLED).
  - `PurchaseOrderModal`: al elegir producto propone su costo actual, salvo que sea 0 — ahí deja el
    campo vacío, porque 0 es "no capturado" y mandarlo sería falsear la valuación.
  - `ReceiveModal`: precarga lo pendiente al costo pactado y permite corregir cantidad y costo real
    de factura. `Idempotency-Key` generada al ABRIR el modal (la intención, no el clic) y quemada
    al concretarse: el doble clic no suma stock dos veces.
- **Ajustes desde Inventario**: `AdjustStockModal` con los dos modos que acepta el backend
  (movimiento `delta` / conteo físico `newQuantity`, exactamente uno), vista previa del stock
  resultante, motivo de lista cerrada y aviso de que el ajuste no se deshace. `AdjustmentHistoryModal`
  por producto o global (botón "Mermas"), con filtro por motivo, paginación y el valor de la pérdida
  calculado con el costo congelado.
- `src/features/utils/product.js`: `productLabel()`. Un `Product` NO tiene campo `name`: se identifica
  con `brand` + `model` (y el stock es `stockQuantity`). Es la misma composición que hace el backend
  al reportar ajustes; centralizada para no volver a asumir un `product.name` que no existe.

- **Fase 4:** ✅ construida y verificada, **pero el correo aún no sale de verdad**: falta el
  dominio propio, así que `app.mail.provider` sigue en `log` (los correos se escriben en el
  registro del servidor, no se envían). Al comprar el dominio: `MAIL_PROVIDER=smtp`, credenciales
  del proveedor y verificar SPF/DKIM. Hasta entonces la fase está hecha en código, no en operación.
  - ✅ **Capa de correo** (`com.idar.optisaas.mail`): el proveedor es CONFIGURACIÓN, no código.
    `MailSender` es un puerto con dos implementaciones que se eligen con `app.mail.provider`:
    `LogMailSender` (por defecto; escribe el correo en el log en vez de enviarlo, para poder usar
    y verificar los flujos sin dominio verificado) y `SmtpMailSender` (envío real; sirve con
    Gmail, Brevo, SES o el del hosting vía `spring.mail.*`).
    **`log` NO va en producción**: dejaría en los logs enlaces válidos para tomar una cuenta.
  - ✅ **Recuperación de contraseña self-service del DUEÑO**: `POST /api/auth/forgot-password` y
    `POST /api/auth/reset-password`, entidad `PasswordResetToken`, migración `V7`.
    Solo el dueño: es quien contrata el servicio y tiene correo propio (hoy 6 de 9 usuarios no
    tienen). A los empleados les restablece el acceso su administrador con el código de
    activación que ya existía.
  - Frontend: "¿Olvidaste tu contraseña?" en el login de administrador y pantalla de contraseña
    nueva. El token llega por `/?reset-token=...` y se borra del historial al instante, para que
    no quede en la barra de direcciones ni se filtre por `Referer`.
  - ✅ **Activación de empleados por correo**: al dar de alta o resetear el acceso, si el empleado
    tiene correo capturado el código le llega solo (`EmployeeActivationMailer`); si no, el
    administrador lo ve en pantalla y se lo dicta, como siempre. Hoy 6 de 9 usuarios no tienen
    correo, así que el camino manual NO se eliminó. `createEmployee` y `resetCredentials` devuelven
    `ActivationDelivery` (usuario + a qué correo se envió, o null) para que la interfaz diga
    cuál de los dos caminos ocurrió.
  - ✅ **Avisos al cliente ("su trabajo está listo")**: botón de WhatsApp y de SMS en el tablero de
    seguimiento, sobre las órdenes en `READY_TO_PICK`. **No usa la API de WhatsApp**: abre `wa.me`
    con el mensaje ya escrito para que quien atiende lo revise y lo mande desde el número de la
    óptica. Sin costo, sin aprobación de Meta, y por el canal que el cliente sí lee — en la base
    hay más clientes con teléfono que con correo, así que el correo habría alcanzado a muy pocos.
    El mensaje se firma con `businessName` de los ajustes de la sucursal.
  - ✅ **Comprobante por correo**: `POST /api/sales/{id}/send-receipt`, `SaleReceiptMailer` y
    plantilla con los renglones, descuento, total, pagado y saldo. **A petición, no automático**:
    mandar un correo a alguien porque compró, sin que lo pidiera, es correo no solicitado; que lo
    pida en el mostrador es el consentimiento. Si el cliente no tenía correo, se guarda el que se
    capture — la base de correos crece con el uso normal. Va con la identidad de la óptica y **sin
    mencionar la marca**: el cliente le compró a su óptica, no a la plataforma. El pie aclara que
    no tiene validez fiscal, para que nadie lo confunda con un CFDI (Fase 5).

#### Decisiones de la Fase 4 que conviene recordar
- **No se puede enviar "desde" el correo del cliente.** Poner su Gmail en el campo `De:` hace que
  el correo se rechace o caiga en spam: el dominio declara por DNS (SPF/DKIM/DMARC) quién puede
  enviar en su nombre, y nuestro servidor no está en esa lista. Para que un correo se vea de la
  óptica: `De` = nuestro dominio verificado, **nombre visible** = `BranchSettings.businessName`,
  y `Reply-To` = `BranchSettings.email`. El cliente ve la óptica y sus respuestas le llegan a ella.
  `EmailMessage.fromBusiness(...)` ya lo soporta para cuando se hagan los correos al cliente final.
  Guardar las credenciales SMTP de cada dueño se descartó: es almacenar la contraseña de correo
  de un cliente para muy poco beneficio.
- **Correos de plataforma vs. de la óptica**: los de la cuenta (recuperar contraseña) se ven como
  Fóvea a propósito — si llegaran con la marca de la tienda del propio usuario, parecerían
  phishing. Los que van al cliente final sí llevan la identidad de la sucursal.
- **Del token solo se guarda el hash SHA-256.** Quien tenga el token puede tomar la cuenta: es un
  secreto equivalente a una contraseña, y así un volcado de la base no entrega tokens usables.
  Un solo uso, 1 hora de vigencia, pedir uno nuevo anula el anterior y cambiar la contraseña quema
  todos los pendientes. El rol se revalida al canjear, no solo al pedir.
- **`forgot-password` responde SIEMPRE lo mismo**, exista o no el correo y sea dueño o empleado.
  Si variara, el endpoint serviría para averiguar qué correos tienen cuenta.
- **El correo de activación del empleado no es ni de plataforma ni de la óptica**, así que el
  nombre visible es «Negocio» **vía Fóvea**. Solo el negocio tendría forma de phishing (un
  correo que aparenta venir de la tienda y pide definir una contraseña); solo Fóvea dejaría al
  empleado sin saber quién lo dio de alta, pudiendo trabajar en varias ópticas. El `Reply-To` va
  a la óptica, que es quien puede ayudarlo. Si la sucursal no tiene ajustes, se cae a un genérico
  antes que mandar un correo sin firmar.
- **El código se sigue mostrando aunque se haya enviado por correo.** Sin ese respaldo, un correo
  en spam o una dirección mal capturada dejaría al empleado sin acceso y al administrador sin
  salida. El texto de la interfaz cambia según el caso.
- **Los correos salen DESPUÉS del commit** (`TransactionSynchronization.afterCommit`): enviarlos
  dentro de la transacción haría que, si esta falla después, el empleado reciba un código de una
  cuenta que no existe. Ojo al tocar esto: los tests unitarios del mailer corren SIN transacción y
  por lo tanto ejercitan el envío inmediato, no el camino de producción — por eso existe
  `EmployeeActivationMailerTransactionTest`, que sí lo cubre (commit y rollback).
- **`wa.me` exige el número con lada de país, sin `+`.** Los teléfonos se capturan en formato
  local (10 dígitos: "228 212 2440"), así que mandarlos pelados abría un chat equivocado o
  ninguno — el aviso al cliente no funcionaba para el caso normal. La normalización vive en
  `src/features/utils/phone.js` y cubre los formatos que aparecen en la práctica: 10 dígitos,
  con `+52`, con `52`, el heredado `521` de móvil (WhatsApp ya no quiere ese 1) y números de
  otro país. Los de menos de 10 dígitos se rechazan con un aviso en vez de generar un enlace
  roto en silencio. Para SMS **no** se aplica: un SMS nacional va al número tal como se capturó.
- **El frontend fabricaba correos falsos.** Al dar de alta un empleado sin correo generaba
  `usuario@optisaas.com`, un buzón inexistente. Con la activación por correo eso se volvió un
  fallo silencioso: el sistema creía que el empleado tenía correo, mandaba el código a la nada y
  le decía al administrador "ya se lo enviamos", así que nadie se lo dictaba y el empleado quedaba
  sin poder activarse. Ahora se manda `null` — que es lo que dispara el camino manual — y `null`
  y no `''` porque la columna es única y dos cadenas vacías chocarían. Si aparecen registros con
  `@optisaas.com`, son de antes: hay que ponerlos en NULL.
- **`app.frontend.url` NO sirve para armar enlaces**: es la lista de orígenes permitidos para CORS
  y su primer valor arrastraba un puerto viejo (5500), así que el enlace del correo apuntaba a
  donde la app ya no vive. Los enlaces usan `app.frontend.publicUrl` (`FRONTEND_PUBLIC_URL`).
  Se detectó ejecutando el flujo, no leyendo el código; hay test de regresión.

- **Alta de ópticas (Fase A del panel de plataforma):** ✅ hecha. Antecede a la Fase 6 y no la
  incluye: **no hay pasarela de pago ni la habrá hasta que cobrar a mano sea el cuello de
  botella.** Con un cliente, cobrar es una transferencia; automatizarlo sería construir una
  máquina para levantar tres monedas. El prepago semestral o anual alarga esa pista años.
  - ✅ **El administrador de plataforma es un CAMPO (`users.platform_admin`), no un rol.** Los
    roles viven en `user_branch_roles`, o sea siempre dentro de una óptica, y las autoridades
    de Spring salen de ahí (`AuthTokenFilter`). Se le crea con CERO vínculos: como todas las
    consultas se acotan a las sucursales del usuario, no hay nada que devolverle. El
    aislamiento es estructural, no una lista de chequeos que alguien deba recordar.
  - ✅ `TenantService.createOptica` da de alta la óptica completa en una transacción: sucursal,
    `BranchSettings` (de ahí salen el ticket, el WhatsApp y la identidad del correo), dueño sin
    contraseña con código de activación, vínculo OWNER y suscripción. `BranchService.createBranch`
    NO se tocó: ese sirve para que un dueño existente abra otra sucursal, que es otra operación.
  - ✅ **Arranque en frío** (`PlatformAdminBootstrap`): con `SEED_ENABLED=false` la base nace sin
    ningún usuario. Crea la cuenta desde variables de entorno si no existe, sin contraseña —
    emite un código que sale por el log. Idempotente. Sin esto no se puede entrar a producción.
  - ✅ `PlatformScopeGuardFilter`: la cuenta de plataforma solo alcanza `/api/platform`,
    `/api/auth` y la salud.
  - ✅ **Solicitudes de acceso (Fase B)**: `RegistrationRequest` (migración `V9`), formulario
    público `POST /api/public/registration-requests` y bandeja en el panel. Aprobar y dar de
    alta la óptica son la MISMA operación: delega en `createOptica` y enlaza la solicitud con
    el dueño creado (`created_owner_id`), así queda el rastro de qué solicitud fue qué cliente.
    Rechazar no borra. Tests: 135 en verde.

#### Decisiones de esta fase que conviene recordar
- **`user_branch_roles.role` tiene un CHECK en la base** que fija los cuatro roles. Agregar un
  quinto valor al enum de Java compila y arranca sin protestar, y Postgres rechaza cada
  inserción en tiempo de ejecución. El diseño elegido no lo toca; si algún día hace falta un
  rol nuevo, va con migración. `audit_log.action` es `varchar(50)` sin CHECK: ahí sí se pueden
  agregar acciones libremente.
- **La lista del guardián es de PERMITIDOS, no de prohibidos**, al revés que `HubScopeGuardFilter`.
  Con una lista de prohibidos, cada endpoint nuevo nace alcanzable para la cuenta que está por
  encima de todos los clientes. Se midió antes de escribirlo: sin el filtro, la cuenta de
  plataforma recibía 200 en `/api/products` y `/api/sales` (cuerpo vacío, porque las consultas
  sí se acotan) y un 500 en `/api/clinical-records`. Llegaba vacío por suerte del diseño, no
  por decisión — y eso es estar a un bug de distancia de una fuga.
- **Registro público NO**, y no por pereza: sin cobro ni límites, cualquiera crearía inquilinos
  en el VPS, y expondría el aislamiento entre inquilinos a internet antes de haberlo probado con
  dos clientes reales. La puerta pública es una página de presentación que pide acceso; las
  cuentas las crea el administrador. Login público sí, registro público no.
- **Una suscripción sin `valid_until` no vence**, a propósito: es el caso del cliente en
  acompañamiento (Mogar afinando el sistema sin plazo).
- **Al cortar por falta de pago: solo lectura, nunca cerrarles sus datos.** Esto es un punto de
  venta; dejar a una óptica sin poder cobrar es dejarla sin operar. Queda pendiente de
  implementar — con pocos clientes la herramienta real es llamarles.
- **El formulario público es la ÚNICA escritura anónima del sistema.** Va con límite por IP
  (5 cada 15 min, reusando `AttemptLimiter` como contador de eventos), topes de longitud en
  todos los campos, campo trampa anti-bot y deduplicación por correo. Y responde **siempre lo
  mismo** —bloqueado, duplicado o aceptado— por la misma razón que `forgot-password`: si
  variara, serviría para averiguar quiénes son clientes o para tantear el límite. Solo acepta
  POST: un GET abierto ahí expondría los datos de contacto de todos los prospectos.
  Ojo: el limitador es en memoria y por instancia, así que se reinicia con cada despliegue
  (ver Fase 7, Redis).
- **Del consentimiento se guarda la FECHA, no un booleano.** Se recogen datos personales; el día
  que haya que demostrar que alguien aceptó, un `true` no dice nada y una marca de tiempo sí.
  Falta el aviso de privacidad al que enlazar — sigue siendo pendiente legal, no técnico.
- **`ClientIp` centraliza la lectura de `X-Forwarded-For`** (bitácora y limitador). Confía en la
  cabecera, lo cual es correcto SOLO porque a la app no se llega más que por Caddy. Si algún día
  se publica el 8080, se puede falsificar tanto la IP de la bitácora como el límite del formulario.

- Fases 5–7: pendientes.

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
El frontend ya está commiteado (Fases 2 y 3), así que ahora esto depende solo del despliegue.
Al activarlo, cubrir también el scope `PURCHASE_RECEIPT` de la recepción de compras.
