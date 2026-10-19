package io.chainlens.evaluation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.chainlens.evaluation.EvaluationDataset.ApprovalExpectation;
import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationDataset.ExpectedValue;
import io.chainlens.evaluation.EvaluationDataset.TransferExpectation;
import io.chainlens.evaluation.EvaluationResult.Difference;
import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.TransferEvent;
import io.chainlens.model.TxFacts;

/** 逐原子字段核对解析结果；不适用字段不进入分母，缺失适用字段算错。 */
public final class ParsingComparator {

    public record Count(long correct, long total) {
        Count plus(Count other) {
            return new Count(correct + other.correct, total + other.total);
        }
    }

    public record Result(Count overall, Map<String, Count> categories, List<Difference> differences) {
    }

    private final String caseId;
    private final Map<String, Count> categories = new LinkedHashMap<>();
    private final List<Difference> differences = new ArrayList<>();

    private ParsingComparator(String caseId) {
        this.caseId = caseId;
    }

    /** 只读 TxFacts 与人工 ground truth 的差异；生产解析器负责生成实际值。 */
    public static Result compare(EvaluationCase item, TxFacts facts) {
        ParsingComparator c = new ParsingComparator(item.caseId());
        var truth = item.groundTruth();
        c.field("status", "status", truth.status(), facts.receipt().isSuccess() ? "SUCCESS" : "FAILED");
        c.field("eth", "valueWei", truth.valueWei(), decimal(facts.valueWei()));
        c.field("eth", "gasCostWei", truth.gasCostWei(), decimal(facts.gasCostWei()));
        c.field("eth", "fromNetWei", truth.fromNetWei(), decimal(facts.fromNetWei()));
        c.field("eth", "toNetWei", truth.toNetWei(), decimal(facts.toNetWei()));
        if (truth.calldata().applicable()) {
            String ref = truth.calldata().rawJsonRef();
            c.atom("calldata", "calldata.status", truth.calldata().status(),
                    facts.calldata().status().name(), ref);
            c.atom("calldata", "calldata.selector", truth.calldata().selector(),
                    facts.calldata().selector(), ref);
            c.atom("calldata", "calldata.resolvedSignature", truth.calldata().resolvedSignature(),
                    facts.calldata().resolvedSignature(), ref);
        }
        c.transfers(truth.transfers(), facts.transfers());
        c.approvals(truth.approvals(), facts.approvals());
        Count overall = c.categories.values().stream().reduce(new Count(0, 0), Count::plus);
        return new Result(overall, Map.copyOf(c.categories), List.copyOf(c.differences));
    }

    private void field(String category, String name, ExpectedValue expected, String actual) {
        if (expected.applicable()) {
            atom(category, name, expected.value(), actual, expected.rawJsonRef());
        }
    }

    private void transfers(List<TransferExpectation> expected, List<TransferEvent> actual) {
        atom("transfers", "transfers.count", Integer.toString(expected.size()),
                Integer.toString(actual.size()), "/result/logs");
        List<TransferExpectation> left = expected.stream().sorted(Comparator.comparing(
                TransferExpectation::logIndex, ParsingComparator::compareDecimal)
                .thenComparing(TransferExpectation::tokenAddress)).toList();
        List<TransferEvent> right = actual.stream().sorted(Comparator.comparing(
                TransferEvent::logIndex).thenComparing(TransferEvent::tokenAddress)).toList();
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            TransferExpectation e = i < left.size() ? left.get(i) : null;
            TransferEvent a = i < right.size() ? right.get(i) : null;
            String path = "transfers[" + i + "]";
            String ref = e == null ? "/result/logs" : e.rawJsonRef();
            atom("transfers", path + ".logIndex", e == null ? null : e.logIndex(),
                    a == null ? null : decimal(a.logIndex()), ref);
            atom("transfers", path + ".tokenAddress", e == null ? null : e.tokenAddress(),
                    a == null ? null : a.tokenAddress(), ref);
            atom("transfers", path + ".from", e == null ? null : e.from(),
                    a == null ? null : a.from(), ref);
            atom("transfers", path + ".to", e == null ? null : e.to(),
                    a == null ? null : a.to(), ref);
            atom("transfers", path + ".valueWei", e == null ? null : e.valueWei(),
                    a == null ? null : decimal(a.valueWei()), ref);
        }
    }

    private void approvals(List<ApprovalExpectation> expected, List<ApprovalEvent> actual) {
        atom("approvals", "approvals.count", Integer.toString(expected.size()),
                Integer.toString(actual.size()), "/result/logs");
        List<ApprovalExpectation> left = expected.stream().sorted(Comparator.comparing(
                ApprovalExpectation::logIndex, ParsingComparator::compareDecimal)
                .thenComparing(ApprovalExpectation::tokenAddress)).toList();
        List<ApprovalEvent> right = actual.stream().sorted(Comparator.comparing(
                ApprovalEvent::logIndex).thenComparing(ApprovalEvent::tokenAddress)).toList();
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            ApprovalExpectation e = i < left.size() ? left.get(i) : null;
            ApprovalEvent a = i < right.size() ? right.get(i) : null;
            String path = "approvals[" + i + "]";
            String ref = e == null ? "/result/logs" : e.rawJsonRef();
            atom("approvals", path + ".logIndex", e == null ? null : e.logIndex(),
                    a == null ? null : decimal(a.logIndex()), ref);
            atom("approvals", path + ".tokenAddress", e == null ? null : e.tokenAddress(),
                    a == null ? null : a.tokenAddress(), ref);
            atom("approvals", path + ".owner", e == null ? null : e.owner(),
                    a == null ? null : a.owner(), ref);
            atom("approvals", path + ".spender", e == null ? null : e.spender(),
                    a == null ? null : a.spender(), ref);
            atom("approvals", path + ".valueWei", e == null ? null : e.valueWei(),
                    a == null ? null : decimal(a.valueWei()), ref);
        }
    }

    private void atom(String category, String path, String expected, String actual, String ref) {
        boolean same = normalize(expected).equals(normalize(actual));
        Count old = categories.getOrDefault(category, new Count(0, 0));
        categories.put(category, new Count(old.correct() + (same ? 1 : 0), old.total() + 1));
        if (!same) {
            differences.add(new Difference(caseId, "PARSING", path, null, expected, actual,
                    null, null, ref));
        }
    }

    private static String normalize(String value) {
        if (value == null) return "<missing>";
        if (value.matches("0x[0-9a-fA-F]{40}")) return value.toLowerCase(java.util.Locale.ROOT);
        return value;
    }

    private static String decimal(BigInteger value) {
        return value == null ? null : value.toString();
    }

    private static int compareDecimal(String left, String right) {
        return new BigInteger(left).compareTo(new BigInteger(right));
    }
}
