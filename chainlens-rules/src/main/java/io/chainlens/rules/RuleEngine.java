package io.chainlens.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;

/**
 * 规则引擎：遍历注册的规则，聚合 Finding。
 *
 * <p>构造时自检（fail-fast，设计文档 6.4）：ruleId 唯一、规则对象非空。
 * 规则引擎异常不允许任何"兜底猜测"——确定性保障坏了就是 bug（设计文档 9.3）。
 *
 * <p>纯 Java、无 Spring 依赖：评测执行器（零 LLM、零容器）直接使用；
 * 装配模块在运行时用 Spring 收集所有 Rule bean 注入本引擎。
 */
public final class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("规则引擎至少需要一条规则");
        }
        Set<String> ids = new HashSet<>();
        for (Rule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("规则列表包含 null 条目");
            }
            if (rule.id() == null || rule.id().isBlank()) {
                throw new IllegalArgumentException("规则缺少 id: " + rule.getClass().getName());
            }
            if (!ids.add(rule.id())) {
                throw new IllegalArgumentException("规则 ID 重复: " + rule.id());
            }
        }
        this.rules = List.copyOf(rules);
    }

    public List<Rule> rules() {
        return rules;
    }

    /** 执行全部规则，按注册顺序聚合命中结果。 */
    public List<Finding> evaluate(TxFacts facts) {
        return rules.stream()
                .flatMap(rule -> rule.evaluate(facts).stream())
                .toList();
    }
}
