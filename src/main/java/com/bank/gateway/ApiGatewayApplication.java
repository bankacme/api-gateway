package com.bank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entrada única al sistema (puerto 8080). Rutas, circuit breakers y timeouts viven en
 * {@code api-gateway.yml} del Config Server (RNF-04); aquí solo hay lo que el YAML no puede
 * expresar: el fallback, el bloqueo de endpoints internos, los filtros y el cuerpo de error.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
