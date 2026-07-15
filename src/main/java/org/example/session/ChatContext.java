package org.example.session;

import org.example.domain.po.ChatMessage;
import java.util.List;

/**
 * 单次模型调用使用的会话上下文。
 *
 * @param summary 已压缩的历史摘要，可为空
 * @param history 摘要覆盖序号之后、状态为 COMPLETED 的最近原始消息
 */
public record ChatContext(String summary, List<ChatMessage> history) {}
