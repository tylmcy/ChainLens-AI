package io.chainlens.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.chainlens.model.ToolCallStep;

/**
 * 工具调用步骤记录器（设计文档 4.6）：
 * 每次工具调用记录名称、参数、耗时、成败与结果摘要，产出 {@link ToolCallStep} 时间线。
 * 线程本地存储：SSE 分析任务在独立虚拟线程执行，同一请求内的调用共享一条时间线。
 */
public final class ToolStepRecorder {

    private static final ThreadLocal<List<ToolCallStep>> STEPS = ThreadLocal.withInitial(ArrayList::new);

    private ToolStepRecorder() {
    }

    /** 记录一次已完成/失败的工具调用。 */
    public static void record(String toolName, String arguments, Instant startedAt,
                              long durationMs, boolean success, String summary) {
        STEPS.get().add(new ToolCallStep(toolName, arguments, startedAt, durationMs, success, summary));
    }

    /** 取当前线程的完整时间线（快照，不消费）。 */
    public static List<ToolCallStep> snapshot() {
        return List.copyOf(STEPS.get());
    }

    /** 清理当前线程的记录（请求结束或失败路径调用）。 */
    public static void clear() {
        STEPS.get().clear();
    }
}
