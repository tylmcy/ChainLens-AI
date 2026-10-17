package io.chainlens.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** M7 知识库配置；目录和表名不接受 HTTP 请求覆盖。 */
@ConfigurationProperties(prefix = "chainlens.knowledge")
public class KnowledgeProperties {

    private boolean enabled;
    private boolean adminEnabled;
    private String directory = "docs/knowledge";
    private int topK = 3;
    // 由 rag-eval-v1 真实 BGE 检索结果校准：目标问句最低约 0.55，无关问句最高约 0.31。
    private double similarityThreshold = 0.50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAdminEnabled() {
        return adminEnabled;
    }

    public void setAdminEnabled(boolean adminEnabled) {
        this.adminEnabled = adminEnabled;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public void validate() {
        if (!"docs/knowledge".equals(directory)) {
            throw new IllegalArgumentException("M7 只允许固定知识目录 docs/knowledge");
        }
        if (topK != 3) {
            throw new IllegalArgumentException("M7 固定返回 top-3");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("相似度阈值必须位于 0 到 1 之间");
        }
    }
}
