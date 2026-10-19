package io.chainlens.app;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.chainlens.agent.TemplateReportGenerator;
import io.chainlens.chain.parse.CuratedSelectorRegistry;
import io.chainlens.evaluation.EvaluationRuleSet;
import io.chainlens.knowledge.NoopKnowledgeSearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FullEvaluationConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(FullEvaluationConfiguration.class)
            .withBean(ChatModel.class, () -> new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    throw new AssertionError("装配测试不应调用付费模型");
                }

                @Override
                public ChatOptions getOptions() {
                    return DeepSeekChatOptions.builder().maxTokens(20).build();
                }
            })
            .withBean(io.chainlens.rules.RuleEngine.class, EvaluationRuleSet::create)
            .withBean(io.chainlens.agent.ReportGenerator.class, TemplateReportGenerator::new)
            .withBean(io.chainlens.chain.parse.SelectorRegistry.class, CuratedSelectorRegistry::new)
            .withBean(io.chainlens.knowledge.KnowledgeSearch.class, NoopKnowledgeSearch::new);

    @Test
    void fullProfileCreatesRunnerWithProviderNeutralChatModel() {
        contexts.withInitializer(context -> context.getEnvironment().setActiveProfiles("evaluation-full"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApplicationRunner.class);
                });
    }

    @Test
    void normalProfileDoesNotCreatePaidEvaluationRunner() {
        contexts.run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));
    }

    @Test
    void refusesModelMetadataThatWouldMisstateCost() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new AssertionError("配置校验不能调用模型");
            }

            @Override
            public ChatOptions getOptions() {
                return DeepSeekChatOptions.builder().model("deepseek-flash").maxTokens(20).build();
            }
        };
        var budget = new io.chainlens.evaluation.ModelBudget.Config("deepseek", "deepseek-flash",
                LocalDate.of(2026, 10, 19), "test", 1000, 20,
                new BigDecimal("0.3"), new BigDecimal("1.2"), BigDecimal.ONE);
        var env = new MockEnvironment().withProperty("spring.ai.model.chat", "deepseek");

        FullEvaluationConfiguration.validateModelSelection(model, budget, env);
        assertThatThrownBy(() -> FullEvaluationConfiguration.validateModelSelection(model,
                new io.chainlens.evaluation.ModelBudget.Config("deepseek", "other",
                        LocalDate.of(2026, 10, 19), "test", 1000, 20,
                        new BigDecimal("0.3"), new BigDecimal("1.2"), BigDecimal.ONE), env))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FullEvaluationConfiguration.validateModelSelection(model, budget,
                new MockEnvironment().withProperty("spring.ai.model.chat", "ollama")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
