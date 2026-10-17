package io.chainlens.knowledge;

/** RAG 默认关闭时的显式空实现；不会加载模型，也不会访问数据库。 */
public final class NoopKnowledgeSearch implements KnowledgeSearch {

    @Override
    public SearchResult search(String query) {
        return SearchResult.unavailable(Availability.DISABLED, "知识库未启用");
    }
}
