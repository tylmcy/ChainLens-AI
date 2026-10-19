package io.chainlens.evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.chainlens.evaluation.EvaluationResult.CaseResult;
import io.chainlens.evaluation.EvaluationResult.Difference;

/** 从同一评测结果生成 JSON 与 Markdown，并分别原子替换，避免留下半份报告。 */
public final class EvaluationReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public record Written(Path json, Path markdown) {
    }

    /** 结果目录由调用方决定，默认约定文件名 latest.json/latest.md。 */
    public Written write(EvaluationResult result, Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
            Path json = outputDirectory.resolve("latest.json");
            Path markdown = outputDirectory.resolve("latest.md");
            writeAtomically(json, MAPPER.writeValueAsBytes(result));
            writeAtomically(markdown, renderMarkdown(result).getBytes(StandardCharsets.UTF_8));
            return new Written(json, markdown);
        } catch (IOException e) {
            throw new IllegalStateException("无法写入评测报告: " + outputDirectory, e);
        }
    }

    static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".chainlens-eval-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** 文本只展示机器结果中的数字和差异，不重新评分。 */
    public String renderMarkdown(EvaluationResult result) {
        StringBuilder out = new StringBuilder();
        out.append("# ChainLens 交易评测结果\n\n");
        out.append("- 数据集：`").append(result.suiteId()).append("/").append(result.datasetVersion()).append("`\n");
        out.append("- 模式：`").append(result.mode()).append("`\n");
        out.append("- Git commit：`").append(result.gitCommit()).append("`\n");
        out.append("- 规则版本：`").append(result.ruleVersion()).append("`\n");
        out.append("- 执行时间：`").append(result.executedAt()).append("`\n");
        out.append("- 结论：**").append(result.passed() ? "通过" : "失败").append("**\n\n");
        out.append("| 指标 | 正确/总数 | 结果 | 门槛 |\n|---|---:|---:|---:|\n");
        out.append("| 解析准确率 | ").append(result.parsingAccuracy().correct()).append("/")
                .append(result.parsingAccuracy().total()).append(" | ")
                .append(percent(result.parsingAccuracy().ratio())).append(" | 98% |\n");
        out.append("| 规则准确率 | ").append(result.ruleAccuracy().correct()).append("/")
                .append(result.ruleAccuracy().total()).append(" | ")
                .append(percent(result.ruleAccuracy().ratio())).append(" | 100% |\n\n");
        out.append("规则混淆矩阵：TP=").append(result.ruleAccuracy().truePositive())
                .append("、TN=").append(result.ruleAccuracy().trueNegative())
                .append("、FP=").append(result.ruleAccuracy().falsePositive())
                .append("、FN=").append(result.ruleAccuracy().falseNegative()).append("。\n\n");
        out.append("## 逐类解析\n\n");
        result.parsingByCategory().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> out.append("- ").append(entry.getKey()).append("：")
                        .append(entry.getValue().correct()).append("/").append(entry.getValue().total())
                        .append("（").append(percent(entry.getValue().ratio())).append("）\n"));
        out.append("\n## 用例差异\n\n");
        boolean hasDifference = false;
        for (CaseResult item : result.cases()) {
            for (Difference diff : item.differences()) {
                hasDifference = true;
                out.append("- `").append(item.caseId()).append("` ")
                        .append(diff.category()).append(" `").append(diff.fieldPath()).append("`：预期 `")
                        .append(safe(diff.expected())).append("`，实际 `")
                        .append(safe(diff.actual())).append("`；ruleId=`")
                        .append(safe(diff.ruleId())).append("`；原始位置 `")
                        .append(safe(diff.rawJsonRef())).append("`。\n");
            }
        }
        if (!hasDifference) out.append("全部用例无差异。\n");
        return out.toString();
    }

    private static String percent(double ratio) {
        return Double.isNaN(ratio) ? "不可计算" : String.format(Locale.ROOT, "%.2f%%", ratio * 100);
    }

    private static String safe(String value) {
        return value == null ? "无" : value.replace("|", "\\|").replace("`", "'")
                .replace("\n", " ").replace("\r", " ");
    }
}
