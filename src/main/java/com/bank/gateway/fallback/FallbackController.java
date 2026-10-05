package com.bank.gateway.fallback;

import com.bank.gateway.error.ErrorBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Destino de {@code fallbackUri: forward:/fallback} de cada ruta: timeout de 2 s, circuito
 * abierto, servicio sin instancias o caído, o un 5xx del servicio. Responde 503 con el cuerpo
 * estándar; si era un POST que mueve dinero, avisa que puede haberse aplicado.
 */
@RestController
public class FallbackController {

    static final String UNAVAILABLE = "El servicio no respondió a tiempo.";
    static final String REPEAT_WITH_SAME_OPERATION_ID =
            UNAVAILABLE + " La operación puede haberse aplicado. Repita con el mismo operationId.";

    @RequestMapping("/fallback")
    public Mono<ResponseEntity<ErrorBody>> fallback(ServerWebExchange exchange) {
        String message = MoneyOperations.isMoneyOperation(exchange.getRequest().getMethod(),
                ErrorBody.originalPath(exchange))
                ? REPEAT_WITH_SAME_OPERATION_ID
                : UNAVAILABLE;
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorBody.of(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message, exchange)));
    }

    /** Destino de las rutas {@code deny-*}: endpoints {@code x-gateway-internal} de los contratos. */
    @RequestMapping("/denied")
    public Mono<ResponseEntity<ErrorBody>> denied(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorBody.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "Endpoint interno.", exchange)));
    }
}
