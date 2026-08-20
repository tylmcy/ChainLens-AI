package io.chainlens.api;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.Finding;
import io.chainlens.model.Network;
import io.chainlens.model.ToolCallStep;
import io.chainlens.model.TxFacts;

/**
 * PostgreSQL 实现：核心数据 JSONB（设计文档 5.6），查询列建索引。
 * 原始 RPC 响应组装进 rpc_payloads 列（证据回溯）。
 */
@Repository
public class PostgresAnalysisRepository implements AnalysisRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public PostgresAnalysisRepository(JdbcClient jdbcClient, ObjectMapper mapper) {
        this.jdbc = jdbcClient;
        this.mapper = mapper;
    }

    @Override
    public void save(AnalysisResult result) {
        try {
            jdbc.sql("""
                            INSERT INTO analyses (
                                id, tx_hash, chain_id, network, status,
                                facts, findings, report, tool_steps, rpc_payloads, metrics,
                                llm_used, degraded, created_at)
                            VALUES (
                                :id, :txHash, :chainId, :network, :status,
                                CAST(:facts AS jsonb), CAST(:findings AS jsonb), CAST(:report AS jsonb),
                                CAST(:toolSteps AS jsonb), CAST(:rpcPayloads AS jsonb), CAST(:metrics AS jsonb),
                                :llmUsed, :degraded, :createdAt)
                            """)
                    .param("id", result.analysisId())
                    .param("txHash", result.txHash())
                    .param("chainId", result.network().chainId())
                    .param("network", result.network().label())
                    .param("status", result.report().status().name())
                    .param("facts", json(result.facts()))
                    .param("findings", json(result.findings()))
                    .param("report", json(result.report()))
                    .param("toolSteps", json(result.toolSteps()))
                    .param("rpcPayloads", rpcPayloads(result.facts()).toString())
                    .param("metrics", json(Map.of("totalDurationMs", result.totalDurationMs())))
                    .param("llmUsed", result.llmUsed())
                    .param("degraded", result.degraded())
                    .param("createdAt", Timestamp.from(result.createdAt()))
                    .update();
        } catch (RuntimeException e) {
            // 数据库写入失败：结果仍可返回（persisted=false），此处抛出交由编排层捕获
            throw e;
        }
    }

    @Override
    public Optional<AnalysisResult> findById(String analysisId) {
        var rows = jdbc.sql("""
                        SELECT tx_hash, chain_id, network, facts, findings, report, tool_steps,
                               llm_used, degraded, metrics, created_at
                        FROM analyses WHERE id = :id
                        """)
                .param("id", analysisId)
                .query((rs, rowNum) -> {
                    try {
                        return new AnalysisResult(
                                analysisId,
                                rs.getString("tx_hash"),
                                Network.fromChainId(rs.getLong("chain_id")),
                                mapper.readValue(rs.getString("report"), AnalysisReport.class),
                                mapper.readValue(rs.getString("facts"), TxFacts.class),
                                mapper.readValue(rs.getString("findings"),
                                        mapper.getTypeFactory().constructCollectionType(List.class, Finding.class)),
                                mapper.readValue(rs.getString("tool_steps"),
                                        mapper.getTypeFactory().constructCollectionType(List.class, ToolCallStep.class)),
                                rs.getBoolean("llm_used"),
                                rs.getBoolean("degraded"),
                                true, // 从库中读出即视为已持久化
                                mapper.readTree(rs.getString("metrics")).path("totalDurationMs").asLong(0L),
                                rs.getTimestamp("created_at").toInstant()
                        );
                    } catch (JacksonException e) {
                        throw new IllegalStateException("历史分析数据反序列化失败: " + analysisId, e);
                    }
                })
                .list();
        return rows.stream().findFirst();
    }

    /** 从案卷内嵌的原始 JSON 组装 rpc_payloads（证据回溯，设计文档 5.4）。 */
    private ObjectNode rpcPayloads(TxFacts facts) {
        ObjectNode root = mapper.createObjectNode();
        putPayload(root, "eth_getTransactionByHash", facts.transaction().rawJson());
        putPayload(root, "eth_getTransactionReceipt", facts.receipt().rawJson());
        putPayload(root, "eth_getBlockByNumber", facts.block().rawJson());
        return root;
    }

    private void putPayload(ObjectNode root, String method, String rawJson) {
        ObjectNode wrapped = mapper.createObjectNode();
        try {
            JsonNode parsed = mapper.readTree(rawJson);
            wrapped.set("result", parsed);
        } catch (JacksonException e) {
            wrapped.put("raw", "unparseable");
        }
        root.set(method, wrapped);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("分析结果序列化失败", e);
        }
    }
}
