package com.bank.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler = new GatewayErrorHandler(
            JsonMapper.builder().findAndAddModules().build());

    @Test
    void anUnexpectedErrorIsA500WithTheStandardBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/customers"));

        handler.handle(exchange, new IllegalStateException("boom")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"INTERNAL_ERROR\"")
                .contains("\"path\":\"/api/v1/customers\"")
                .doesNotContain("boom");
    }

    @Test
    void anotherClientErrorKeepsItsStatus() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/customers"));

        handler.handle(exchange, new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"METHOD_NOT_ALLOWED\"");
    }

    @Test
    void aServiceUnavailableStatusUsesTheGatewayMessage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/reports"));

        handler.handle(exchange, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).block();

        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    }

    @Test
    void doesNothingOnceTheResponseIsCommitted() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/customers"));
        exchange.getResponse().setComplete().block();

        assertThatThrownBy(() -> handler.handle(exchange, new IllegalStateException("late")).block())
                .hasMessageContaining("late");
    }
}
