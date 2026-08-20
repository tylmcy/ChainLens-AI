package io.chainlens.api;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.chainlens.agent.AnalysisProgressListener;
import io.chainlens.agent.AnalysisService;
import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.Finding;

/**
 * API 层分析编排（设计文档 3.1 / 3.2 / 3.7）：
 * <ul>
 *   <li>并发闸：最多 {@code chainlens.concurrency.max}（默认 4）个并发分析，超出 429；</li>
 *   <li>同步 JSON 模式：分析 → 落库（DB 失败 persisted=false，结果仍返回）→ 响应；</li>
 *   <li>SSE 模式：分析在虚拟线程执行器上运行，进度实时推送；
 *       客户端断开只停止推送，分析继续完成并落库（结果按 analysisId 可查）；</li>
 *   <li>SseEmitter 超时 5 分钟，与整次分析硬上限一致（设计文档 9.2）。</li>
 * </ul>
 */
@Service
public class AnalysisApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisApplicationService.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60_000L;

    private final AnalysisService analysisService;
    private final AnalysisRepository repository;
    private final Semaphore semaphore;
    private final ExecutorService analysisExecutor;

    public AnalysisApplicationService(AnalysisService analysisService,
                                      AnalysisRepository repository,
                                      Semaphore semaphore,
                                      ExecutorService analysisExecutor) {
        this.analysisService = analysisService;
        this.repository = repository;
        this.semaphore = semaphore;
        this.analysisExecutor = analysisExecutor;
    }

    /** 同步分析并落库。 */
    public AnalysisResult analyze(String txHash) {
        return withPermit(() -> {
            AnalysisResult result = analysisService.analyze(txHash, AnalysisProgressListener.noop());
            return persistQuietly(result);
        });
    }

    /** SSE 直播：立即返回 emitter，分析在虚拟线程上执行。 */
    public SseEmitter startStreaming(String txHash) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        if (!semaphore.tryAcquire()) {
            // 响应已进入流模式，无法改回 HTTP 429：以 analysis.failed 事件返回契约错误
            TooManyRequestsException error = new TooManyRequestsException(
                    "并发分析数已达上限，请稍后重试");
            sendEvent(emitter, "analysis.failed", ErrorMapper.map(error));
            emitter.complete();
            return emitter;
        }
        SseBridge bridge = new SseBridge(emitter);
        analysisExecutor.submit(() -> {
            try {
                AnalysisResult result = analysisService.analyze(txHash, bridge);
                persistQuietly(result);
            } catch (Throwable e) {
                log.warn("SSE 分析失败: {}", e.getMessage());
                bridge.onAnalysisFailed(e);
            } finally {
                semaphore.release();
                emitter.complete();
            }
        });
        return emitter;
    }

    /** 从历史重放时间线（M3 无工具步骤：单事件回放；M5 后回放完整 tool_steps）。 */
    public SseEmitter replay(String analysisId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        analysisExecutor.submit(() -> {
            try {
                Optional<AnalysisResult> stored = repository.findById(analysisId);
                if (stored.isEmpty()) {
                    sendEvent(emitter, "analysis.failed",
                            ErrorMapper.map(new AnalysisNotFoundException(analysisId)));
                } else {
                    sendEvent(emitter, "analysis.completed", stored.get());
                }
            } catch (Throwable e) {
                sendEvent(emitter, "analysis.failed", ErrorMapper.map(e));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private AnalysisResult persistQuietly(AnalysisResult result) {
        try {
            repository.save(result);
            return withPersisted(result, true);
        } catch (RuntimeException e) {
            log.error("分析结果落库失败（persisted=false，结果仍返回）: {}", e.getMessage());
            return withPersisted(result, false);
        }
    }

    private static AnalysisResult withPersisted(AnalysisResult result, boolean persisted) {
        return new AnalysisResult(result.analysisId(), result.txHash(), result.network(), result.report(),
                result.facts(), result.findings(), result.toolSteps(), result.llmUsed(), result.degraded(),
                persisted, result.totalDurationMs(), result.createdAt());
    }

    private <T> T withPermit(java.util.concurrent.Callable<T> action) {
        if (!semaphore.tryAcquire()) {
            throw new TooManyRequestsException("并发分析数已达上限，请稍后重试");
        }
        try {
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } finally {
            semaphore.release();
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开：停止推送（分析继续完成并落库，设计文档 3.7）
            log.debug("SSE 推送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /** AnalysisProgressListener → SSE 事件桥。 */
    private final class SseBridge implements AnalysisProgressListener {

        private final SseEmitter emitter;

        private SseBridge(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onStage(String stage, String message) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stage", stage);
            data.put("message", message);
            sendEvent(emitter, "stage.started", data);
        }

        @Override
        public void onRulesCompleted(List<Finding> findings) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", findings.size());
            data.put("ruleIds", findings.stream().map(Finding::ruleId).toList());
            sendEvent(emitter, "rules.completed", data);
        }

        @Override
        public void onReportCompleted(AnalysisReport report) {
            sendEvent(emitter, "report.completed", report);
        }

        @Override
        public void onAnalysisCompleted(AnalysisResult result) {
            sendEvent(emitter, "analysis.completed", result);
        }

        @Override
        public void onAnalysisFailed(Throwable error) {
            sendEvent(emitter, "analysis.failed", ErrorMapper.map(error));
        }
    }
}
