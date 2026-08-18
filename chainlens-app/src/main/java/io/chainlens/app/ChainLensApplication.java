package io.chainlens.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ChainLens AI 装配与启动入口。
 * M0 阶段仅验证模块图可编译；Web 服务与配置在后续里程碑接入。
 */
@SpringBootApplication
public class ChainLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChainLensApplication.class, args);
    }
}
