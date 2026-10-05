package com.bank.gateway.filter;

import com.bank.gateway.GatewayAttributes;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Conserva el {@code X-Request-Id} que llega o genera un UUID; lo reenvía al servicio y lo
 * devuelve en la respuesta. Es un {@link WebFilter} (no un GlobalFilter) para que también cubra
 * las respuestas que no pasan por una ruta, como el 404.
 */
@Component
public class RequestIdFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(GatewayAttributes.REQUEST_ID_HEADER);
        String requestId = incoming == null || incoming.isBlank() ? UUID.randomUUID().toString() : incoming;
        exchange.getAttributes().put(GatewayAttributes.REQUEST_ID, requestId);
        exchange.getAttributes().put(GatewayAttributes.ORIGINAL_PATH, exchange.getRequest().getPath().value());

        ServerWebExchange withId = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(GatewayAttributes.REQUEST_ID_HEADER,
                        requestId)))
                .build();
        withId.getResponse().beforeCommit(() -> {
            withId.getResponse().getHeaders().set(GatewayAttributes.REQUEST_ID_HEADER, requestId);
            return Mono.empty();
        });
        return chain.filter(withId);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
