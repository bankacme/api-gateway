# Secuencia — Circuit breaker del Gateway (timeout, apertura, media apertura, cierre)

Implementado con el filtro `CircuitBreaker` de cada ruta (`bank-config/api-gateway.yml`),
`resilience4j.circuitbreaker.configs.default` / `timelimiter.configs.default` y
`FallbackController` (`/fallback` → 503). Lo prueba `CircuitBreakerTest` con el yml real.

Valores: ventana de 10 llamadas, mínimo 5, umbral 50 %, 10 s abierto, 3 llamadas en media apertura,
TimeLimiter 2 s, `statusCodes: [500, 502, 503, 504]` cuentan como fallo.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente HTTP
    participant RF as RequestIdFilter
    participant GW as Ruta + filtro CircuitBreaker<br/>(p. ej. account-service)
    participant CB as Resilience4j<br/>(breaker + TimeLimiter 2 s)
    participant LB as LoadBalancer (Eureka)
    participant S as account-service
    participant FB as FallbackController

    C->>RF: GET /api/v1/accounts/{id}
    RF->>GW: + X-Request-Id
    GW->>CB: ¿estado del breaker?

    alt CLOSED (normal)
        CB->>LB: elegir instancia
        LB->>S: GET /api/v1/accounts/{id}
        alt responde a tiempo con 2xx/4xx
            S-->>GW: 200 / 404 (los 4xx no cuentan como fallo)
            GW-->>C: respuesta del servicio
        else tarda más de 2 s o responde 500/502/503/504
            CB-->>CB: registra fallo en la ventana
            CB->>FB: forward:/fallback
            FB-->>C: 503 SERVICE_UNAVAILABLE (cuerpo estándar)
        end
        Note over CB: con ≥ 5 llamadas y ≥ 50 % de fallos pasa a OPEN
    else OPEN (10 s)
        Note over CB,S: no se llama al servicio
        CB->>FB: forward:/fallback
        FB-->>C: 503 SERVICE_UNAVAILABLE inmediato
    else HALF_OPEN (tras los 10 s)
        CB->>S: deja pasar 3 llamadas de prueba
        alt las pruebas salen bien
            CB-->>CB: vuelve a CLOSED
            GW-->>C: respuesta del servicio
        else siguen fallando
            CB-->>CB: vuelve a OPEN otros 10 s
            FB-->>C: 503 SERVICE_UNAVAILABLE
        end
    end
```

## Notas

- **Un breaker por servicio** (`customer-service`, `account-service`, …): si cae uno, las rutas de
  los demás siguen cerradas.
- **Operaciones de dinero.** En el 503 de una ruta de dinero (`MoneyOperations`) el mensaje avisa que
  el resultado es incierto y que se reintente con el **mismo** `operationId`; el servicio lo deduplica.
- **Sin reintentos en el Gateway ni en el balanceador** (`spring.cloud.loadbalancer.retry.enabled=false`):
  reintentar un POST de dinero lo decide el cliente, no la infraestructura.
- **Tope del cliente HTTP 3 s** (`httpclient.response-timeout`), siempre por encima de los 2 s del
  TimeLimiter, para que el corte lo haga el breaker y se cuente como fallo.
