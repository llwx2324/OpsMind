package org.example.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 会话信息响应对象。
 *
 * <p>用于返回会话的轻量级元信息，包含会话 ID、消息对数以及创建时间。</p>
 */
@Getter
@Setter
public class SessionInfoResponse {

    /**
     * 会话 ID。
     */
    private String sessionId;

    /**
     * 当前保存的消息对数量。
     */
    private int messagePairCount;

    /**
     * 会话创建时间戳（毫秒）。
     */
    private long createTime;
}
