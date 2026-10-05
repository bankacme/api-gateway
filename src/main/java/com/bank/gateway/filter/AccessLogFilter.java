package com.bank.gateway.filter;

import com.bank.gateway.GatewayAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Una línea por solicitud: método, ruta sin parámetros de consulta, estado, milisegundos y
 * {@code X-Request-Id}. Nunca cuerpos ni tokens.
 */
@Component
public class AccessLogFilter implements WebFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            LOG.info("{} {} {} {}ms requestId={}",
                    exchange.getRequest().getMethod(),
                    exchange.getAttributeOrDefault(GatewayAttributes.ORIGINAL_PATH,
                            exchange.getRequest().getPath().value()),
                    status == null ? 200 : status.value(),
                    (System.nanoTime() - start) / 1_000_000,
                    exchange.getAttributeOrDefault(GatewayAttributes.REQUEST_ID, "-"));
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
