package org.example.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 简化后的告警展示对象。
 *
 * <p>用于将 Prometheus 告警压缩为前端更易展示的结构，仅保留告警名称、描述、状态、激活时间和持续时间等核心信息。</p>
 */
@Data
public class SimplifiedAlert {

    /**
     * 告警名称，通常来自 labels.alertname。
     */
    @JsonProperty("alert_name")
    private String alertName;

    /**
     * 告警描述或摘要，通常来自 annotations.description 或 summary。
     */
    @JsonProperty("description")
    private String description;

    /**
     * 告警状态，例如 firing、pending、resolved。
     */
    @JsonProperty("state")
    private String state;

    /**
     * 告警激活时间。
     */
    @JsonProperty("active_at")
    private String activeAt;

    /**
     * 告警持续时间，供前端展示或排序使用。
     */
    @JsonProperty("duration")
    private String duration;
}
