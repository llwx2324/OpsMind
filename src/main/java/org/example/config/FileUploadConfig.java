package org.example.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件上传配置。
 *
 * <p>用于配置上传目录和允许的文件扩展名列表，供文件上传 Controller 与索引流程使用。</p>
 */
@Getter
@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    /**
     * 上传文件保存路径（目录），通过配置 file.upload.path 注入
     */
    private String path;

    /**
     * 允许的文件扩展名，逗号分隔，例如 "txt,md"
     */
    private String allowedExtensions;

    public void setPath(String path) {
        this.path = path;
    }

    public void setAllowedExtensions(String allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
