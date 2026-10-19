package io.chainlens.mcp;

import java.util.List;

/** 4-byte selector 的候选签名集合；多个候选时不得武断选择唯一函数。 */
public record McpSelectorResolution(String selector, Status status, List<String> candidates) {

    public McpSelectorResolution {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public enum Status {
        RESOLVED,
        AMBIGUOUS,
        UNRESOLVED
    }
}
