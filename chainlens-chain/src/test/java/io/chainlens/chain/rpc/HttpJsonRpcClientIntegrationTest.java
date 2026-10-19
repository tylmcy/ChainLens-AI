package io.chainlens.chain.rpc;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSON-RPC 客户端集成测试（WireMock 模拟节点）：
 * 覆盖正常响应 / 交易不存在（null）/ 429 限流重试 / 5xx 重试耗尽 / id 不匹配协议错误。
 * 对应设计文档 3.1、3.4、9.1、9.2。
 */
@WireMockTest
class HttpJsonRpcClientIntegrationTest {

    private HttpJsonRpcClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        client = new HttpJsonRpcClient(RpcConfig.defaults(wm.getHttpBaseUrl()));
    }

    private static void stubRpc(String method, String resultJson, String errorJson, int httpStatus) {
        String body;
        if (errorJson != null) {
            body = "{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"error\":" + errorJson + "}";
        } else {
            body = "{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":" + resultJson + "}";
        }
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo(method)))
                .willReturn(aResponse()
                        .withStatus(httpStatus)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    @DisplayName("正常响应：chainId 解析为十进制")
    void parsesChainId() {
        stubRpc("eth_chainId", "\"0xaa36a7\"", null, 200);

        assertThat(client.chainId()).isEqualTo(11155111L);
    }

    @Test
    @DisplayName("节点明确返回 null → TransactionNotFoundException（不是 503）")
    void nullResultMeansTransactionNotFound() {
        stubRpc("eth_getTransactionByHash", "null", null, 200);

        assertThatThrownBy(() -> client.getTransactionByHash("0xabc"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("429 限流 → 重试后成功（指数退避，透明恢复）")
    void retriesOn429() {
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .inScenario("rate-limit")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("Retried"));
        stubRpc("eth_chainId", "\"0xaa36a7\"", null, 200);

        assertThat(client.chainId()).isEqualTo(11155111L);
    }

    @Test
    @DisplayName("5xx 持续失败 → 重试耗尽后 RpcUnavailableException（上层映射 503）")
    void unavailableAfterRetriesExhausted() {
        stubRpc("eth_chainId", null, null, 500);

        assertThatThrownBy(() -> client.chainId())
                .isInstanceOf(RpcUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("JSON-RPC error 对象（如历史状态不可查）→ 非重试 RpcException，但不回显节点原文")
    void propagatesJsonRpcError() {
        stubRpc("eth_getCode", null, "{\"code\":-32000,\"message\":\"header not found SECRET_SENTINEL\"}", 200);

        assertThatThrownBy(() -> client.getCode("0x1234", java.math.BigInteger.ONE))
                .isInstanceOf(RpcException.class)
                .satisfies(e -> {
                    assertThat(((RpcException) e).isRetryable()).isFalse();
                    assertThat(e.getMessage()).contains("code=-32000")
                            .doesNotContain("header not found", "SECRET_SENTINEL");
                });
    }

    @Test
    @DisplayName("节点不可达时异常不得包含 URL 中的 RPC Key")
    void unreachableNodeDoesNotExposeRpcKey() {
        var guarded = new HttpJsonRpcClient(RpcConfig.defaults(
                "http://127.0.0.1:1/?api_key=SECRET_SENTINEL"));

        assertThatThrownBy(guarded::chainId)
                .isInstanceOf(RpcUnavailableException.class)
                .hasMessageNotContaining("SECRET_SENTINEL")
                .hasNoCause();
    }

    @Test
    @DisplayName("响应 id 不匹配 → RpcProtocolException（协议错误不重试）")
    void protocolErrorOnIdMismatch() {
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":999,\"result\":\"0xaa36a7\"}")));

        assertThatThrownBy(() -> client.chainId())
                .isInstanceOf(RpcProtocolException.class);
    }
}
