package io.chainlens.app;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import io.chainlens.agent.AgenticAnalysisService;
import io.chainlens.agent.AnalysisService;
import io.chainlens.agent.ReportGenerator;
import io.chainlens.chain.TxFactsAssembler;
import io.chainlens.chain.rpc.EthRpcClient;
import io.chainlens.chain.rpc.HttpJsonRpcClient;
import io.chainlens.chain.rpc.RpcConfig;
import io.chainlens.chain.parse.SelectorRegistry;
import io.chainlens.evaluation.EvaluationDataset;
import io.chainlens.evaluation.EvaluationDatasetLoader;
import io.chainlens.evaluation.FullEvaluationReportWriter;
import io.chainlens.evaluation.FullEvaluationRunner;
import io.chainlens.evaluation.FullNetworkRouter;
import io.chainlens.evaluation.GitRevision;
import io.chainlens.evaluation.ModelBudget;
import io.chainlens.evaluation.NetworkGuardedFactsProvider;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.rules.RuleEngine;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;

/** 仅显式 evaluation-full profile 装配真实 Agent 评测，不改变默认 REST/MCP 服务。 */
@Configuration(proxyBeanMethods = false)
@Profile("evaluation-full")
public class FullEvaluationConfiguration {

    /** 使用已有 ChatModel 抽象；数据集和价格从显式环境变量读取。 */
    @Bean
    ApplicationRunner fullEvaluationApplicationRunner(ChatModel chatModel,
                                                       RuleEngine rules,
                                                       ReportGenerator reportGenerator,
                                                       SelectorRegistry selectors,
                                                       KnowledgeSearch knowledgeSearch,
                                                       ObjectProvider<MeterRegistry> meterRegistry,
                                                       Environment env) {
        return args -> {
            Path output = Path.of(env.getProperty("CHAINLENS_EVAL_OUTPUT_DIR", "eval/results/full/latest"));
            FullEvaluationReportWriter writer = new FullEvaluationReportWriter();
            try {
                ModelBudget.Config budget = budgetConfig(env);
                validateModelSelection(chatModel, budget, env);
                if (chatModel.getOptions() == null || chatModel.getOptions().getMaxTokens() == null
                        || chatModel.getOptions().getMaxTokens() <= 0
                        || chatModel.getOptions().getMaxTokens() > budget.maxOutputTokens()) {
                    throw new IllegalArgumentException("ChatModel 的 maxTokens 未配置或超过评测上界");
                }
                Path manifest = Path.of(env.getProperty("CHAINLENS_EVAL_DATASET",
                        "eval/cases/transaction-eval-v1.json"));
                Path fixtures = Path.of(env.getProperty("CHAINLENS_EVAL_FIXTURES", "eval/fixtures"));
                EvaluationDataset dataset = EvaluationDatasetLoader.load(manifest, fixtures);
                Set<String> selected = parseCaseIds(env.getProperty("CHAINLENS_EVAL_CASE_IDS", ""));
                Map<Long, FullNetworkRouter.Endpoint> endpoints = new HashMap<>();
                addEndpoint(endpoints, 31337L, env.getProperty("CHAINLENS_EVAL_ANVIL_RPC_URL", ""),
                        chatModel, rules, reportGenerator, selectors, knowledgeSearch,
                        meterRegistry.getIfAvailable());
                addEndpoint(endpoints, 1L, env.getProperty("CHAINLENS_EVAL_MAINNET_RPC_URL", ""),
                        chatModel, rules, reportGenerator, selectors, knowledgeSearch,
                        meterRegistry.getIfAvailable());
                var result = new FullEvaluationRunner(new FullNetworkRouter(endpoints))
                        .run(dataset, selected, budget, GitRevision.current(Path.of(".")));
                writer.write(result, output);
                if (!result.passed()) {
                    throw new EvaluationFailed();
                }
            } catch (EvaluationFailed e) {
                throw e;
            } catch (RuntimeException e) {
                writer.writeFailure(output, e.getClass().getSimpleName());
                // 不将异常原文写入启动日志：RPC URL 可能包含凭据。
                throw new EvaluationFailed();
            }
        };
    }

    /** 只有配置了 URL 的网络才装配；full runner 根据所选用例检查完整性。 */
    private static void addEndpoint(Map<Long, FullNetworkRouter.Endpoint> endpoints, long chainId,
                                    String rpcUrl, ChatModel model, RuleEngine rules,
                                    ReportGenerator reportGenerator, SelectorRegistry selectors,
                                    KnowledgeSearch knowledgeSearch, MeterRegistry meters) {
        if (rpcUrl == null || rpcUrl.isBlank()) {
            return;
        }
        EthRpcClient rpc = new HttpJsonRpcClient(RpcConfig.defaults(rpcUrl));
        var facts = new NetworkGuardedFactsProvider(chainId, new TxFactsAssembler(rpc));
        AnalysisService service = new AgenticAnalysisService(facts, rules, model, rpc, selectors,
                knowledgeSearch, reportGenerator, meters);
        endpoints.put(chainId, new FullNetworkRouter.Endpoint(rpc, service));
    }

    /** 缺少单价和 token 上界时立即失败，不使用可能过时的默认价格。 */
    private static ModelBudget.Config budgetConfig(Environment env) {
        return new ModelBudget.Config(required(env, "CHAINLENS_EVAL_PROVIDER"),
                required(env, "CHAINLENS_EVAL_MODEL"),
                LocalDate.parse(required(env, "CHAINLENS_EVAL_PRICE_DATE")),
                required(env, "CHAINLENS_EVAL_CONTEXT_SOURCE"),
                Integer.parseInt(required(env, "CHAINLENS_EVAL_MAX_INPUT_TOKENS")),
                Integer.parseInt(required(env, "CHAINLENS_EVAL_MAX_OUTPUT_TOKENS")),
                new BigDecimal(required(env, "CHAINLENS_EVAL_INPUT_USD_PER_MILLION")),
                new BigDecimal(required(env, "CHAINLENS_EVAL_OUTPUT_USD_PER_MILLION")),
                new BigDecimal(required(env, "CHAINLENS_EVAL_BUDGET_USD")));
    }

    /** 避免报告所记模型/provider 与实际 Spring AI 实例不一致而误算费用。 */
    static void validateModelSelection(ChatModel model, ModelBudget.Config budget, Environment env) {
        String activeProvider = env.getProperty("spring.ai.model.chat", "none");
        String activeModel = model.getOptions() == null ? null : model.getOptions().getModel();
        if (!budget.provider().equalsIgnoreCase(activeProvider)
                || !budget.model().equals(activeModel)) {
            throw new IllegalArgumentException("Full 评测声明的 provider/model 与实际 ChatModel 不一致");
        }
    }

    private static String required(Environment env, String name) {
        String value = env.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少 Full 评测配置：" + name);
        }
        return value;
    }

    private static Set<String> parseCaseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 明确的非零退出状态，同时避免把含 URL 的底层异常打印到公开 CI 日志。 */
    static final class EvaluationFailed extends RuntimeException {
        EvaluationFailed() {
            super("Full 评测未通过；查看 eval/results/full artifact");
        }
    }
}
