package io.chainlens.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import io.chainlens.agent.knowledge.KnowledgeSearch;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TxFacts facts;
    private final List<Finding> findings;
    private final EthRpcClient rpcClient;
    private final SelectorRegistry selectorRegistry;
    private final KnowledgeSearch knowledgeSearch;
    private final AnalysisProgressListener listener;

    public AgentTools(TxFacts facts,
                      List<Finding> findings,
                      EthRpcClient rpcClient,
                      SelectorRegistry selectorRegistry,
                      KnowledgeSearch knowledgeSearch,
                      AnalysisProgressListener listener) {
        this.facts = facts;
        this.findings = findings;
        this.rpcClient = rpcClient;
        this.selectorRegistry = selectorRegistry;
        this.knowledgeSearch = knowledgeSearch;
        this.listener = listener;
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
        return timed("getContractCode", json(Map.of("address", address)), () -> {
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
        return timed("getBlock", "{}", () -> json(Map.of(
                "number", facts.block().number().toString(),
                "timestamp", Long.toString(facts.block().timestamp()),
                "baseFeePerGas", facts.block().baseFeePerGas() == null ? null : facts.block().baseFeePerGas().toString())));
    }

    @Tool(description = "解析 calldata 的 4-byte selector，返回候选函数签名列表。注意：selector 存在碰撞，可能无法唯一确定函数。")
    public String resolveSelector(String selector) {
        return timed("resolveSelector", json(Map.of("selector", selector)), () -> json(Map.of(
                "selector", selector,
                "candidates", selectorRegistry.candidates(selector))));
    }

    @Tool(description = "检索知识库（规则解释、Ethereum 术语、安全提示）。知识库只提供解释性知识，不提供链上事实。")
    public String searchKnowledge(String query) {
        return timed("searchKnowledge", json(Map.of("query", query)), () -> {
            List<KnowledgeSearch.KnowledgeHit> hits = knowledgeSearch.search(query);
            return json(Map.of(
                    "available", !hits.isEmpty(),
                    "hits", hits));
        });
    }

    /** 记录工具调用步骤并转发进度事件。 */
    private String timed(String toolName, String arguments, java.util.function.Supplier<String> action) {
        Instant startedAt = Instant.now();
        listener.onToolStarted(toolName);
        try {
            String result = action.get();
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
            return json(Map.of("error", e.getMessage()));
        }
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
