package org.example.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 日志主题输出对象。
 *
 * <p>用于承载日志主题工具的返回结果，包含可用主题列表、可查询地域、默认地域以及状态消息。</p>
 */
@Data
public class LogTopicsOutput {

    /**
     * 查询是否成功。
     */
    @JsonProperty("success")
    private boolean success;

    /**
     * 日志主题列表。
     */
    @JsonProperty("topics")
    private List<LogTopicInfo> topics;

    /**
     * 支持的地域列表。
     */
    @JsonProperty("available_regions")
    private List<String> availableRegions;

    /**
     * 默认地域。
     */
    @JsonProperty("default_region")
    private String defaultRegion;

    /**
     * 返回消息。
     */
    @JsonProperty("message")
    private String message;
}
