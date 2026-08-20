package io.chainlens.api;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.chainlens.agent.AnalysisService;
import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.Network;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE 事件流契约测试：使用真实 AnalysisApplicationService + 假 AnalysisService，
 * 验证 stage.started → rules.completed → report.completed → analysis.completed 事件序列。
 */
@WebMvcTest(AnalysisController.class)
@Import(SseAnalysisStreamTest.TestConfig.class)
class SseAnalysisStreamTest {

    private static final String HASH = "0xabc0000000000000000000000000000000000000000000000000000000000001";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnalysisRepository repository;

    @Test
    @DisplayName("SSE 直播：事件按设计顺序流式返回，最终事件含完整结果")
    void streamsStructuredEvents() throws Exception {
        var mvcResult = mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"txHash\":\"" + HASH + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:stage.started")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:rules.completed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:report.completed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:analysis.completed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"analysisId\":\"id-sse\"")));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        Semaphore analysisSemaphore() {
            return new Semaphore(4);
        }

        @Bean(destroyMethod = "shutdown")
        ExecutorService analysisExecutor() {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        /** 假编排服务：同步触发全部回调，便于断言事件序列。 */
        @Bean
        AnalysisService analysisService() {
            return (txHash, listener) -> {
                listener.onStage("forensics", "开始链上取证与解析");
                listener.onRulesCompleted(List.of());
                AnalysisReport report = new AnalysisReport("摘要", AnalysisStatus.SUCCESS,
                        List.of(), List.of(), List.of(),
                        List.of("本报告由自动化工具基于公开链上数据生成，不构成投资建议，也不是完整的安全审计。"));
                listener.onReportCompleted(report);
                AnalysisResult result = new AnalysisResult("id-sse", txHash, Network.ANVIL, report,
                        null, List.of(), List.of(), false, false, false, 1L, Instant.EPOCH);
                listener.onAnalysisCompleted(result);
                return result;
            };
        }

        @Bean
        AnalysisApplicationService analysisApplicationService(AnalysisService analysisService,
                                                              AnalysisRepository repository,
                                                              Semaphore semaphore,
                                                              ExecutorService executor) {
            return new AnalysisApplicationService(analysisService, repository, semaphore, executor);
        }
    }
}
