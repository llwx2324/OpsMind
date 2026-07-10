package org.example.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档分片配置。
 *
 * <p>包含分片的最大字符数和分片之间的重叠字符数两个核心参数，影响检索质量与文档分片粒度。
 * 默认值：maxSize=800, overlap=100。可以根据业务文档类型调整以平衡段落完整性与检索准确性。</p>
 */
@Getter
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkConfig {
    
    /**
     * 每个分片的最大字符数
     */
    private int maxSize = 800;
    
    /**
     * 分片之间的重叠字符数
     */
    private int overlap = 100;

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }
}
