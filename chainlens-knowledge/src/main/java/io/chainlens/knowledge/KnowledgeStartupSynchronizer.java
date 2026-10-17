package io.chainlens.knowledge;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/** 应用 ready 后用虚拟线程同步知识，避免模型下载或数据库故障阻塞主服务启动。 */
public final class KnowledgeStartupSynchronizer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStartupSynchronizer.class);
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeReadiness readiness;
    private final AtomicBoolean started = new AtomicBoolean();

    public KnowledgeStartupSynchronizer(KnowledgeIngestionService ingestionService, KnowledgeReadiness readiness) {
        this.ingestionService = ingestionService;
        this.readiness = readiness;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("chainlens-knowledge-sync").start(() -> {
            readiness.syncing();
            try {
                KnowledgeSyncStats stats = ingestionService.synchronize();
                readiness.ready();
                log.info("知识库同步完成: {}", stats);
            } catch (RuntimeException e) {
                readiness.failed("知识库同步失败");
                log.warn("知识库同步失败，确定性分析仍可继续", e);
            }
        });
    }
}
