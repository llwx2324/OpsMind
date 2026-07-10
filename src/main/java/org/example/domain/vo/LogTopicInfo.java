package org.example.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 日志主题信息对象。
 *
 * <p>用于描述一个日志主题的业务含义、示例查询以及关联告警，方便前端展示和智能检索引导。</p>
 */
@Data
public class LogTopicInfo {

    /**
     * 主题名称。
     */
    @JsonProperty("topic_name")
    private String topicName;

    /**
     * 主题描述，用于解释该日志主题覆盖的业务范围。
     */
    @JsonProperty("description")
    private String description;

    /**
     * 示例查询语句，帮助用户快速构造日志检索条件。
     */
    @JsonProperty("example_queries")
    private List<String> exampleQueries;

    /**
     * 与该日志主题相关的告警列表。
     */
    @JsonProperty("related_alerts")
    private List<String> relatedAlerts;
}
