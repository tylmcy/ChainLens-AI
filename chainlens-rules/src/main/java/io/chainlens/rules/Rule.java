package io.chainlens.rules;

import java.util.List;

import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;

/**
 * 确定性风险规则（设计文档 6.1）：
 * <ul>
 *   <li>同输入 → 同输出，可单测、可评测；</li>
 *   <li>纯函数评估器，不 import 任何 LLM 类；</li>
 *   <li>声明式：规则 = 元数据（id）+ evaluate，引擎只负责遍历聚合。</li>
 * </ul>
 */
public interface Rule {

    /** 规则编号，全局唯一（引擎启动自检强制）。 */
    String id();

    /** 对案卷评估，返回命中列表（无命中返回空列表，不返回 null）。 */
    List<Finding> evaluate(TxFacts facts);
}
