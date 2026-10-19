package io.chainlens.app;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpSchema;

/** 进程级 stdio 验收：真实启动第二个 JVM，Claude Desktop 使用的就是这条通路。 */
@Testcontainers
@Tag("db")
class McpStdioProcessIntegrationTest {

    private static final String TX_HASH =
            "0xabc0000000000000000000000000000000000000000000000000000000000001";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static WireMockServer rpcNode;

    @BeforeAll
    static void startRpcFixture() throws IOException {
        rpcNode = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        rpcNode.start();
        stubFullNode();
    }

    @AfterAll
    static void stopRpcFixture() {
        if (rpcNode != null) {
            rpcNode.stop();
        }
    }

    @Test
    void stdioChildProcessUsesCleanProtocolChannelAndExitsAfterClientCloses() {
        String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path");
        ServerParameters parameters = ServerParameters.builder(javaCommand)
                .args("-cp", classpath, ChainLensApplication.class.getName(),
                        "--spring.profiles.active=mcp-stdio",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--chainlens.eth.rpc-url=" + rpcNode.baseUrl(),
                        "--spring.ai.model.chat=none",
                        "--spring.ai.model.embedding=none")
                .build();
        StdioClientTransport transport = new StdioClientTransport(
                parameters, new JacksonMcpJsonMapperSupplier().get());
        List<String> stderr = java.util.Collections.synchronizedList(new ArrayList<>());
        transport.setStdErrorHandler(stderr::add);

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            try (var client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("claude-desktop-test", "1.0"))
                    .initializationTimeout(Duration.ofSeconds(10))
                    .requestTimeout(Duration.ofSeconds(10))
                    .build()) {
                client.initialize();
                List<McpSchema.Tool> tools = client.listTools().tools();
                assertThat(tools).extracting(McpSchema.Tool::name).containsExactly(
                        "analyzeTransaction", "getCaseFacts", "getRiskFindings",
                        "resolveSelector", "searchKnowledge");

                McpSchema.CallToolResult facts = client.callTool(new McpSchema.CallToolRequest(
                        "getCaseFacts", Map.of("txHash", TX_HASH)));
                assertThat(facts.isError()).isNotEqualTo(true);
                assertThat(facts.structuredContent().toString()).contains(TX_HASH, "SEPOLIA");
            }
            // closeGracefully 关闭子进程 stdin；stdio server 收到 EOF 后必须正常退出。
            transport.awaitForExit();
        });

        assertThat(stderr).anySatisfy(line -> assertThat(line).contains("Started ChainLensApplication"));
        assertThat(stderr).noneSatisfy(line -> assertThat(line)
                .contains("DEEPSEEK_API_KEY", "CHAINLENS_MCP_TOKEN"));
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = McpStdioProcessIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void stubResult(String method, String fixtureName) throws IOException {
        rpcNode.stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo(method)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":"
                                + fixture(fixtureName) + "}")));
    }

    private static void stubFullNode() throws IOException {
        rpcNode.stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0xaa36a7\"}")));
        stubResult("eth_getTransactionByHash", "tx-legacy.json");
        stubResult("eth_getTransactionReceipt", "receipt.json");
        stubResult("eth_getBlockByNumber", "block.json");
        rpcNode.stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getCode")))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0x6080604052\"}")));
    }
}
