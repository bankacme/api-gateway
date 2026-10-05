package com.bank.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Levanta el Gateway real (puerto aleatorio) con un WireMock por servicio de destino. Cada
 * WireMock responde por defecto 200 {@code {"service": "<nombre>"}}, así una prueba sabe a qué
 * servicio llegó la petición.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class GatewayTestSupport {

    protected static final List<String> SERVICES = List.of("auth-service", "customer-service", "account-service",
            "credit-service", "transaction-service", "report-service", "debit-service", "yanki-service");

    private static final Map<String, WireMockServer> BACKENDS = new LinkedHashMap<>();

    static {
        for (String service : SERVICES) {
            WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
            server.start();
            BACKENDS.put(service, server);
        }
    }

    @Autowired
    protected WebTestClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        BACKENDS.forEach((service, server) -> registry.add(
                "spring.cloud.discovery.client.simple.instances." + service + "[0].uri", server::baseUrl));
    }

    /**
     * Las clases comparten el contexto (y con él los breakers): sin reiniciarlos, las llamadas de
     * una prueba cuentan en la ventana de la siguiente.
     */
    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @BeforeEach
    void resetBackends() {
        BACKENDS.forEach((service, server) -> {
            server.resetAll();
            server.stubFor(any(anyUrl()).willReturn(okJson("{\"service\": \"" + service + "\"}")));
        });
    }

    protected static WireMockServer backend(String service) {
        return BACKENDS.get(service);
    }
}
