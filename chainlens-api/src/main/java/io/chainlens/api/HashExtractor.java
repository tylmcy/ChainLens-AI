package io.chainlens.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯正则哈希提取（设计文档 1.3 / 3.1 阶段 0）：
 * 固定格式（0x + 64 位十六进制）用正则即可，不调用 LLM——更快、更确定、零成本。
 */
public final class HashExtractor {

    private static final Pattern TX_HASH = Pattern.compile("0x[0-9a-fA-F]{64}");

    private HashExtractor() {
    }

    /** 提取第一个匹配；无匹配返回 null。 */
    public static String extract(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        Matcher matcher = TX_HASH.matcher(question);
        return matcher.find() ? matcher.group() : null;
    }
}
