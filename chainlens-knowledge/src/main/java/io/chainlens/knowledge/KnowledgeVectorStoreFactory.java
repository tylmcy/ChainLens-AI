package io.chainlens.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

/** 用固定的 512 维/HNSW/cosine 配置创建知识向量库。 */
public final class KnowledgeVectorStoreFactory {

    private KnowledgeVectorStoreFactory() {
    }

    public static PgVectorStore create(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        // Spring AI 2.0.0 的生命周期顺序是“先校验、后初始化”。首次空库若两个开关同时
        // 打开会在建表前失败，所以先用同配置幂等建表，再返回开启严格校验的正式实例。
        PgVectorStore bootstrap = builder(jdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .vectorTableValidationsEnabled(false)
                .build();
        bootstrap.afterPropertiesSet();

        return builder(jdbcTemplate, embeddingModel)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(true)
                .build();
    }

    private static PgVectorStore.PgVectorStoreBuilder builder(JdbcTemplate jdbcTemplate,
                                                               EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName(KnowledgeIngestionService.TABLE)
                .idType(PgVectorStore.PgIdType.TEXT)
                .dimensions(512)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .removeExistingVectorStoreTable(false);
    }
}
