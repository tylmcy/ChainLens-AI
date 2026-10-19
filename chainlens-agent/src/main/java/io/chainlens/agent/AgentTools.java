package io.chainlens.agent;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.chain.parse.SelectorRegistry;
import io.chainlens.chain.rpc.EthRpcClient;
import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;

/**
 * Agent 工具集（设计文档 4.2 的 6 个只读工具）：
 * getCaseFacts / getRiskFindings 是"案卷工具"（返回确定性结果，LLM 只能引用）；
 * getContractCode / getBlock / resolveSelector 是补充取证；
 * searchKnowledge 是知识检索（M5 空实现，M7 接 RAG）。
 *
 * <p>所有工具都会记录 {@link io.chainlens.model.ToolCallStep} 并通知进度监听器；
 * 没有任何"发交易"类工具——以不存在代替禁止（设计文档 9.4）。
 */
public final class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);
    // TxFacts/Evidence 含 Instant；显式序列化为 ISO-8601，避免工具结果因时间字段失败而退化成 {}。
    private static final ObjectMapper MAPPER = toolObjectMapper();

    private final TxFacts facts;
    private final List<Finding> findings;
    private final EthRpcClient rpcClient;
    private final SelectorRegistry selectorRegistry;
    private final KnowledgeSearch knowledgeSearch;
    private final AnalysisProgressListener listener;
    private final MeterRegistry meterRegistry;
    private final Set<String> returnedKnowledgeSources = new LinkedHashSet<>();
    private boolean knowledgeSearchAttempted;
    private String knowledgeUnavailableReason = "";

    public AgentTools(TxFacts facts,
                      List<Finding> findings,
                      EthRpcClient rpcClient,
                      SelectorRegistry selectorRegistry,
                      KnowledgeSearch knowledgeSearch,
                      AnalysisProgressListener listener,
                      MeterRegistry meterRegistry) {
        this.facts = facts;
        this.findings = findings;
        this.rpcClient = rpcClient;
        this.selectorRegistry = selectorRegistry;
        this.knowledgeSearch = knowledgeSearch;
        this.listener = listener;
        this.meterRegistry = meterRegistry;
    }

    @Tool(description = "获取已完成的链上取证案卷：交易、回执、区块、ETH 变化、ERC-20 事件与 calldata 解析。所有事实均来自 Ethereum JSON-RPC。")
    public String getCaseFacts() {
        return timed("getCaseFacts", "{}", () -> json(facts));
    }

    @Tool(description = "获取确定性风险规则引擎的命中结果（ruleId/severity/confidence/evidence），这些字段由机器定案，不得改动。")
    public String getRiskFindings() {
        return timed("getRiskFindings", "{}", () -> json(findings));
    }

    @Tool(description = "查询地址在交易发生区块的合约代码（eth_getCode 带区块号），用于判断交互对象是否为合约。")
    public String getContractCode(String address) {
        return timed("getContractCode", nullableArgument("address", address), () -> {
            if (address == null || address.isBlank()) {
                throw new IllegalArgumentException("缺少地址参数");
            }
            String code = rpcClient.getCode(address, facts.receipt().blockNumber()).asText();
            boolean hasCode = code != null && code.length() > 2;
            return json(Map.of(
                    "address", address,
                    "hasCode", hasCode,
                    "codeLengthBytes", hasCode ? (code.length() - 2) / 2 : 0,
                    "queriedAtBlock", facts.receipt().blockNumber().toString()));
        });
    }

    @Tool(description = "查询当前分析交易所在区块的上下文信息（区块号、时间戳、baseFee）。")
    public String getBlock() {
        return timed("getBlock", "{}", () -> {
            // 伦敦升级前的历史区块没有 baseFee；保留 JSON null 表示“不适用”。
            Map<String, String> block = new LinkedHashMap<>();
            block.put("number", facts.block().number().toString());
            block.put("timestamp", Long.toString(facts.block().timestamp()));
            block.put("baseFeePerGas", facts.block().baseFeePerGas() == null
                    ? null : facts.block().baseFeePerGas().toString());
            return json(block);
        });
    }

    @Tool(description = "解析 calldata 的 4-byte selector，返回候选函数签名列表。注意：selector 存在碰撞，可能无法唯一确定函数。")
    public String resolveSelector(String selector) {
        return timed("resolveSelector", nullableArgument("selector", selector), () -> {
            if (selector == null || selector.isBlank()) {
                throw new IllegalArgumentException("缺少 selector 参数");
            }
            return json(Map.of("selector", selector, "candidates", selectorRegistry.candidates(selector)));
        });
    }

    @Tool(description = "检索知识库（规则解释、Ethereum 术语、安全提示）。查询应包含已命中的 ruleId 或具体风险名称，不要使用“风险分析”等泛化词。知识库只提供解释性知识，不提供链上事实。")
    public String searchKnowledge(String query) {
        return timed("searchKnowledge", nullableArgument("query", query), () -> {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("缺少检索词");
            }
            knowledgeSearchAttempted = true;
            KnowledgeSearch.SearchResult result = knowledgeSearch.search(query);
            result.hits().forEach(hit -> returnedKnowledgeSources.add(hit.source()));
            knowledgeUnavailableReason = result.available() ? "" : result.reason();
            return json(Map.of(
                    "available", result.available(),
                    "status", result.status(),
                    "reason", result.reason(),
                    "hits", result.hits()));
        });
    }

    /** 本次工具调用真正返回过的知识来源，供最终报告白名单校验。 */
    Set<String> returnedKnowledgeSources() {
        return Set.copyOf(returnedKnowledgeSources);
    }

    /** 搜索尝试过但不可用时返回原因；未尝试或可用时为空。 */
    String knowledgeUnavailableReason() {
        return knowledgeSearchAttempted ? knowledgeUnavailableReason : "";
    }

    /** 记录工具调用步骤并转发进度事件（设计文档 4.6：工具调用成功率指标）。 */
    private String timed(String toolName, String arguments, java.util.function.Supplier<String> action) {
        Instant startedAt = Instant.now();
        listener.onToolStarted(toolName);
        boolean success = false;
        try {
            String result = action.get();
            success = true;
            long duration = java.time.Duration.between(startedAt, Instant.now()).toMillis();
            String summary = result.length() > 120 ? result.substring(0, 120) + "…" : result;
            ToolStepRecorder.record(toolName, arguments, startedAt, duration, true, summary);
            listener.onToolCompleted(toolName, true, duration, summary);
            return result;
        } catch (RuntimeException e) {
            long duration = java.time.Duration.between(startedAt, Instant.now()).toMillis();
            ToolStepRecorder.record(toolName, arguments, startedAt, duration, false, e.getMessage());
            listener.onToolCompleted(toolName, false, duration, e.getMessage());
            log.warn("工具调用失败 {}: {}", toolName, e.getMessage());
            // 异常消息可能为空，也可能包含节点细节；工具结果只暴露稳定的异常类型。
            return json(Map.of("error", e.getClass().getSimpleName()));
        } finally {
            recordMetrics(toolName, success);
        }
    }

    private void recordMetrics(String toolName, boolean success) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("chainlens.tool.calls", "tool", toolName, "status", success ? "success" : "failure")
                .increment();
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("工具结果序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /** 模型可能省略工具参数；序列化 null 以便失败步骤也能被追踪。 */
    private static String nullableArgument(String name, String value) {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(name, value);
        return json(arguments);
    }

    private static ObjectMapper toolObjectMapper() {
        SimpleModule timeModule = new SimpleModule();
        timeModule.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(Instant value, JsonGenerator generator, SerializerProvider serializers)
                    throws IOException {
                generator.writeString(value.toString());
            }
        });
        return new ObjectMapper().registerModule(timeModule);
    }
}
