package io.chainlens.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.chain.TxFactsProvider;
import io.chainlens.chain.parse.SelectorRegistry;
import io.chainlens.chain.rpc.EthRpcClient;
import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.RuleEngine;

/**
 * Agentic 分析服务（M5，设计文档 4.1 阶段 B）：
 * 阶段 A（确定性取证 + 规则）之后，由 LLM 手动工具调用循环收集证据并生成报告。
 *
 * <p>四道护栏（设计文档 4.4）：
 * <ol>
 *   <li>强制取证：阶段 A 与 LLM 无关，永远完整；</li>
 *   <li>上下文装配：若 LLM 未调用 getCaseFacts/getRiskFindings，自动注入工具结果再要求生成；</li>
 *   <li>事实一致性校验：ruleId 覆盖 + 哈希/长数字原文比对 + 免责声明首条；</li>
 *   <li>Schema 校验 + 重试 1 次 → 失败降级为模板报告（degraded=true）。</li>
 * </ol>
 *
 * <p>手动循环（最多 6 轮）而非框架内部执行，是为了精确控制轮数上限并记录
 * 每一步 {@link io.chainlens.model.ToolCallStep}。业务代码只依赖 {@link ChatModel}
 * 接口，不 import 任何 Provider 类（设计文档 4.5 / 12.2）。
 */
public final class AgenticAnalysisService implements AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AgenticAnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String PROMPT_RESOURCE = "prompts/system-report.md";
    static final int MAX_TOOL_ROUNDS = 6;
    static final int MAX_REPORT_ATTEMPTS = 2;

    private final TxFactsProvider factsProvider;
    private final RuleEngine ruleEngine;
    private final ChatModel chatModel;
    private final EthRpcClient rpcClient;
    private final SelectorRegistry selectorRegistry;
    private final KnowledgeSearch knowledgeSearch;
    private final ReportGenerator fallbackGenerator;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final String systemPrompt;

    public AgenticAnalysisService(TxFactsProvider factsProvider,
                                  RuleEngine ruleEngine,
                                  ChatModel chatModel,
                                  EthRpcClient rpcClient,
                                  SelectorRegistry selectorRegistry,
                                  KnowledgeSearch knowledgeSearch,
                                  ReportGenerator fallbackGenerator,
                                  io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.factsProvider = factsProvider;
        this.ruleEngine = ruleEngine;
        this.chatModel = chatModel;
        this.rpcClient = rpcClient;
        this.selectorRegistry = selectorRegistry;
        this.knowledgeSearch = knowledgeSearch;
        this.fallbackGenerator = fallbackGenerator;
        this.meterRegistry = meterRegistry;
        this.systemPrompt = loadSystemPrompt();
    }

    @Override
    public AnalysisResult analyze(String txHash, AnalysisProgressListener listener) {
        long start = System.currentTimeMillis();
        ToolStepRecorder.clear();
        try {
            // ---- 阶段 A：强制取证 + 规则（确定性，与 LLM 无关） ----
            listener.onStage("forensics", "开始链上取证与解析（交易/回执/区块/代码）");
            TxFacts facts = factsProvider.assemble(txHash, (rpcNanos, parsingNanos) -> {
                listener.onSegmentTiming("rpc", rpcNanos);
                listener.onSegmentTiming("parsing", parsingNanos);
            });

            listener.onStage("rules", "执行确定性风险规则");
            long rulesStarted = System.nanoTime();
            List<Finding> findings;
            try {
                findings = ruleEngine.evaluate(facts);
            } finally {
                listener.onSegmentTiming("rules", System.nanoTime() - rulesStarted);
            }
            listener.onRulesCompleted(findings);

            // ---- 阶段 B：LLM 工具调用循环 ----
            listener.onStage("agent", "LLM 规划并调用分析工具");
            AgentTools tools = new AgentTools(facts, findings, rpcClient, selectorRegistry,
                    knowledgeSearch, listener, meterRegistry);
            List<ToolCallback> callbacks = List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(tools).build().getToolCallbacks());
            ToolCallingChatOptions toolOptions = compatibleToolOptions(callbacks);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.add(new UserMessage("请分析交易 " + txHash + "：先通过工具收集证据，再生成中文风险报告。"));

            Set<String> calledTools = new HashSet<>();
            String finalText = null;
            for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
                AssistantMessage assistant = callModel(new Prompt(messages, toolOptions), listener)
                        .getResult().getOutput();
                messages.add(assistant);
                if (assistant.hasToolCalls()) {
                    List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                    for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                        calledTools.add(call.name());
                        String result = executeTool(callbacks, call);
                        responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
                    }
                    messages.add(ToolResponseMessage.builder().responses(responses).build());
                } else {
                    finalText = assistant.getText();
                    break;
                }
            }

            // ---- 护栏 ②：上下文装配（缺案卷工具 → 自动注入） ----
            if (!calledTools.contains("getCaseFacts") || !calledTools.contains("getRiskFindings")) {
                List<ToolResponseMessage.ToolResponse> injected = new ArrayList<>();
                if (!calledTools.contains("getCaseFacts")) {
                    injected.add(new ToolResponseMessage.ToolResponse(
                            "guardrail-case-facts", "getCaseFacts", tools.getCaseFacts()));
                }
                if (!calledTools.contains("getRiskFindings")) {
                    injected.add(new ToolResponseMessage.ToolResponse(
                            "guardrail-risk-findings", "getRiskFindings", tools.getRiskFindings()));
                }
                messages.add(ToolResponseMessage.builder().responses(injected).build());
                messages.add(new UserMessage("系统已自动补充上述案卷与规则数据（强制证据），请基于它们生成最终报告。"));
                finalText = null;
            }

            // ---- 报告生成：校验 + 重试 + 降级（护栏 ③④） ----
            boolean degraded = false;
            AnalysisReport report = null;
            messages.add(new UserMessage("请输出最终报告 JSON（只输出 JSON，不要其他内容）。"));
            if (finalText != null) {
                // 模型在工具循环末尾有时已经输出合法报告，先复用它以减少一次调用。
                ReportAssembly initial = assembleReport(finalText, facts, findings,
                        tools.returnedKnowledgeSources(), tools.knowledgeUnavailableReason());
                if (initial.report() != null && initial.violations().isEmpty()) {
                    report = initial.report();
                } else {
                    log.warn("LLM 工具循环结束语不是合法报告: {}", initial.violations());
                    messages.add(new UserMessage("你的过渡回复不是最终报告："
                            + String.join("；", initial.violations()) + "。现在请输出完整 JSON。"));
                }
            }
            for (int attempt = 1; report == null && attempt <= MAX_REPORT_ATTEMPTS; attempt++) {
                String candidate = textAnswer(messages, toolOptions, listener);
                ReportAssembly assembly = assembleReport(candidate, facts, findings,
                        tools.returnedKnowledgeSources(), tools.knowledgeUnavailableReason());
                if (assembly.report() != null && assembly.violations().isEmpty()) {
                    report = assembly.report();
                    break;
                }
                // 只记录校验原因，不记录完整模型输出，避免把案卷数据大量写入日志。
                log.warn("LLM 报告第 {} 次校验未通过: {}", attempt, assembly.violations());
                String feedback = assembly.report() == null
                        ? assembly.violations().isEmpty() ? "输出不是合法 JSON" : assembly.violations().get(0)
                        : String.join("；", assembly.violations());
                messages.add(new UserMessage("你的上一条输出未通过校验：" + feedback + "。请修正后重新输出完整 JSON。"));
            }
            if (report == null) {
                log.warn("LLM 报告生成未通过校验，降级为模板报告");
                report = fallbackGenerator.generate(facts, findings);
                degraded = true;
            }
            listener.onReportCompleted(report);

            AnalysisResult result = new AnalysisResult(
                    UUID.randomUUID().toString(),
                    txHash,
                    facts.network(),
                    report,
                    facts,
                    findings,
                    ToolStepRecorder.snapshot(),
                    true,    // llmUsed
                    degraded,
                    false,   // persisted（api 层落库后置 true）
                    System.currentTimeMillis() - start,
                    Instant.now()
            );
            listener.onAnalysisCompleted(result);
            return result;
        } catch (RuntimeException e) {
            listener.onAnalysisFailed(e);
            throw e;
        } finally {
            ToolStepRecorder.clear();
        }
    }

    /** 执行单个工具调用（失败记录为失败步骤，不中断会话）。 */
    private static String executeTool(List<ToolCallback> callbacks, AssistantMessage.ToolCall call) {
        for (ToolCallback callback : callbacks) {
            if (callback.getToolDefinition().name().equals(call.name())) {
                try {
                    return callback.call(call.arguments());
                } catch (RuntimeException e) {
                    // 只记录工具名与异常类别；模型参数可能含用户交易数据，不写入日志。
                    Throwable cause = e.getCause();
                    log.warn("工具回调异常 {}: {} / {}", call.name(), e.getClass().getSimpleName(),
                            cause == null ? "无底层异常" : cause.getClass().getSimpleName());
                    throw e;
                }
            }
        }
        return "{\"error\":\"未知工具: " + call.name() + "\"}";
    }

    private String textAnswer(List<Message> messages, ToolCallingChatOptions toolOptions,
                              AnalysisProgressListener listener) {
        ChatResponse response = callModel(new Prompt(messages, toolOptions), listener);
        return response.getResult().getOutput().getText();
    }

    /** 对每次模型往返采样，包括工具循环与报告重试；缺失 usage 不推测。 */
    private ChatResponse callModel(Prompt prompt, AnalysisProgressListener listener) {
        listener.beforeModelCall(prompt.getOptions() == null ? null : prompt.getOptions().getMaxTokens());
        long started = System.nanoTime();
        try {
            ChatResponse response = chatModel.call(prompt);
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            // Spring AI 用 EmptyUsage 的 0/0 占位；它不代表 provider 真正返回零 token。
            if (usage instanceof EmptyUsage) {
                usage = null;
            }
            listener.onModelUsage(usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    usage == null ? null : usage.getTotalTokens());
            return response;
        } catch (RuntimeException e) {
            listener.onModelFailure(e.getClass().getSimpleName());
            throw e;
        } finally {
            listener.onSegmentTiming("llm", System.nanoTime() - started);
        }
    }

    /**
     * 在模型自带的默认选项上追加工具。
     *
     * <p>Spring AI 2.0 的部分 Provider（例如 DeepSeek）会把请求选项转回自己的
     * Provider 类型。直接新建通用 {@code DefaultToolCallingChatOptions} 会在运行时触发
     * {@code ClassCastException}。通过 {@link ChatModel#getOptions()} 的 {@code mutate()}
     * 保留具体 Provider 类型，同时让业务代码仍只依赖 Spring AI 的通用接口。
     */
    private ToolCallingChatOptions compatibleToolOptions(List<ToolCallback> callbacks) {
        ChatOptions defaultOptions = chatModel.getOptions();
        if (defaultOptions instanceof ToolCallingChatOptions providerOptions) {
            return providerOptions.mutate().toolCallbacks(callbacks).build();
        }
        return ToolCallingChatOptions.builder().toolCallbacks(callbacks).build();
    }

    /** LLM JSON → 合并机器字段 → 校验（返回违规列表）。 */
    private ReportAssembly assembleReport(String candidate,
                                          TxFacts facts,
                                          List<Finding> rulesFindings,
                                          Set<String> allowedKnowledgeSources,
                                          String knowledgeUnavailableReason) {
        List<String> violations = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(extractJson(candidate));
            if (root == null || !root.isObject()) {
                return new ReportAssembly(null, List.of("输出不是 JSON 对象"));
            }
            String summary = root.path("summary").asText("");
            if (summary.isBlank()) {
                violations.add("summary 缺失或为空");
            }
            List<String> actions = new ArrayList<>();
            root.path("actions").forEach(a -> actions.add(a.asText()));
            List<String> limitations = new ArrayList<>();
            root.path("limitations").forEach(l -> limitations.add(l.asText()));
            if (knowledgeUnavailableReason != null && !knowledgeUnavailableReason.isBlank()) {
                limitations.add("知识增强未使用：" + knowledgeUnavailableReason);
            }

            // LLM 的 findings 必须覆盖每个 ruleId（多/少/重复都违规）
            Map<String, String[]> llmFindings = new HashMap<>();
            root.path("findings").forEach(f -> {
                String ruleId = f.path("ruleId").asText("");
                String title = f.path("title").asText("");
                String description = f.path("description").asText("");
                if (!ruleId.isBlank() && llmFindings.putIfAbsent(ruleId, new String[]{title, description}) != null) {
                    violations.add("findings 中 ruleId 重复：" + ruleId);
                }
            });
            List<String> expected = rulesFindings.stream().map(Finding::ruleId).sorted().toList();
            List<String> actual = llmFindings.keySet().stream().sorted().toList();
            if (!expected.equals(actual)) {
                violations.add("findings.ruleId 覆盖不一致：期望 " + expected + "，实际 " + actual);
            }

            // 机器字段由规则引擎定案；LLM 只提供 title/description 措辞
            List<Finding> merged = new ArrayList<>();
            for (Finding ruleFinding : rulesFindings) {
                String[] llmText = llmFindings.get(ruleFinding.ruleId());
                String title = (llmText != null && !llmText[0].isBlank()) ? llmText[0] : ruleFinding.title();
                String description = (llmText != null && !llmText[1].isBlank()) ? llmText[1] : ruleFinding.description();
                merged.add(new Finding(ruleFinding.ruleId(), ruleFinding.severity(), ruleFinding.confidence(),
                        title, description, ruleFinding.evidence()));
            }

            List<AnalysisReport.Source> sources = new ArrayList<>(List.of(
                    new AnalysisReport.Source("CHAIN", "交易", "eth_getTransactionByHash / " + facts.transaction().hash()),
                    new AnalysisReport.Source("CHAIN", "回执", "eth_getTransactionReceipt / " + facts.transaction().hash()),
                    new AnalysisReport.Source("CHAIN", "区块", "eth_getBlockByNumber / " + facts.receipt().blockNumber())
            ));
            // CHAIN 来源由机器固定生成；LLM 只能从本次 searchKnowledge 真正返回过的白名单中选 KNOWLEDGE。
            root.path("sources").forEach(source -> {
                String type = source.path("type").asText("");
                String reference = source.path("reference").asText("");
                if ("KNOWLEDGE".equals(type)) {
                    if (!allowedKnowledgeSources.contains(reference)) {
                        violations.add("引用了工具未返回的知识来源：" + reference);
                    } else {
                        String label = source.path("label").asText("知识库");
                        AnalysisReport.Source approved = new AnalysisReport.Source("KNOWLEDGE", label, reference);
                        if (!sources.contains(approved)) {
                            sources.add(approved);
                        }
                    }
                } else if (!type.isBlank() && !"CHAIN".equals(type)) {
                    violations.add("未知来源类型：" + type);
                }
            });

            AnalysisReport report = new AnalysisReport(
                    summary,
                    facts.receipt().isSuccess() ? AnalysisStatus.SUCCESS : AnalysisStatus.FAILED,
                    actions,
                    merged,
                    sources,
                    limitations
            );
            violations.addAll(FactConsistencyValidator.validate(report, facts, rulesFindings));
            return new ReportAssembly(report, violations);
        } catch (Exception e) {
            return new ReportAssembly(null, List.of("JSON 解析失败：" + e.getMessage()));
        }
    }

    /** 从模型输出中提取 JSON（容忍模型偶尔包裹 ```json 代码块）。 */
    private static String extractJson(String text) {
        String trimmed = text == null ? "" : text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static String loadSystemPrompt() {
        try (InputStream in = AgenticAnalysisService.class.getClassLoader().getResourceAsStream(PROMPT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("系统提示词资源缺失: " + PROMPT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("系统提示词加载失败: " + PROMPT_RESOURCE, e);
        }
    }

    private record ReportAssembly(AnalysisReport report, List<String> violations) {
    }
}
