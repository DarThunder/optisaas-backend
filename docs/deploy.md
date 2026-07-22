# Despliegue de Fóvea en producción

Guía para poner el sistema en un VPS con HTTPS. Asume Docker y Docker Compose instalados.

## Cómo encaja todo

Un solo **Caddy** atiende el puerto 443 y reparte: lo que empieza por `/api` va al backend,
todo lo demás es el frontend estático. No es una preferencia estética — el frontend llama al
backend con rutas relativas (`fetch('/api/...')`), así que **ambos tienen que responder en el
mismo origen**. A cambio se gana bastante: CORS deja de ser un problema, las cookies viajan
como de mismo sitio (`SameSite=Lax` basta) y solo hay un certificado que renovar.

```
Internet ──443──> Caddy ──┬── /api/*  ──> app:8080      (red interna, sin puerto público)
                          └── resto    ──> /srv (build del frontend)
                                            app ──> postgres-db:5432  (sin puerto público)
```

Ni la app ni la base publican puertos. A la app solo se llega por Caddy: además de evitar que
se salte el TLS, impide falsificar la cabecera `X-Forwarded-For`, que la bitácora de auditoría
registra como la IP responsable de cada acción sensible.

## Requisitos previos

1. **VPS** con Docker (~100–120 MXN/mes en Hetzner o DigitalOcean bastan de sobra).
2. **Dominio con el DNS ya apuntando a la IP del servidor.** No es opcional ni se puede dejar
   para después: Let's Encrypt valida el dominio conectándose en vivo, así que si el DNS no
   resuelve todavía, Caddy no consigue certificado y el sitio no levanta con HTTPS.
   Comprueba antes: `dig +short fovea.com.mx` debe devolver la IP del VPS.
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
ACME_EMAIL=un-correo-que-leas@ejemplo.com
APP_CORS_ALLOWED_ORIGINS=https://fovea.com.mx
FRONTEND_PUBLIC_URL=https://fovea.com.mx
MAIL_PROVIDER=smtp
PLATFORM_ADMIN_USERNAME=tu-usuario
```

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
