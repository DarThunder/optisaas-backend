# Optisaas Core - Enterprise Optical ERP

![Estado: Battle Tested](https://img.shields.io/badge/State-Battle_Tested-green) ![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-brightgreen) ![JPA](https://img.shields.io/badge/Hibernate-Pessimistic_Lock-red)

**El backend definitivo para la gestión óptica moderna.**

> _"Vender lentes es fácil. Gestionar inventario concurrente sin vender aire... eso es ingeniería."_

**Optisaas Core** no es otro CRUD genérico. Es un sistema de gestión empresarial diseñado con **arquitectura Multi-tenant lógica**, seguridad de grado bancario con autenticación en dos fases y un motor transaccional blindado contra condiciones de carrera.

Diseñado para escalar desde una sola tienda hasta franquicias masivas, garantizando que los datos de una sucursal sean matemáticamente invisibles para otra y que el inventario sea sagrado, incluso durante un ataque de ventas simultáneas.

## Tabla de Contenidos

- [Características](#características)
- [Instalación](#instalación)
- [Configuración](#configuración)
- [Uso (Endpoints API)](#uso-endpoints-api)
  - [Módulo Auth](#módulo-auth)
  - [Módulo Ventas (Transactional)](#módulo-ventas-transactional)
  - [Módulo Clínico](#módulo-clínico)
- [Arquitectura & Resiliencia](#arquitectura--resiliencia)

## Características

- **Multi-tenancy Real**: Aislamiento lógico de datos mediante `TenantContext` y Filtros de Hibernate (`@Filter`). Un desarrollador junior no puede filtrar datos de otra sucursal por error, el sistema lo impide a nivel de ORM.
- **Seguridad en 2 Fases**: Flujo de login único: Credenciales Globales (Pre-Auth) + PIN de Sucursal Local = Token de Acceso Completo. Permite que un mismo optometrista trabaje en múltiples sucursales con permisos distintos.
- **Race Condition Proof**: Implementación de **Pessimistic Locking** (`SELECT ... FOR UPDATE`) en inventarios y saldos. El sistema prefiere encolar a un cliente por 50ms antes que permitir una venta de stock inexistente o un doble pago.
- **Precios Dinámicos JSONB**: Motor de reglas de precios almacenado en estructuras JSON nativas dentro de SQL, permitiendo configuraciones complejas (Esfera/Cilindro/Material) sin explotar el modelo relacional.
- **Auditoría Automática**: Inyección automática de `branch_id` y timestamps mediante `EntityListeners` y Aspectos. Cero código repetitivo en los controladores.

## Instalación

Para desplegar **Optisaas** y dormir tranquilo sabiendo que tu inventario cuadra:

```bash
./scripts/startup.sh

```

_Nota: Esto levantará el servicio Spring Boot (Puerto 8080) y la base de datos PostgreSQL necesaria para soportar los bloqueos pesimistas._

## Configuración

Respetamos los **12-Factor App**. Configura estas variables en tu `.env` o dashboard de despliegue.

| Variable         | Descripción                        | Default (Dev)                              |
| ---------------- | ---------------------------------- | ------------------------------------------ |
| `DB_URL`         | URL JDBC de PostgreSQL             | `jdbc:postgresql://postgres:5432/optisaas` |
| `JWT_SECRET`     | Llave maestra HS256 (min 32 bytes) | _(Auto-generada)_                          |
| `FRONTEND_URL`   | Origen permitido para CORS         | `http://localhost:3000`                    |
| `JWT_EXPIRATION` | Duración del token Full (ms)       | `86400000` (24h)                           |

## Uso (Endpoints API)

La API es RESTful, predecible y segura por defecto.

### Módulo Auth

| Método   | Endpoint                  | Descripción                                                   | Body Requerido                           |
| -------- | ------------------------- | ------------------------------------------------------------- | ---------------------------------------- |
| **POST** | `/api/auth/login`         | Paso 1: Valida usuario/pass. Retorna Cookie `PRE_AUTH`.       | `{ "email": "...", "password": "..." }`  |
| **POST** | `/api/auth/select-branch` | Paso 2: Valida PIN. Retorna Cookie `FULL` con Rol real.       | `{ "branchId": 1, "branchPin": "1234" }` |
| **GET**  | `/api/auth/my-branches`   | Lista sucursales disponibles para el usuario pre-autenticado. | _Cookie Pre-Auth_                        |

### Módulo Ventas (Transactional)

| Método   | Endpoint                   | Descripción                                     | Body / Notas                             |
| -------- | -------------------------- | ----------------------------------------------- | ---------------------------------------- |
| **POST** | `/api/sales`               | Crea una venta. **Bloquea stock atómicamente**. | `{ "clientId": 1, "items": [...] }`      |
| **POST** | `/api/sales/{id}/payments` | Abona pago. **Previene deuda negativa**.        | `{ "amount": 150.00, "method": "CASH" }` |
| **GET**  | `/api/sales/{id}`          | Obtiene detalle de venta y saldo restante.      | _N/A_                                    |

### Módulo Clínico

| Método   | Endpoint                        | Descripción                               | Body / Notas                                     |
| -------- | ------------------------------- | ----------------------------------------- | ------------------------------------------------ |
| **POST** | `/api/clinical/records`         | Crea historial. Inyecta `branch_id` auto. | `{ "sphereRight": -2.00, ... }`                  |
| **POST** | `/api/clinical/calculate-price` | Cotizador basado en matriz JSONB y Stock. | `{ "clinicalRecordId": 1, "treatment": "Blue" }` |

## Arquitectura & Resiliencia

Este proyecto ha sido sometido al protocolo `chaos_test.sh` para garantizar integridad financiera.

### Resultados de Chaos Testing

El sistema ha sobrevivido a ráfagas de concurrencia simulando un "Black Friday":

- **Integridad de Inventario:** ✅ Escenario de 20 peticiones concurrentes para 10 items de stock. Resultado: **10 Ventas Exitosas, 10 Rechazos Controlados (400 Bad Request)**. Cero sobreventa.
- **Integridad Financiera:** ✅ Escenario de 5 intentos de pago simultáneos sobre la misma deuda. Resultado: **1 Pago Aceptado, 4 Rechazados**. Saldo nunca es negativo.
- **Estabilidad:** ✅ Cero errores `500 Internal Server Error` y cero `StackOverflowError` (gracias Lombok exclusion) durante las pruebas de estrés.
