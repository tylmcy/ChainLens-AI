package io.chainlens.evaluation;

import java.math.BigInteger;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.chainlens.evaluation.EvaluationDataset.EvaluationCase;

import static org.assertj.core.api.Assertions.assertThat;

/** 独立于生产解析器，逐条核对固定标注和原始 RPC 字段，避免评测与解析器自证正确。 */
class FrozenDatasetIntegrityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final String APPROVAL_TOPIC =
            "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925";

    @Test
    void everyFrozenAnnotationPointsBackToRawRpc() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path fixtures = root.resolve("eval/fixtures");
        EvaluationDataset dataset = EvaluationDatasetLoader.load(
                root.resolve("eval/cases/transaction-eval-v1.json"), fixtures);

        for (EvaluationCase item : dataset.cases()) {
            Path directory = fixtures.resolve(item.fixtureDir());
            JsonNode tx = read(directory, "transaction");
            JsonNode receipt = read(directory, "receipt");
            JsonNode block = read(directory, "block");
            assertThat(tx.at("/result/hash").asText()).as(item.caseId()).isEqualToIgnoringCase(item.txHash());
            assertThat(receipt.at("/result/transactionHash").asText()).as(item.caseId())
                    .isEqualToIgnoringCase(item.txHash());
            assertThat(tx.at("/result/blockNumber").asText()).as(item.caseId())
                    .isEqualTo(receipt.at("/result/blockNumber").asText());
            assertThat(block.at("/result/number").asText()).as(item.caseId())
                    .isEqualTo(receipt.at("/result/blockNumber").asText());

            boolean success = "0x1".equals(receipt.at("/result/status").asText());
            BigInteger value = quantity(tx.at("/result/value"));
            BigInteger price = "0x2".equals(tx.at("/result/type").asText())
                    ? quantity(receipt.at("/result/effectiveGasPrice"))
                    : quantity(tx.at("/result/gasPrice"));
            BigInteger gas = quantity(receipt.at("/result/gasUsed")).multiply(price);
            BigInteger toNet = success ? value : BigInteger.ZERO;
            var truth = item.groundTruth();
            assertThat(truth.status().value()).as(item.caseId()).isEqualTo(success ? "SUCCESS" : "FAILED");
            assertThat(truth.valueWei().value()).as(item.caseId()).isEqualTo(value.toString());
            assertThat(truth.gasCostWei().value()).as(item.caseId()).isEqualTo(gas.toString());
            assertThat(truth.fromNetWei().value()).as(item.caseId()).isEqualTo(gas.add(toNet).negate().toString());
            assertThat(truth.toNetWei().value()).as(item.caseId()).isEqualTo(toNet.toString());
            assertThat(tx.at(truth.calldata().rawJsonRef()).isTextual()).as(item.caseId()).isTrue();
            String input = tx.at("/result/input").asText();
            assertThat(truth.calldata().selector()).as(item.caseId()).isEqualTo(
                    input.length() >= 10 ? input.substring(0, 10).toLowerCase() : null);

            for (var event : truth.transfers()) {
                JsonNode log = receipt.at(event.rawJsonRef());
                assertThat(log.isObject()).as(item.caseId()).isTrue();
                assertThat(log.path("topics").get(0).asText()).as(item.caseId())
                        .isEqualToIgnoringCase(TRANSFER_TOPIC);
                assertThat(log.path("address").asText()).as(item.caseId())
                        .isEqualToIgnoringCase(event.tokenAddress());
                assertThat(log.path("topics").get(1).asText()).as(item.caseId()).endsWithIgnoringCase(event.from().substring(2));
                assertThat(log.path("topics").get(2).asText()).as(item.caseId()).endsWithIgnoringCase(event.to().substring(2));
                assertThat(quantity(log.path("data")).toString()).as(item.caseId()).isEqualTo(event.valueWei());
                assertThat(quantity(log.path("logIndex")).toString()).as(item.caseId()).isEqualTo(event.logIndex());
            }
            for (var event : truth.approvals()) {
                JsonNode log = receipt.at(event.rawJsonRef());
                assertThat(log.isObject()).as(item.caseId()).isTrue();
                assertThat(log.path("topics").get(0).asText()).as(item.caseId())
                        .isEqualToIgnoringCase(APPROVAL_TOPIC);
                assertThat(log.path("address").asText()).as(item.caseId())
                        .isEqualToIgnoringCase(event.tokenAddress());
                assertThat(log.path("topics").get(1).asText()).as(item.caseId()).endsWithIgnoringCase(event.owner().substring(2));
                assertThat(log.path("topics").get(2).asText()).as(item.caseId()).endsWithIgnoringCase(event.spender().substring(2));
                assertThat(quantity(log.path("data")).toString()).as(item.caseId()).isEqualTo(event.valueWei());
                assertThat(quantity(log.path("logIndex")).toString()).as(item.caseId()).isEqualTo(event.logIndex());
            }
            for (var finding : truth.expectedFindings()) {
                String file = switch (finding.rpcMethod()) {
                    case "eth_getTransactionByHash" -> "transaction";
                    case "eth_getTransactionReceipt" -> "receipt";
                    case "eth_getCode" -> "code";
                    default -> throw new IllegalArgumentException(item.caseId() + " 未知证据方法");
                };
                assertThat(read(directory, file).at(finding.jsonPointer()).isMissingNode())
                        .as(item.caseId() + " " + finding.ruleId() + " " + finding.jsonPointer()).isFalse();
            }
        }
    }

    /** 只读完整 JSON-RPC envelope，保留 /result 开头的 JSON Pointer 语义。 */
    private static JsonNode read(Path directory, String file) throws Exception {
        return MAPPER.readTree(directory.resolve(file + ".json").toFile());
    }

    private static BigInteger quantity(JsonNode node) {
        return new BigInteger(node.asText().substring(2), 16);
    }
}
