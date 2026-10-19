package io.chainlens.evaluation;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.chainlens.chain.TxFactsAssembler;
import io.chainlens.evaluation.EvaluationDataset.ApprovalExpectation;
import io.chainlens.evaluation.EvaluationDataset.CalldataExpectation;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationDataset.ExpectedValue;
import io.chainlens.evaluation.EvaluationDataset.GroundTruth;
import io.chainlens.evaluation.EvaluationDataset.Source;
import io.chainlens.evaluation.EvaluationDataset.SourceType;
import io.chainlens.evaluation.EvaluationDataset.TransferExpectation;
import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.TransferEvent;
import io.chainlens.model.TxFacts;

import static org.assertj.core.api.Assertions.assertThat;

class ParsingComparatorTest {

    private static final String A = "0x" + "1".repeat(40);
    private static final String B = "0x" + "2".repeat(40);
    private static final String TOKEN = "0x" + "3".repeat(40);

    @TempDir Path temp;

    @Test
    void missingApplicableValueCountsAsErrorWhileInapplicableDoesNot() throws Exception {
        TxFacts facts = facts();
        GroundTruth truth = truth(List.of(), List.of(),
                new ExpectedValue(true, "0", "/result/gasUsed"));
        ParsingComparator.Result result = ParsingComparator.compare(item(truth), facts);
        assertThat(result.overall().total()).isEqualTo(10);
        assertThat(result.overall().correct()).isEqualTo(9);
        assertThat(result.differences()).extracting(d -> d.fieldPath()).containsExactly("gasCostWei");

        GroundTruth skipGas = truth(List.of(), List.of(), new ExpectedValue(false, null, null));
        ParsingComparator.Result skipped = ParsingComparator.compare(item(skipGas), facts);
        assertThat(skipped.overall().total()).isEqualTo(9);
        assertThat(skipped.overall().correct()).isEqualTo(9);
    }

    @Test
    void comparesEventsByStableMultisetOrderAndReportsMissingOrExtra() throws Exception {
        TxFacts base = facts();
        TransferEvent first = new TransferEvent(TOKEN, A, B, BigInteger.TEN, BigInteger.ONE);
        TransferEvent second = new TransferEvent(TOKEN, A, B, BigInteger.TEN, BigInteger.TWO);
        ApprovalEvent approval = new ApprovalEvent(TOKEN, A, B, BigInteger.ONE, BigInteger.valueOf(3));
        TxFacts reordered = withEvents(base, List.of(second, first), List.of(approval));
        GroundTruth truth = truth(List.of(
                transfer("2"), transfer("1")), List.of(
                new ApprovalExpectation(TOKEN, A, B, "1", "3", "/result/logs/2")),
                new ExpectedValue(false, null, null));

        ParsingComparator.Result correct = ParsingComparator.compare(item(truth), reordered);
        assertThat(correct.differences()).isEmpty();

        TxFacts missing = withEvents(base, List.of(first), List.of());
        ParsingComparator.Result wrong = ParsingComparator.compare(item(truth), missing);
        assertThat(wrong.differences()).extracting(d -> d.fieldPath())
                .contains("transfers.count", "approvals.count", "transfers[1].logIndex");
        assertThat(wrong.categories().get("transfers").correct())
                .isLessThan(wrong.categories().get("transfers").total());
    }

    private TxFacts facts() throws Exception {
        EvaluationDatasetLoaderTest.writeFixture(temp.resolve("case-one"), 31337);
        return new TxFactsAssembler(new FixtureEthRpcClient("case-one",
                EvaluationDatasetLoaderTest.HASH, temp.resolve("case-one")))
                .assemble(EvaluationDatasetLoaderTest.HASH);
    }

    private static GroundTruth truth(List<TransferExpectation> transfers,
                                     List<ApprovalExpectation> approvals, ExpectedValue gas) {
        ExpectedValue status = new ExpectedValue(true, "SUCCESS", "/result/status");
        ExpectedValue zero = new ExpectedValue(true, "0", "/result/value");
        return new GroundTruth(status, zero, gas, zero, zero,
                new CalldataExpectation(true, "UNRESOLVED", null, null, "/result/input"),
                transfers, approvals, List.of());
    }

    private static TransferExpectation transfer(String index) {
        return new TransferExpectation(TOKEN, A, B, "10", index, "/result/logs/" + (Integer.parseInt(index) - 1));
    }

    private static EvaluationCase item(GroundTruth truth) {
        return new EvaluationCase("case-one", SourceType.ANVIL, "anvil-local", 31337,
                EvaluationDatasetLoaderTest.HASH,
                new Source("test", "anvil://test", "2026-10-18", "Apache-2.0"), "case-one", truth);
    }

    private static TxFacts withEvents(TxFacts base, List<TransferEvent> transfers, List<ApprovalEvent> approvals) {
        return new TxFacts(base.network(), base.transaction(), base.receipt(), base.block(),
                base.gasCostWei(), base.valueWei(), base.fromNetWei(), base.toNetWei(),
                transfers, approvals, base.unknownLogTopics(), base.calldata(),
                base.contractCode(), base.fetchedAt());
    }
}
