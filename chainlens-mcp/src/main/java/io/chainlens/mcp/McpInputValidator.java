package io.chainlens.mcp;

import java.util.Locale;
import java.util.regex.Pattern;

/** 公共 MCP 参数校验：先拒绝非法输入，再允许任何 RPC、规则或向量检索调用。 */
public final class McpInputValidator {

    static final int MAX_KNOWLEDGE_QUERY_LENGTH = 200;
    private static final Pattern TX_HASH = Pattern.compile("0x[0-9a-fA-F]{64}");
    private static final Pattern SELECTOR = Pattern.compile("0x[0-9a-fA-F]{8}");

    private McpInputValidator() {
    }

    public static String transactionHash(String value) {
        String normalized = trim(value);
        if (normalized == null || !TX_HASH.matcher(normalized).matches()) {
            throw invalid("交易哈希格式非法（应为 0x + 64 位十六进制）");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String selector(String value) {
        String normalized = trim(value);
        if (normalized == null || !SELECTOR.matcher(normalized).matches()) {
            throw invalid("selector 格式非法（应为 0x + 8 位十六进制）");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String knowledgeQuery(String value) {
        String normalized = trim(value);
        if (normalized == null || normalized.isEmpty()) {
            throw invalid("知识查询不能为空");
        }
        if (normalized.length() > MAX_KNOWLEDGE_QUERY_LENGTH) {
            throw invalid("知识查询不能超过 " + MAX_KNOWLEDGE_QUERY_LENGTH + " 个字符");
        }
        return normalized;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static ChainLensMcpException invalid(String message) {
        return McpErrorMapper.clientError(McpErrorCode.INVALID_INPUT, message, false);
    }
}
