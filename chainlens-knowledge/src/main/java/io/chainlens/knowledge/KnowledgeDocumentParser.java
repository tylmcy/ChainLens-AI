package io.chainlens.knowledge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 解析项目自有的、字段受限的 Markdown front matter；不支持任意 YAML 对象。 */
public final class KnowledgeDocumentParser {

    private static final Set<String> REQUIRED_FIELDS = Set.of("id", "title", "category", "ruleIds", "version");
    private static final Set<String> RULE_IDS = Set.of(
            "ERC20-UNLIMITED-APPROVAL",
            "TX-FAILED",
            "TRANSFER-TO-ZERO-ADDRESS",
            "INTERACTION-WITH-EOA",
            "UNKNOWN-SELECTOR",
            "MANY-TOKEN-TRANSFERS",
            "RISK-ADDRESS-HIT");
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,63}");
    private static final Pattern CATEGORY = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+){0,2}");

    public KnowledgeDocument parse(Path managedRoot, Path file, String markdown) {
        Path root = managedRoot.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        // 先做路径边界检查，防止以后管理接口误把 ../ 或外部绝对路径带进来。
        if (!normalizedFile.startsWith(root) || !normalizedFile.getFileName().toString().endsWith(".md")) {
            throw new IllegalArgumentException("知识文档必须位于受管目录且扩展名为 .md");
        }
        if (markdown == null || !markdown.startsWith("---\n")) {
            throw new IllegalArgumentException("知识文档缺少 front matter 起始分隔符");
        }
        int end = markdown.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException("知识文档缺少 front matter 结束分隔符");
        }

        Map<String, String> fields = parseFields(markdown.substring(4, end));
        if (!fields.keySet().equals(REQUIRED_FIELDS)) {
            Set<String> missing = new java.util.HashSet<>(REQUIRED_FIELDS);
            missing.removeAll(fields.keySet());
            Set<String> extra = new java.util.HashSet<>(fields.keySet());
            extra.removeAll(REQUIRED_FIELDS);
            throw new IllegalArgumentException("front matter 字段不完整或包含未知字段，缺少=" + missing + "，多余=" + extra);
        }

        String id = scalar(fields, "id");
        String title = scalar(fields, "title");
        String category = scalar(fields, "category");
        String version = scalar(fields, "version");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id 只能使用小写字母、数字和连字符");
        }
        if (title.isBlank() || title.length() > 100 || looksLikeLocation(title)) {
            throw new IllegalArgumentException("title 为空、过长或包含路径/URL");
        }
        if (!CATEGORY.matcher(category).matches()) {
            throw new IllegalArgumentException("category 格式非法");
        }
        if (!VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("version 格式非法");
        }

        List<String> ruleIds = parseRuleIds(fields.get("ruleIds"));
        String body = markdown.substring(end + 5).strip();
        if (body.isBlank()) {
            throw new IllegalArgumentException("知识文档正文不能为空");
        }
        String source = root.relativize(normalizedFile).toString().replace('\\', '/');
        return new KnowledgeDocument(id, title, category, ruleIds, version, source, body);
    }

    private static Map<String, String> parseFields(String frontMatter) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String rawLine : frontMatter.lines().toList()) {
            String line = rawLine.strip();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("front matter 仅支持 key: value 单行格式");
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("front matter 字段重复: " + key);
            }
        }
        return result;
    }

    private static String scalar(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        if ((stripped.startsWith("\"") && stripped.endsWith("\""))
                || (stripped.startsWith("'") && stripped.endsWith("'"))) {
            stripped = stripped.substring(1, stripped.length() - 1).strip();
        }
        return stripped;
    }

    private static List<String> parseRuleIds(String value) {
        String raw = value == null ? "" : value.strip();
        if (!raw.startsWith("[") || !raw.endsWith("]")) {
            throw new IllegalArgumentException("ruleIds 必须使用单行列表，例如 [TX-FAILED]");
        }
        String inner = raw.substring(1, raw.length() - 1).strip();
        if (inner.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : inner.split(",")) {
            String ruleId = item.strip().replace("\"", "").replace("'", "");
            if (!RULE_IDS.contains(ruleId)) {
                throw new IllegalArgumentException("未知 ruleId: " + ruleId);
            }
            if (result.contains(ruleId)) {
                throw new IllegalArgumentException("ruleId 重复: " + ruleId);
            }
            result.add(ruleId);
        }
        return List.copyOf(result);
    }

    private static boolean looksLikeLocation(String value) {
        String lower = value.toLowerCase();
        return lower.contains("://") || lower.startsWith("file:") || value.contains("../") || value.contains("..\\");
    }
}
