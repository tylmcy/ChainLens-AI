package io.chainlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import io.chainlens.agent.AnalysisService;
import io.chainlens.chain.TxFactsProvider;
import io.chainlens.chain.parse.SelectorRegistry;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.rules.RuleEngine;
import io.chainlens.rules.rule.FailedTransactionRule;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/** 用官方 MCP Java 客户端走真实 HTTP socket，而不是只调用 Java 方法。 */
@SpringBootTest(
        classes = McpStreamableHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.address=127.0.0.1",
                "spring.ai.mcp.server.name=chainlens-test",
                "spring.ai.mcp.server.version=0.1.0",
                "spring.ai.mcp.server.protocol=STREAMABLE",
                "spring.ai.mcp.server.type=SYNC",
                "spring.ai.mcp.server.capabilities.tool=true",
                "spring.ai.mcp.server.capabilities.resource=false",
                "spring.ai.mcp.server.capabilities.prompt=false",
                "spring.ai.mcp.server.capabilities.completion=false",
                "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp",
                "spring.ai.model.chat=none",
                "spring.ai.model.embedding=none",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
        })
class McpStreamableHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void initializesDiscoversFiveToolsAndCallsOneOverStreamableHttp() {
        var transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .build();
        try (var client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("chainlens-integration-test", "1.0"))
                .initializationTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofSeconds(5))
                .build()) {
            McpSchema.InitializeResult initialized = client.initialize();
            assertThat(initialized.serverInfo().name()).isEqualTo("chainlens-test");

            List<McpSchema.Tool> tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name).containsExactly(
                    "analyzeTransaction", "getCaseFacts", "getRiskFindings",
                    "resolveSelector", "searchKnowledge");
            assertThat(tools).allSatisfy(tool -> {
                String requiredParameter = switch (tool.name()) {
                    case "resolveSelector" -> "selector";
                    case "searchKnowledge" -> "query";
                    default -> "txHash";
                };
                assertThat(tool.inputSchema()).containsEntry("required", List.of(requiredParameter));
                assertThat(tool.outputSchema()).isNotEmpty();
                assertThat(tool.annotations().readOnlyHint()).isTrue();
                assertThat(tool.annotations().destructiveHint()).isFalse();
            });

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "resolveSelector", Map.of("selector", "0xa9059cbb")));
            assertThat(result.isError()).isNotEqualTo(true);
            assertThat(result.structuredContent().toString()).contains(
                    "transfer(address,uint256)", "RESOLVED");

            McpSchema.CallToolResult invalid = client.callTool(new McpSchema.CallToolRequest(
                    "resolveSelector", Map.of("selector", "bad-secret-selector")));
            assertThat(invalid.isError()).isTrue();
            assertThat(invalid.content().toString())
                    .contains("INVALID_INPUT", "traceId")
                    .doesNotContain("McpInputValidator", "java.lang", "API_KEY", "bad-secret-selector");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        ChainLensMcpTools chainLensMcpTools() {
            AnalysisService unavailableAnalysis = (hash, listener) -> {
                throw new UnsupportedOperationException("not used by this protocol test");
            };
            TxFactsProvider unavailableFacts = hash -> {
                throw new UnsupportedOperationException("not used by this protocol test");
            };
            SelectorRegistry selectors = selector -> "0xa9059cbb".equals(selector)
                    ? List.of("transfer(address,uint256)") : List.of();
            KnowledgeSearch knowledge = query -> KnowledgeSearch.SearchResult.available(List.of());
            return new ChainLensMcpTools(
                    unavailableAnalysis, unavailableFacts,
                    new RuleEngine(List.of(new FailedTransactionRule())), selectors, knowledge,
                    new Semaphore(2), null, "streamable-http");
        }
    }
}
