package io.chainlens.knowledge;

import java.util.List;

/**
 * RAG 知识检索端口。
 *
 * <p>端口放在 knowledge 模块，调用方只认识“可用状态 + 命中内容”，
 * 不需要知道底层是 pgvector、ONNX 还是关闭态空实现。
 */
public interface KnowledgeSearch {

    /**
     * 一条可追溯的知识命中。
     * source 是仓库内固定文档路径，不接受用户输入的路径或 URL。
     */
    record KnowledgeHit(
            String content,
            String source,
            String title,
            List<String> ruleIds,
            double similarity
    ) {
        public KnowledgeHit {
            ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
        }
    }

    /** 区分“关闭/未就绪/故障”和“检索成功但没有命中”，避免把故障伪装成空结果。 */
    enum Availability {
        AVAILABLE,
        DISABLED,
        NOT_READY,
        UNAVAILABLE
    }

    /** 检索结果；只有 AVAILABLE 才代表本次确实执行了向量检索。 */
    record SearchResult(Availability status, String reason, List<KnowledgeHit> hits) {
        public SearchResult {
            if (status == null) {
                throw new IllegalArgumentException("知识检索状态不能为空");
            }
            reason = reason == null ? "" : reason;
            hits = hits == null ? List.of() : List.copyOf(hits);
            if (status != Availability.AVAILABLE && !hits.isEmpty()) {
                throw new IllegalArgumentException("不可用状态不能携带知识命中");
            }
        }

        public boolean available() {
            return status == Availability.AVAILABLE;
        }

        public static SearchResult available(List<KnowledgeHit> hits) {
            return new SearchResult(Availability.AVAILABLE, "", hits);
        }

        public static SearchResult unavailable(Availability status, String reason) {
            if (status == Availability.AVAILABLE) {
                throw new IllegalArgumentException("AVAILABLE 状态请使用 available 工厂方法");
            }
            return new SearchResult(status, reason, List.of());
        }
    }

    SearchResult search(String query);
}
