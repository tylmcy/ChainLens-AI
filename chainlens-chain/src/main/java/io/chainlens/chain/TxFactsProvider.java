package io.chainlens.chain;

import io.chainlens.model.TxFacts;

/**
 * 取证能力抽象：agent 层通过此接口依赖取证，便于单元测试与评测替换。
 */
public interface TxFactsProvider {

    TxFacts assemble(String txHash);
}
