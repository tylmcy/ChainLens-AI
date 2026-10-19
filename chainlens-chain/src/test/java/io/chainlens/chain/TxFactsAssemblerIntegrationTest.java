package io.chainlens.chain;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.chainlens.chain.rpc.HttpJsonRpcClient;
import io.chainlens.chain.rpc.RpcConfig;
import io.chainlens.chain.rpc.TransactionNotFoundException;
import io.chainlens.model.ContractCodeInfo;
import io.chainlens.model.Network;
import io.chainlens.model.TxFacts;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 全链路取证组装集成测试：WireMock 模拟完整节点（chainId → 交易 → 回执 → 区块 → 代码），
 * 用与真实节点一致的 JSON 形态验证 TxFactsAssembler 的每个环节。
 * 同时把解析产物导出到 target/m1-demo-txfacts.json，作为 M1 里程碑的演示证据。
 */
@WireMockTest
class TxFactsAssemblerIntegrationTest {

    private static final String TX_HASH = "0xabc0000000000000000000000000000000000000000000000000000000000001";

    private TxFactsAssembler assembler;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        assembler = new TxFactsAssembler(new HttpJsonRpcClient(RpcConfig.defaults(wm.getHttpBaseUrl())));
    }

    private String fixture(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/fixtures/" + name)) {
            assertThat(in).as("fixture 存在: " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void stubResult(String method, String fixtureName) throws IOException {
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo(method)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":"
                                + fixture(fixtureName) + "}")));
    }

    private void stubFullNode() throws IOException {
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0xaa36a7\"}")));
        stubResult("eth_getTransactionByHash", "tx-legacy.json");
        stubResult("eth_getTransactionReceipt", "receipt.json");
        stubResult("eth_getBlockByNumber", "block.json");
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getCode")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0x6080604052\"}")));
    }

    @Test
    @DisplayName("完整取证：网络/成功判定/ETH 变化/事件/calldata/合约代码 全链路断言")
    void assemblesCompleteTxFacts() throws IOException {
        stubFullNode();

        long[] timing = {-1L, -1L};
        TxFacts facts = assembler.assemble(TX_HASH, (rpcNanos, parsingNanos) -> {
            timing[0] = rpcNanos;
            timing[1] = parsingNanos;
        });

        assertThat(timing[0]).as("五次 JSON-RPC 调用的累计耗时").isPositive();
        assertThat(timing[1]).as("解析与装配耗时").isGreaterThanOrEqualTo(0L);

        // 网络（chainId 0xaa36a7 = 11155111 Sepolia）
        assertThat(facts.network()).isEqualTo(Network.SEPOLIA);

        // 成功判定（receipt.status）
        assertThat(facts.receipt().isSuccess()).isTrue();

        // ETH 变化：value=1e18，gasCost=21000×1e9=2.1e13
        assertThat(facts.valueWei()).isEqualTo(new BigInteger("1000000000000000000"));
        assertThat(facts.gasCostWei()).isEqualTo(new BigInteger("21000000000000"));
        assertThat(facts.toNetWei()).isEqualTo(new BigInteger("1000000000000000000"));
        assertThat(facts.fromNetWei())
                .isEqualTo(new BigInteger("1000000000000000000").add(new BigInteger("21000000000000")).negate());

        // 事件：1 个 Transfer + 1 个 Approval（无限额度）
        assertThat(facts.transfers()).hasSize(1);
        assertThat(facts.transfers().get(0).from()).isEqualTo("0x1111111111111111111111111111111111111111");
        assertThat(facts.transfers().get(0).to()).isEqualTo("0x2222222222222222222222222222222222222222");
        assertThat(facts.approvals()).hasSize(1);
        assertThat(facts.approvals().get(0).valueWei())
                .isEqualTo(new BigInteger("2").pow(256).subtract(BigInteger.ONE));

        // 未解码 topic 如实列出（未知事件 + 布局异常的 Transfer）
        assertThat(facts.unknownLogTopics())
                .contains("0xdeadbeef00000000000000000000000000000000000000000000000000000000",
                        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");

        // calldata：transfer(address,uint256) → RESOLVED
        assertThat(facts.calldata().status()).isEqualTo(io.chainlens.model.CalldataAnalysis.Status.RESOLVED);
        assertThat(facts.calldata().resolvedSignature()).isEqualTo("transfer(address,uint256)");

        // 合约代码：在交易所在区块查询（0x6080604052 → 10 hex 字符 = 5 字节）
        ContractCodeInfo code = facts.contractCode();
        assertThat(code.available()).isTrue();
        assertThat(code.hasCode()).isTrue();
        assertThat(code.codeSize()).isEqualTo(BigInteger.valueOf(5));
        assertThat(code.queriedAtBlock()).isEqualTo(new BigInteger("68943"));

        exportDemoJson(facts);
    }

    @Test
    @DisplayName("EIP-1559 交易（type 0x2）用 effectiveGasPrice 计算 gas 成本")
    void eip1559GasCostUsesEffectiveGasPrice() throws IOException {
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0xaa36a7\"}")));
        stubResult("eth_getTransactionByHash", "tx-1559.json");
        // 复用 legacy 回执（gasUsed=0x5208，effectiveGasPrice=0x3b9aca00）——1559 交易的
        // 关键差异在 tx.gasPrice 缺失，gas 成本必须来自 effectiveGasPrice
        stubResult("eth_getTransactionReceipt", "receipt.json");
        stubResult("eth_getBlockByNumber", "block.json");
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getCode")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0x\"}")));

        TxFacts facts = assembler.assemble("0xabc0000000000000000000000000000000000000000000000000000000000002");

        assertThat(facts.transaction().type()).isEqualTo(2);
        assertThat(facts.transaction().gasPrice()).isNull();
        assertThat(facts.gasCostWei()).isEqualTo(new BigInteger("21000000000000"));
        // value=0、失败? status=0x1 成功，value=0 → toNet=0
        assertThat(facts.toNetWei()).isZero();
        // to 地址无代码：available=true, hasCode=false
        assertThat(facts.contractCode().hasCode()).isFalse();
        assertThat(facts.contractCode().available()).isTrue();
    }

    @Test
    @DisplayName("节点不支持历史 eth_getCode → 如实记录不可查，分析不中断")
    void historicalCodeUnavailableDoesNotFail() throws IOException {
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0xaa36a7\"}")));
        stubResult("eth_getTransactionByHash", "tx-legacy.json");
        stubResult("eth_getTransactionReceipt", "receipt.json");
        stubResult("eth_getBlockByNumber", "block.json");
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getCode")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"error\":{\"code\":-32000,\"message\":\"header not found\"}}")));

        TxFacts facts = assembler.assemble(TX_HASH);

        assertThat(facts.contractCode().available()).isFalse();
        assertThat(facts.contractCode().unavailableReason()).contains("历史合约代码查询失败")
                .doesNotContain("header not found");
        assertThat(facts.receipt().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("交易不存在 → TransactionNotFoundException 直接上抛（不产出半截案卷）")
    void transactionNotFoundPropagates() {
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_chainId")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":\"0xaa36a7\"}")));
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getTransactionByHash")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withTransformers("response-template")
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":{{jsonPath request.body '$.id'}},\"result\":null}")));

        assertThatThrownBy(() -> assembler.assemble(TX_HASH))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    /** M1 演示证据：把解析产物导出为 JSON 文件供人工查验。 */
    private static void exportDemoJson(TxFacts facts) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Path out = Path.of("target/m1-demo-txfacts.json");
        Files.writeString(out, mapper.writeValueAsString(facts));
    }
}
