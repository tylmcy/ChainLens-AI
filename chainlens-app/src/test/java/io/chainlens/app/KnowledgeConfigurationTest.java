package io.chainlens.app;

import java.lang.reflect.Proxy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import io.chainlens.knowledge.KnowledgeIngestionService;
import io.chainlens.knowledge.KnowledgeReadiness;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.knowledge.NoopKnowledgeSearch;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KnowledgeConfiguration.class);

    @Test
    void 默认关闭只装配Noop且不需要模型数据库() {
        assertNoopContext(contextRunner);
    }

    @Test
    void 只打开知识开关但未启用嵌入模型时仍安全降级() {
        assertNoopContext(contextRunner.withPropertyValues("chainlens.knowledge.enabled=true"));
    }

    @Test
    void 同时启用知识库和嵌入模型时装配真实检索链路() {
        contextRunner
                // 用替身隔离本测试的目标 Bean 图，不在上下文单测中连接真实数据库或下载模型。
                .withBean(VectorStore.class, () -> noOpProxy(VectorStore.class))
                .withBean(JdbcTemplate.class,
                        () -> new JdbcTemplate(noOpProxy(DataSource.class)))
                .withBean(EmbeddingModel.class, () -> noOpProxy(EmbeddingModel.class))
                .withPropertyValues(
                        "chainlens.knowledge.enabled=true",
                        "spring.ai.model.embedding=transformers")
                .run(context -> {
                    assertThat(context).hasSingleBean(KnowledgeSearch.class);
                    assertThat(context.getBean(KnowledgeSearch.class))
                            .isInstanceOf(io.chainlens.knowledge.PgVectorKnowledgeSearch.class);
                    assertThat(context).hasSingleBean(KnowledgeReadiness.class);
                    assertThat(context).hasSingleBean(KnowledgeIngestionService.class);
                    assertThat(context).hasSingleBean(io.chainlens.knowledge.KnowledgeStartupSynchronizer.class);
                });
    }

    private static void assertNoopContext(ApplicationContextRunner runner) {
        runner.run(context -> {
            assertThat(context).hasSingleBean(KnowledgeSearch.class);
            assertThat(context.getBean(KnowledgeSearch.class)).isInstanceOf(NoopKnowledgeSearch.class);
            assertThat(context).doesNotHaveBean(KnowledgeReadiness.class);
            assertThat(context).doesNotHaveBean(KnowledgeIngestionService.class);
        });
    }

    /** 条件装配测试不会调用依赖，用 JDK Proxy 避免 Mockito 在新 JDK 上自附加 agent。 */
    @SuppressWarnings("unchecked")
    private static <T> T noOpProxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
