package io.chainlens.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.chainlens.api.TraceIdFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全链路演示测试（M3 里程碑证据）：真实 Boot 应用（随机端口）+ WireMock RPC 节点
 * + Testcontainers PostgreSQL，走真实 HTTP 完成"输入哈希 → 取证 → 规则 → 降级报告 → 落库"。
 *
 * <p>默认随构建运行，需要 Docker 守护进程 + 镜像仓库可达；
 * 单独运行本测试：mvn verify -Dgroups=db。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("db")
class FullStackAnalysisIntegrationTest {

    private static final String TX_HASH = "0xabc0000000000000000000000000000000000000000000000000000000000001";

    static WireMockServer rpcNode;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        rpcNode = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        rpcNode.start();
        registry.add("chainlens.eth.rpc-url", rpcNode::baseUrl);
    }

    @AfterAll
    static void stopNode() {
        rpcNode.stop();
    }

    @LocalServerPort
    int port;

    /** Boot 4 移除了 TestRestTemplate，改用 RestTestClient（MockMvc 风格的真实 HTTP 客户端）。 */
    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = FullStackAnalysisIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void stubResult(String method, String fixtureName) throws IOException {
        rpcNode.stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo(method)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":"
                                + fixture(fixtureName) + "}")));
    }

    private static void stubFullNode() throws IOException {
        rpcNode.stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0xaa36a7\"}")));
        stubResult("eth_getTransactionByHash", "tx-legacy.json");
        stubResult("eth_getTransactionReceipt", "receipt.json");
        stubResult("eth_getBlockByNumber", "block.json");
        rpcNode.stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getCode")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0x6080604052\"}")));
    }

    @Test
    @DisplayName("完整链路：POST 哈希 → 200 报告（无限额度授权命中）→ 历史可查 → persisted=true")
    void fullJsonFlow() throws Exception {
        stubFullNode();

        String responseBody = client().post().uri("/api/v1/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"txHash\":\"" + TX_HASH + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(TraceIdFilter.TRACE_ID_HEADER)
                .expectBody(String.class)
                .returnResult().getResponseBody();

        JsonNode body = new ObjectMapper().readTree(responseBody);
        assertThat(body.path("llmUsed").asBoolean()).isFalse();
        assertThat(body.path("degraded").asBoolean()).isTrue();
        assertThat(body.path("persisted").asBoolean()).isTrue();
        assertThat(body.path("report").path("status").asText()).isEqualTo("SUCCESS");
        assertThat(body.path("report").path("limitations").get(0).asText())
                .contains("不构成投资建议");
        assertThat(body.path("findings").toString())
                .contains("ERC20-UNLIMITED-APPROVAL");
        String analysisId = body.path("analysisId").asText();
        assertThat(analysisId).isNotBlank();

        // 历史查询：同一 analysisId 可回读，persisted=true
        String storedBody = client().get().uri("/api/v1/analyses/" + analysisId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();
        assertThat(new ObjectMapper().readTree(storedBody).path("analysisId").asText())
                .isEqualTo(analysisId);
    }

    @Test
    @DisplayName("错误契约：交易不存在 → 404 TRANSACTION_NOT_FOUND（节点明确返回 null，不伪造）")
    void transactionNotFoundContract() {
        rpcNode.stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0xaa36a7\"}")));
        rpcNode.stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getTransactionByHash")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformers("response-template")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":null}")));

        String errorBody = client().post().uri("/api/v1/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"txHash\":\"" + TX_HASH + "\"}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(errorBody).contains("TRANSACTION_NOT_FOUND");
    }

    @Test
    @DisplayName("OpenAPI：文档端点可访问并公开 analyses API")
    void openApiIsAvailable() {
        String apiDocs = client().get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(apiDocs).contains("/api/v1/analyses", "Ethereum 交易分析");
    }
}
