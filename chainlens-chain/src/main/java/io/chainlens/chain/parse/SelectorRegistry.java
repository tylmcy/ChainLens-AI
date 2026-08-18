package io.chainlens.chain.parse;

import java.util.List;

/**
 * 4-byte selector → 候选函数签名列表。
 * selector 存在碰撞，registry 必须返回全部候选而非单一答案（设计文档 5.3）。
 */
public interface SelectorRegistry {

    /**
     * @param selector 带 0x 前缀的 4-byte selector（如 0xa9059cbb）
     * @return 候选签名列表（可空列表表示无候选）
     */
    List<String> candidates(String selector);
}
