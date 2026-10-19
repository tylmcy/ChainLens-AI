package io.chainlens.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.chainlens.evaluation.EvaluationDataset.CalldataExpectation;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationDataset.ExpectedValue;
import io.chainlens.evaluation.EvaluationDataset.GroundTruth;
import io.chainlens.evaluation.EvaluationDataset.Source;
import io.chainlens.evaluation.EvaluationDataset.SourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationDatasetLoaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final String HASH = "0x" + "a".repeat(64);
    static final String TO = "0x" + "b".repeat(40);

    @TempDir Path temp;

    @Test
    void fullDatasetRoundTripsAndValidates() throws Exception {
        Path fixtures = temp.resolve("fixtures");
        List<EvaluationCase> cases = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String id = "case-" + i;
            SourceType type = i < 15 ? SourceType.ANVIL : SourceType.HISTORICAL;
            long chainId = type == SourceType.ANVIL ? 31337 : 1;
            writeFixture(fixtures.resolve(id), chainId);
            cases.add(testCase(id, type));
        }
        Path manifest = temp.resolve("dataset.json");
        MAPPER.writeValue(manifest.toFile(), new EvaluationDataset("transaction-eval", "v1", cases));

        EvaluationDataset loaded = EvaluationDatasetLoader.load(manifest, fixtures);

        assertThat(loaded.cases()).hasSize(30);
        assertThat(loaded).isEqualTo(MAPPER.readValue(manifest.toFile(), EvaluationDataset.class));
    }

    @Test
    void rejectsMissingApplicableGroundTruthWithCaseId() throws Exception {
        Path fixture = temp.resolve("case-one");
        writeFixture(fixture, 31337);
        EvaluationCase valid = testCase("case-one", SourceType.ANVIL);
        GroundTruth old = valid.groundTruth();
        GroundTruth broken = new GroundTruth(null, old.valueWei(), old.gasCostWei(), old.fromNetWei(),
                old.toNetWei(), old.calldata(), old.transfers(), old.approvals(), old.expectedFindings());
        EvaluationCase item = new EvaluationCase(valid.caseId(), valid.sourceType(), valid.network(),
                valid.chainId(), valid.txHash(), valid.source(), valid.fixtureDir(), broken);

        assertThatThrownBy(() -> EvaluationDatasetLoader.validateCase(item, temp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("case-one")
                .hasMessageContaining("status");
    }

    @Test
    void rejectsMissingFixtureAndContradictoryHash() throws Exception {
        EvaluationCase item = testCase("case-one", SourceType.ANVIL);
        assertThatThrownBy(() -> EvaluationDatasetLoader.validateCase(item, temp))
                .hasMessageContaining("case-one").hasMessageContaining("chain-id");

        writeFixture(temp.resolve("case-one"), 31337);
        Files.writeString(temp.resolve("case-one/transaction.json"), envelope(
                transaction("0x" + "f".repeat(64))));
        assertThatThrownBy(() -> EvaluationDatasetLoader.validateCase(item, temp))
                .hasMessageContaining("case-one").hasMessageContaining("交易哈希");
    }

    @Test
    void rejectsDuplicateCaseIdAndWrongSourceRatio() throws Exception {
        Path fixture = temp.resolve("case-one");
        writeFixture(fixture, 31337);
        EvaluationCase item = testCase("case-one", SourceType.ANVIL);
        List<EvaluationCase> duplicate = new ArrayList<>();
        for (int i = 0; i < 30; i++) duplicate.add(item);
        assertThatThrownBy(() -> EvaluationDatasetLoader.validate(
                new EvaluationDataset("transaction-eval", "v1", duplicate), temp))
                .hasMessageContaining("case-one").hasMessageContaining("重复");

        List<EvaluationCase> allAnvil = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String id = "anvil-" + i;
            writeFixture(temp.resolve(id), 31337);
            allAnvil.add(testCase(id, SourceType.ANVIL));
        }
        assertThatThrownBy(() -> EvaluationDatasetLoader.validate(
                new EvaluationDataset("transaction-eval", "v1", allAnvil), temp))
                .hasMessageContaining("ANVIL=30").hasMessageContaining("HISTORICAL=0");
    }

    @Test
    void replaysRpcAndKeepsCodeUnavailableExplicit() throws Exception {
        Path fixture = temp.resolve("case-one");
        writeFixture(fixture, 31337);
        FixtureEthRpcClient rpc = new FixtureEthRpcClient("case-one", HASH, fixture);
        assertThat(rpc.chainId()).isEqualTo(31337);
        assertThat(rpc.getTransactionByHash(HASH).path("hash").asText()).isEqualTo(HASH);
        assertThat(rpc.getTransactionReceipt(HASH).path("transactionHash").asText()).isEqualTo(HASH);
        assertThat(rpc.getBlockByNumber(rpc.blockNumber()).path("number").asText()).isEqualTo("0x1");
        assertThat(rpc.getCode(TO, rpc.blockNumber()).asText()).isEqualTo("0x");
        assertThatThrownBy(() -> rpc.getCode("0x" + "c".repeat(40), rpc.blockNumber()))
                .hasMessageContaining("case-one").hasMessageContaining("之外");

        Files.writeString(fixture.resolve("code.json"),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"historical state unavailable\"}}");
        FixtureEthRpcClient unavailable = new FixtureEthRpcClient("case-one", HASH, fixture);
        assertThatThrownBy(() -> unavailable.getCode(TO, unavailable.blockNumber()))
                .hasMessageContaining("case-one").hasMessageContaining("historical state unavailable");

        Files.delete(fixture.resolve("code.json"));
        assertThatThrownBy(() -> new FixtureEthRpcClient("case-one", HASH, fixture))
                .hasMessageContaining("case-one").hasMessageContaining("code");
    }

    static EvaluationCase testCase(String id, SourceType type) {
        long chainId = type == SourceType.ANVIL ? 31337 : 1;
        String network = type == SourceType.ANVIL ? "anvil-local" : "mainnet";
        ExpectedValue status = new ExpectedValue(true, "SUCCESS", "/result/status");
        ExpectedValue zero = new ExpectedValue(true, "0", "/result/value");
        GroundTruth truth = new GroundTruth(status, zero, zero, zero, zero,
                new CalldataExpectation(true, "UNRESOLVED", null, null, "/result/input"),
                List.of(), List.of(), List.of());
        return new EvaluationCase(id, type, network, chainId, HASH,
                new Source("test source", "https://example.org/tx", "2026-10-18", "Apache-2.0"), id, truth);
    }

    static void writeFixture(Path directory, long chainId) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("chain-id.json"), envelope("\"0x" + Long.toHexString(chainId) + "\""));
        Files.writeString(directory.resolve("transaction.json"), envelope(
                transaction(HASH)));
        Files.writeString(directory.resolve("receipt.json"), envelope(
                "{\"transactionHash\":\"" + HASH + "\",\"blockNumber\":\"0x1\",\"status\":\"0x1\",\"gasUsed\":\"0x5208\",\"logs\":[]}"));
        Files.writeString(directory.resolve("block.json"), envelope("{\"number\":\"0x1\",\"timestamp\":\"0x1\"}"));
        Files.writeString(directory.resolve("code.json"), envelope("\"0x\""));
    }

    private static String transaction(String hash) {
        return "{\"hash\":\"" + hash + "\",\"blockNumber\":\"0x1\",\"to\":\"" + TO
                + "\",\"from\":\"0x" + "c".repeat(40)
                + "\",\"value\":\"0x0\",\"input\":\"0x\",\"gas\":\"0x5208\",\"type\":\"0x0\"}";
    }

    private static String envelope(String result) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + result + "}";
    }
}
