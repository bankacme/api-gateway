# api-gateway

Entrada única al sistema bancario (P2, paso 2.2). Ficha: `bank-docs/services/api-gateway.md`.

- Puerto **8080**. Todo se usa a través de él: `http://localhost:8080/api/v1/...`.
- Spring Cloud Gateway (WebFlux) con `lb://<servicio>` resuelto por Eureka.
- **Spring Cloud 2025.0:** el starter es `spring-cloud-starter-gateway-server-webflux` y las
  propiedades van bajo `spring.cloud.gateway.server.webflux.*` (el prefijo antiguo ya no se lee).
- Rutas, circuit breakers y timeouts **en YAML**: `bank-config/api-gateway.yml` (RNF-04). El
  `application.yml` local solo tiene el nombre y `spring.config.import`.

## Qué hace
| Tema | Cómo |
|---|---|
| Rutas | Una por servicio, por prefijo y sin quitar `/api/v1` (`order: 10`). Lo que no está listado → 404 estándar. Sin descubrimiento automático de rutas; sin ruta a `config-server` ni `eureka-server` |
| Endpoints internos | `deny-account-movements` y `deny-transaction-records` (`order: -10`) → `forward:/denied` → 403 `FORBIDDEN` |
| Circuit breaker | Uno por servicio (nombre = servicio), `configs.default`: ventana de 10, mínimo 5 llamadas, 50 %, 10 s abierto |
| Timeout | TimeLimiter de **2 s** en todas las rutas; conexión 1 s; tope del cliente HTTP 3 s |
| Qué es fallo | Timeout, error de conexión, sin instancias y respuestas 500/502/503/504 (`statusCodes`). Los 4xx y el 202 pasan tal cual |
| Fallback | `FallbackController` (`/fallback`): 503 `SERVICE_UNAVAILABLE`; si era un POST que mueve dinero, pide repetir con el mismo `operationId` |
| `X-Request-Id` | Se conserva o se genera, se reenvía y se devuelve (`RequestIdFilter`) |
| Registro | Una línea por solicitud: método, ruta, estado, ms, `X-Request-Id` (`AccessLogFilter`) |
| Errores propios | `GatewayErrorHandler`: 404 y 500 con el cuerpo estándar |

## Pruebas
Levantan el Gateway real con el **`api-gateway.yml` de `../bank-config`** y un WireMock por servicio
(cada `lb://` se resuelve con el `SimpleDiscoveryClient`). Necesitan `bank-config` y `bank-docs`
junto a este proyecto.
- `RoutingTest`: recorre **todas las operaciones de los 8 `openapi.yaml`** de `bank-docs` y comprueba
  que cada una llega a su servicio, o recibe 403 si es `x-gateway-internal`; orden bloqueo → ruta
  general; prefijo intacto; `Authorization` y `X-Request-Id` reenviados; 404 estándar.
- `CircuitBreakerTest`: servicio lento (2,6 s) → 503 en ~2 s; 202 a los 1,6 s llega como 202; POST de
  dinero con timeout → mensaje de repetir; 5 errores 500 → circuito abierto y la 6.ª no llega al
  servicio; conexión cortada → 503; 422 pasa sin abrir el circuito.
- Unitarias: `GatewayErrorHandlerTest`, `MoneyOperationsTest`.

## Estado
- [x] Rutas, bloqueo, circuit breaker de 2 s, fallback, `X-Request-Id`, registro y errores estándar.
- [ ] JWT (`security.enabled=true`): P3, con `auth-service`.
- [ ] Diagramas de la ficha (§11).
- [ ] Dockerfile (paso 2.7).

## Diagramas

- Secuencia del circuit breaker (timeout, apertura, media apertura, cierre): `docs/sequence/circuit-breaker.md`.

## Comandos
- Compilar, estilo, pruebas y cobertura: `.\mvnw verify`
- Arrancar (necesita `config-server` y `eureka-server` arriba): `.\mvnw spring-boot:run`
