package io.chainlens.api;

import jakarta.validation.constraints.Size;

/**
 * 分析请求（设计文档 3.1 阶段 0）：txHash 或 question 二选一。
 * question 仅作纯正则哈希提取输入（不调用 LLM，设计文档 1.3）。
 */
public record AnalysisRequest(
        String txHash,
        @Size(max = 500, message = "question 不能超过 500 字符") String question
) {
}
