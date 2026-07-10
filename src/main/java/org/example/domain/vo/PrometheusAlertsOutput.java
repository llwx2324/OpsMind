package org.example.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Prometheus 告警输出对象。
 *
 * <p>用于将告警查询工具返回的结果转换为前端可消费的结构，包含成功标识、简化后的告警列表以及错误/提示信息。</p>
 */
@Data
public class PrometheusAlertsOutput {

    /**
     * 查询是否成功。
     */
    @JsonProperty("success")
    private boolean success;

    /**
     * 简化后的告警列表。
     */
    @JsonProperty("alerts")
    private List<SimplifiedAlert> alerts;

    /**
     * 提示信息。
     */
    @JsonProperty("message")
    private String message;

    /**
     * 错误信息，通常在 success=false 时填充。
     */
    @JsonProperty("error")
    private String error;
}
