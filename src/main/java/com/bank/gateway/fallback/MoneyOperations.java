package com.bank.gateway.fallback;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Los POST que mueven dinero: tras un timeout pueden haberse aplicado, así que el fallback le
 * pide al cliente repetir con el mismo {@code operationId} (son idempotentes).
 */
final class MoneyOperations {

    private static final List<PathPattern> PATTERNS = List.of(
            "/api/v1/deposits",
            "/api/v1/withdrawals",
            "/api/v1/transfers",
            "/api/v1/credits/*/payments",
            "/api/v1/credit-cards/*/payments",
            "/api/v1/credit-cards/*/charges",
            "/api/v1/debit-cards/*/payments",
            "/api/v1/wallets/*/payments").stream()
            .map(PathPatternParser.defaultInstance::parse)
            .toList();

    private MoneyOperations() {
    }

    static boolean isMoneyOperation(HttpMethod method, String path) {
        if (!HttpMethod.POST.equals(method)) {
            return false;
        }
        PathContainer container = PathContainer.parsePath(path);
        return PATTERNS.stream().anyMatch(pattern -> pattern.matches(container));
    }
}
