package io.chainlens.evaluation;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.chainlens.chain.TxFactsAssembler;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.RuleEngine;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationRuleSetTest {

    @TempDir Path temp;

    @Test
    void usesProductionAssemblerAndAllSevenRules() throws Exception {
        Path directory = temp.resolve("sample");
        EvaluationDatasetLoaderTest.writeFixture(directory, 31337);
        FixtureEthRpcClient rpc = new FixtureEthRpcClient("sample", EvaluationDatasetLoaderTest.HASH, directory);

        TxFacts facts = new TxFactsAssembler(rpc).assemble(EvaluationDatasetLoaderTest.HASH);
        RuleEngine engine = EvaluationRuleSet.create();
        Set<String> ids = engine.rules().stream().map(rule -> rule.id()).collect(Collectors.toSet());

        assertThat(facts.network().label()).isEqualTo("anvil-local");
        assertThat(facts.receipt().isSuccess()).isTrue();
        assertThat(engine.rules()).hasSize(7);
        assertThat(ids).containsExactlyInAnyOrder(
                "ERC20-UNLIMITED-APPROVAL", "TX-FAILED", "TRANSFER-TO-ZERO-ADDRESS",
                "INTERACTION-WITH-EOA", "UNKNOWN-SELECTOR", "MANY-TOKEN-TRANSFERS",
                "RISK-ADDRESS-HIT");
        assertThat(engine.evaluate(facts)).isEmpty();
    }
}
