package io.chainlens.knowledge;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeStartupSynchronizerTest {

    @Test
    void ready事件不等待同步完成且成功后标记可用() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        KnowledgeIngestionService service = service(() -> {
            entered.countDown();
            await(release);
            return new KnowledgeSyncStats(1, 0, 0, 0, 0);
        });
        KnowledgeReadiness readiness = new KnowledgeReadiness();
        KnowledgeStartupSynchronizer synchronizer = new KnowledgeStartupSynchronizer(service, readiness);

        long started = System.nanoTime();
        synchronizer.onApplicationEvent(null);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(elapsedMs).isLessThan(100);
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(readiness.current().availability()).isEqualTo(KnowledgeSearch.Availability.NOT_READY);
        release.countDown();
        awaitStatus(readiness, KnowledgeSearch.Availability.AVAILABLE);
    }

    @Test
    void 同步失败只把知识状态标为不可用() throws Exception {
        KnowledgeReadiness readiness = new KnowledgeReadiness();
        KnowledgeStartupSynchronizer synchronizer = new KnowledgeStartupSynchronizer(
                service(() -> { throw new IllegalStateException("db down"); }), readiness);

        synchronizer.onApplicationEvent(null);

        awaitStatus(readiness, KnowledgeSearch.Availability.UNAVAILABLE);
        assertThat(readiness.current().reason()).contains("同步失败");
    }

    private static KnowledgeIngestionService service(java.util.function.Supplier<KnowledgeSyncStats> action) {
        return new KnowledgeIngestionService(null, null, null, null, null) {
            @Override
            public KnowledgeSyncStats synchronize() {
                return action.get();
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void awaitStatus(KnowledgeReadiness readiness,
                                    KnowledgeSearch.Availability expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (readiness.current().availability() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(readiness.current().availability()).isEqualTo(expected);
    }
}
