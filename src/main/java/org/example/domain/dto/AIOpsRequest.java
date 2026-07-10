package org.example.domain.dto;

import lombok.Data;

/**
 * AI Ops 分析请求 DTO。
 *
 * <p>用于承载用户对运维告警分析或智能排障的自然语言需求，作为多 Agent 编排流程的输入。</p>
 */
@Data
public class AIOpsRequest {

    /**
     * 用户的运维分析请求文本。
     */
    private String userRequest;
}
