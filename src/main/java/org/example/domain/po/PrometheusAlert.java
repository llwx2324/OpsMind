package org.example.domain.po;

import lombok.Data;

import java.util.Map;

/**
 * Prometheus 告警记录。
 *
 * <p>对应 Prometheus / Alertmanager 返回的单条告警对象，包含标签、注解、状态和当前值等信息。
 * 该对象用于告警解析与二次封装展示，不承担业务计算逻辑。</p>
 */
@Data
public class PrometheusAlert {

    /**
     * 告警标签集合，通常包含 alertname、severity、instance 等元信息。
     */
    private Map<String, String> labels;

    /**
     * 告警注解集合，用于存放描述、摘要、处理建议等补充信息。
     */
    private Map<String, String> annotations;

    /**
     * 告警状态，例如 firing、pending、resolved。
     */
    private String state;

    /**
     * 告警激活时间，保持字符串以兼容不同来源的时间格式。
     */
    private String activeAt;

    /**
     * 告警对应的指标值或表达式求值结果。
     */
    private String value;
}
