package org.example.domain.po;

import lombok.Data;

/**
 * Prometheus 告警查询结果。
 *
 * <p>用于封装 Prometheus API 返回的整体结构，包括状态、数据和错误信息。
 * 该对象是告警查询工具与外部 API 之间的反序列化中间层。</p>
 */
@Data
public class PrometheusAlertsResult {

    /**
     * 返回状态，通常为 success 或 error。
     */
    private String status;

    /**
     * 具体告警数据。
     */
    private AlertsData data;

    /**
     * 错误信息，状态异常时可能非空。
     */
    private String error;

    /**
     * 错误类型，便于排障和上层分类处理。
     */
    private String errorType;
}
