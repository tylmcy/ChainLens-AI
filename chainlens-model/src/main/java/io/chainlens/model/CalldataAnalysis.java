package io.chainlens.model;

import java.util.List;

/**
 * calldata 解释结果。
 *
 * <p>4-byte selector 存在碰撞（不同签名可哈希出相同前 4 字节），仅凭 selector
 * 不能唯一确定函数，因此使用三态而非二态：
 * <ul>
 *   <li>{@code RESOLVED}：selector 库中恰好一个候选（V1 无本地 ABI 源，ABI 佐证留待后续）；</li>
 *   <li>{@code AMBIGUOUS}：多个候选，如实列出全部候选，不武断选一；</li>
 *   <li>{@code UNRESOLVED}：无候选（或本就没有 calldata，此时 hasInput=false）。</li>
 * </ul>
 */
public record CalldataAnalysis(
        boolean hasInput,
        String selector,
        List<String> candidates,
        String resolvedSignature,
        Status status
) {

    public enum Status {
        RESOLVED,
        AMBIGUOUS,
        UNRESOLVED
    }
}
