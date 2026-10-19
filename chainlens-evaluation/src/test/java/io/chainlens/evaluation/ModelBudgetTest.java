package io.chainlens.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelBudgetTest {

    /** 每 token 为 1 USD，方便人工核对边界；生产单价仍按百万 token 配置。 */
    private static ModelBudget.Config config(String total) {
        return new ModelBudget.Config("mock", "fixed", LocalDate.of(2026, 10, 19),
                "模型上下文上界测试", 100, 20,
                new BigDecimal("1000000"), new BigDecimal("1000000"), new BigDecimal(total));
    }

    @Test
    void exactReservationAndActualCostAtLimitStopsNextCall() {
        ModelBudget budget = new ModelBudget(config("120"));
        budget.beginCase("one");
        budget.beforeCall(20);
        budget.settle(100, 20);

        assertThat(budget.snapshot().verifiedCostUsd()).isEqualByComparingTo("120");
        assertThat(budget.snapshot().unresolvedReserveUsd()).isEqualByComparingTo("0");
        assertThat(budget.stopped()).isTrue();
        assertThatThrownBy(() -> budget.beginCase("two"))
                .isInstanceOf(ModelBudget.Halt.class);
    }

    @Test
    void rejectsNextCallBeforeBudgetCanBeExceeded() {
        ModelBudget budget = new ModelBudget(config("130"));
        budget.beginCase("one");
        budget.beforeCall(20);
        budget.settle(20, 10);

        assertThatThrownBy(() -> budget.beforeCall(20))
                .isInstanceOf(ModelBudget.Halt.class).hasMessageContaining("预留费用");
        assertThat(budget.snapshot().verifiedCostUsd()).isEqualByComparingTo("30");
        assertThat(budget.snapshot().unresolvedReserveUsd()).isEqualByComparingTo("0");
    }

    @Test
    void missingUsageKeepsReserveAndDoesNotInventCost() {
        ModelBudget budget = new ModelBudget(config("120"));
        budget.beginCase("one");
        budget.beforeCall(20);

        assertThatThrownBy(() -> budget.settle(null, 5))
                .isInstanceOf(ModelBudget.Halt.class).hasMessageContaining("usage");
        assertThat(budget.snapshot().verifiedCostUsd()).isEqualByComparingTo("0");
        assertThat(budget.snapshot().unresolvedReserveUsd()).isEqualByComparingTo("120");
        assertThat(budget.snapshot().caseCostsUsd()).isEmpty();
    }

    @Test
    void missingOrOversizedRequestLimitCannotStartModelCall() {
        ModelBudget missing = new ModelBudget(config("120"));
        missing.beginCase("one");
        assertThatThrownBy(() -> missing.beforeCall(null))
                .isInstanceOf(ModelBudget.Halt.class).hasMessageContaining("maxTokens");

        ModelBudget oversized = new ModelBudget(config("120"));
        oversized.beginCase("one");
        assertThatThrownBy(() -> oversized.beforeCall(21))
                .isInstanceOf(ModelBudget.Halt.class).hasMessageContaining("上界");
    }

    @Test
    void localOllamaMayHaveZeroApiPriceButPaidProviderMayNot() {
        var local = new ModelBudget.Config("ollama", "qwen3:1.7b", LocalDate.of(2026, 10, 19),
                "Ollama model registry", 40960, 1024, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE);
        ModelBudget budget = new ModelBudget(local);
        budget.beginCase("one");
        budget.beforeCall(1024);
        budget.settle(100, 50);
        assertThat(budget.snapshot().verifiedCostUsd()).isEqualByComparingTo("0");
        assertThat(budget.stopped()).isFalse();

        assertThatThrownBy(() -> new ModelBudget.Config("deepseek", "deepseek-flash",
                LocalDate.of(2026, 10, 19), "price page", 1000000, 1024,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
