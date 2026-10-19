package io.chainlens.evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** 从同一 Full 结果对象生成 JSON 与 Markdown，保留成本未知和未执行样本。 */
public final class FullEvaluationReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public record Written(Path json, Path markdown) {
    }

    /** 输出目录由命令入口选定，文件名固定以便 Actions 上传 artifact。 */
    public Written write(FullEvaluationResult result, Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
            Path json = outputDirectory.resolve("full.json");
            Path markdown = outputDirectory.resolve("full.md");
            EvaluationReportWriter.writeAtomically(json, MAPPER.writeValueAsBytes(result));
            EvaluationReportWriter.writeAtomically(markdown, renderMarkdown(result).getBytes(StandardCharsets.UTF_8));
            return new Written(json, markdown);
        } catch (IOException e) {
            throw new IllegalStateException("无法写入 Full 评测报告: " + outputDirectory, e);
        }
    }

    /** 启动前预检失败也留下不含 RPC URL 或密钥的可上传失败产物。 */
    public Written writeFailure(Path outputDirectory, String failureType) {
        try {
            Files.createDirectories(outputDirectory);
            Path json = outputDirectory.resolve("full-error.json");
            Path markdown = outputDirectory.resolve("full-error.md");
            Map<String, Object> failure = Map.of("mode", "full", "passed", false,
                    "executedAt", Instant.now().toString(), "failureType", failureType);
            EvaluationReportWriter.writeAtomically(json, MAPPER.writeValueAsBytes(failure));
            EvaluationReportWriter.writeAtomically(markdown,
                    ("# Full 评测未启动或中断\n\n失败类型：`" + safe(failureType)
                            + "`。请在本地检查配置和节点状态；产物不包含凭据。\n")
                            .getBytes(StandardCharsets.UTF_8));
            return new Written(json, markdown);
        } catch (IOException e) {
            throw new IllegalStateException("无法写入 Full 失败产物", e);
        }
    }

    /** 文本仅投影机器结果，不单独计算指标或把未知成本当作零。 */
    public String renderMarkdown(FullEvaluationResult result) {
        StringBuilder out = new StringBuilder("# ChainLens Full 交易评测\n\n");
        out.append("- 数据集：`").append(result.suiteId()).append("/")
                .append(result.datasetVersion()).append("`\n");
        out.append("- 模式：`").append(result.mode()).append("`\n");
        out.append("- Git commit：`").append(result.gitCommit()).append("`\n");
        out.append("- 规则版本：`").append(result.ruleVersion()).append("`\n");
        out.append("- 执行时间：`").append(result.executedAt()).append("`\n");
        out.append("- Provider / 模型：`").append(result.budget().config().provider()).append(" / ")
                .append(result.budget().config().model()).append("`\n");
        out.append("- 结论：**").append(result.passed() ? "通过" : "失败或不完整").append("**\n\n");
        out.append("| 指标 | 正确/总数 | 结果 | 门槛 |\n|---|---:|---:|---:|\n");
        metric(out, "解析准确率", result.parsingAccuracy(), "98%");
        metric(out, "规则准确率", result.ruleAccuracy(), "100%");
        metric(out, "工具调用成功率", result.toolSuccessRate(), "95%");
        metric(out, "事实一致率", result.factConsistencyRate(), "98%");
        out.append("\n## 延迟（纳秒，最近秩 P50/P95）\n\n");
        for (Map.Entry<String, FullEvaluationResult.Latency> entry : result.latencyNanos().entrySet()
                .stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var latency = entry.getValue();
            out.append("- ").append(entry.getKey()).append("：样本 ").append(latency.samples())
                    .append("，P50=").append(latency.p50() == null ? "不可计算" : latency.p50())
                    .append("，P95=").append(latency.p95() == null ? "不可计算" : latency.p95())
                    .append("\n");
        }
        var budget = result.budget();
        out.append("\n## 模型费用（USD）\n\n");
        out.append("- 单价来源日期：").append(budget.config().priceAsOf()).append("\n");
        out.append("- 输入 token 上界来源：").append(safe(budget.config().contextWindowSource())).append("\n");
        out.append("- 输入/输出单价（每百万 token）：")
                .append(budget.config().inputUsdPerMillion()).append(" / ")
                .append(budget.config().outputUsdPerMillion()).append("\n");
        out.append("- 预算：").append(budget.config().budgetUsd()).append("\n");
        out.append("- 已核实费用：").append(budget.verifiedCostUsd()).append("\n");
        out.append("- 费用不可核实的保留预留：").append(budget.unresolvedReserveUsd()).append("\n");
        if (budget.stopped()) {
            out.append("- 停止原因：").append(safe(budget.stopReason())).append("\n");
        }
        var usage = result.modelUsage();
        out.append("\n## 模型 token 用量（provider 原始返回）\n\n");
        out.append("- 调用数：").append(usage.calls()).append("；可结算 usage：")
                .append(usage.completeCalls()).append("；缺失输入/输出 usage：")
                .append(usage.incompleteCalls()).append("\n");
        out.append("- 已报告输入/输出 token 合计：").append(usage.inputTokens())
                .append(" / ").append(usage.outputTokens()).append("\n");
        out.append("- provider 报告的 total token 合计：").append(usage.reportedTotalTokens())
                .append("；未报告 total 的调用数：").append(usage.missingReportedTotalCalls()).append("\n");
        out.append("\n## 用例与差异\n\n");
        for (FullEvaluationResult.Case item : result.cases()) {
            out.append("- `").append(item.caseId()).append("`：")
                    .append(item.passed() ? "通过" : "失败")
                    .append("；已核实费用 ").append(item.verifiedCostUsd()).append(" USD\n");
            for (int index = 0; index < item.modelCalls().size(); index++) {
                var call = item.modelCalls().get(index);
                out.append("  - 模型调用 ").append(index + 1).append("：输入 ")
                        .append(token(call.inputTokens())).append("，输出 ")
                        .append(token(call.outputTokens())).append("，provider total ")
                        .append(token(call.totalTokens())).append(" token\n");
            }
            item.differences().forEach(diff -> out.append("  - ").append(safe(diff.fieldPath()))
                    .append("：预期 ").append(safe(diff.expected()))
                    .append("，实际 ").append(safe(diff.actual()))
                    .append("；ruleId=").append(safe(diff.ruleId()))
                    .append("；原始位置 ").append(safe(diff.rawJsonRef())).append("\n"));
        }
        if (!result.unexecutedCaseIds().isEmpty()) {
            out.append("\n未执行用例：").append(String.join("、", result.unexecutedCaseIds())).append("。\n");
        }
        out.append("\n费用由模型返回的 usage 和配置单价计算；provider 隐藏重试与最终账单需另行核对。\n");
        return out.toString();
    }

    private static void metric(StringBuilder out, String name, FullEvaluationResult.Count count, String threshold) {
        out.append("| ").append(name).append(" | ").append(count.correct()).append("/")
                .append(count.total()).append(" | ")
                .append(Double.isNaN(count.ratio()) ? "不可计算"
                        : String.format(Locale.ROOT, "%.2f%%", count.ratio() * 100))
                .append(" | ").append(threshold).append(" |\n");
    }

    private static String safe(String value) {
        return value == null ? "无" : value.replace("|", "\\|").replace("`", "'")
                .replace("\n", " ").replace("\r", " ");
    }

    private static String token(Integer value) {
        return value == null ? "不可用" : value.toString();
    }
}
