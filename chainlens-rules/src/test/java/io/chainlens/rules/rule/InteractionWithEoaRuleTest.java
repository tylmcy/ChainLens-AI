package io.chainlens.rules.rule;

import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.model.ContractCodeInfo;
import io.chainlens.model.Severity;
import io.chainlens.rules.TxFactsFactory;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionWithEoaRuleTest {

    private final InteractionWithEoaRule rule = new InteractionWithEoaRule();

    private static ContractCodeInfo eoaNoCode() {
        return new ContractCodeInfo(TxFactsFactory.TO, true, false, BigInteger.ZERO, BigInteger.ONE, null);
    }

    private static ContractCodeInfo contractWithCode() {
        return new ContractCodeInfo(TxFactsFactory.TO, true, true, BigInteger.valueOf(5), BigInteger.ONE, null);
    }

    @Test
    @DisplayName("正例：无代码 + calldata 非空 → LOW / 0.95，证据来自 eth_getCode")
    void eoaWithCalldataTriggers() {
        var facts = TxFactsFactory.create()
                .withContractCode(eoaNoCode())
                .withInput("0xa9059cbb" + "0".repeat(64))
                .build();

        var findings = rule.evaluate(facts);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleId()).isEqualTo("INTERACTION-WITH-EOA");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.LOW);
        assertThat(findings.get(0).confidence()).isEqualTo(0.95);
        assertThat(findings.get(0).evidence().rpcMethod()).isEqualTo("eth_getCode");
    }

    @Test
    @DisplayName("反例：目标地址有合约代码")
    void contractWithCodeDoesNotTrigger() {
        var facts = TxFactsFactory.create()
                .withContractCode(contractWithCode())
                .withInput("0xa9059cbb" + "0".repeat(64))
                .build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }

    @Test
    @DisplayName("反例：无代码但 calldata 为空（普通转账）")
    void eoaWithoutCalldataDoesNotTrigger() {
        var facts = TxFactsFactory.create()
                .withContractCode(eoaNoCode())
                .withInput("0x")
                .build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }

    @Test
    @DisplayName("边界：历史代码不可查 → 不触发（不猜测，降级矩阵）")
    void unavailableCodeDoesNotTrigger() {
        var facts = TxFactsFactory.create() // 默认 contractCode 即 available=false
                .withInput("0xa9059cbb" + "0".repeat(64))
                .build();

        assertThat(rule.evaluate(facts)).isEmpty();
    }
}
