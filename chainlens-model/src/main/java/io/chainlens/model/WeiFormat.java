package io.chainlens.model;

import java.math.BigInteger;

/**
 * 金额格式化（设计文档 5.7）：内部 BigInteger（wei），展示层 18 位小数，
 * 最多保留 6 位小数、去掉尾零；负数加前缀。
 */
public final class WeiFormat {

    private static final BigInteger TEN_POW_18 = BigInteger.TEN.pow(18);
    private static final int MAX_FRACTION_DIGITS = 6;

    private WeiFormat() {
    }

    /** wei → 十进制 ETH 字符串（如 "1.5"、"0.0001"、"0"）。 */
    public static String formatEther(BigInteger wei) {
        if (wei == null) {
            return "0";
        }
        boolean negative = wei.signum() < 0;
        BigInteger abs = wei.abs();
        BigInteger[] div = abs.divideAndRemainder(TEN_POW_18);
        String whole = div[0].toString();
        String fraction = String.format("%018d", div[1]).replaceAll("0+$", "");
        if (fraction.length() > MAX_FRACTION_DIGITS) {
            fraction = fraction.substring(0, MAX_FRACTION_DIGITS);
        }
        String value = fraction.isEmpty() ? whole : whole + "." + fraction;
        return negative ? "-" + value : value;
    }
}
