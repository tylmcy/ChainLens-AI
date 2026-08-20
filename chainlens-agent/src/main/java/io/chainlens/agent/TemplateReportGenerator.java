package io.chainlens.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.Finding;
import io.chainlens.model.Severity;
import io.chainlens.model.TxFacts;
import io.chainlens.model.WeiFormat;

/**
 * 确定性模板报告生成器（设计文档 3.3 降级路径）：
 * LLM 不可用时仍产出结构化中文报告，llmUsed=false 由 AnalysisResult 标注。
 *
 * <p>硬性约束（设计文档 6.3 / 9.5）：
 * <ul>
 *   <li>findings 原样呈现规则引擎结果（机器字段零改动）；</li>
 *   <li>limitations 首条必须是免责声明；</li>
 *   <li>sources 只引用链上数据定位（知识库 M7 接入后增加 KNOWLEDGE 类）。</li>
 * </ul>
 */
public final class TemplateReportGenerator implements ReportGenerator {

    public static final String DISCLAIMER =
            "本报告由自动化工具基于公开链上数据生成，不构成投资建议，也不是完整的安全审计；请结合多方信息自行判断。";

    /** 规则 → 建议动作（确定性映射）。 */
    private static final Map<String, String> ACTIONS = Map.of(
            "ERC20-UNLIMITED-APPROVAL", "立即撤销对 spender 的授权（调用 approve(spender, 0) 将额度归零）",
            "RISK-ADDRESS-HIT", "交易参与方命中风险地址列表，请核对交互对象并停止进一步转账",
            "TRANSFER-TO-ZERO-ADDRESS", "请确认转入零地址是否为预期的销毁操作",
            "INTERACTION-WITH-EOA", "请核对目标地址是否为预期的合约地址",
            "UNKNOWN-SELECTOR", "请核对目标合约地址与 ABI 来源",
            "TX-FAILED", "交易未生效，无需处理；若为本人操作，请检查 gas 与 revert 原因后重试",
            "MANY-TOKEN-TRANSFERS", "请核对是否为预期的批量操作（分发/空投/拆分）"
    );

    @Override
    public AnalysisReport generate(TxFacts facts, List<Finding> findings) {
        List<String> actions = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Finding finding : findings) {
            String action = ACTIONS.get(finding.ruleId());
            if (action != null && seen.putIfAbsent(finding.ruleId(), true) == null) {
                actions.add(action);
            }
        }

        List<String> limitations = new ArrayList<>();
        limitations.add(DISCLAIMER);
        limitations.add("本报告由确定性模板生成，未使用 LLM（llmUsed=false）。");
        limitations.add("V1 不解析 revert 原因（需 trace 重放）。");
        if (facts.calldata() != null && facts.calldata().hasInput()
                && facts.calldata().status() != io.chainlens.model.CalldataAnalysis.Status.RESOLVED) {
            limitations.add("calldata 函数签名无法唯一解析，请以候选列表为准。");
        }
        if (facts.contractCode() != null && !facts.contractCode().available()) {
            limitations.add("目标地址的历史合约代码不可查（" + facts.contractCode().unavailableReason() + "）。");
        }
        if (!facts.unknownLogTopics().isEmpty()) {
            limitations.add("存在 " + facts.unknownLogTopics().size() + " 类未能解码的事件日志。");
        }

        List<AnalysisReport.Source> sources = List.of(
                new AnalysisReport.Source("CHAIN", "交易", "eth_getTransactionByHash / " + facts.transaction().hash()),
                new AnalysisReport.Source("CHAIN", "回执", "eth_getTransactionReceipt / " + facts.transaction().hash()),
                new AnalysisReport.Source("CHAIN", "区块", "eth_getBlockByNumber / " + facts.receipt().blockNumber())
        );

        return new AnalysisReport(
                summary(facts, findings),
                facts.receipt().isSuccess() ? AnalysisStatus.SUCCESS : AnalysisStatus.FAILED,
                actions,
                findings,
                sources,
                limitations
        );
    }

    private static String summary(TxFacts facts, List<Finding> findings) {
        long high = findings.stream().filter(f -> f.severity() == Severity.HIGH).count();
        long medium = findings.stream().filter(f -> f.severity() == Severity.MEDIUM).count();
        long low = findings.stream().filter(f -> f.severity() == Severity.LOW).count();
        String statusText = facts.receipt().isSuccess() ? "成功" : "失败";
        return String.format(
                "该交易在 %s 网络上执行%s：从 %s 向 %s 转出 %s ETH（另支付 gas 约 %s ETH），"
                        + "涉及 %d 次 ERC-20 转移与 %d 次授权；确定性规则引擎命中 %d 条风险"
                        + "（高 %d / 中 %d / 低 %d）。",
                facts.network().label(),
                statusText,
                shortAddress(facts.transaction().from()),
                facts.transaction().to() == null ? "(合约创建)" : shortAddress(facts.transaction().to()),
                WeiFormat.formatEther(facts.valueWei()),
                WeiFormat.formatEther(facts.gasCostWei()),
                facts.transfers().size(),
                facts.approvals().size(),
                findings.size(),
                high, medium, low
        );
    }

    private static String shortAddress(String address) {
        if (address == null) {
            return "(空)";
        }
        return address.substring(0, 10) + "…" + address.substring(address.length() - 8);
    }
}
