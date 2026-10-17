package io.chainlens.knowledge;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeCorpusTest {

    private static final Set<String> ALL_RULE_IDS = Set.of(
            "ERC20-UNLIMITED-APPROVAL", "TX-FAILED", "TRANSFER-TO-ZERO-ADDRESS",
            "INTERACTION-WITH-EOA", "UNKNOWN-SELECTOR", "MANY-TOKEN-TRANSFERS", "RISK-ADDRESS-HIT");

    @Test
    void 项目知识文档全部合法且覆盖七条规则() {
        Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();
        KnowledgeCorpusLoader.KnowledgeCorpus corpus = new KnowledgeCorpusLoader().load(
                repositoryRoot, repositoryRoot.resolve("docs/knowledge"));

        Set<String> actualRuleIds = corpus.documents().stream()
                .flatMap(document -> document.ruleIds().stream())
                .collect(Collectors.toSet());
        assertThat(actualRuleIds).isEqualTo(ALL_RULE_IDS);
        assertThat(corpus.documents()).hasSize(9);
        assertThat(corpus.chunks()).hasSizeBetween(20, 40);
        assertThat(corpus.chunks()).allSatisfy(chunk -> {
            assertThat(chunk.source()).startsWith("docs/knowledge/");
            assertThat(chunk.title()).isNotBlank();
            assertThat(chunk.heading()).isNotBlank();
        });
    }
}
