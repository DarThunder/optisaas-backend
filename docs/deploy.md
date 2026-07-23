# Despliegue de Fóvea en producción

Guía para poner el sistema en un VPS con HTTPS. Asume Docker y Docker Compose instalados.

## Cómo encaja todo

Un solo **Caddy** atiende el 443 y sirve **dos dominios**:

```
fovea.com.mx      → página de presentación (estática)
                    + POST /api/public/registration-requests, y nada más

app.fovea.com.mx  → el sistema: frontend + /api/*
```

Van separados porque la aplicación es una SPA y sirve su `index.html` para cualquier ruta que
no reconozca: en el mismo dominio taparía a la portada. Y de paso, la página pública no
comparte nada con la aplicación que guarda expedientes de pacientes.

Puertas adentro el sistema **sigue siendo de un solo origen**: en `app.fovea.com.mx` conviven
el frontend y `/api`. Eso no es estética — el frontend llama con rutas relativas
(`fetch('/api/...')`), así que necesita compartir origen. A cambio, CORS deja de importar y
bastan cookies `SameSite=Lax`. El formulario de la presentación tampoco cruza origen, porque
Caddy lo hace pasar desde su propio dominio.

```
Internet ──443──> Caddy ──┬── fovea.com.mx     ──> /srv/landing (estático)
                          │     └── POST /api/public/registration-requests ──> app:8080
                          └── app.fovea.com.mx ┬── /api/*  ──> app:8080
                                               └── resto   ──> /srv/app (build del frontend)
                                                     app ──> postgres-db:5432
```

Ni la app ni la base publican puertos. A la app solo se llega por Caddy: además de evitar que
se salte el TLS, impide falsificar la cabecera `X-Forwarded-For`, que la bitácora de auditoría
registra como la IP responsable de cada acción sensible y que limita el formulario público.
Verificado: enviando `X-Forwarded-For: 1.2.3.4` a mano, Caddy la sobrescribe con la IP real.

## Requisitos previos

1. **VPS** con Docker (~100–120 MXN/mes en Hetzner o DigitalOcean bastan de sobra).
2. **Los DOS dominios con el DNS ya apuntando a la IP del servidor**, tanto el de la
   presentación como el del sistema (un registro A para cada uno, a la misma IP). No es
   opcional ni se puede dejar para después: Let's Encrypt valida conectándose en vivo, así que
   si el DNS no resuelve todavía, Caddy no consigue certificado y el sitio no levanta con
   HTTPS. Comprueba antes que ambos devuelvan la IP del VPS:

   ```bash
   dig +short fovea.com.mx && dig +short app.fovea.com.mx
   ```
3. **Los dos repos clonados uno al lado del otro**, porque compose construye el frontend
   desde `../optisaas-frontend`:

   ```
   /srv/fovea/optisaas-backend
   /srv/fovea/optisaas-frontend
   ```
4. Puertos 80 y 443 abiertos en el firewall. El 80 hace falta aunque todo sea HTTPS: por ahí
   valida Let's Encrypt y por ahí redirige Caddy a HTTPS.

## Puesta en marcha

```bash
cd /srv/fovea
git clone <repo-backend> optisaas-backend
git clone <repo-frontend> optisaas-frontend
cd optisaas-backend
cp .env.example .env
```

Edita `.env`. Lo mínimo que **debe** cambiar respecto al ejemplo:

```bash
DB_PASSWORD=<contraseña larga y única>
JWT_SECRET=<genera uno nuevo: openssl rand -base64 32>
COOKIE_SECURE=true
SEED_ENABLED=false
DDL_AUTO=validate
DOMAIN=fovea.com.mx
APP_DOMAIN=app.fovea.com.mx
ACME_EMAIL=un-correo-que-leas@ejemplo.com
APP_CORS_ALLOWED_ORIGINS=https://app.fovea.com.mx
FRONTEND_PUBLIC_URL=https://app.fovea.com.mx
MAIL_PROVIDER=smtp
PLATFORM_ADMIN_USERNAME=tu-usuario
```

> **`FRONTEND_PUBLIC_URL` es el dominio del SISTEMA, no el de la presentación.** De ahí salen
> los enlaces de los correos: quien recupera su contraseña va a entrar, no a leer el folleto.

> **`PLATFORM_ADMIN_USERNAME` no es opcional en producción.** Con `SEED_ENABLED=false` la base
> arranca sin ningún usuario: sin esta cuenta no hay forma de entrar a dar de alta la primera
> óptica salvo insertando SQL a mano con un hash de BCrypt generado aparte.

> **`JWT_SECRET` se genera en el servidor, no se reutiliza.** El secreto anterior estuvo
> commiteado en el repositorio: quien tenga acceso al historial puede firmar sesiones válidas.
>
> **`COOKIE_SECURE=true` exige HTTPS funcionando.** Si lo pones sin TLS, el navegador descarta
> la cookie de sesión y el login parece "no hacer nada" — sin ningún error visible.
>
> **`MAIL_PROVIDER=log` no puede llegar a producción.** Escribe los correos en el log del
> servidor, incluidos los enlaces de recuperación de contraseña: cualquiera con acceso a los
> logs puede tomar la cuenta del dueño.

Levantar:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

La primera vez tarda: construye el backend con Maven y el frontend con npm. Caddy pide el
certificado solo; en ~30 segundos el sitio responde por HTTPS.

## Comprobaciones después de desplegar

```bash
# 1. Todo arriba. frontend-build debe aparecer como 'exited (0)': es correcto,
#    construye el frontend y termina.
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# 2. El certificado se emitió (si falla, aquí se ve por qué).
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs caddy | grep -i "certificate\|error"

# 3. El frontend responde por HTTPS.
curl -sI https://fovea.com.mx | head -3

# 4. La API responde a través del proxy (401 es la respuesta correcta a credenciales falsas:
#    significa que la petición llegó al backend y este la evaluó).
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://fovea.com.mx/api/auth/login \
  -H "Content-Type: application/json" -d '{"identifier":"x@x.com","password":"x"}'

# 5. La app NO es accesible saltándose el proxy (debe fallar la conexión).
curl -s --max-time 5 http://<IP-DEL-VPS>:8080/actuator/health && echo "MAL: expuesto" || echo "OK: cerrado"
```

Después, la prueba que ninguna de las anteriores cubre: **entra y haz una venta de prueba.**

## Primer acceso: activar la cuenta de plataforma

Al arrancar por primera vez, el servidor crea la cuenta de administrador de plataforma y
escribe su código de activación en el log. No se envía por correo ni se guarda en ningún lado:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs app | grep "administrador de plataforma" -A1
```

Con ese código, en la pantalla de "Primer ingreso" defines tu contraseña. El mensaje no vuelve
a aparecer en reinicios posteriores: la cuenta ya existe y el arranque no la toca.

Desde ahí das de alta cada óptica cliente (`POST /api/platform/tenants`): crea su sucursal, sus
ajustes, la cuenta de su dueño y su suscripción de prueba, y devuelve el código de activación
del dueño para dictárselo o dejar que le llegue por correo.

> Esa cuenta administra el servicio, **no opera ópticas**: no pertenece a ninguna sucursal y un
> filtro le rechaza cualquier ruta fuera de su panel. Poder dar de alta a un cliente no implica
> poder mirar dentro de su negocio — lo cual importa, porque ahí hay datos de salud.

## Antes de publicar la página de presentación

La presentación vive en el repo del frontend, en `optisaas-frontend/landing/`: es HTML estático
que Caddy sirve directo (bind mount), sin build ni imagen. Cambiar un texto es editar el archivo
y recargar. Está ahí y no en el backend para que corregir un texto no dispare el CI de Maven.

Quedan tres cosas marcadas `[PENDIENTE]` dentro de `optisaas-frontend/landing/index.html`:

1. **Tu número de WhatsApp**, con lada de país y sin `+` (para México: `52` + 10 dígitos).
2. **Capturas de pantalla reales.** Tapa nombres y datos de pacientes antes de publicarlas:
   son datos personales, y las graduaciones además son datos de salud.
3. **El aviso de privacidad** (`optisaas-frontend/landing/aviso-privacidad.html`) es **un borrador y no debe
   publicarse así**. Es un esqueleto con las secciones que exige la LFPDPPP para que la
   conversación con quien sepa de esto sea corta, no un documento válido. Lleva `noindex` y un
   recuadro de advertencia; quítalos solo cuando esté revisado.

> El formulario ya recoge datos personales en cuanto la página esté en línea. El aviso no es
> un trámite posterior: es lo que hace legítimo guardarlos.

Un detalle de mantenimiento: en el resto del proyecto el nombre de la marca sale de
`app.brand.name` y `brand.js`, y hay tests que impiden escribirlo a mano. La presentación es
HTML sin backend que lo inyecte, así que es **el único lugar donde "Fóvea" está escrito a
mano**. Si algún día cambia el nombre, acuérdate de este archivo.

## Respaldos

`scripts/backup.sh` deja un dump comprimido y verificado en `backups/`. En cron:

```bash
0 2 * * * cd /srv/fovea/optisaas-backend && ./scripts/backup.sh >> backups/backup.log 2>&1
```

Restaurar (**destructivo**: la base queda exactamente como el respaldo):

```bash
./scripts/restore.sh backups/optisaas_20260722_150947.sql.gz
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart app
```

`restore.sh` guarda un `pre-restore_*.sql.gz` con el estado previo antes de destruir nada, por
si el respaldo elegido resulta ser el equivocado.

> **Los respaldos viven en el mismo servidor que la base.** Eso protege de un borrado
> accidental, no de perder el servidor. Copiarlos fuera (otro proveedor, un disco, S3) sigue
> pendiente y es lo que convierte esto en un respaldo de verdad.

## Actualizar a una versión nueva

```bash
cd /srv/fovea/optisaas-backend && git pull
cd ../optisaas-frontend && git pull
cd ../optisaas-backend
./scripts/backup.sh                     # antes de migrar el esquema, siempre
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Flyway aplica las migraciones nuevas al arrancar. El frontend se reconstruye y Caddy sirve el
build nuevo: `index.html` va con `no-store`, así que los navegadores toman el bundle nuevo en
la siguiente carga en vez de quedarse pegados al viejo.

## Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| Caddy no consigue certificado | El DNS aún no apunta al VPS, o el puerto 80 está cerrado |
| El login "no hace nada" | `COOKIE_SECURE=true` sin HTTPS: el navegador descarta la cookie |
| 502 en `/api` | La app no arrancó. `docker compose logs app` |
| El sitio da 404 | `frontend-build` falló; el volumen quedó vacío. Revisa sus logs |
| Enlaces de correo apuntan a otro sitio | `FRONTEND_PUBLIC_URL` mal puesto (no es `APP_CORS_ALLOWED_ORIGINS`) |

## Pendiente tras el primer despliegue

Con el frontend nuevo ya servido y sin bundles viejos en caché, se puede volver
**`Idempotency-Key` obligatorio** en el backend (ver `roadmap.md`, Fase 2). Cubrir también el
scope `PURCHASE_RECEIPT` de la recepción de compras.
