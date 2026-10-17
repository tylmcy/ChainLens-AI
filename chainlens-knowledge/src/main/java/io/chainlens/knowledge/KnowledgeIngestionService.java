package io.chainlens.knowledge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 将受管 Markdown 知识集事务化、幂等地同步到 pgvector。 */
public class KnowledgeIngestionService {

    public static final String TABLE = "knowledge_chunks";
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeCorpusLoader loader;
    private final Path repositoryRoot;
    private final Path knowledgeDirectory;

    public KnowledgeIngestionService(VectorStore vectorStore,
                                     JdbcTemplate jdbcTemplate,
                                     KnowledgeCorpusLoader loader,
                                     Path repositoryRoot,
                                     Path knowledgeDirectory) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.loader = loader;
        this.repositoryRoot = repositoryRoot;
        this.knowledgeDirectory = knowledgeDirectory;
    }

    /**
     * 解析在写库前全部完成；数据库写入位于同一事务，嵌入或 SQL 任一失败都会整体回滚。
     */
    @Transactional
    public KnowledgeSyncStats synchronize() {
        try {
            KnowledgeCorpusLoader.KnowledgeCorpus corpus = loader.load(repositoryRoot, knowledgeDirectory);
            Map<String, ExistingChunk> existing = loadExisting();
            Set<String> targetKeys = new HashSet<>();
            List<String> deleteIds = new ArrayList<>();
            List<Document> upserts = new ArrayList<>();
            int added = 0;
            int updated = 0;
            int unchanged = 0;

            for (KnowledgeChunk chunk : corpus.chunks()) {
                String logicalKey = logicalKey(chunk.source(), chunk.heading(), chunk.ordinal());
                targetKeys.add(logicalKey);
                ExistingChunk old = existing.get(logicalKey);
                if (old == null) {
                    added++;
                    upserts.add(toDocument(chunk, logicalKey));
                } else if (old.id().equals(chunk.id()) && old.contentHash().equals(chunk.contentHash())) {
                    unchanged++;
                } else {
                    updated++;
                    deleteIds.add(old.id());
                    upserts.add(toDocument(chunk, logicalKey));
                }
            }
            existing.forEach((key, value) -> {
                if (!targetKeys.contains(key)) {
                    deleteIds.add(value.id());
                }
            });
            int deleted = Math.max(0, deleteIds.size() - updated);

            if (!deleteIds.isEmpty()) {
                vectorStore.delete(deleteIds);
            }
            if (!upserts.isEmpty()) {
                vectorStore.add(upserts);
            }
            return new KnowledgeSyncStats(added, updated, deleted, unchanged, 0);
        } catch (KnowledgeSyncException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new KnowledgeSyncException("知识库同步失败", e);
        }
    }

    private Map<String, ExistingChunk> loadExisting() {
        Map<String, ExistingChunk> result = new HashMap<>();
        String sql = "SELECT id, metadata ->> 'logicalKey', metadata ->> 'contentHash' FROM " + TABLE;
        jdbcTemplate.query(sql, (row, rowNumber) -> Map.entry(
                        row.getString(2), new ExistingChunk(row.getString(1), row.getString(3))))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static Document toDocument(KnowledgeChunk chunk, String logicalKey) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", chunk.source());
        metadata.put("title", chunk.title());
        metadata.put("heading", chunk.heading());
        metadata.put("category", chunk.category());
        metadata.put("ruleIds", chunk.ruleIds());
        metadata.put("version", chunk.version());
        metadata.put("contentHash", chunk.contentHash());
        metadata.put("logicalKey", logicalKey);
        return Document.builder().id(chunk.id()).text(chunk.content()).metadata(metadata).build();
    }

    private static String logicalKey(String source, String heading, int ordinal) {
        return source + "#" + heading + "#" + ordinal;
    }

    private record ExistingChunk(String id, String contentHash) {
    }
}
