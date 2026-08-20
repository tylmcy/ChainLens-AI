package io.chainlens.api;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试切片锚点：chainlens-api 是库模块（无 @SpringBootApplication），
 * Boot 4 的切片测试（@WebMvcTest/@JdbcTest）需要一个配置类作为向上搜索的根。
 */
@SpringBootApplication
class ApiTestApplication {
}
