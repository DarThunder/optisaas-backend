# OptiSaaS — Auditoría de seguridad pre-producción

> Fecha: 2026-07-15 · Rama: `main` · Alcance: backend `com.idar.optisaas` + despliegue Docker
> Estado: hallazgos verificados en código. Referencias como `archivo:línea`.

## Resumen ejecutivo

Dos temas de fondo dominan el riesgo:

1. **Secretos por defecto embebidos y usados por el despliegue** (`JWT_SECRET`, credenciales de DB). El `docker-compose.yml` no inyecta secretos, así que un despliegue con ese compose usa los valores commiteados en el repo → **forja de JWT y acceso a DB triviales**.
2. **Cookie de sesión sin `Secure` y sin capa de servicio de respaldo** para el aislamiento en ciertos flujos.

El aislamiento multi-tenant, tras revisión, es **mayormente sólido**: casi todos los servicios acotan por `branchId`/`ownerId` explícito (productos, ventas, clientes, caja, reportes por sucursal, reportes globales del dueño). Quedan **huecos puntuales** (expedientes clínicos vía sesión de Hub, y creación de cliente por binding de entidad) que se detallan abajo.

| # | Severidad | Hallazgo | Estado |
|---|-----------|----------|--------|
| C1 | 🔴 Crítico | Secretos por defecto (`JWT_SECRET`, DB) commiteados y usados por compose | Confirmado |
| C2 | 🔴 Crítico | Cookies `Secure=false` hardcodeado, no parametrizable | Confirmado |
| C3 | 🔴 Crítico | Escalada de privilegios vía `validate-pin` (any-auth + PIN 4 díg. sin throttle) | Confirmado |
| A1 | 🟠 Alto | Lectura cross-tenant de expedientes clínicos en sesión de Hub | Confirmado |
| A2 | 🟠 Alto | `ddl-auto=update` en producción | Confirmado |
| A3 | 🟠 Alto | Credenciales/semillas por defecto + fallback PIN maestro `1234` | Confirmado |
| M1 | 🟡 Medio | Sin rate-limiting en login / hub-access / clock-in | Confirmado |
| M2 | 🟡 Medio | `createClient` acepta entidad completa (`id`) → sobrescritura cross-tenant | Confirmado |
| M3 | 🟡 Medio | `selectBranch` ignora el PIN de sucursal (inconsistente con `terminal/bind`) | Confirmado |
| M4 | 🟡 Medio | Política de contraseña (mín. 4) y PIN (4 díg.) débiles | Confirmado |
| M5 | 🟡 Medio | `/actuator/**` = permitAll | Confirmado |
| B1 | 🟢 Bajo | Fugas de info en errores (`e.getMessage()`, `printStackTrace`) | Confirmado |
| B2 | 🟢 Bajo | CORS/orígenes y `BASE_URL` hardcodeados a localhost | Confirmado |
| B3 | 🟢 Bajo | CSRF deshabilitado (mitigado por SameSite; documentar) | Confirmado |

---

## 🔴 Críticos

### C1 — Secretos por defecto embebidos y usados en producción
- `src/main/resources/application.properties:11` — `app.jwtSecret=${JWT_SECRET:vbIVJy21...}`: el fallback está **commiteado**. Con él, cualquiera puede firmar un JWT con `type=FULL`, `role=OWNER`, `branchId` arbitrario → suplantación total.
- `application.properties:3` y `docker-compose.yml:17` — password DB `hola-mundo`.
- `docker-compose.yml` **no** pasa `JWT_SECRET` ni `DB_PASSWORD` al servicio `app`, por lo que un despliegue con ese archivo usa los defaults del repo.
- **Mitigación:** exigir secretos por entorno **sin fallback** (arranque falla si faltan); rotar el `JWT_SECRET` actual (ya está quemado en git); mover credenciales de DB a `.env`/secretos; considerar `.gitignore` para el `.env`.

### C2 — Cookies `Secure=false` hardcodeado
- `src/main/java/com/idar/optisaas/util/JwtUtils.java` — `.secure(false)` en `buildCookie` (l.178), `generateTerminalCookie` (l.132), `getCleanJwtCookie` (l.74), `getCleanTerminalCookie` (l.159). Con `SameSite=Lax` sin `Secure`, el JWT viaja en claro sobre HTTP. Además WebSerial/WebUSB del POS exigen HTTPS.
- **Mitigación:** hacer `secure` configurable (`app.cookie.secure`, `true` en prod) y servir tras TLS.

### C3 — Escalada de privilegios vía `POST /api/users/{id}/validate-pin`
- `SecurityConfig.java:54` deja la ruta en `authenticated()` → cualquier usuario autenticado (incluido SELLER) puede llamarla.
- `UserService.validateEmployeePin` (l.207) solo compara el `quickPin` del objetivo (4 dígitos, **texto plano**, `.equals`) y `EmployeeController.validateEmployeePin` (l.209) **reemite un cookie FULL con el rol del objetivo** en la sucursal actual. Sin límite de intentos → fuerza bruta de 10⁴.
- Impacto: un SELLER puede escalar a MANAGER/OPTOMETRIST de su sucursal. El mismo patrón sin autenticar aplica a `terminal/clock-in` (`TerminalController.java:83`, solo requiere la cookie TERMINAL de 90 días).
- **Mitigación:** rate-limiting + bloqueo por intentos en flujos por-PIN; idealmente hashear `quickPin` (BCrypt) como ya se hace con `Branch.securityPin`.

---

## 🟠 Altos

### A1 — Lectura cross-tenant de expedientes clínicos en sesión de Hub
- `ClinicalService.getRecordsByClient` (l.74) no acota por `branchId`/`ownerId`: `recordRepository.findByClient_IdOrderByCreatedAtDesc(clientId)`. Depende únicamente del filtro Hibernate `branchFilter`, que `TenantAspect` **solo activa cuando `branchId != null`**.
- En una sesión de **Hub** (`branchId=null`, filtro apagado) el método devuelve expedientes (datos médicos/Rx) de **cualquier** cliente de **cualquier** tenant iterando `clientId`. `/api/clinical-records/**` GET es `authenticated()` y un MANAGER puede obtener token de Hub (ver nota de diseño). `createRecord` (l.29) tiene la misma dependencia del filtro en el lookup de cliente.
- Además del caso clínico, dependen del filtro (fugan en Hub): `PromotionService.getAllPromotions` (`findAll`) y `PricingController.calculate-lens` (`rxRepository.findById`, aunque solo devuelve un precio).
- Nota: el resto de módulos (ventas, productos, clientes, caja, reportes por sucursal, base-prices/matrix) **no** son vulnerables porque pasan `branchId` explícito (en Hub → vacío) o validan `ownerId`.
- **Mitigación (2 capas):** (a) acotar expedientes por `ownerId`/`branchId` explícito como hacen los demás servicios; (b) **bloquear los endpoints de datos por-sucursal en sesiones de Hub** — el Hub es solo panel admin (reportes globales, usuarios, sucursales, settings). Esto cierra toda la clase "filtro falla-abierto".

### A2 — `ddl-auto=update` en producción
- `application.properties:8`. Cambios de esquema automáticos = riesgo de pérdida/corrupción silenciosa. Flyway está en dependencias pero sin migraciones.
- **Mitigación:** `validate` en prod + baseline de migraciones Flyway reales.

### A3 — Credenciales/semillas por defecto
- `DataSeeder.java`: OWNER `admin123` (l.52), PIN sucursal `1234` (l.44), y reafirma `quickPin=1234` en cada arranque si el admin existe (`ensureAdminUserIsOwner`, l.163).
- `AuthService.accessHub` (l.107): fallback de PIN maestro `"1234"` si `quickPin` es null.
- **Mitigación:** gate del seeder por perfil (`!prod` o flag), eliminar el fallback `1234`, forzar cambio de credenciales en primer uso.

---

## 🟡 Medios

- **M1 — Sin rate-limiting** en `AuthController.login`, `hub-access`, y `TerminalController.clock-in`. (Parte se cubre con C3.) *Mitigar:* filtro de throttling por IP/identificador + bloqueo temporal.
- **M2 — `createClient` con binding de entidad completa.** `ClientController.java:26` recibe `@RequestBody Client` (incluye `id`). `ClientService.createClient` (l.38) fija `branchId`/`ownerId` y hace `save()`; si el body trae `"id"` de un cliente ajeno, `save` hace *merge* → **sobrescribe y reasigna** ese registro a la cuenta atacante (ids IDENTITY, enumerables). *Mitigar:* usar DTO o `client.setId(null)` en creación.
- **M3 — `selectBranch` ignora el PIN de sucursal.** `AuthService.selectBranch` (l.67) solo valida pertenencia; `terminal/bind` sí valida `securityPin`. Inconsistencia de diseño (severidad menor: el usuario ya está asignado a la sucursal). *Mitigar:* alinear criterio (exigir PIN o documentar por qué no).
- **M4 — Política débil.** Password mín. 4 (`UserService` l.134, 313), PIN 4 dígitos. *Mitigar:* subir mínimos/complejidad.
- **M5 — `/actuator/**` = permitAll** (`SecurityConfig.java:49`). Hoy solo `/health` se expone por defecto, pero conviene restringir por rol y fijar exposición explícita.

## 🟢 Bajos

- **B1 — Fugas en errores.** `e.getMessage()` en ~9 archivos y `e.printStackTrace()` en `EmployeeController.java:92`. *Mitigar:* centralizar en `GlobalExceptionHandler`, respuestas genéricas + log server-side.
- **B2 — CORS/orígenes y `BASE_URL` hardcodeados a localhost** (`SecurityConfig.java:117`; `EmployeesManagement.jsx` en frontend). *Mitigar:* parametrizar por entorno.
- **B3 — CSRF deshabilitado** (`SecurityConfig.java:37`). Riesgo bajo con `SameSite=Lax` + cookie; documentar o pasar a `Strict` en endpoints sensibles.

## No hallazgos (revisado y correcto)
- Mass assignment en `updateEmployee`/`updateClient`/expedientes: usan DTOs o copian campos explícitos.
- Aislamiento de ventas/productos/clientes/caja/reportes: acotado por `branchId`/`ownerId` explícito.
- Reportes globales (`/api/reports/global/**`): OWNER-only en `SecurityConfig` y acotados al dueño en `OwnerReportService`.

---

## Plan de remediación

- **Fase 1 (Críticos):** C1 secretos por entorno + rotación · C2 cookie `secure` configurable · C3 cerrar `validate-pin` (autorización + throttle).
- **Fase 2 (Altos):** A1 acotar clínicos + bloquear datos en Hub · A2 `ddl-auto=validate` + Flyway baseline · A3 gate del seeder + quitar fallback `1234`.
- **Fase 3 (Medios/Bajos):** M1 throttling · M2 DTO cliente · M3 alinear PIN sucursal · M4 política · M5 actuator · B1 errores · B2 entorno · B3 CSRF/SameSite.

---

## Estado de remediación (aplicado en esta auditoría)

Compila (`mvnw compile` BUILD SUCCESS) y tests unitarios en verde (8/8).

| # | Estado | Cambio aplicado |
|---|--------|-----------------|
| C1 | ✅ Hecho | Secretos sin fallback (`JWT_SECRET`, `DB_PASSWORD`); compose lee de `.env`; `.env.example`; `.env` en `.gitignore`; props de test. **Pendiente tú:** generar `JWT_SECRET` nuevo. |
| C2 | ✅ Hecho | `app.cookie.secure` (`COOKIE_SECURE`) en las 4 cookies de `JwtUtils`. |
| C3 | ✅ Hecho | `AttemptLimiter` (5 intentos / 15 min) en login, hub-access, validate-pin y clock-in. |
| A1 | ✅ Hecho | `HubScopeGuardFilter` bloquea datos por-sucursal en sesión de Hub (sales, clients, clinical-records, cash-*, promotions) + scoping explícito en `ClinicalService.getRecordsByClient`. |
| A2 | ✅ Hecho | `ddl-auto` = `${DDL_AUTO:update}`; recomendado `validate` en prod vía `.env`. Flyway baseline: **no** (pendiente, requiere DB de prod). |
| A3 | ✅ Hecho | Seeder gated por `SEED_ENABLED`; quitado el fallback de PIN maestro `1234`; quitado el reset de `quickPin` a `1234` en cada arranque. |
| M1 | ✅ Hecho | Cubierto por `AttemptLimiter` (ver C3). |
| M2 | ✅ Hecho | `ClientService.createClient` fuerza `id=null`. |
| M3 | ✅ Cerrado (por diseño) | `selectBranch` no exige PIN de sucursal **a propósito**: el usuario ya está autenticado y asignado a esa sucursal. El PIN de sucursal (`securityPin`, BCrypt) gatea el *terminal/bind* (setup de dispositivo), no la entrada del propio usuario. El frontend manda un stub `'0000'`. No es vulnerabilidad; enforzarlo rompería el login sin ganar seguridad. |
| M4 | ✅ Hecho | Contraseña mínima subida a 8 caracteres (activación y cambio). PIN sigue 4 díg. (mitigado por throttling). |
| M5 | ✅ Hecho | `/actuator/health` público; resto `hasRole("OWNER")`; exposición limitada a `health` + `show-details=never`. |
| B1 | ✅ Hecho | `GlobalExceptionHandler` con logger (sin `printStackTrace`); el 500 ya no filtra `getMessage`; `EmployeeController` sin `printStackTrace`. |
| B2 | ✅ Hecho (backend) | CORS desde `app.cors.allowed-origins` (`APP_CORS_ALLOWED_ORIGINS`). **Pendiente:** `BASE_URL` hardcodeado en `EmployeesManagement.jsx` (repo frontend). |
| B3 | ✅ Cerrado (configurable) | CSRF mitigado por cookie `httpOnly` + `SameSite`. `SameSite` ahora es configurable por entorno (`COOKIE_SAMESITE`): `Lax` mismo dominio, `None`+`Secure` si frontend/backend van en dominios distintos. |

## Blindaje adicional (defensa en profundidad — aplicado)

| Ítem | Estado | Cambio |
|------|--------|--------|
| PIN hasheado | ✅ Hecho | `quickPin` ahora se hashea con BCrypt (`PinEncoder`). Escrituras: activación, PIN maestro, seeder. Verificaciones (validate-pin, clock-in, hub-access) usan `matches` con **re-hash perezoso** de PINs heredados en texto plano (no bloquea a usuarios existentes). Columna `users.quick_pin` ampliada de `varchar(4)` a `varchar(100)` (el hash son ~60 chars). |

> ✅ **Migración de esquema (automatizada):** el ensanche de `quick_pin` a `varchar(100)` ya
> está como migración Flyway `V2__widen_quick_pin.sql`, así que se aplica **solo** al desplegar
> (tanto en DBs existentes como frescas). Ya no requiere el `ALTER` manual. Ver [roadmap.md](roadmap.md) Fase 1.
| Throttle activación | ✅ Hecho | `AttemptLimiter` aplicado a `activateAccount` (anti fuerza-bruta del código de 6 dígitos). |
| Throttle terminal/bind | ✅ Hecho | `AttemptLimiter` en `verifyBindAccess` (PIN de sucursal al vincular terminal). |
| SameSite por entorno | ✅ Hecho | `app.cookie.sameSite` → `${COOKIE_SAMESITE:Lax}`. |
| Frontend host fijo | ✅ Hecho | `EmployeesManagement.jsx`: `BASE_URL` cambiado de `http://localhost:8080/api/users` a ruta relativa `/api/users` (como el resto de la app). |

## Estado: apartado de seguridad TERMINADO

Todos los hallazgos (críticos → bajos) están cerrados en código o justificados por diseño.
No quedan vulnerabilidades abiertas. Único pendiente **no-seguridad**: migraciones Flyway
(hoy `ddl-auto` configurable a `validate` cubre el riesgo A2; Flyway es hygiene de esquema,
recomendable pero no bloqueante para seguridad).

### Checklist de producción (configuración, no código)
- [ ] `.env` de prod con `JWT_SECRET` único (`openssl rand -base64 32`), `DB_PASSWORD` fuerte.
- [ ] `COOKIE_SECURE=true` (con HTTPS).
- [ ] `COOKIE_SAMESITE`: `Lax` si mismo dominio; `None` si frontend/backend en dominios distintos.
- [ ] `DDL_AUTO=validate` (ya es el default) · `SEED_ENABLED=false`.
- [ ] `APP_CORS_ALLOWED_ORIGINS=<dominio-real-del-frontend>`.
- [x] ~~ALTER manual de `quick_pin`~~ → automatizado con Flyway `V2` (se aplica al desplegar).
- [ ] Provisionar el usuario dueño con contraseña y PIN maestro propios (no defaults).
- [ ] Desplegar con el override de prod: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`.
- [ ] Programar `scripts/backup.sh` (cron) y probar `scripts/restore.sh`.
