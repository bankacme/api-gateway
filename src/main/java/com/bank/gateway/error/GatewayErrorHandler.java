package com.bank.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Errores que no pasan por el fallback ni por un servicio (ruta inexistente → 404, fallo propio
 * del Gateway → 500) con el cuerpo estándar. Va antes (-2) que el manejador por defecto de Boot (-1).
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(error);
        }
        ErrorBody body = toBody(exchange, error);
        response.setStatusCode(HttpStatus.valueOf(body.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private ErrorBody toBody(ServerWebExchange exchange, Throwable error) {
        HttpStatus status = error instanceof ResponseStatusException statusError
                ? HttpStatus.resolve(statusError.getStatusCode().value())
                : null;
        if (status == HttpStatus.NOT_FOUND) {
            return ErrorBody.of(status, "NOT_FOUND", "Ruta no encontrada.", exchange);
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return ErrorBody.of(status, "SERVICE_UNAVAILABLE", "El servicio no respondió a tiempo.", exchange);
        }
        if (status != null && status.is4xxClientError()) {
            return ErrorBody.of(status, status.name(), status.getReasonPhrase(), exchange);
        }
        LOG.error("Unexpected gateway error on {}", ErrorBody.originalPath(exchange), error);
        return ErrorBody.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del Gateway.",
                exchange);
    }
}
