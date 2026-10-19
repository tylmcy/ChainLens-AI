package io.chainlens.evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** 报告中的代码版本定位；无法读取时明确写 unavailable，不猜测 commit。 */
public final class GitRevision {

    private GitRevision() {
    }

    public static String current(Path workDirectory) {
        return current(workDirectory, System.getenv("GITHUB_SHA"));
    }

    /** 注入 CI 值的包级入口，用于验证无 Git 信息时的明确降级。 */
    static String current(Path workDirectory, String ci) {
        if (ci != null && ci.matches("[0-9a-fA-F]{40}")) return ci;
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--verify", "HEAD")
                    .directory(workDirectory.toFile()).redirectErrorStream(true).start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "unavailable";
            }
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && value.matches("[0-9a-fA-F]{40}") ? value : "unavailable";
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "unavailable";
        }
    }
}
