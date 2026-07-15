package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 应用入口。
 *
 * <p>负责启动 OpsMind 服务并加载 Spring 容器，同时启用会话压缩所需的异步任务，
 * 以及长期记忆提取、数据过期清理所需的定时任务。</p>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
