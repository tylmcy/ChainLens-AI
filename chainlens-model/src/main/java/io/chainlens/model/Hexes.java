package io.chainlens.chain;

import java.math.BigInteger;

/**
 * hex 字符串工具。所有输入输出规范：
 * <ul>
 *   <li>数量型（BigInteger/Long）：输入必须带 0x 前缀（Ethereum JSON-RPC 数量格式）；</li>
 *   <li>地址：统一小写、带 0x 前缀的 40 位 hex（EIP-55 校验展示不在本层处理）。</li>
 * </ul>
 */
public final class Hexes {

    private Hexes() {
    }

    /** "0x1a" → 26。异常格式抛 IllegalArgumentException（调用方负责失败语义）。 */
    public static BigInteger hexToBigInteger(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex 不能为 null");
        }
        String normalized = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (normalized.isEmpty()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(normalized, 16);
    }

    /** "0x1a" → 26L。 */
    public static long hexToLong(String hex) {
        return hexToBigInteger(hex).longValueExact();
    }

    /** 数字 → 数量型 hex（"0x" + 无前导零），0 → "0x0"。 */
    public static String toQuantity(long value) {
        return "0x" + Long.toHexString(value);
    }

    public static String toQuantity(BigInteger value) {
        return "0x" + value.toString(16);
    }

    /** 地址归一化：去 0x、补零、小写、恢复 0x 前缀。 */
    public static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String raw = address.startsWith("0x") || address.startsWith("0X") ? address.substring(2) : address;
        if (raw.length() > 40) {
            raw = raw.substring(raw.length() - 40);
        }
        return "0x" + "0".repeat(40 - raw.length()) + raw.toLowerCase();
    }

    /** 40 位 hex（可带 0x）转为 topic 形式：0x + 24 字节零填充 + 20 字节地址。 */
    public static String addressToTopic(String address) {
        String normalized = normalizeAddress(address);
        return "0x" + "0".repeat(24) + normalized.substring(2);
    }
}
