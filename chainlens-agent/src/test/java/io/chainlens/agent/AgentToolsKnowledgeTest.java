package io.chainlens.agent;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.chainlens.chain.parse.CuratedSelectorRegistry;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolsKnowledgeTest {

    @Test
    void 案卷与规则工具能序列化Instant而不是空对象() throws Exception {
        Finding finding = new Finding("TEST-RULE", Severity.LOW, 1.0, "测试", "测试描述", null);
        AgentTools tools = new AgentTools(
                AgTxFactsFactory.create().build(), List.of(finding), null,
                new CuratedSelectorRegistry(), query -> KnowledgeSearch.SearchResult.available(List.of()),
                AnalysisProgressListener.noop(), null);

        JsonNode facts = new ObjectMapper().readTree(tools.getCaseFacts());
        JsonNode findings = new ObjectMapper().readTree(tools.getRiskFindings());

        assertThat(facts.path("transaction").path("hash").asText()).isEqualTo(AgTxFactsFactory.TX_HASH);
        assertThat(facts.path("fetchedAt").asText()).isEqualTo("1970-01-01T00:00:00Z");
        assertThat(findings.get(0).path("ruleId").asText()).isEqualTo("TEST-RULE");
    }

    @Test
    void 知识工具返回稳定的状态原因与命中Json契约() throws Exception {
        KnowledgeSearch search = query -> KnowledgeSearch.SearchResult.available(List.of(
                new KnowledgeSearch.KnowledgeHit(
                        "解释内容", "docs/knowledge/failed-transaction.md#含义", "交易失败",
                        List.of("TX-FAILED"), 0.91)));
        AgentTools tools = new AgentTools(
                AgTxFactsFactory.create().build(), List.of(), null,
                new CuratedSelectorRegistry(), search, AnalysisProgressListener.noop(), null);

        JsonNode json = new ObjectMapper().readTree(tools.searchKnowledge("失败交易"));

        assertThat(json.path("available").asBoolean()).isTrue();
        assertThat(json.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(json.path("reason").asText()).isEmpty();
        assertThat(json.path("hits").get(0).path("source").asText())
                .isEqualTo("docs/knowledge/failed-transaction.md#含义");
    }
}
