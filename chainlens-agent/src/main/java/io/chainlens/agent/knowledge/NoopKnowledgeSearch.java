package io.chainlens.agent.knowledge;

import java.util.List;

/**
 * 知识检索的显式空实现（M5 阶段，RAG 在 M7 接入）：
 * 返回空列表并附带"未启用"说明——知识是增强项，缺失不影响主链路（设计文档 9.3）。
 */
public final class NoopKnowledgeSearch implements KnowledgeSearch {

    @Override
    public List<KnowledgeHit> search(String query) {
        return List.of();
    }
}
