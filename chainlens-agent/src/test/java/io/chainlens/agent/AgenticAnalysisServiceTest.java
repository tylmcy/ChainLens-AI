package io.chainlens.agent;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import io.chainlens.agent.knowledge.NoopKnowledgeSearch;
import io.chainlens.chain.parse.CuratedSelectorRegistry;
import io.chainlens.chain.rpc.EthRpcClient;
import io.chainlens.model.ApprovalEvent;
import io.chainlens.model.AnalysisResult;
import io.chainlens.model.Finding;
import io.chainlens.model.TxFacts;
import io.chainlens.rules.RuleEngine;
import io.chainlens.rules.rule.UnlimitedApprovalRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgenticAnalysisService 离线测试：脚本化 ChatModel 模拟四种流程——
 * 正常工具调用 / 护栏自动补采 / 校验失败重试 / 彻底失败降级。
 * 全程无真实 LLM（事实一致性校验的价值也在这里：不花钱即可测编排逻辑）。
 */
class AgenticAnalysisServiceTest {

    private static final String DISCLAIMER = TemplateReportGenerator.DISCLAIMER;
    private static final String TX_HASH = "0xabc0000000000000000000000000000000000000000000000000000000000001";

    /** 按队列顺序返回预设响应的假模型。 */
    private static final class FakeChatModel implements ChatModel {

        private final Deque<ChatResponse> script;

        FakeChatModel(ChatResponse... responses) {
            this.script = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            assertThat(script).as("脚本耗尽（模型被调用了超过预期次数）").isNotEmpty();
            return script.poll();
        }
    }

    private static ChatResponse text(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private static ChatResponse toolCall(String name, String arguments) {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("call-1", "function", name, arguments);
        return new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().toolCalls(List.of(call)).build())));
    }

    /** 与单条规则（无限额度授权）匹配的合法报告 JSON。 */
    private static String validReportJson() {
        return """
                {
                  "summary": "该交易在 anvil-local 网络上执行成功，涉及一次无限额度授权。",
                  "actions": ["建议撤销授权"],
                  "findings": [
                    {"ruleId": "ERC20-UNLIMITED-APPROVAL", "title": "检测到无限额度授权", "description": "授权额度为最大值，存在资产被转走的风险。"}
                  ],
                  "limitations": ["%s"]
                }
                """.formatted(DISCLAIMER);
    }

    private static TxFacts factsWithUnlimitedApproval() {
        return AgTxFactsFactory.create()
                .withApprovals(List.of(new ApprovalEvent(AgTxFactsFactory.TOKEN, AgTxFactsFactory.FROM,
                        "0x4444444444444444444444444444444444444444",
                        AgTxFactsFactory.TWO_256_MINUS_1, BigInteger.ONE)))
                .build();
    }

    private static final EthRpcClient FAKE_RPC = new EthRpcClient() {
        @Override
        public long chainId() {
            return 31337;
        }

        @Override
        public JsonNode getTransactionByHash(String txHash) {
            return null;
        }

        @Override
        public JsonNode getTransactionReceipt(String txHash) {
            return null;
        }

        @Override
        public JsonNode getBlockByNumber(BigInteger blockNumber) {
            return null;
        }

        @Override
        public JsonNode getCode(String address, BigInteger blockNumber) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("0x6080604052");
        }

        @Override
        public BigInteger blockNumber() {
            return BigInteger.ONE;
        }
    };

    private static AgenticAnalysisService service(ChatModel model, TxFacts facts) {
        return new AgenticAnalysisService(
                hash -> facts,
                new RuleEngine(List.of(new UnlimitedApprovalRule())),
                model,
                FAKE_RPC,
                new CuratedSelectorRegistry(),
                new NoopKnowledgeSearch(),
                new TemplateReportGenerator(),
                null);
    }

    @Test
    @DisplayName("正常流程：模型调用两个案卷工具后产出合法报告 → llmUsed=true，工具步骤落库，机器字段保留")
    void normalToolCallingFlow() {
        FakeChatModel model = new FakeChatModel(
                toolCall("getCaseFacts", "{}"),
                toolCall("getRiskFindings", "{}"),
                text(validReportJson()));
        TxFacts facts = factsWithUnlimitedApproval();

        AnalysisResult result = service(model, facts).analyze(TX_HASH, AnalysisProgressListener.noop());

        assertThat(result.llmUsed()).isTrue();
        assertThat(result.degraded()).isFalse();
        assertThat(result.toolSteps()).extracting(s -> s.toolName())
                .containsExactly("getCaseFacts", "getRiskFindings");
        // 机器字段（severity/confidence/evidence）由规则引擎定案，LLM 只能润色措辞
        assertThat(result.findings()).hasSize(1);
        Finding finding = result.findings().get(0);
        assertThat(finding.ruleId()).isEqualTo("ERC20-UNLIMITED-APPROVAL");
        assertThat(finding.severity()).isEqualTo(io.chainlens.model.Severity.HIGH);
        assertThat(finding.confidence()).isEqualTo(1.0);
        assertThat(finding.title()).isEqualTo("检测到无限额度授权");
        assertThat(result.report().limitations().get(0)).isEqualTo(DISCLAIMER);
    }

    @Test
    @DisplayName("护栏②：模型不调用工具直接写报告 → 自动补采后重新生成，结果仍有效")
    void guardrailInjectsMandatoryEvidence() {
        // 第一次输出被丢弃（无工具证据），注入强制证据后第二次输出生效
        FakeChatModel model = new FakeChatModel(
                text(validReportJson()),
                text(validReportJson()));
        TxFacts facts = factsWithUnlimitedApproval();

        AnalysisResult result = service(model, facts).analyze(TX_HASH, AnalysisProgressListener.noop());

        assertThat(result.llmUsed()).isTrue();
        assertThat(result.degraded()).isFalse();
        assertThat(result.report().summary()).contains("无限额度授权");
    }

    @Test
    @DisplayName("护栏④：首次输出未通过校验 → 带违规反馈重试一次 → 通过")
    void validationFailureRetriesOnce() {
        String invalid = """
                {
                  "summary": "该交易成功完成。",
                  "actions": [],
                  "findings": [{"ruleId": "TX-FAILED", "title": "交易失败", "description": "desc"}],
                  "limitations": ["%s"]
                }
                """.formatted(DISCLAIMER);
        FakeChatModel model = new FakeChatModel(
                text(validReportJson()),          // 第 1 轮（无工具，被丢弃）
                text(invalid),                    // 第 1 次报告尝试（ruleId 不一致）
                text(validReportJson()));         // 第 2 次报告尝试（修正后通过）
        TxFacts facts = factsWithUnlimitedApproval();

        AnalysisResult result = service(model, facts).analyze(TX_HASH, AnalysisProgressListener.noop());

        assertThat(result.degraded()).isFalse();
        assertThat(result.findings()).extracting(Finding::ruleId)
                .containsExactly("ERC20-UNLIMITED-APPROVAL");
    }

    @Test
    @DisplayName("彻底失败 → 降级模板报告（degraded=true，findings 仍来自规则引擎）")
    void fallsBackToTemplateWhenLlmKeepsFailing() {
        String invalid = "这不是 JSON";
        FakeChatModel model = new FakeChatModel(
                text(validReportJson()),   // 无工具，被丢弃
                text(invalid),             // 报告尝试 1 失败
                text(invalid));            // 报告尝试 2 失败 → 降级
        TxFacts facts = factsWithUnlimitedApproval();

        AnalysisResult result = service(model, facts).analyze(TX_HASH, AnalysisProgressListener.noop());

        assertThat(result.llmUsed()).isTrue();
        assertThat(result.degraded()).isTrue();
        assertThat(result.report().limitations().get(0)).isEqualTo(DISCLAIMER);
        assertThat(result.findings()).extracting(Finding::ruleId)
                .containsExactly("ERC20-UNLIMITED-APPROVAL");
    }
}
