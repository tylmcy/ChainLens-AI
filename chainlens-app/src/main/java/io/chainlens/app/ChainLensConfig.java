package io.chainlens.app;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.chainlens.agent.AnalysisService;
import io.chainlens.agent.DeterministicAnalysisService;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.agent.ReportGenerator;
import io.chainlens.agent.TemplateReportGenerator;
import io.chainlens.api.TraceIdFilter;
import io.chainlens.chain.TxFactsAssembler;
import io.chainlens.chain.TxFactsProvider;
import io.chainlens.chain.parse.CuratedSelectorRegistry;
import io.chainlens.chain.parse.SelectorRegistry;
import io.chainlens.chain.rpc.EthRpcClient;
import io.chainlens.chain.rpc.HttpJsonRpcClient;
import io.chainlens.chain.rpc.RpcConfig;
import io.chainlens.rules.Rule;
import io.chainlens.rules.RuleEngine;
import io.chainlens.rules.risk.BuiltInRiskAddressList;
import io.chainlens.rules.rule.FailedTransactionRule;
import io.chainlens.rules.rule.InteractionWithEoaRule;
import io.chainlens.rules.rule.ManyTokenTransfersRule;
import io.chainlens.rules.rule.RiskAddressHitRule;
import io.chainlens.rules.rule.TransferToZeroAddressRule;
import io.chainlens.rules.rule.UnknownSelectorRule;
import io.chainlens.rules.rule.UnlimitedApprovalRule;
import io.chainlens.mcp.ChainLensMcpTools;
import io.chainlens.mcp.McpTokenFilter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 装配与启动配置：把 chain/rules/agent 的纯 Java 组件组装为 Spring Bean。
 * 依赖方向遵守设计文档 12.2：仅 app 层知晓全部模块。
 */
@Configuration
public class ChainLensConfig {

    // ---- 链访问层 ----

    @Bean
    RpcConfig rpcConfig(@Value("${chainlens.eth.rpc-url}") String rpcUrl) {
        return RpcConfig.defaults(rpcUrl);
    }

    @Bean
    EthRpcClient ethRpcClient(RpcConfig config) {
        return new HttpJsonRpcClient(config);
    }

    @Bean
    TxFactsProvider txFactsProvider(EthRpcClient client) {
        return new TxFactsAssembler(client);
    }

    @Bean
    SelectorRegistry selectorRegistry() {
        // MCP 与 Agent 共享同一份精选 selector 登记表，避免两套解析逻辑漂移。
        return new CuratedSelectorRegistry();
    }

    // ---- 规则引擎（Spring 自动收集全部 Rule bean 注入引擎） ----

    @Bean
    BuiltInRiskAddressList builtInRiskAddressList() {
        return BuiltInRiskAddressList.fromClasspath(BuiltInRiskAddressList.DEFAULT_RESOURCE);
    }

    @Bean
    UnlimitedApprovalRule unlimitedApprovalRule() {
        return new UnlimitedApprovalRule();
    }

    @Bean
    FailedTransactionRule failedTransactionRule() {
        return new FailedTransactionRule();
    }

    @Bean
    TransferToZeroAddressRule transferToZeroAddressRule() {
        return new TransferToZeroAddressRule();
    }

    @Bean
    InteractionWithEoaRule interactionWithEoaRule() {
        return new InteractionWithEoaRule();
    }

    @Bean
    UnknownSelectorRule unknownSelectorRule() {
        return new UnknownSelectorRule();
    }

    @Bean
    ManyTokenTransfersRule manyTokenTransfersRule() {
        return new ManyTokenTransfersRule();
    }

    @Bean
    RiskAddressHitRule riskAddressHitRule(BuiltInRiskAddressList builtIn) {
        return new RiskAddressHitRule(List.of(builtIn));
    }

    @Bean
    RuleEngine ruleEngine(List<Rule> rules) {
        return new RuleEngine(rules);
    }

    // ---- Agent 层 ----
    // 有 ChatModel（配置了 LLM Key）→ Agentic 编排；无 → 确定性降级链路（设计文档 9.3）

    @Bean
    ReportGenerator reportGenerator() {
        return new TemplateReportGenerator();
    }

    @Bean
    AnalysisService analysisService(org.springframework.beans.factory.ObjectProvider<ChatModel> chatModel,
                                    TxFactsProvider factsProvider,
                                    RuleEngine ruleEngine,
                                    ReportGenerator reportGenerator,
                                    EthRpcClient rpcClient,
                                    SelectorRegistry selectorRegistry,
                                    KnowledgeSearch knowledgeSearch,
                                    ObjectProvider<MeterRegistry> meterRegistry) {
        ChatModel model = chatModel.getIfAvailable();
        if (model != null) {
            return new io.chainlens.agent.AgenticAnalysisService(
                    factsProvider, ruleEngine, model, rpcClient,
                    selectorRegistry,
                    knowledgeSearch, reportGenerator, meterRegistry.getIfAvailable());
        }
        return new DeterministicAnalysisService(factsProvider, ruleEngine, reportGenerator);
    }

    // ---- API 层支撑：并发闸 + 虚拟线程执行器（SSE 异步执行） ----

    @Bean
    Semaphore analysisSemaphore(@Value("${chainlens.concurrency.max:4}") int maxConcurrency) {
        return new Semaphore(maxConcurrency);
    }

    @Bean
    ChainLensMcpTools chainLensMcpTools(AnalysisService analysisService,
                                        TxFactsProvider factsProvider,
                                        RuleEngine ruleEngine,
                                        SelectorRegistry selectorRegistry,
                                        KnowledgeSearch knowledgeSearch,
                                        Semaphore analysisSemaphore,
                                        ObjectProvider<MeterRegistry> meterRegistry,
                                        @Value("${chainlens.mcp.transport:streamable-http}") String transport) {
        // MCP 和 REST/SSE 共享 analysisSemaphore，从进程入口统一限制 RPC/LLM 压力。
        return new ChainLensMcpTools(analysisService, factsProvider, ruleEngine, selectorRegistry,
                knowledgeSearch, analysisSemaphore, meterRegistry.getIfAvailable(), transport);
    }

    @Bean(destroyMethod = "close")
    ExecutorService analysisExecutor() {
        // Java 21 虚拟线程：SSE 分析任务在虚拟线程上执行，客户端断开不阻塞请求线程
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.addUrlPatterns("/api/*", "/mcp");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    FilterRegistrationBean<McpTokenFilter> mcpTokenFilter(
            @Value("${chainlens.mcp.token:}") String configuredToken) {
        FilterRegistrationBean<McpTokenFilter> registration =
                new FilterRegistrationBean<>(new McpTokenFilter(configuredToken));
        registration.addUrlPatterns("/mcp");
        registration.setOrder(2);
        return registration;
    }
}
