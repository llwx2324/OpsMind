package org.example.domain.po;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警数据容器。
 *
 * <p>用于承载 Prometheus 告警查询结果中的 alerts 数组，作为上层响应对象的中间结构。
 * 该对象主要用于反序列化远端告警 API 返回值，不直接暴露给前端。</p>
 */
@Data
public class AlertsData {

    /**
     * 告警列表，默认初始化为空集合，避免反序列化后空指针问题。
     */
    private List<PrometheusAlert> alerts = new ArrayList<>();
}
