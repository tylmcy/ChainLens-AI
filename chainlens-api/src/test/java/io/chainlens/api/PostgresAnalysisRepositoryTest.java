package io.chainlens.api;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.CalldataAnalysis;
import io.chainlens.model.ContractCodeInfo;
import io.chainlens.model.EthBlock;
import io.chainlens.model.EthReceipt;
import io.chainlens.model.EthTransaction;
import io.chainlens.model.Network;
import io.chainlens.model.TxFacts;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库集成测试（Testcontainers 真 PostgreSQL）：
 * 分析结果 JSONB 全量落库与读取回环（设计文档 5.6）。
 *
 * <p>默认随构建运行，需要 Docker 守护进程 + 镜像仓库可达；
 * 单独运行本测试：mvn verify -Dgroups=db。
 */
@Testcontainers
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.sql.init.mode=always")
@Tag("db")
class PostgresAnalysisRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcClient jdbcClient;

    private static PostgresAnalysisRepository repository(JdbcClient jdbcClient) {
        ObjectMapper mapper = JsonMapper.builder().build();
        return new PostgresAnalysisRepository(jdbcClient, mapper);
    }

    private static AnalysisResult sampleResult() {
        EthTransaction tx = new EthTransaction(
                "0xabc0000000000000000000000000000000000000000000000000000000000001",
                "0x1111111111111111111111111111111111111111",
                "0x3333333333333333333333333333333333333333",
                BigInteger.ONE, BigInteger.valueOf(1_000_000_000L), null, null,
                BigInteger.valueOf(21000), BigInteger.ZERO, 0, "0x", BigInteger.ONE,
                "{\"hash\":\"0xabc0\"}");
        EthReceipt receipt = new EthReceipt(
                "0xabc0000000000000000000000000000000000000000000000000000000000001",
                "0x1", BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L),
                null, List.of(), BigInteger.ONE, "{\"status\":\"0x1\"}");
        TxFacts facts = new TxFacts(
                Network.ANVIL, tx, receipt,
                new EthBlock(BigInteger.ONE, 1700000000L, null, "{\"number\":\"0x1\"}"),
                BigInteger.valueOf(21_000_000_000_000L), BigInteger.ONE,
                BigInteger.valueOf(-21_000_000_000_001L), BigInteger.ONE,
                List.of(), List.of(), List.of(),
                new CalldataAnalysis(false, null, List.of(), null, CalldataAnalysis.Status.UNRESOLVED),
                ContractCodeInfo.unavailable("0x3333333333333333333333333333333333333333", BigInteger.ONE, "测试"),
                Instant.EPOCH);
        return new AnalysisResult(
                "id-roundtrip", facts.transaction().hash(), Network.ANVIL,
                new AnalysisReport("摘要", AnalysisStatus.SUCCESS, List.of(), List.of(), List.of(),
                        List.of("免责声明")),
                facts, List.of(), List.of(), false, true, false, 42L, Instant.EPOCH);
    }

    @Test
    @DisplayName("保存 → 读取回环：JSONB 全字段一致（含 rpc_payloads 证据列）")
    void saveAndLoadRoundTrip() {
        PostgresAnalysisRepository repository = repository(jdbcClient);
        AnalysisResult original = sampleResult();

        repository.save(original);
        AnalysisResult loaded = repository.findById("id-roundtrip").orElseThrow();

        assertThat(loaded.analysisId()).isEqualTo("id-roundtrip");
        assertThat(loaded.txHash()).isEqualTo(original.txHash());
        assertThat(loaded.network()).isEqualTo(Network.ANVIL);
        assertThat(loaded.report().status()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(loaded.degraded()).isTrue();
        assertThat(loaded.persisted()).isTrue();
        assertThat(loaded.totalDurationMs()).isEqualTo(42L);
        assertThat(loaded.facts().transaction().hash()).isEqualTo(original.facts().transaction().hash());

        // rpc_payloads 证据列确实写入（含 result 节点）
        String payload = jdbcClient.sql("SELECT rpc_payloads::text FROM analyses WHERE id = 'id-roundtrip'")
                .query(String.class).single();
        assertThat(payload).contains("eth_getTransactionByHash", "eth_getTransactionReceipt", "result");

        assertThat(repository.findById("missing")).isEmpty();
    }
}
