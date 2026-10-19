package io.chainlens.mcp;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import io.chainlens.agent.AnalysisProgressListener;
import io.chainlens.agent.AnalysisService;
import io.chainlens.chain.TxFactsProvider;
import io.chainlens.chain.parse.SelectorRegistry;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.RuleEngine;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 面向外部 MCP 客户端的五个无状态只读工具。
 *
 * <p>与内部 {@code AgentTools} 不同，这里每次都显式接收 txHash，绝不保存“当前交易”；
 * 因而多个 Claude/Cursor 会话并发调用时不会串案。
 */
public final class ChainLensMcpTools {

    private final AnalysisService analysisService;
    private final TxFactsProvider factsProvider;
    private final RuleEngine ruleEngine;
    private final SelectorRegistry selectorRegistry;
    private final KnowledgeSearch knowledgeSearch;
    private final Semaphore analysisSemaphore;
    private final MeterRegistry meterRegistry;
    private final String transport;

    public ChainLensMcpTools(AnalysisService analysisService,
                             TxFactsProvider factsProvider,
                             RuleEngine ruleEngine,
                             SelectorRegistry selectorRegistry,
                             KnowledgeSearch knowledgeSearch,
                             Semaphore analysisSemaphore,
                             MeterRegistry meterRegistry,
                             String transport) {
        this.analysisService = analysisService;
        this.factsProvider = factsProvider;
        this.ruleEngine = ruleEngine;
        this.selectorRegistry = selectorRegistry;
        this.knowledgeSearch = knowledgeSearch;
        this.analysisSemaphore = analysisSemaphore;
        this.meterRegistry = meterRegistry;
        this.transport = transport == null || transport.isBlank() ? "streamable-http" : transport;
    }

    @McpTool(
            name = "analyzeTransaction",
            title = "完整分析 Ethereum 交易",
            description = "对指定 Ethereum 交易执行 JSON-RPC 取证、确定性规则和结构化中文报告。链上事实不由模型生成，MCP 调用不会保存历史。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = true))
    public McpAnalysisResultView analyzeTransaction(
            @McpToolParam(description = "Ethereum 交易哈希：0x + 64 位十六进制", required = true)
            String txHash) {
        return observed("analyzeTransaction", () -> {
            String normalized = McpInputValidator.transactionHash(txHash);
            return withAnalysisPermit(() -> McpAnalysisResultView.from(
                    analysisService.analyze(normalized, AnalysisProgressListener.noop())));
        });
    }

    @McpTool(
            name = "getCaseFacts",
            title = "获取交易取证案卷",
            description = "查询 transaction、receipt、block、ETH 变化、ERC-20 事件和 calldata 解析。事实只来自配置的 Ethereum JSON-RPC。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = true))
    public McpTxFactsView getCaseFacts(
            @McpToolParam(description = "Ethereum 交易哈希：0x + 64 位十六进制", required = true)
            String txHash) {
        return observed("getCaseFacts", () -> {
            String normalized = McpInputValidator.transactionHash(txHash);
            return withAnalysisPermit(() -> McpTxFactsView.from(factsProvider.assemble(normalized)));
        });
    }

    @McpTool(
            name = "getRiskFindings",
            title = "执行确定性风险规则",
            description = "重新取得指定交易的链上事实并执行确定性规则，返回 ruleId、severity、confidence 和 evidence；不使用模型新增结论。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = true))
    public McpRiskFindings getRiskFindings(
            @McpToolParam(description = "Ethereum 交易哈希：0x + 64 位十六进制", required = true)
            String txHash) {
        return observed("getRiskFindings", () -> {
            String normalized = McpInputValidator.transactionHash(txHash);
            return withAnalysisPermit(() -> {
                TxFacts facts = factsProvider.assemble(normalized);
                return new McpRiskFindings(normalized, facts.network(),
                        ruleEngine.evaluate(facts).stream().map(McpFindingView::from).toList());
            });
        });
    }

    @McpTool(
            name = "resolveSelector",
            title = "解析 4-byte selector",
            description = "从本地精选登记表返回 4-byte selector 的全部候选函数签名；selector 可能碰撞，多个候选时不会武断选一。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public McpSelectorResolution resolveSelector(
            @McpToolParam(description = "带 0x 前缀的 4-byte selector", required = true)
            String selector) {
        return observed("resolveSelector", () -> {
            String normalized = McpInputValidator.selector(selector);
            List<String> candidates = selectorRegistry.candidates(normalized);
            McpSelectorResolution.Status status = candidates.isEmpty()
                    ? McpSelectorResolution.Status.UNRESOLVED
                    : candidates.size() == 1
                            ? McpSelectorResolution.Status.RESOLVED
                            : McpSelectorResolution.Status.AMBIGUOUS;
            return new McpSelectorResolution(normalized, status, candidates);
        });
    }

    @McpTool(
            name = "searchKnowledge",
            title = "检索 ChainLens 知识库",
            description = "检索受控规则解释、Ethereum 术语和安全提示。知识片段只能用于解释，不能证明当前交易事实或新增 finding。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public KnowledgeSearch.SearchResult searchKnowledge(
            @McpToolParam(description = "具体 ruleId、风险名称或 Ethereum 术语，最多 200 字符", required = true)
            String query) {
        return observed("searchKnowledge", () -> {
            String normalized = McpInputValidator.knowledgeQuery(query);
            try {
                return knowledgeSearch.search(normalized);
            } catch (ChainLensMcpException error) {
                throw error;
            } catch (RuntimeException error) {
                // 知识端口的正常关闭/未就绪由 SearchResult 表达；抛异常代表真正故障。
                throw McpErrorMapper.clientError(McpErrorCode.KNOWLEDGE_UNAVAILABLE,
                        "知识库检索暂时不可用，确定性交易分析仍可继续", true);
            }
        });
    }

    /** 三个交易工具与 REST/SSE 共用同一 permit 池，任何异常都必须释放。 */
    private <T> T withAnalysisPermit(Supplier<T> action) {
        if (!analysisSemaphore.tryAcquire()) {
            throw McpErrorMapper.clientError(McpErrorCode.TOO_MANY_REQUESTS,
                    "并发分析数已达上限，请稍后重试", true);
        }
        try {
            return action.get();
        } finally {
            analysisSemaphore.release();
        }
    }

    /** 统一记录低基数指标，并把所有下游异常收敛成安全的稳定错误。 */
    private <T> T observed(String toolName, Supplier<T> action) {
        long started = System.nanoTime();
        String status = "success";
        try {
            return action.get();
        } catch (RuntimeException error) {
            status = "failure";
            throw McpErrorMapper.map(error);
        } finally {
            if (meterRegistry != null) {
                meterRegistry.counter("chainlens.mcp.tool.calls",
                        "tool", toolName, "transport", transport, "status", status).increment();
                meterRegistry.timer("chainlens.mcp.tool.duration",
                        "tool", toolName, "transport", transport, "status", status)
                        .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            }
        }
    }

}
