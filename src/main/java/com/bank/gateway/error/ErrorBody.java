package com.bank.gateway.error;

import com.bank.gateway.GatewayAttributes;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

/** Cuerpo estándar de error ({@code ErrorResponse} de common-schemas.yaml). */
public record ErrorBody(Instant timestamp, int status, String code, String message, String path) {

    public static ErrorBody of(HttpStatus status, String code, String message, ServerWebExchange exchange) {
        return new ErrorBody(Instant.now(), status.value(), code, message, originalPath(exchange));
    }

    /** La ruta que pidió el cliente, aunque la petición se haya reenviado a /fallback o /denied. */
    public static String originalPath(ServerWebExchange exchange) {
        return exchange.getAttributeOrDefault(GatewayAttributes.ORIGINAL_PATH,
                exchange.getRequest().getPath().value());
    }
}
