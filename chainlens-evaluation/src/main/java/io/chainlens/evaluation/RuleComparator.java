package io.chainlens.evaluation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;
import io.chainlens.evaluation.EvaluationDataset.ExpectedFinding;
import io.chainlens.evaluation.EvaluationResult.Difference;
import io.chainlens.evaluation.EvaluationResult.RuleMetric;
import io.chainlens.model.Evidence;
import io.chainlens.model.Finding;

/** 七规则逐用例二分类，同时检查每项命中的证据结构。 */
public final class RuleComparator {

    public record Decision(String ruleId, String classification) {
    }

    public record Result(RuleMetric metric, List<Decision> decisions, List<Difference> differences) {
    }

    private RuleComparator() {
    }

    /** 每条用例必须恰好评估七个互异 ruleId；结构错误计作该规则判断失败。 */
    public static Result compare(EvaluationCase item, List<Finding> actual, List<String> ruleIds) {
        if (ruleIds.size() != 7 || new HashSet<>(ruleIds).size() != 7) {
            throw new IllegalArgumentException(item.caseId() + " 评测规则集合必须恰好包含七个互异 ruleId");
        }
        for (Finding finding : actual) {
            if (finding == null || !ruleIds.contains(finding.ruleId())) {
                throw new IllegalArgumentException(item.caseId() + " 规则引擎返回未知或空 finding");
            }
        }
        long tp = 0, tn = 0, fp = 0, fn = 0;
        List<Decision> decisions = new ArrayList<>();
        List<Difference> differences = new ArrayList<>();
        for (String id : ruleIds.stream().sorted().toList()) {
            List<ExpectedFinding> expected = item.groundTruth().expectedFindings().stream()
                    .filter(f -> id.equals(f.ruleId())).sorted(Comparator.comparing(ExpectedFinding::jsonPointer)).toList();
            List<Finding> observed = actual.stream().filter(f -> id.equals(f.ruleId()))
                    .sorted(Comparator.comparing(f -> pointer(f.evidence()))).toList();
            String classification;
            if (expected.isEmpty() && observed.isEmpty()) {
                tn++;
                classification = "TN";
            } else if (expected.isEmpty()) {
                fp++;
                classification = "FP";
                differences.add(diff(item.caseId(), classification, id, null, observed.getFirst(),
                        "未预期命中却产生 finding"));
            } else if (observed.isEmpty()) {
                fn++;
                classification = "FN";
                differences.add(diff(item.caseId(), classification, id, expected.getFirst(), null,
                        "预期命中但未产生 finding"));
            } else if (expected.size() != observed.size() || !sameFindings(expected, observed)) {
                fn++;
                classification = "FN";
                differences.add(diff(item.caseId(), "STRUCTURE", id, expected.getFirst(), observed.getFirst(),
                        "finding 数量或 ruleId/severity/confidence/evidence/原始定位不一致"));
            } else {
                tp++;
                classification = "TP";
            }
            decisions.add(new Decision(id, classification));
        }
        return new Result(new RuleMetric(tp, tn, fp, fn, 1.0), List.copyOf(decisions), List.copyOf(differences));
    }

    private static boolean sameFindings(List<ExpectedFinding> expected, List<Finding> actual) {
        for (int i = 0; i < expected.size(); i++) {
            ExpectedFinding e = expected.get(i);
            Finding a = actual.get(i);
            Evidence ev = a.evidence();
            if (!e.ruleId().equals(a.ruleId()) || !e.severity().equals(a.severity().name())
                    || Double.compare(e.confidence(), a.confidence()) != 0 || ev == null
                    || ev.rawJsonRef() == null || ev.transactionHash() == null
                    || !e.rpcMethod().equals(ev.rawJsonRef().rpcMethod())
                    || !e.jsonPointer().equals(ev.rawJsonRef().jsonPointer())
                    || !sameIndex(e.logIndex(), ev.logIndex())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameIndex(String expected, BigInteger actual) {
        return expected == null ? actual == null : actual != null && expected.equals(actual.toString());
    }

    private static String pointer(Evidence evidence) {
        return evidence == null || evidence.rawJsonRef() == null
                ? "" : evidence.rawJsonRef().jsonPointer();
    }

    private static Difference diff(String caseId, String category, String ruleId,
                                   ExpectedFinding expected, Finding actual, String reason) {
        Evidence evidence = actual == null ? null : actual.evidence();
        String ref = evidence == null || evidence.rawJsonRef() == null
                ? expected == null ? "/result" : expected.jsonPointer()
                : evidence.rawJsonRef().jsonPointer();
        String actualText = actual == null ? null : actual.ruleId() + "/" + actual.severity();
        return new Difference(caseId, category, "findings." + ruleId, ruleId,
                expected == null ? null : expected.ruleId() + "/" + expected.severity(), actualText,
                actual == null ? null : Double.toString(actual.confidence()),
                reason + (evidence == null ? "" : "；" + evidence), ref);
    }
}
