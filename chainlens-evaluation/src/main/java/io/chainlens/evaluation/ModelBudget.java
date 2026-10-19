package io.chainlens.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full 评测的模型调用准入闸门。每次调用前预留最坏情况费用，响应后才按真实
 * input/output usage 结算。无法核实费用时保留预留并终止，绝不写成零费用。
 */
public final class ModelBudget {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    /** 单价单位为 USD / 百万 token；上界必须来自已核对的模型配置。 */
    public record Config(String provider, String model, LocalDate priceAsOf,
                         String contextWindowSource, int maxInputTokens, int maxOutputTokens,
                         BigDecimal inputUsdPerMillion, BigDecimal outputUsdPerMillion,
                         BigDecimal budgetUsd) {
        public Config {
            if (blank(provider) || blank(model) || priceAsOf == null || blank(contextWindowSource)
                    || maxInputTokens <= 0 || maxOutputTokens <= 0
                    || inputUsdPerMillion == null || outputUsdPerMillion == null || budgetUsd == null
                    || inputUsdPerMillion.signum() < 0 || outputUsdPerMillion.signum() < 0
                    || budgetUsd.signum() <= 0
                    || (!"ollama".equalsIgnoreCase(provider)
                        && inputUsdPerMillion.signum() == 0 && outputUsdPerMillion.signum() == 0)) {
                throw new IllegalArgumentException("Full 模式缺少模型、单价来源、token 上界或正预算");
            }
        }

        private static boolean blank(String text) {
            return text == null || text.isBlank();
        }
    }

    public record Snapshot(Config config, BigDecimal verifiedCostUsd,
                           BigDecimal unresolvedReserveUsd, Map<String, BigDecimal> caseCostsUsd,
                           boolean stopped, String stopReason) {
    }

    /** 预算或真实 usage 不可用时中止当前用例与后续模型调用。 */
    public static final class Halt extends RuntimeException {
        public Halt(String message) {
            super(message);
        }
    }

    private final Config config;
    private final Map<String, BigDecimal> caseCosts = new LinkedHashMap<>();
    private BigDecimal verifiedCost = BigDecimal.ZERO;
    private BigDecimal unresolvedReserve = BigDecimal.ZERO;
    private BigDecimal pendingReserve;
    private String currentCase;
    private String stopReason;

    public ModelBudget(Config config) {
        this.config = config;
    }

    /** 开始一个用例；模型调用必须属于明确的 caseId。 */
    public void beginCase(String caseId) {
        if (stopReason != null) {
            throw new Halt(stopReason);
        }
        if (caseId == null || caseId.isBlank() || pendingReserve != null) {
            throw new IllegalStateException("预算用例状态无效");
        }
        currentCase = caseId;
    }

    /** requestMaxOutputTokens 来自实际 Prompt 选项，不能仅信任评测配置。 */
    public void beforeCall(Integer requestMaxOutputTokens) {
        if (stopReason != null) {
            throw new Halt(stopReason);
        }
        if (currentCase == null || pendingReserve != null) {
            throw new IllegalStateException("模型预算调用状态无效");
        }
        if (requestMaxOutputTokens == null || requestMaxOutputTokens <= 0
                || requestMaxOutputTokens > config.maxOutputTokens()) {
            stopReason = "模型请求缺少可核对的 maxTokens，或超过配置的输出 token 上界";
            throw new Halt(stopReason);
        }
        BigDecimal reserve = cost(config.maxInputTokens(), requestMaxOutputTokens);
        if (verifiedCost.add(unresolvedReserve).add(reserve).compareTo(config.budgetUsd()) > 0) {
            stopReason = "下一次模型调用的最坏情况预留费用超过剩余预算";
            throw new Halt(stopReason);
        }
        pendingReserve = reserve;
    }

    /** 根据 provider 返回的真实 token 结算；超出声明上界是严重配置错误。 */
    public void settle(Integer inputTokens, Integer outputTokens) {
        if (pendingReserve == null) {
            throw new IllegalStateException("没有待结算的模型调用");
        }
        if (inputTokens == null || outputTokens == null || inputTokens < 0 || outputTokens < 0) {
            unresolvedReserve = unresolvedReserve.add(pendingReserve);
            pendingReserve = null;
            stopReason = "模型未返回完整 token usage，实际费用不可核实";
            throw new Halt(stopReason);
        }
        BigDecimal actual = cost(inputTokens, outputTokens);
        verifiedCost = verifiedCost.add(actual);
        caseCosts.merge(currentCase, actual, BigDecimal::add);
        pendingReserve = null;
        if (inputTokens > config.maxInputTokens() || outputTokens > config.maxOutputTokens()) {
            stopReason = "模型实际 token 超出声明上界，预算预留不再可靠";
            throw new Halt(stopReason);
        }
        if (verifiedCost.compareTo(config.budgetUsd()) >= 0) {
            stopReason = "已核实模型费用触及预算上限";
        }
    }

    /** 异常响应可能已由 provider 计费，因此保留预留并停止。 */
    public void failCall(String errorType) {
        if (pendingReserve != null) {
            unresolvedReserve = unresolvedReserve.add(pendingReserve);
            pendingReserve = null;
        }
        if (stopReason == null) {
            stopReason = "模型调用失败（" + errorType + "），实际费用不可核实";
        }
    }

    public boolean stopped() {
        return stopReason != null;
    }

    public Snapshot snapshot() {
        return new Snapshot(config, verifiedCost, unresolvedReserve,
                Map.copyOf(caseCosts), stopped(), stopReason);
    }

    private BigDecimal cost(int inputTokens, int outputTokens) {
        return config.inputUsdPerMillion().multiply(BigDecimal.valueOf(inputTokens))
                .add(config.outputUsdPerMillion().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 12, RoundingMode.UP).stripTrailingZeros();
    }
}
