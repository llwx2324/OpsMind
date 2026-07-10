package org.example.domain.po;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;


/**
 * 日志条目对象。
 *
 * <p>用于承载从日志系统或日志查询接口返回的单条日志记录，包含时间戳、级别、服务名、实例标识、日志正文以及指标信息。
 * 该对象通常作为日志查询接口的出参，供前端展示和后续分析使用。</p>
 */
@Data
public class LogEntry {

    /**
     * 日志时间戳，保持字符串形式以兼容不同日志源的时间格式。
     */
    @JsonProperty("timestamp")
    private String timestamp;

    /**
     * 日志级别，例如 INFO、WARN、ERROR。
     */
    @JsonProperty("level")
    private String level;

    /**
     * 产生日志的服务名称。
     */
    @JsonProperty("service")
    private String service;

    /**
     * 产生日志的实例标识，用于定位具体运行节点。
     */
    @JsonProperty("instance")
    private String instance;

    /**
     * 日志正文内容。
     */
    @JsonProperty("message")
    private String message;

    /**
     * 附加指标信息，通常用于承载日志上下文中的键值对字段。
     */
    @JsonProperty("metrics")
    private Map<String, String> metrics;
}
