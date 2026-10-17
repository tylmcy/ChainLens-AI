package io.chainlens.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/** 使用 Spring AI VectorStore 的 top-3 知识检索实现。 */
public final class PgVectorKnowledgeSearch implements KnowledgeSearch {

    private final VectorStore vectorStore;
    private final KnowledgeReadiness readiness;
    private final int topK;
    private final double similarityThreshold;

    public PgVectorKnowledgeSearch(VectorStore vectorStore,
                                   KnowledgeReadiness readiness,
                                   int topK,
                                   double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.readiness = readiness;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public SearchResult search(String query) {
        if (query == null || query.isBlank()) {
            return SearchResult.available(List.of());
        }
        KnowledgeReadiness.State state = readiness.current();
        if (state.availability() != Availability.AVAILABLE) {
            return SearchResult.unavailable(state.availability(), state.reason());
        }
        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query.strip())
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build());
            List<KnowledgeHit> hits = documents == null ? List.of() : documents.stream()
                    .map(PgVectorKnowledgeSearch::toHit)
                    .toList();
            return SearchResult.available(hits);
        } catch (RuntimeException e) {
            return SearchResult.unavailable(Availability.UNAVAILABLE, "知识检索暂时不可用");
        }
    }

    private static KnowledgeHit toHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String source = String.valueOf(metadata.getOrDefault("source", ""));
        String heading = String.valueOf(metadata.getOrDefault("heading", ""));
        return new KnowledgeHit(
                document.getText(),
                heading.isBlank() ? source : source + "#" + heading,
                String.valueOf(metadata.getOrDefault("title", "")),
                stringList(metadata.get("ruleIds")),
                document.getScore() == null ? 0.0 : document.getScore());
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        iterable.forEach(item -> result.add(String.valueOf(item)));
        return List.copyOf(result);
    }
}
