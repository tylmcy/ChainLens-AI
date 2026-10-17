package io.chainlens.knowledge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** pgvector 真数据库集成：表结构、幂等同步、更新清理、回滚与检索。 */
@Testcontainers
@Tag("db")
class KnowledgePgVectorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "pgvector/pgvector:0.8.6-pg17-bookworm");

    @TempDir
    Path tempRoot;

    private JdbcTemplate jdbcTemplate;
    private PgVectorStore vectorStore;
    private ControllableEmbeddingModel embeddingModel;
    private TransactionTemplate transactionTemplate;
    private Path knowledgeDirectory;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS knowledge_chunks");
        embeddingModel = new ControllableEmbeddingModel();
        vectorStore = KnowledgeVectorStoreFactory.create(jdbcTemplate, embeddingModel);
        vectorStore.afterPropertiesSet();
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        copyKnowledgeFiles();
    }

    @Test
    void 首次重复修改删除与失败回滚都保持一致() throws Exception {
        KnowledgeIngestionService service = service();

        KnowledgeSyncStats first = inTransaction(service);
        assertThat(first.added()).isBetween(20, 40);
        assertThat(first.updated()).isZero();
        assertThat(rowCount()).isEqualTo(first.added());

        KnowledgeSyncStats repeated = inTransaction(service);
        assertThat(repeated.unchanged()).isEqualTo(first.added());
        assertThat(repeated.added()).isZero();

        Path changed = knowledgeDirectory.resolve("failed-transaction.md");
        Files.writeString(changed, Files.readString(changed) + "\n\n补充说明：失败原因必须以可验证数据为准。\n");
        KnowledgeSyncStats modified = inTransaction(service);
        assertThat(modified.updated()).isGreaterThanOrEqualTo(1);

        Files.delete(knowledgeDirectory.resolve("risk-address-hit.md"));
        KnowledgeSyncStats deleted = inTransaction(service);
        assertThat(deleted.deleted()).isGreaterThanOrEqualTo(1);

        int beforeFailure = rowCount();
        Files.writeString(changed, Files.readString(changed) + "\n再次修改以触发嵌入。\n");
        embeddingModel.fail = true;
        assertThatThrownBy(() -> inTransaction(service)).isInstanceOf(KnowledgeSyncException.class);
        assertThat(rowCount()).isEqualTo(beforeFailure);
    }

    @Test
    void top3检索区分命中和可用但空结果() {
        KnowledgeIngestionService service = service();
        inTransaction(service);
        KnowledgeReadiness readiness = new KnowledgeReadiness();
        readiness.ready();
        PgVectorKnowledgeSearch search = new PgVectorKnowledgeSearch(vectorStore, readiness, 3, 0.70);

        KnowledgeSearch.SearchResult hit = search.search("最大额度授权给 spender 有什么风险");
        assertThat(hit.available()).isTrue();
        assertThat(hit.hits()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(hit.hits().get(0).source()).contains("erc20-unlimited-approval.md");

        KnowledgeSearch.SearchResult empty = search.search("晚饭和屏幕亮度");
        assertThat(empty.available()).isTrue();
        assertThat(empty.hits()).isEmpty();
    }

    private KnowledgeIngestionService service() {
        return new KnowledgeIngestionService(vectorStore, jdbcTemplate, new KnowledgeCorpusLoader(),
                tempRoot, knowledgeDirectory);
    }

    private KnowledgeSyncStats inTransaction(KnowledgeIngestionService service) {
        return transactionTemplate.execute(status -> service.synchronize());
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_chunks", Integer.class);
    }

    private void copyKnowledgeFiles() throws IOException {
        knowledgeDirectory = tempRoot.resolve("docs/knowledge");
        Files.createDirectories(knowledgeDirectory);
        Path source = Path.of("..").toAbsolutePath().normalize().resolve("docs/knowledge");
        try (var files = Files.list(source)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".md")).toList()) {
                Files.copy(file, knowledgeDirectory.resolve(file.getFileName()));
            }
        }
    }

    /** 测试嵌入只表达少量固定语义，避免 DB 测试下载真实 ONNX 模型。 */
    private static final class ControllableEmbeddingModel implements EmbeddingModel {
        private boolean fail;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            if (fail) {
                throw new IllegalStateException("模拟嵌入失败");
            }
            List<Embedding> embeddings = java.util.stream.IntStream.range(0, request.getInstructions().size())
                    .mapToObj(index -> new Embedding(vector(request.getInstructions().get(index)), index))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            if (fail) {
                throw new IllegalStateException("模拟嵌入失败");
            }
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return 512;
        }

        private static float[] vector(String text) {
            float[] vector = new float[512];
            if (text.contains("授权") || text.contains("spender") || text.contains("approve")) {
                vector[0] = 1.0f;
            } else if (text.contains("失败") || text.contains("revert") || text.contains("gas")) {
                vector[1] = 1.0f;
            } else if (text.contains("晚饭") || text.contains("亮度")) {
                vector[511] = 1.0f;
            } else {
                vector[2] = 1.0f;
            }
            return vector;
        }
    }
}
