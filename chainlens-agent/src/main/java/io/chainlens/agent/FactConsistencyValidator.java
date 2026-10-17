package io.chainlens.agent;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.chainlens.model.AnalysisReport;
import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;
import io.chainlens.model.TransferEvent;

/**
 * 事实一致性校验（设计文档 4.4 护栏 ③）：
 * 报告中的每个哈希与长数字必须能在证据数据中找到原文，防 LLM 改写数字。
 *
 * <p>校验规则（确定性、可全自动执行）：
 * <ol>
 *   <li>findings 的 ruleId 多重集合必须与规则引擎输出完全一致（多/少/改任一字段即失败）；</li>
 *   <li>文本中的哈希型 token（0x + ≥40 位 hex）必须存在于允许集合（所有参与地址 + txHash）；</li>
 *   <li>文本中的长数字（≥10 位十进制）必须存在于允许集合（wei 值、gas、区块号等十进制原文）；</li>
 *   <li>limitations 首条必须是免责声明。</li>
 * </ol>
 * 短数字（如"2^256-1"中的 256）不校验——无法机械判定，且机器字段（severity/confidence）
 * 不来自 LLM 文本，事实风险已由 ① 控制。
 */
public final class FactConsistencyValidator {

    private static final Pattern HASH_TOKEN = Pattern.compile("0x[0-9a-fA-F]{40,}");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{10,}");

    private FactConsistencyValidator() {
    }

    /** 校验；返回违规列表（空 = 通过）。 */
    public static List<String> validate(AnalysisReport report, TxFacts facts, List<Finding> rulesFindings) {
        List<String> violations = new ArrayList<>();

        // ① ruleId 多重集合一致
        List<String> expected = rulesFindings.stream().map(Finding::ruleId).sorted().toList();
        List<String> actual = report.findings().stream().map(Finding::ruleId).sorted().toList();
        if (!expected.equals(actual)) {
            violations.add("findings.ruleId 集合与规则引擎不一致：期望 " + expected + "，实际 " + actual);
        }

        // ②/③ 允许集合
        Set<String> allowedStrings = allowedStrings(facts);

        List<String> texts = new ArrayList<>();
        if (report.summary() != null) {
            texts.add(report.summary());
        }
        report.actions().forEach(texts::add);
        report.limitations().forEach(texts::add);
        report.findings().forEach(f -> {
            if (f.title() != null) {
                texts.add(f.title());
            }
            if (f.description() != null) {
                texts.add(f.description());
            }
        });

        for (String text : texts) {
            // 先摘除哈希 token（其内部数字不参与数字检查），再检查剩余文本中的长数字
            Matcher hashMatcher = HASH_TOKEN.matcher(text);
            StringBuffer cleaned = new StringBuffer();
            while (hashMatcher.find()) {
                String token = hashMatcher.group().toLowerCase();
                if (!allowedStrings.contains(token)) {
                    violations.add("文本中出现证据之外的哈希：" + token);
                }
                hashMatcher.appendReplacement(cleaned, "");
            }
            hashMatcher.appendTail(cleaned);

            Matcher numberMatcher = LONG_NUMBER.matcher(cleaned);
            while (numberMatcher.find()) {
                String token = numberMatcher.group();
                if (!allowedStrings.contains(token)) {
                    violations.add("文本中出现证据之外的长数字：" + token);
                }
            }
        }

        // ④ 免责声明首条
        if (report.limitations().isEmpty()
                || !TemplateReportGenerator.DISCLAIMER.equals(report.limitations().get(0))) {
            violations.add("limitations 首条必须是免责声明");
        }

        return violations;
    }

    /** 证据允许集合：所有地址/哈希/长数字的十进制原文（精确字符串匹配）。 */
    private static Set<String> allowedStrings(TxFacts facts) {
        Set<String> allowed = new HashSet<>();
        allowed.add(facts.transaction().hash().toLowerCase());
        addAddress(allowed, facts.transaction().from());
        addAddress(allowed, facts.transaction().to());
        for (TransferEvent t : facts.transfers()) {
            addAddress(allowed, t.tokenAddress());
            addAddress(allowed, t.from());
            addAddress(allowed, t.to());
            addNumber(allowed, t.valueWei());
        }
        for (ApprovalEvent a : facts.approvals()) {
            addAddress(allowed, a.tokenAddress());
            addAddress(allowed, a.owner());
            addAddress(allowed, a.spender());
            addNumber(allowed, a.valueWei());
        }
        addNumber(allowed, facts.valueWei());
        addNumber(allowed, facts.gasCostWei());
        addNumber(allowed, facts.transaction().value());
        addNumber(allowed, facts.transaction().gas());
        addNumber(allowed, facts.transaction().gasPrice());
        addNumber(allowed, facts.receipt().gasUsed());
        addNumber(allowed, facts.receipt().effectiveGasPrice());
        addNumber(allowed, facts.receipt().blockNumber());
        addNumber(allowed, facts.block().number());
        allowed.add(Long.toString(facts.block().timestamp()));
        addNumber(allowed, facts.block().baseFeePerGas());
        if (facts.contractCode() != null) {
            addAddress(allowed, facts.contractCode().address());
            addNumber(allowed, facts.contractCode().codeSize());
        }
        return allowed;
    }

    private static void addAddress(Set<String> allowed, String address) {
        if (address != null) {
            allowed.add(address.toLowerCase());
        }
    }

    private static void addNumber(Set<String> allowed, BigInteger number) {
        if (number != null) {
            allowed.add(number.toString());
        }
    }
}
