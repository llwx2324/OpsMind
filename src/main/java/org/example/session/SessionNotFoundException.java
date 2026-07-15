package org.example.session;

/**
 * 当前租户和用户范围内不存在指定会话时抛出的异常。
 *
 * <p>对越权访问同样返回“不存在”，避免泄露其他用户的会话是否存在。</p>
 */
public class SessionNotFoundException extends RuntimeException {
    /**
     * @param id 未找到或无权访问的会话 ID
     */
    public SessionNotFoundException(String id) { super("Chat session not found: " + id); }
}
