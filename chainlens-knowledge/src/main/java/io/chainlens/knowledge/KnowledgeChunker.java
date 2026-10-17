package io.chainlens.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 按 Markdown 标题和中文段落做确定性切块。 */
public final class KnowledgeChunker {

    public static final int TARGET_MIN = 300;
    public static final int TARGET_MAX = 500;
    public static final int OVERLAP_MAX = 80;
    private static final Pattern HEADING = Pattern.compile("^(#{2,4})\\s+(.+)$");

    public List<KnowledgeChunk> chunk(KnowledgeDocument document) {
        List<Section> sections = sections(document.body());
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            int ordinal = 0;
            for (String content : splitSection(section.content())) {
                String normalized = normalize(content);
                if (normalized.isBlank()) {
                    continue;
                }
                String contentHash = sha256(normalized);
                String id = sha256(document.source() + "\n" + section.heading() + "\n" + normalized);
                chunks.add(new KnowledgeChunk(id, normalized, document.source(), document.title(),
                        section.heading(), ordinal++, document.category(), document.ruleIds(), document.version(), contentHash));
            }
        }
        return List.copyOf(chunks);
    }

    private static List<Section> sections(String body) {
        List<Section> result = new ArrayList<>();
        String heading = "概述";
        StringBuilder content = new StringBuilder();
        for (String line : body.replace("\r\n", "\n").split("\n", -1)) {
            Matcher matcher = HEADING.matcher(line.strip());
            if (matcher.matches()) {
                addSection(result, heading, content);
                heading = matcher.group(2).strip();
                content = new StringBuilder();
            } else {
                content.append(line).append('\n');
            }
        }
        addSection(result, heading, content);
        return result;
    }

    private static void addSection(List<Section> target, String heading, StringBuilder content) {
        String normalized = normalize(content.toString());
        if (!normalized.isBlank()) {
            target.add(new Section(heading, normalized));
        }
    }

    private static List<String> splitSection(String content) {
        List<String> paragraphs = List.of(content.split("\\n\\s*\\n"));
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String normalized = normalize(paragraph);
            if (normalized.length() > TARGET_MAX) {
                flush(chunks, current);
                chunks.addAll(splitLong(normalized));
            } else if (!current.isEmpty() && current.length() + 2 + normalized.length() > TARGET_MAX) {
                flush(chunks, current);
                current.append(overlap(chunks.get(chunks.size() - 1)));
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(normalized);
            } else {
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(normalized);
            }
        }
        flush(chunks, current);
        return chunks;
    }

    /** 长段落优先在中文句号附近断开；找不到标点时才按字符硬切。 */
    private static List<String> splitLong(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + TARGET_MAX, text.length());
            if (end < text.length()) {
                int boundary = -1;
                for (int i = end; i >= Math.min(start + TARGET_MIN, end); i--) {
                    char c = text.charAt(i - 1);
                    if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                        boundary = i;
                        break;
                    }
                }
                if (boundary > start) {
                    end = boundary;
                }
            }
            result.add(normalize(text.substring(start, end)));
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_MAX, start + 1);
        }
        return result;
    }

    private static String overlap(String previous) {
        int start = Math.max(0, previous.length() - OVERLAP_MAX);
        return previous.substring(start).strip();
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(normalize(current.toString()));
            current.setLength(0);
        }
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .lines()
                .map(String::stripTrailing)
                .collect(java.util.stream.Collectors.joining("\n"))
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    private record Section(String heading, String content) {
    }
}
