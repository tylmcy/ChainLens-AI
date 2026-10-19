package io.chainlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.rules.RuleEngine;
import io.chainlens.rules.rule.FailedTransactionRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class McpMetricsTest {

    @Test
    void recordsOnlyLowCardinalityToolTransportAndStatusTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ChainLensMcpTools tools = new ChainLensMcpTools(
                (hash, listener) -> { throw new UnsupportedOperationException(); },
                hash -> { throw new UnsupportedOperationException(); },
                new RuleEngine(List.of(new FailedTransactionRule())),
                selector -> List.of("transfer(address,uint256)"),
                query -> KnowledgeSearch.SearchResult.available(List.of()),
                new Semaphore(1), registry, "streamable-http");

        tools.resolveSelector("0xa9059cbb");
        assertThatThrownBy(() -> tools.searchKnowledge(" ")).isInstanceOf(ChainLensMcpException.class);

        assertThat(registry.get("chainlens.mcp.tool.calls")
                .tags("tool", "resolveSelector", "transport", "streamable-http", "status", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("chainlens.mcp.tool.calls")
                .tags("tool", "searchKnowledge", "transport", "streamable-http", "status", "failure")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("chainlens.mcp.tool.duration").meters()).hasSize(2);
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            Set<String> tagKeys = meter.getId().getTags().stream()
                    .map(io.micrometer.core.instrument.Tag::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(tagKeys).isSubsetOf("tool", "transport", "status");
            assertThat(meter.getId().toString())
                    .doesNotContain("0xa9059cbb", "API_KEY", "Token", "RPC", "transfer(address,uint256)");
        });
    }
}
