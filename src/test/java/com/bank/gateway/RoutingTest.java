package com.bank.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.yaml.snakeyaml.Yaml;

class RoutingTest extends GatewayTestSupport {

    private static final Path CONTRACTS = Path.of("../bank-docs/contracts");
    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    /**
     * Recorre los paths de los 8 openapi.yaml de bank-docs: cada operación pública llega a su
     * servicio y cada {@code x-gateway-internal} recibe 403. Evita otro caso como
     * {@code credit-recovery-runs}, que faltaba en la tabla de rutas.
     */
    @Test
    @SuppressWarnings("unchecked")
    void everyContractOperationReachesItsServiceOrIsBlocked() throws IOException {
        assumeTrue(Files.isDirectory(CONTRACTS), "bank-docs no está junto a este proyecto");
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String service : SERVICES) {
            Map<String, Object> contract;
            try (InputStream in = Files.newInputStream(CONTRACTS.resolve(service).resolve("openapi.yaml"))) {
                contract = new Yaml().load(in);
            }
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) contract.get("paths");
            for (Map.Entry<String, Map<String, Object>> path : paths.entrySet()) {
                for (String method : METHODS) {
                    Object operation = path.getValue().get(method);
                    if (operation == null) {
                        continue;
                    }
                    boolean internal = Boolean.TRUE.equals(((Map<String, Object>) operation).get("x-gateway-internal"));
                    String failure = check(service, method, toUrl(path.getKey()), internal);
                    if (failure != null) {
                        failures.add(failure);
                    }
                    checked++;
                }
            }
        }
        assertThat(failures).isEmpty();
        assertThat(checked).as("operaciones revisadas").isGreaterThan(50);
    }

    private String check(String service, String method, String url, boolean internal) {
        EntityExchangeResult<String> result = client.method(HttpMethod.valueOf(method.toUpperCase()))
                .uri(url)
                .exchange()
                .expectBody(String.class)
                .returnResult();
        int status = result.getStatus().value();
        String body = String.valueOf(result.getResponseBody());
        boolean ok = internal
                ? status == 403 && body.contains("\"FORBIDDEN\"")
                : status == 200 && body.contains("\"" + service + "\"");
        return ok ? null : method.toUpperCase() + " " + url + " (" + service + (internal ? ", interno" : "")
                + ") -> " + status + " " + body;
    }

    private static String toUrl(String contractPath) {
        String concrete = contractPath.replaceAll("\\{[^}]+}", "p1");
        return contractPath.startsWith("/.well-known") ? concrete : "/api/v1" + concrete;
    }

    @Test
    void internalEndpointsAreBlockedBeforeTheGeneralRoute() {
        client.post().uri("/api/v1/accounts/acc-1/movements")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.path").isEqualTo("/api/v1/accounts/acc-1/movements")
                .jsonPath("$.timestamp").isNotEmpty();
        client.post().uri("/api/v1/accounts/acc-1/movements/op-00001/reversal")
                .exchange()
                .expectStatus().isForbidden();
        client.post().uri("/api/v1/transactions/records")
                .exchange()
                .expectStatus().isForbidden();
        backend("account-service").verify(0, postRequestedFor(urlPathMatching(".*/movements.*")));
        backend("transaction-service").verify(0, postRequestedFor(urlEqualTo("/api/v1/transactions/records")));

        client.get().uri("/api/v1/accounts/acc-1/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("account-service");
    }

    @Test
    void keepsThePrefixAndForwardsAuthorizationAndRequestId() {
        client.get().uri("/api/v1/customers/c1?type=PERSONAL")
                .header("Authorization", "Bearer abc.def.ghi")
                .header(GatewayAttributes.REQUEST_ID_HEADER, "req-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(GatewayAttributes.REQUEST_ID_HEADER, "req-123");

        backend("customer-service").verify(getRequestedFor(urlEqualTo("/api/v1/customers/c1?type=PERSONAL"))
                .withHeader("Authorization", equalTo("Bearer abc.def.ghi"))
                .withHeader(GatewayAttributes.REQUEST_ID_HEADER, equalTo("req-123")));
    }

    @Test
    void generatesARequestIdWhenMissingAndSendsItToTheService() {
        String requestId = client.get().uri("/api/v1/credits")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(GatewayAttributes.REQUEST_ID_HEADER);

        assertThat(requestId).isNotBlank();
        backend("credit-service").verify(getRequestedFor(urlEqualTo("/api/v1/credits"))
                .withHeader(GatewayAttributes.REQUEST_ID_HEADER, equalTo(requestId)));
    }

    @Test
    void anUnknownRouteGetsAStandard404() {
        client.get().uri("/api/v1/unknown/1")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists(GatewayAttributes.REQUEST_ID_HEADER)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.path").isEqualTo("/api/v1/unknown/1");
    }

    @Test
    void configServerAndEurekaHaveNoRoute() {
        client.get().uri("/eureka/apps").exchange().expectStatus().isNotFound();
        client.get().uri("/customer-service/default").exchange().expectStatus().isNotFound();
    }

    @Test
    void healthIsServedByTheGatewayItself() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
