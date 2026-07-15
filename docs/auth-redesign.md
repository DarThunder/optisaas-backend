# Rediseño de autenticación y entrada — OptiSaaS

**Estado:** ✅ Implementado (Fases 1–5). Ver "Nota de cierre" al final.
**Autor:** Equipo OptiSaaS · **Última actualización:** 2026-07-13

---

## 1. Objetivo

Cambiar la entrada al sistema de un modelo de *"desbloqueo de terminal con cuenta de empresa"* a un modelo **centrado en la persona**: cada quien entra con **sus propias credenciales** y el sistema lo enruta según su rol. Nadie necesita las credenciales del dueño/empresa para abrir una sucursal.

## 2. Modelo actual (a reemplazar)

1. **"Desbloquear Terminal"** — login con `usuario/email + contraseña` (etiquetado *"Cuenta de Empresa"*). Genera token `PRE_AUTH`.
2. **"¿Quién eres?"** — el usuario elige:
   - **Administrador** → PIN maestro (`quickPin` del dueño) → Hub global (token `FULL`, `branchId=null`).
   - **Colaborador** → **segundo login** con sus credenciales → entra automático a su primera sucursal.
3. El admin, dentro del Hub, elige sucursal (envía el PIN de sucursal, **que el backend ignora**).

### Problemas detectados
- El paso 1 aparenta requerir credenciales del negocio → habría que repartirlas a quien abre la tienda.
- **Doble autenticación** para el colaborador.
- **PIN maestro sobrecargado:** es a la vez la llave del panel admin y el mismo tipo de PIN de 4 díg de "ficha de turno".
- **PIN de sucursal (`security_pin`) no se valida:** se define al crear la sucursal pero `AuthService.selectBranch` no lo comprueba. Código a medio conectar.

## 3. Modelo objetivo

**Login personal único** → enrutado por rol:

| Rol | Destino |
|-----|---------|
| **OWNER (dueño)** | Pantalla de elección: **Panel de administrador** (Hub) *o* **Entrar a una sucursal**. |
| **MANAGER / SELLER / OPTOMETRIST** | 1 sucursal asignada → entra directo; varias → elige cuál. |

### Decisiones aprobadas
1. **Terminal compartida + ficha por PIN.** Quien *abre* la sucursal entra con su login personal (ya no con la cuenta de empresa); durante el día, los demás cambian de turno con su PIN de 4 díg sin reteclear contraseña. *(Comportamiento de ficha intra-día ya existente, se conserva.)*
2. **PIN maestro = 2º factor del panel admin.** Entrar al Hub pide contraseña **+** PIN maestro. Se separa conceptualmente el *PIN maestro* (dueño → panel) del *PIN de ficha* (empleado → turno).
3. **PIN de sucursal = "abrir la tienda".** Al entrar a una sucursal se valida el `security_pin` de esa sucursal (secreto compartido del local). Se **activa** la validación hoy ausente.
4. **Botón "Volver al panel admin"** integrado a este enrutado (no como parche aparte).

## 4. Flujos objetivo

### 4.1 Colaborador (gerente/vendedor/optometrista)
```
Login personal (usuario/email + contraseña)
  → ¿1 sucursal?  sí → PIN de sucursal → sesión de sucursal
                  no → elegir sucursal → PIN de sucursal → sesión de sucursal
  → (intra-día) cambio de turno con PIN de ficha de cada empleado
```

### 4.2 Dueño (OWNER)
```
Login personal
  → elección:
      • Panel de administrador → PIN maestro → Hub global
      • Entrar a una sucursal  → elegir sucursal → PIN de sucursal → sesión de sucursal
  → dentro de una sucursal: botón "Volver al panel admin" → PIN maestro → Hub
```

## 5. Seguridad

- **Contraseña personal**: primer factor, siempre.
- **PIN de sucursal**: gate del local (evita que un empleado asignado entre a una tienda que no le toca abrir; refuerza el control físico del punto de venta).
- **PIN maestro**: 2º factor del área sensible (panel admin), incluso en terminal compartida.
- El backend sigue siendo la fuente de verdad de permisos (`SecurityConfig`); el enrutado del frontend es solo UX.
- El token `FULL` con `branchId=null` = sesión de Hub; con `branchId` = sesión de sucursal. `/api/auth/me` debe reconocer **ambas**.

## 6. Cambios técnicos

### Backend
- `AuthService.selectBranch`: **validar `branchPin`** contra `Branch.securityPin` (requiere `BranchRepository`). Rechazar si no coincide.
- `/api/auth/hub-access` (ya existe): reutilizable para "Volver al panel admin" desde una sesión de sucursal (valida `quickPin` del token vigente y reemite token de Hub). No requiere endpoint nuevo.
- `/api/auth/me`: exponer también la sesión de Hub (hoy responde 401 si `branchId` es null) para que el enrutado sobreviva recargas.

### Frontend
- `LoginView`: reescribir el flujo (login personal → enrutado por rol). Quitar el encuadre de "Cuenta de Empresa / Desbloquear Terminal".
- `App.jsx`: enrutar entre **Hub** y **Sucursal** a nivel de app (hoy el Hub vive dentro de `LoginView`), de modo que "Volver al panel admin" funcione y sobreviva recargas.
- `MainLayout`: botón **"Volver al panel admin"** visible solo para OWNER → dispara el gate de PIN maestro → Hub.

## 7. Plan por fases

> Las validaciones de PIN son **cambios que rompen** el flujo actual, por lo que backend y frontend de cada capacidad se **despliegan juntos** (nunca a medias).

- **Fase 1 — Enrutado de sesión (base):** `App.jsx` reconoce Hub vs Sucursal; `/api/auth/me` reporta la sesión de Hub. Sin cambios que rompan.
- **Fase 2 — Login personal + enrutado por rol:** reescritura de `LoginView`; elección del dueño (panel/sucursal).
- **Fase 3 — PIN de sucursal "abrir tienda":** `selectBranch` valida el PIN; el frontend lo recolecta en el flujo de entrada.
- **Fase 4 — Botón "Volver al panel admin":** en `MainLayout`, integrado al enrutado de la Fase 1.

## 8. Fuera de alcance (por ahora)
- Facturación/suscripción real (pasarela vs. contacto) — pendiente de decisión aparte.
- Recuperación de contraseña / correo transaccional.
- Auditoría de accesos por sucursal.

## 9. Nota de cierre — lo que se implementó

Durante la implementación, la Decisión 3 (PIN de sucursal) evolucionó al **patrón profesional de terminal vinculable** (estilo Square/Toast), que resuelve mejor el objetivo de no compartir las credenciales de la cuenta:

- **Terminal vinculable:** un dispositivo/navegador se marca como "terminal" de una sucursal con un **token de terminal** (JWT en cookie httpOnly aparte, 90 días, revocable). El **PIN de sucursal se valida al VINCULAR** (una vez), no en cada entrada.
- **Fichaje por PIN:** con la terminal vinculada, los empleados inician turno con **solo su PIN** (endpoint `clock-in`), sin credenciales de la cuenta.

### Fases entregadas
- **Fase 1 — Enrutado de sesión:** `App.jsx` enruta `loading/anon/hub/branch`; `/api/auth/me` reporta `scope` HUB/BRANCH.
- **Fase 2 — Login personal + enrutado por rol:** `LoginView` reescrito; sin doble autenticación.
- **Fase 3 — Terminal (backend):** `TerminalController` + `TerminalService` + token de terminal (`bind` / `status` / `clock-in` / `unbind`).
- **Fase 4 — Login con 2 puertas + fichaje (frontend):** pantalla de fichaje por PIN, login admin, vincular/desvincular, activación accesible desde la terminal.
- **Fase 5 — "Volver al panel admin":** botón en `MainLayout` (solo OWNER) que reemite el token de hub validando el PIN maestro (reutiliza `/api/auth/hub-access`).

### Endpoints nuevos/clave
- `GET/PUT /api/users/me[/profile|/password|/master-pin]` — auto-servicio de perfil.
- `POST /api/auth/terminal/bind|clock-in|unbind`, `GET /api/auth/terminal/status`.
- `POST /api/auth/hub-access` — reutilizado para "volver al panel admin".
