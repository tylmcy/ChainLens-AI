package io.chainlens.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 从仓库内受管目录加载、校验并切分完整知识集。 */
public final class KnowledgeCorpusLoader {

    private final KnowledgeDocumentParser parser;
    private final KnowledgeChunker chunker;

    public KnowledgeCorpusLoader() {
        this(new KnowledgeDocumentParser(), new KnowledgeChunker());
    }

    KnowledgeCorpusLoader(KnowledgeDocumentParser parser, KnowledgeChunker chunker) {
        this.parser = parser;
        this.chunker = chunker;
    }

    public KnowledgeCorpus load(Path repositoryRoot, Path knowledgeDirectory) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path directory = knowledgeDirectory.toAbsolutePath().normalize();
        if (!directory.startsWith(root) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("知识目录必须是仓库内已存在的目录");
        }
        try {
            List<Path> files;
            try (var stream = Files.list(directory)) {
                files = stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
            List<KnowledgeDocument> documents = new ArrayList<>();
            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (Path file : files) {
                KnowledgeDocument document = parser.parse(
                        root, file, Files.readString(file, StandardCharsets.UTF_8));
                documents.add(document);
                chunks.addAll(chunker.chunk(document));
            }
            return new KnowledgeCorpus(documents, chunks);
        } catch (IOException e) {
            throw new IllegalStateException("读取知识目录失败: " + directory, e);
        }
    }

    public record KnowledgeCorpus(List<KnowledgeDocument> documents, List<KnowledgeChunk> chunks) {
        public KnowledgeCorpus {
            documents = List.copyOf(documents);
            chunks = List.copyOf(chunks);
        }
    }
}
