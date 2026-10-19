package io.chainlens.chain.parse;

import java.util.List;
import java.util.Map;

/**
 * V1 自建精选 selector 表（常用 ERC-20 函数签名，设计文档 12.5：自撰、Apache-2.0）。
 *
 * <p>selector 以 hex 常量直接内置（这些值是全网公认常量，在单测中与公开资料校验），
 * 避免仅为计算 keccak 引入 web3j:crypto（含签名能力，超出薄客户端边界）。
 * 若未来需要动态计算任意签名，再评估引入纯哈希实现。
 */
public final class CuratedSelectorRegistry implements SelectorRegistry {

    private static final Map<String, List<String>> TABLE = Map.ofEntries(
            entry("0xa9059cbb", "transfer(address,uint256)"),
            entry("0x23b872dd", "transferFrom(address,address,uint256)"),
            entry("0x095ea7b3", "approve(address,uint256)"),
            entry("0x70a08231", "balanceOf(address)"),
            entry("0xdd62ed3e", "allowance(address,address)"),
            entry("0x18160ddd", "totalSupply()"),
            entry("0x313ce567", "decimals()"),
            entry("0x95d89b41", "symbol()"),
            entry("0x06fdde03", "name()"),
            entry("0x42842e0e", "safeTransferFrom(address,address,uint256)"),
            entry("0xb88d4fde", "safeTransferFrom(address,address,uint256,bytes)"),
            entry("0x39509351", "increaseAllowance(address,uint256)"),
            entry("0xa457c2cd", "decreaseAllowance(address,uint256)"),
            // 两个函数的 keccak256 前 4 字节相同；没有可信 ABI 时不可选定 burn。
            Map.entry("0x42966c68", List.of("burn(uint256)", "collate_propagate_storage(bytes16)")),
            entry("0x40c10f19", "mint(address,uint256)"),
            entry("0xf2fde38b", "transferOwnership(address)")
    );

    private static Map.Entry<String, List<String>> entry(String selector, String signature) {
        return Map.entry(selector, List.of(signature));
    }

    @Override
    public List<String> candidates(String selector) {
        if (selector == null) {
            return List.of();
        }
        return TABLE.getOrDefault(selector.toLowerCase(), List.of());
    }
}
