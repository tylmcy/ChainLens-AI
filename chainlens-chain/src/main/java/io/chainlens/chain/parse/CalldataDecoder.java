package io.chainlens.chain.parse;

import java.util.List;

import io.chainlens.model.CalldataAnalysis;
import io.chainlens.model.CalldataAnalysis.Status;

/**
 * calldata → {@link CalldataAnalysis}（三态，设计文档 5.3）：
 * RESOLVED（恰一个候选）/ AMBIGUOUS（多候选，如实列出）/ UNRESOLVED（无候选或本无 calldata）。
 */
public final class CalldataDecoder {

    private static final int SELECTOR_LENGTH_WITH_PREFIX = 10; // 0x + 8 hex

    private final SelectorRegistry registry;

    public CalldataDecoder(SelectorRegistry registry) {
        this.registry = registry;
    }

    public CalldataAnalysis decode(String input) {
        if (input == null || input.isEmpty() || "0x".equalsIgnoreCase(input)) {
            return new CalldataAnalysis(false, null, List.of(), null, Status.UNRESOLVED);
        }
        if (input.length() < SELECTOR_LENGTH_WITH_PREFIX) {
            // 有数据但不足 4 字节：无法构成 selector
            return new CalldataAnalysis(true, null, List.of(), null, Status.UNRESOLVED);
        }
        String selector = input.substring(0, SELECTOR_LENGTH_WITH_PREFIX).toLowerCase();
        List<String> candidates = registry.candidates(selector);
        if (candidates.isEmpty()) {
            return new CalldataAnalysis(true, selector, List.of(), null, Status.UNRESOLVED);
        }
        if (candidates.size() == 1) {
            return new CalldataAnalysis(true, selector, candidates, candidates.get(0), Status.RESOLVED);
        }
        return new CalldataAnalysis(true, selector, candidates, null, Status.AMBIGUOUS);
    }
}
