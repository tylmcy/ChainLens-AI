package io.chainlens.api;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.chainlens.chain.rpc.RpcUnavailableException;
import io.chainlens.chain.rpc.TransactionNotFoundException;
import io.chainlens.model.AnalysisReport;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.AnalysisStatus;
import io.chainlens.model.Network;
import io.chainlens.model.TxFacts;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制器契约测试：JSON/SSE 两种响应、输入校验、错误契约映射（400/404/429/503）。
 * 编排服务与仓库均为 mock（契约只验证 HTTP 层行为）。
 */
@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    private static final String HASH = "0xabc0000000000000000000000000000000000000000000000000000000000001";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnalysisApplicationService service;

    @MockitoBean
    AnalysisRepository repository;

    private static AnalysisResult result(String txHash) {
        return new AnalysisResult(
                "id-1", txHash, Network.ANVIL,
                new AnalysisReport("摘要", AnalysisStatus.SUCCESS, List.of(), List.of(),
                        List.of(), List.of(TemplateReportGeneratorDisclaimers.DISCLAIMER)),
                null, List.of(), List.of(), false, false, true, 123L, Instant.EPOCH);
    }

    /** 仅用于填充 limitations 的常量（与 agent 模块的免责声明一致，避免测试引入 agent 依赖）。 */
    private static final class TemplateReportGeneratorDisclaimers {
        static final String DISCLAIMER = "本报告由自动化工具基于公开链上数据生成，不构成投资建议，也不是完整的安全审计；请结合多方信息自行判断。";
    }

    @Test
    @DisplayName("POST txHash → 200，返回完整分析结果")
    void analyzeWithTxHash() throws Exception {
        when(service.analyze(HASH)).thenReturn(result(HASH));

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txHash\":\"" + HASH + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("id-1"))
                .andExpect(jsonPath("$.llmUsed").value(false))
                .andExpect(jsonPath("$.report.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST question（含哈希）→ 纯正则提取后进入分析")
    void analyzeWithQuestion() throws Exception {
        when(service.analyze(HASH)).thenReturn(result(HASH));

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"请分析这笔交易 " + HASH + " 的风险\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("id-1"));
    }

    @Test
    @DisplayName("无法提取哈希 → 400 INVALID_INPUT")
    void missingHashReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"以太坊是什么\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("哈希格式非法 → 400 INVALID_INPUT")
    void malformedHashReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txHash\":\"0x1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("question 超 500 字符 → 400（请求大小治理）")
    void oversizedQuestionReturns400() throws Exception {
        String longQuestion = "问".repeat(501);
        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + longQuestion + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("交易不存在 → 404 TRANSACTION_NOT_FOUND")
    void transactionNotFoundReturns404() throws Exception {
        when(service.analyze(HASH)).thenThrow(new TransactionNotFoundException(HASH));

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txHash\":\"" + HASH + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("节点不可达 → 503 RPC_UNAVAILABLE")
    void rpcUnavailableReturns503() throws Exception {
        when(service.analyze(HASH)).thenThrow(new RpcUnavailableException("节点不可达"));

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txHash\":\"" + HASH + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RPC_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    @DisplayName("并发超限 → 429 TOO_MANY_REQUESTS + Retry-After")
    void tooManyRequestsReturns429() throws Exception {
        when(service.analyze(HASH)).thenThrow(new TooManyRequestsException("并发分析数已达上限"));

        mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txHash\":\"" + HASH + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("GET /{id} 命中 → 200；未命中 → 404 ANALYSIS_NOT_FOUND")
    void getById() throws Exception {
        when(repository.findById("id-1")).thenReturn(java.util.Optional.of(result(HASH)));

        mockMvc.perform(get("/api/v1/analyses/id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("id-1"));

        mockMvc.perform(get("/api/v1/analyses/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    }

    @Test
    @DisplayName("未注册的 API 路径 → 404，不被全局兜底转成 500")
    void unregisteredApiReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge/sync"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("SSE 模式：POST + Accept text/event-stream → 流式事件序列")
    void analyzeWithSseStream() throws Exception {
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        when(service.startStreaming(HASH)).thenReturn(emitter);

        var mvcResult = mockMvc.perform(post("/api/v1/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"txHash\":\"" + HASH + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.complete();
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }
}
