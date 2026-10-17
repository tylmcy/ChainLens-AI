package io.chainlens.evaluation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.chainlens.knowledge.KnowledgeCorpusLoader;
import io.chainlens.knowledge.KnowledgeIngestionService;
import io.chainlens.knowledge.KnowledgeReadiness;
import io.chainlens.knowledge.KnowledgeSearch;
import io.chainlens.knowledge.KnowledgeVectorStoreFactory;
import io.chainlens.knowledge.PgVectorKnowledgeSearch;

import static org.assertj.core.api.Assertions.assertThat;

/** 显式 {@code -P rag-eval} 才运行的真实 BGE + pgvector 检索评测。 */
@Testcontainers
@Tag("smoke")
@Tag("rag-eval")
class RagRetrievalEvaluationTest {

    private static final String DEFAULT_MODEL =
            "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model.onnx";
    private static final String DEFAULT_TOKENIZER =
            "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/tokenizer.json";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "pgvector/pgvector:0.8.6-pg17-bookworm");

    @Test
    void 十二条中文问句的Top3目标命中率至少百分之九十() throws Exception {
        double threshold = Double.parseDouble(System.getProperty("rag.similarity.threshold", "0.50"));
        TransformersEmbeddingModel embeddingModel = embeddingModel();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.execute("DROP TABLE IF EXISTS knowledge_chunks");
        PgVectorStore store = KnowledgeVectorStoreFactory.create(jdbc, embeddingModel);
        store.afterPropertiesSet();

        Path root = Path.of("..").toAbsolutePath().normalize();
        new KnowledgeIngestionService(store, jdbc, new KnowledgeCorpusLoader(),
                root, root.resolve("docs/knowledge")).synchronize();
        KnowledgeReadiness readiness = new KnowledgeReadiness();
        readiness.ready();
        KnowledgeSearch search = new PgVectorKnowledgeSearch(store, readiness, 3, threshold);

        JsonNode dataset;
        try (InputStream input = getClass().getResourceAsStream("/rag-eval-v1.json")) {
            dataset = new ObjectMapper().readTree(input);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int expectedCases = 0;
        int hits = 0;
        for (JsonNode item : dataset.path("cases")) {
            KnowledgeSearch.SearchResult result = search.search(item.path("query").asText());
            String expected = item.path("expectedSource").isNull()
                    ? null : item.path("expectedSource").asText();
            boolean matched = expected == null
                    ? result.hits().isEmpty()
                    : result.hits().stream().anyMatch(hit -> hit.source().startsWith(expected));
            if (expected != null) {
                expectedCases++;
                if (matched) {
                    hits++;
                }
            }
            rows.add(Map.of(
                    "id", item.path("id").asText(),
                    "query", item.path("query").asText(),
                    "expectedSource", expected == null ? "" : expected,
                    "matched", matched,
                    "returnedSources", result.hits().stream().map(KnowledgeSearch.KnowledgeHit::source).toList(),
                    "returnedHits", result.hits().stream().map(hit -> Map.of(
                            "source", hit.source(),
                            "similarity", hit.similarity())).toList()));
        }
        double hitRate = expectedCases == 0 ? 0.0 : (double) hits / expectedCases;
        writeArtifacts(dataset.path("version").asText(), rows, hits, expectedCases, hitRate, threshold);

        assertThat(hitRate).isGreaterThanOrEqualTo(0.90);
    }

    private static TransformersEmbeddingModel embeddingModel() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(System.getProperty("model.uri", env("CHAINLENS_EMBEDDING_MODEL_URI", DEFAULT_MODEL)));
        model.setTokenizerResource(System.getProperty(
                "tokenizer.uri", env("CHAINLENS_EMBEDDING_TOKENIZER_URI", DEFAULT_TOKENIZER)));
        model.setModelOutputName("last_hidden_state");
        model.setTokenizerOptions(Map.of("padding", "true"));
        model.setResourceCacheDirectory(System.getProperty(
                "embedding.cache.dir", env("CHAINLENS_EMBEDDING_CACHE_DIR", ".cache/chainlens/onnx")));
        model.afterPropertiesSet();
        return model;
    }

    private static void writeArtifacts(String version,
                                       List<Map<String, Object>> rows,
                                       int hits,
                                       int total,
                                       double hitRate,
                                       double threshold) throws Exception {
        Path output = Path.of("..").toAbsolutePath().normalize().resolve("eval/results/rag");
        Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("rag-eval-v1.json").toFile(), Map.of(
                "version", version,
                "embeddingModel", "Xenova/bge-small-zh-v1.5 (ONNX, 512d)",
                "similarityThreshold", threshold,
                "hits", hits,
                "expectedCases", total,
                "top3HitRate", hitRate,
                "cases", rows));
        String markdown = """
                # RAG 检索评测

                - 数据集版本：%s
                - 有目标来源的问句：%d
                - 嵌入模型：Xenova/bge-small-zh-v1.5（ONNX, 512d）
                - 相似度阈值：%.2f
                - top-3 命中：%d
                - top-3 命中率：%.2f%%

                逐例结果见 `rag-eval-v1.json`。两条无关查询单独记录是否被阈值过滤，不计入目标来源命中率。
                """.formatted(version, total, threshold, hits, hitRate * 100.0);
        Files.writeString(output.resolve("rag-eval-v1.md"), markdown);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
