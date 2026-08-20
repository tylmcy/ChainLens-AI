package io.chainlens.agent.knowledge;

import java.util.List;

/**
 * 知识检索端口（RAG，设计文档 7.5）。
 * M5 使用 {@link NoopKnowledgeSearch}（知识库未启用，如实标注）；
 * M7 以 pgvector 实现替换。
 */
public interface KnowledgeSearch {

    /** 检索结果：片段 + 来源标注。 */
    record KnowledgeHit(String content, String source) {
    }

    /**
     * 检索相关知识片段。
     *
     * @return 空列表表示知识库不可用或无匹配（调用方如实标注，绝不伪造知识）
     */
    List<KnowledgeHit> search(String query);
}
