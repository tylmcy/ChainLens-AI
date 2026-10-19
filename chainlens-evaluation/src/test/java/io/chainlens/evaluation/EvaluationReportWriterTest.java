package io.chainlens.evaluation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationReportWriterTest {

    @TempDir Path temp;

    @Test
    void jsonAndMarkdownUseSameSortedResultAndAtomicFiles() throws Exception {
        OfflineEvaluationRunnerTest.Suite suite = OfflineEvaluationRunnerTest.suite(temp);
        EvaluationResult result = new OfflineEvaluationRunner().run(
                EvaluationDatasetLoader.load(suite.manifest(), suite.fixtures()), suite.fixtures(), "unavailable");
        Path reports = temp.resolve("reports");

        EvaluationReportWriter.Written files = new EvaluationReportWriter().write(result, reports);

        assertThat(files.json()).exists();
        assertThat(files.markdown()).exists();
        assertThat(new ObjectMapper().readTree(files.json().toFile()).path("cases").size()).isEqualTo(30);
        String markdown = Files.readString(files.markdown());
        assertThat(markdown).contains("210/210", "Git commit：`unavailable`", "全部用例无差异");
        try (var paths = Files.list(reports)) {
            assertThat(paths.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("latest.json", "latest.md");
        }
    }

    @Test
    void unavailableGitRevisionIsExplicit() {
        assertThat(GitRevision.current(temp, null)).isEqualTo("unavailable");
    }

    @Test
    void markdownSnapshotKeepsNumeratorsAndThresholds() {
        EvaluationResult result = new EvaluationResult("transaction-eval", "v1", "rules-v1",
                "offline", "unavailable", Instant.parse("2026-10-18T00:00:00Z"),
                new EvaluationResult.Metric(49, 50, .98),
                new EvaluationResult.RuleMetric(10, 200, 0, 0, 1.0),
                Map.of("status", new EvaluationResult.Metric(30, 30, .98)),
                List.of(), true);

        assertThat(new EvaluationReportWriter().renderMarkdown(result)).isEqualTo("""
                # ChainLens 交易评测结果

                - 数据集：`transaction-eval/v1`
                - 模式：`offline`
                - Git commit：`unavailable`
                - 规则版本：`rules-v1`
                - 执行时间：`2026-10-18T00:00:00Z`
                - 结论：**通过**

                | 指标 | 正确/总数 | 结果 | 门槛 |
                |---|---:|---:|---:|
                | 解析准确率 | 49/50 | 98.00% | 98% |
                | 规则准确率 | 210/210 | 100.00% | 100% |

                规则混淆矩阵：TP=10、TN=200、FP=0、FN=0。

                ## 逐类解析

                - status：30/30（100.00%）

                ## 用例差异

                全部用例无差异。
                """);
    }
}
