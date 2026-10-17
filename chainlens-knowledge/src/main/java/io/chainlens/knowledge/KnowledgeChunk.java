package io.chainlens.knowledge;

import java.util.List;

/** 可入库的确定性知识片段。 */
public record KnowledgeChunk(
        String id,
        String content,
        String source,
        String title,
        String heading,
        int ordinal,
        String category,
        List<String> ruleIds,
        String version,
        String contentHash
) {
    public KnowledgeChunk {
        ruleIds = List.copyOf(ruleIds);
    }
}
