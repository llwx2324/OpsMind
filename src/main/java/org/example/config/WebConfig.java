package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web MVC 配置。
 *
 * <p>主要解决 HTTP 消息的字符集问题，确保字符串与 JSON 的默认编码为 UTF-8，避免中文乱码。
 * 该配置通过优先注册 StringHttpMessageConverter 与 MappingJackson2HttpMessageConverter 实现。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    /** 复用 Spring Boot 统一配置的 ObjectMapper，保证缓存与 HTTP JSON 的时间格式一致。 */
    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper Spring Boot 管理的 JSON 序列化器
     */
    public WebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 添加 UTF-8 字符串转换器
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false); // 不设置 Accept-Charset
        converters.add(0, stringConverter);
        
        // 复用容器中的 ObjectMapper，避免自行 new 实例丢失日期格式及其他全局配置。
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        jsonConverter.setDefaultCharset(StandardCharsets.UTF_8);
        converters.add(1, jsonConverter);
    }
    
}
