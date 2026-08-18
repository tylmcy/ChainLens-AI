package io.chainlens.rules.risk;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.chainlens.model.Hexes;

/**
 * 内置风险地址列表：从 classpath JSON 加载（本地静态样例，演示用），
 * 支持追加自定义条目（合并时不覆盖内置条目来源）。
 *
 * <p>注意：内置条目是**演示样例**（虚构地址 + 明确标注来源），不是真实情报；
 * 接入真实情报源时必须按设计文档 12.5 核实许可并记录到 THIRD_PARTY_NOTICES.md。
 */
public final class BuiltInRiskAddressList implements RiskAddressList {

    public static final String DEFAULT_RESOURCE = "risk-addresses.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String source;
    private final Map<String, Entry> entries = new HashMap<>();

    /** 从 classpath 资源加载（默认 risk-addresses.json）。 */
    public static BuiltInRiskAddressList fromClasspath(String resource) {
        try (InputStream in = BuiltInRiskAddressList.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("风险地址列表资源不存在: " + resource);
            }
            JsonNode root = MAPPER.readTree(in);
            if (root == null || !root.isArray()) {
                throw new IllegalStateException("风险地址列表资源格式非法（应为数组）: " + resource);
            }
            BuiltInRiskAddressList list = new BuiltInRiskAddressList("chainlens-builtin-" + resource);
            for (JsonNode node : root) {
                list.add(node.path("address").asText(), node.path("label").asText(),
                        node.path("source").asText("chainlens-builtin-sample"));
            }
            return list;
        } catch (IOException e) {
            throw new IllegalStateException("风险地址列表资源读取失败: " + resource, e);
        }
    }

    public BuiltInRiskAddressList(String source) {
        this.source = source;
    }

    public void add(String address, String label, String entrySource) {
        String normalized = Hexes.normalizeAddress(address);
        entries.put(normalized, new Entry(normalized, label, entrySource));
    }

    public void addAll(Collection<Entry> extra) {
        extra.forEach(e -> entries.put(Hexes.normalizeAddress(e.address()), e));
    }

    public Map<String, Entry> entries() {
        return Map.copyOf(entries);
    }

    @Override
    public Optional<Entry> find(String normalizedAddress) {
        return Optional.ofNullable(entries.get(Hexes.normalizeAddress(normalizedAddress)));
    }

    @Override
    public String source() {
        return source;
    }
}
