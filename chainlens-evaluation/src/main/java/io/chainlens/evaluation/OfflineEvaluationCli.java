package io.chainlens.evaluation;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 命令行入口；默认 offline，只有显式 full 才允许将来进入付费链路。 */
public final class OfflineEvaluationCli {

    private OfflineEvaluationCli() {
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    /** 可测试的执行入口，返回进程退出码而不在测试进程中调用 System.exit。 */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Map<String, String> options = options(args);
            String mode = options.getOrDefault("mode", "offline");
            if (!"offline".equals(mode)) {
                throw new IllegalArgumentException("当前入口仅支持 --mode=offline；full 模式需显式专用入口");
            }
            Path manifest = Path.of(options.getOrDefault("dataset", "eval/cases/transaction-eval-v1.json"));
            Path fixtures = Path.of(options.getOrDefault("fixtures", "eval/fixtures"));
            Path output = Path.of(options.getOrDefault("output", "eval/results/offline"));
            EvaluationDataset dataset = EvaluationDatasetLoader.load(manifest, fixtures);
            EvaluationResult result = new OfflineEvaluationRunner().run(dataset, fixtures,
                    GitRevision.current(Path.of(".")));
            EvaluationReportWriter.Written written = new EvaluationReportWriter().write(result, output);
            out.println("offline 解析 " + result.parsingAccuracy().correct() + "/"
                    + result.parsingAccuracy().total() + "，规则 " + result.ruleAccuracy().correct()
                    + "/" + result.ruleAccuracy().total() + "，通过=" + result.passed());
            out.println("报告：" + written.json() + "；" + written.markdown());
            return result.passed() ? 0 : 1;
        } catch (RuntimeException e) {
            err.println("交易评测失败：" + e.getMessage());
            return 2;
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("参数应为 --名称=值：" + arg);
            }
            int equals = arg.indexOf('=');
            String key = arg.substring(2, equals);
            if (!java.util.Set.of("mode", "dataset", "fixtures", "output").contains(key)
                    || arg.substring(equals + 1).isBlank()) {
                throw new IllegalArgumentException("未知或空参数：" + arg);
            }
            options.put(key, arg.substring(equals + 1));
        }
        return options;
    }
}
