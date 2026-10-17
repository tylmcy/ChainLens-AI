package io.chainlens.knowledge;

import java.util.List;

/** 通过校验后的受管 Markdown 知识文档。 */
public record KnowledgeDocument(
        String id,
        String title,
        String category,
        List<String> ruleIds,
        String version,
        String source,
        String body
) {
    public KnowledgeDocument {
        ruleIds = List.copyOf(ruleIds);
    }
}
