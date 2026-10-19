package io.chainlens.evaluation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationDataset.ExpectedFinding;
import io.chainlens.evaluation.EvaluationDataset.ExpectedValue;
import io.chainlens.evaluation.EvaluationDataset.GroundTruth;
import io.chainlens.evaluation.EvaluationDataset.SourceType;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineEvaluationRunnerTest {

    @TempDir Path temp;

    @Test
    void repeatsThirtyCasesWithoutExternalServices() throws Exception {
        Suite suite = suite(temp);
        EvaluationDataset loaded = EvaluationDatasetLoader.load(suite.manifest(), suite.fixtures());
        OfflineEvaluationRunner runner = new OfflineEvaluationRunner();

        EvaluationResult first = runner.run(loaded, suite.fixtures(), "unavailable");
        EvaluationResult second = runner.run(loaded, suite.fixtures(), "unavailable");

        assertThat(first.passed()).isTrue();
        assertThat(first.ruleAccuracy().total()).isEqualTo(210);
        assertThat(first.ruleAccuracy().trueNegative()).isEqualTo(210);
        assertThat(first.parsingAccuracy().correct()).isEqualTo(first.parsingAccuracy().total());
        assertThat(first.cases()).isEqualTo(second.cases());
        assertThat(first.parsingAccuracy()).isEqualTo(second.parsingAccuracy());

        List<EvaluationCase> reversed = new ArrayList<>(loaded.cases());
        java.util.Collections.reverse(reversed);
        EvaluationResult reordered = runner.run(new EvaluationDataset("transaction-eval", "v1", reversed),
                suite.fixtures(), "unavailable");
        assertThat(reordered.cases()).isEqualTo(first.cases());
    }

    @Test
    void cliSucceedsAndWritesMatchingJsonAndMarkdown() throws Exception {
        Suite suite = suite(temp);
        Path output = temp.resolve("reports");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exit = OfflineEvaluationCli.run(new String[] {
                "--mode=offline", "--dataset=" + suite.manifest(),
                "--fixtures=" + suite.fixtures(), "--output=" + output
        }, new PrintStream(stdout), new PrintStream(stderr));

        assertThat(exit).isZero();
        assertThat(stdout.toString()).contains("规则 210/210").contains("通过=true");
        assertThat(stderr.toString()).isEmpty();
        assertThat(Files.readString(output.resolve("latest.json"))).contains("\"passed\" : true");
        assertThat(Files.readString(output.resolve("latest.md"))).contains("210/210").contains("全部用例无差异");
    }

    @Test
    void createsReportsAndReturnsNonzeroForRuleRegression() throws Exception {
        Suite suite = suite(temp);
        EvaluationDataset original = EvaluationDatasetLoader.load(suite.manifest(), suite.fixtures());
        EvaluationCase first = original.cases().getFirst();
        GroundTruth old = first.groundTruth();
        GroundTruth modified = new GroundTruth(old.status(), old.valueWei(), old.gasCostWei(),
                old.fromNetWei(), old.toNetWei(), old.calldata(), old.transfers(), old.approvals(),
                List.of(new ExpectedFinding("TX-FAILED", "HIGH", 1.0,
                        "eth_getTransactionReceipt", "/result/status", null)));
        List<EvaluationCase> cases = new ArrayList<>(original.cases());
        cases.set(0, new EvaluationCase(first.caseId(), first.sourceType(), first.network(),
                first.chainId(), first.txHash(), first.source(), first.fixtureDir(), modified));
        new ObjectMapper().writeValue(suite.manifest().toFile(),
                new EvaluationDataset(original.suiteId(), original.version(), cases));

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Path output = temp.resolve("reports");
        int exit = OfflineEvaluationCli.run(new String[] {
                "--dataset=" + suite.manifest(), "--fixtures=" + suite.fixtures(), "--output=" + output
        }, new PrintStream(stdout), new PrintStream(stderr));

        assertThat(exit).isEqualTo(1);
        assertThat(Files.readString(output.resolve("latest.json"))).contains("falseNegative");
        assertThat(Files.readString(output.resolve("latest.md"))).contains("case-00").contains("FN");
        assertThat(stderr.toString()).isEmpty();
    }

    @Test
    void parserErrorIsVisibleAndNeverPassesGate() throws Exception {
        Suite suite = suite(temp);
        EvaluationDataset loaded = EvaluationDatasetLoader.load(suite.manifest(), suite.fixtures());
        Files.writeString(suite.fixtures().resolve("case-00/transaction.json"), "{\"jsonrpc\":\"2.0\",\"result\":null}");

        EvaluationResult result = new OfflineEvaluationRunner().run(loaded, suite.fixtures(), "unavailable");

        assertThat(result.passed()).isFalse();
        assertThat(result.cases().getFirst().differences()).singleElement()
                .satisfies(d -> assertThat(d.category()).isEqualTo("ERROR"));
    }

    static Suite suite(Path directory) throws Exception {
        Path fixtures = directory.resolve("fixtures");
        Path manifest = directory.resolve("dataset.json");
        List<EvaluationCase> cases = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String id = String.format("case-%02d", i);
            SourceType type = i < 15 ? SourceType.ANVIL : SourceType.HISTORICAL;
            EvaluationDatasetLoaderTest.writeFixture(fixtures.resolve(id),
                    type == SourceType.ANVIL ? 31337 : 1);
            EvaluationCase base = EvaluationDatasetLoaderTest.testCase(id, type);
            GroundTruth truth = base.groundTruth();
            GroundTruth corrected = new GroundTruth(truth.status(), truth.valueWei(),
                    new ExpectedValue(false, null, null), truth.fromNetWei(), truth.toNetWei(),
                    truth.calldata(), truth.transfers(), truth.approvals(), truth.expectedFindings());
            cases.add(new EvaluationCase(base.caseId(), base.sourceType(), base.network(),
                    base.chainId(), base.txHash(), base.source(), base.fixtureDir(), corrected));
        }
        new ObjectMapper().writeValue(manifest.toFile(), new EvaluationDataset("transaction-eval", "v1", cases));
        return new Suite(manifest, fixtures);
    }

    record Suite(Path manifest, Path fixtures) {
    }
}
