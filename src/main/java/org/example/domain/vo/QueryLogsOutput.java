package org.example.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.example.domain.po.LogEntry;

import java.util.List;

/**
 * 日志查询输出对象。
 *
 * <p>用于承载日志查询工具的返回结果，包括查询是否成功、地域、日志主题、查询条件、日志列表和总数等信息。
 * 该对象主要供前端展示与 AI 工具调用结果回传使用。</p>
 */
@Data
public class QueryLogsOutput {

    /**
     * 查询是否成功。
     */
    @JsonProperty("success")
    private boolean success;

    /**
     * 查询地域，用于标识日志所在的云区域。
     */
    @JsonProperty("region")
    private String region;

    /**
     * 日志主题名，通常对应日志服务中的 topic。
     */
    @JsonProperty("log_topic")
    private String logTopic;

    /**
     * 实际执行的查询条件。
     */
    @JsonProperty("query")
    private String query;

    /**
     * 查询到的日志列表。
     */
    @JsonProperty("logs")
    private List<LogEntry> logs;

    /**
     * 日志总条数。
     */
    @JsonProperty("total")
    private int total;

    /**
     * 返回消息，通常用于描述成功或失败原因。
     */
    @JsonProperty("message")
    private String message;
}
