package io.chainlens.evaluation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** CI 与本地共用的 30 条离线质量门槛；即使失败也保留 JSON/Markdown 差异报告。 */
@Tag("transaction-evaluation")
class TransactionOfflineEvaluationGateTest {

    @TempDir Path temp;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fullFrozenDatasetMeetsOfflineThresholds() {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path output = Path.of(System.getProperty("chainlens.evaluation.output",
                root.resolve("eval/results/offline").toString()));
        Path dataset = Path.of(System.getProperty("chainlens.evaluation.dataset",
                root.resolve("eval/cases/transaction-eval-v1.json").toString()));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code = OfflineEvaluationCli.run(new String[] {
                "--mode=offline",
                "--dataset=" + dataset,
                "--fixtures=" + root.resolve("eval/fixtures"),
                "--output=" + output
        }, new PrintStream(stdout), new PrintStream(stderr));

        assertThat(code).as("%s%n%s", stdout, stderr).isZero();
        assertThat(Files.exists(output.resolve("latest.json"))).isTrue();
        assertThat(Files.exists(output.resolve("latest.md"))).isTrue();
    }

    @Test
    void parsingRegressionFailsAndKeepsDiffReport() throws Exception {
        ObjectNode dataset = officialDatasetCopy();
        ArrayNode cases = (ArrayNode) dataset.get("cases");
        // 故意把所有成功交易标成失败，确保低于 98%，但不触碰受控数据集。
        for (var item : cases) {
            ObjectNode status = (ObjectNode) item.path("groundTruth").path("status");
            if ("SUCCESS".equals(status.path("value").asText())) status.put("value", "FAILED");
        }

        int exit = runMutated(dataset, "parsing-failure");

        assertThat(exit).isEqualTo(1);
        String json = Files.readString(temp.resolve("parsing-failure/latest.json"));
        assertThat(json).contains("\"passed\" : false", "\"category\" : \"PARSING\"",
                "\"expected\" : \"FAILED\"", "\"actual\" : \"SUCCESS\"");
    }

    @Test
    void ruleRegressionFailsAndKeepsClassification() throws Exception {
        ObjectNode dataset = officialDatasetCopy();
        ObjectNode caseItem = null;
        for (var item : (ArrayNode) dataset.get("cases")) {
            if ("anvil-plain-eth".equals(item.path("caseId").asText())) {
                caseItem = (ObjectNode) item;
                break;
            }
        }
        assertThat(caseItem).isNotNull();
        ArrayNode findings = (ArrayNode) caseItem.path("groundTruth").path("expectedFindings");
        ObjectNode falseExpectation = MAPPER.createObjectNode();
        falseExpectation.put("ruleId", "TX-FAILED");
        falseExpectation.put("severity", "MEDIUM");
        falseExpectation.put("confidence", 1.0);
        falseExpectation.put("rpcMethod", "eth_getTransactionReceipt");
        falseExpectation.put("jsonPointer", "/result/status");
        falseExpectation.putNull("logIndex");
        findings.add(falseExpectation);

        int exit = runMutated(dataset, "rule-failure");

        assertThat(exit).isEqualTo(1);
        String json = Files.readString(temp.resolve("rule-failure/latest.json"));
        String markdown = Files.readString(temp.resolve("rule-failure/latest.md"));
        assertThat(json).contains("\"falseNegative\" : 1", "\"category\" : \"FN\"",
                "\"ruleId\" : \"TX-FAILED\"");
        assertThat(markdown).contains("FN=1", "TX-FAILED");
    }

    /** 每次复制正式清单到临时目录，故障注入结束后正式 ground truth 原样保留。 */
    private ObjectNode officialDatasetCopy() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        return (ObjectNode) MAPPER.readTree(root.resolve("eval/cases/transaction-eval-v1.json").toFile());
    }

    private int runMutated(ObjectNode dataset, String name) throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path manifest = temp.resolve(name + ".json");
        MAPPER.writeValue(manifest.toFile(), dataset);
        return OfflineEvaluationCli.run(new String[] {
                "--dataset=" + manifest,
                "--fixtures=" + root.resolve("eval/fixtures"),
                "--output=" + temp.resolve(name)
        }, new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
    }
}
