package com.bank.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.Test;

/**
 * Circuit breaker y timeout de 2 s con la configuración real de api-gateway.yml. Los breakers se
 * reinician antes de cada prueba (GatewayTestSupport), así que cada una empieza con la ventana
 * vacía.
 */
class CircuitBreakerTest extends GatewayTestSupport {

    @Test
    void aServiceSlowerThanTwoSecondsGetsA503InTwoSeconds() {
        backend("customer-service").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withFixedDelay(2600)));

        long start = System.currentTimeMillis();
        client.get().uri("/api/v1/customers/c1")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("El servicio no respondió a tiempo.")
                .jsonPath("$.path").isEqualTo("/api/v1/customers/c1");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).as("ms hasta el 503").isBetween(1900L, 2500L);
    }

    @Test
    void aPendingOperationAnsweredInUnderTwoSecondsArrivesAs202() {
        // La espera de las sagas (1,5 s) + margen: tiene que llegar como 202, no como 503.
        backend("transaction-service").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(202)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\": \"PENDING\"}")
                .withFixedDelay(1600)));

        client.post().uri("/api/v1/deposits")
                .exchange()
                .expectStatus().isAccepted()
                .expectBody().jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void aTimedOutMoneyOperationTellsTheClientToRepeatWithTheSameOperationId() {
        backend("debit-service").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(201)
                .withFixedDelay(2600)));

        client.post().uri("/api/v1/debit-cards/dc-1/payments")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").value(message ->
                        assertThat((String) message).contains("Repita con el mismo operationId"));
    }

    @Test
    void fiveServerErrorsOpenTheCircuitAndTheNextCallFailsFastWithoutReachingTheService() {
        backend("report-service").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 5; i++) {
            client.get().uri("/api/v1/reports/product-categories/SAVINGS")
                    .exchange()
                    .expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
        }
        long start = System.currentTimeMillis();
        client.get().uri("/api/v1/reports/product-categories/SAVINGS")
                .exchange()
                .expectStatus().isEqualTo(503);
        long elapsed = System.currentTimeMillis() - start;

        backend("report-service").verify(5, anyRequestedFor(anyUrl()));
        assertThat(elapsed).as("ms con el circuito abierto").isLessThan(500L);
    }

    @Test
    void aConnectionFailureGetsA503() {
        backend("yanki-service").stubFor(any(anyUrl()).willReturn(aResponse()
                .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        client.get().uri("/api/v1/wallets/w-1")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void businessErrorsPassThroughUnchangedAndDoNotOpenTheCircuit() {
        backend("credit-service").stubFor(any(anyUrl()).willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\": \"OVERPAYMENT\"}")));

        for (int i = 0; i < 6; i++) {
            client.post().uri("/api/v1/credits/cr-1/payments")
                    .exchange()
                    .expectStatus().isEqualTo(422)
                    .expectBody().jsonPath("$.code").isEqualTo("OVERPAYMENT");
        }
        backend("credit-service").verify(6, anyRequestedFor(anyUrl()));
    }
}
