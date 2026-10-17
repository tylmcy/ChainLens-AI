package io.chainlens.knowledge;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
class PgVectorKnowledgeSearchTest {

    @Test
    void 未就绪时不访问向量库() {
        FakeVectorStore store = new FakeVectorStore();
        KnowledgeReadiness readiness = new KnowledgeReadiness();
        PgVectorKnowledgeSearch search = new PgVectorKnowledgeSearch(store, readiness, 3, 0.7);

        KnowledgeSearch.SearchResult result = search.search("无限授权");

        assertThat(result.status()).isEqualTo(KnowledgeSearch.Availability.NOT_READY);
        assertThat(store.searchCalls).isZero();
    }

    @Test
    void 映射Top3命中的来源规则与相似度() {
        FakeVectorStore store = new FakeVectorStore();
        KnowledgeReadiness readiness = new KnowledgeReadiness();
        readiness.ready();
        Document document = Document.builder()
                .id("chunk-1")
                .text("无限授权允许 spender 后续代扣代币。")
                .metadata(Map.of(
                        "source", "docs/knowledge/erc20-unlimited-approval.md",
                        "heading", "这条规则在说什么",
                        "title", "ERC-20 无限额度授权",
                        "ruleIds", List.of("ERC20-UNLIMITED-APPROVAL")))
                .score(0.93)
                .build();
        store.results = List.of(document);
        PgVectorKnowledgeSearch search = new PgVectorKnowledgeSearch(store, readiness, 3, 0.7);

        KnowledgeSearch.SearchResult result = search.search("最大授权安全吗");

        assertThat(result.available()).isTrue();
        assertThat(result.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.source()).isEqualTo("docs/knowledge/erc20-unlimited-approval.md#这条规则在说什么");
            assertThat(hit.ruleIds()).containsExactly("ERC20-UNLIMITED-APPROVAL");
            assertThat(hit.similarity()).isEqualTo(0.93);
        });
    }

    @Test
    void 数据库或嵌入失败降级为不可用() {
        FakeVectorStore store = new FakeVectorStore();
        KnowledgeReadiness readiness = new KnowledgeReadiness();
        readiness.ready();
        store.failure = new IllegalStateException("db down");

        KnowledgeSearch.SearchResult result = new PgVectorKnowledgeSearch(store, readiness, 3, 0.7)
                .search("查询");

        assertThat(result.status()).isEqualTo(KnowledgeSearch.Availability.UNAVAILABLE);
        assertThat(result.hits()).isEmpty();
    }

    private static final class FakeVectorStore implements VectorStore {
        private List<Document> results = List.of();
        private RuntimeException failure;
        private int searchCalls;

        @Override
        public void add(List<Document> documents) {
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            searchCalls++;
            if (failure != null) {
                throw failure;
            }
            return results;
        }
    }
}
