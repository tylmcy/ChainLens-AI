package io.chainlens.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import io.chainlens.knowledge.KnowledgeIngestionService;
import io.chainlens.knowledge.KnowledgeReadiness;
import io.chainlens.knowledge.KnowledgeSyncStats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeAdminControllerTest {

    @Test
    void 管理开关默认关闭时不注册端点() {
        adminContext().run(context -> assertThat(context)
                .doesNotHaveBean(KnowledgeAdminController.class));
    }

    @Test
    void 管理开关显式开启时才注册端点() {
        adminContext()
                .withPropertyValues(
                        "chainlens.knowledge.admin-enabled=true",
                        "chainlens.knowledge.enabled=true",
                        "spring.ai.model.embedding=transformers")
                .run(context -> assertThat(context)
                        .hasSingleBean(KnowledgeAdminController.class));
    }

    @Test
    void 管理开关开启但Rag未就绪时不注册端点() {
        adminContext()
                .withPropertyValues("chainlens.knowledge.admin-enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(KnowledgeAdminController.class));
    }

    @Test
    void loopback调用同步固定知识集并返回统计() {
        CountingIngestionService service = new CountingIngestionService();
        KnowledgeReadiness readiness = new KnowledgeReadiness();
        KnowledgeAdminController controller = new KnowledgeAdminController(service, readiness);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        KnowledgeSyncStats stats = controller.synchronize(request);

        assertThat(stats).isEqualTo(new KnowledgeSyncStats(3, 1, 0, 20, 0));
        assertThat(service.calls).isEqualTo(1);
        assertThat(readiness.current().availability()).isEqualTo(
                io.chainlens.knowledge.KnowledgeSearch.Availability.AVAILABLE);
    }

    @Test
    void 远端地址被拒绝且不会执行同步() {
        CountingIngestionService service = new CountingIngestionService();
        KnowledgeAdminController controller = new KnowledgeAdminController(service, new KnowledgeReadiness());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");

        assertThatThrownBy(() -> controller.synchronize(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(service.calls).isZero();
    }

    /** 控制器没有 path/url 入参，测试替身只能执行预先配置好的固定同步。 */
    private static final class CountingIngestionService extends KnowledgeIngestionService {
        private int calls;

        private CountingIngestionService() {
            super(null, null, null, null, null);
        }

        @Override
        public KnowledgeSyncStats synchronize() {
            calls++;
            return new KnowledgeSyncStats(3, 1, 0, 20, 0);
        }
    }

    private static ApplicationContextRunner adminContext() {
        return new ApplicationContextRunner()
                .withBean(KnowledgeIngestionService.class, CountingIngestionService::new)
                .withBean(KnowledgeReadiness.class, KnowledgeReadiness::new)
                .withUserConfiguration(KnowledgeAdminController.class);
    }
}
