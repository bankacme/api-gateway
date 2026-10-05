package com.bank.gateway.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class MoneyOperationsTest {

    @Test
    void recognisesThePostsThatMoveMoney() {
        assertThat(MoneyOperations.isMoneyOperation(HttpMethod.POST, "/api/v1/deposits")).isTrue();
        assertThat(MoneyOperations.isMoneyOperation(HttpMethod.POST, "/api/v1/transfers")).isTrue();
        assertThat(MoneyOperations.isMoneyOperation(HttpMethod.POST, "/api/v1/credit-cards/cc-1/charges")).isTrue();
        assertThat(MoneyOperations.isMoneyOperation(HttpMethod.POST, "/api/v1/wallets/w-1/payments")).isTrue();
    }

    @Test
    void readsAndOtherPostsAreNotMoneyOperations() {
        assertThat(MoneyOperations.isMoneyOperation(HttpMethod.GET, "/api/v1/deposits")).isFalse();
        assertThat(MoneyOperations.isMoneyOperation(HttpMethod.POST, "/api/v1/customers")).isFalse();
        assertThat(MoneyOperations.isMoneyOperation(HttpMethod.POST, "/api/v1/credits")).isFalse();
    }
}
