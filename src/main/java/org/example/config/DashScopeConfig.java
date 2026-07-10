package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.net.http.HttpClient;

/**
 * DashScope API 配置。
 *
 * <p>为 Spring AI / DashScope 相关客户端提供统一的 RestClient.Builder 配置，主要用于设置网络超时时间，
 * 避免大模型请求在网络波动或上游响应缓慢时无限阻塞。</p>
 */
@Configuration
public class DashScopeConfig {

    @Value("${spring.ai.dashscope.chat.options.timeout:180000}")
    private long timeout;

    /**
     * 配置 RestClient.Builder，设置超时时间。
     *
     * <p>Spring AI 会自动使用该 Bean 构建底层 HTTP 客户端，因此此处配置会影响模型调用、流式输出等所有 DashScope 请求。</p>
     *
     * @return 已配置超时时间的 RestClient.Builder
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        // 使用 JDK HttpClient 统一设置连接超时，避免依赖已废弃的 Spring HTTP 工厂实现。
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }
}
