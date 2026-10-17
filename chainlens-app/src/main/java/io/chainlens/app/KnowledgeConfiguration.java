package io.chainlens.app;

import java.nio.file.Path;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.chainlens.knowledge.KnowledgeCorpusLoader;
import io.chainlens.knowledge.KnowledgeIngestionService;
import io.chainlens.knowledge.KnowledgeProperties;
import io.chainlens.knowledge.KnowledgeReadiness;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.knowledge.KnowledgeStartupSynchronizer;
import io.chainlens.knowledge.KnowledgeVectorStoreFactory;
import io.chainlens.knowledge.NoopKnowledgeSearch;
import io.chainlens.knowledge.PgVectorKnowledgeSearch;

/** M7 RAG 条件装配：默认 Noop，显式启用且存在嵌入模型时才连接 pgvector。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KnowledgeProperties.class)
@Import({KnowledgeConfiguration.RagEnabledConfiguration.class,
        KnowledgeConfiguration.FallbackConfiguration.class})
public class KnowledgeConfiguration {

    /** 两个开关必须同时成立；只打开 knowledge 不会误加载模型或访问向量库。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression("'${chainlens.knowledge.enabled:false}' == 'true' "
            + "&& '${spring.ai.model.embedding:none}' == 'transformers'")
    static class RagEnabledConfiguration {

        @Bean
        @ConditionalOnMissingBean(VectorStore.class)
        VectorStore knowledgeVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
            return KnowledgeVectorStoreFactory.create(jdbcTemplate, embeddingModel);
        }

        @Bean
        KnowledgeReadiness knowledgeReadiness() {
            return new KnowledgeReadiness();
        }

        @Bean
        KnowledgeIngestionService knowledgeIngestionService(VectorStore vectorStore,
                                                            JdbcTemplate jdbcTemplate,
                                                            KnowledgeProperties properties) {
            properties.validate();
            Path repositoryRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path knowledgeDirectory = repositoryRoot.resolve(properties.getDirectory()).normalize();
            return new KnowledgeIngestionService(vectorStore, jdbcTemplate, new KnowledgeCorpusLoader(),
                    repositoryRoot, knowledgeDirectory);
        }

        @Bean
        KnowledgeSearch pgVectorKnowledgeSearch(VectorStore vectorStore,
                                                KnowledgeReadiness readiness,
                                                KnowledgeProperties properties) {
            properties.validate();
            return new PgVectorKnowledgeSearch(vectorStore, readiness,
                    properties.getTopK(), properties.getSimilarityThreshold());
        }

        @Bean
        KnowledgeStartupSynchronizer knowledgeStartupSynchronizer(KnowledgeIngestionService ingestionService,
                                                                   KnowledgeReadiness readiness) {
            return new KnowledgeStartupSynchronizer(ingestionService, readiness);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression("'${chainlens.knowledge.enabled:false}' != 'true' "
            + "|| '${spring.ai.model.embedding:none}' != 'transformers'")
    static class FallbackConfiguration {
        @Bean
        KnowledgeSearch noopKnowledgeSearch() {
            return new NoopKnowledgeSearch();
        }
    }
}
