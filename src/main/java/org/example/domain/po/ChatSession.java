package org.example.domain.po;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 聊天会话对象。
 *
 * <p>用于在内存中保存单个会话的消息历史、创建时间以及会话窗口大小。
 * 该对象被 Controller 层按 sessionId 缓存和复用，配合内部锁保证多线程访问时的历史读写安全。</p>
 */
public class ChatSession {

    /**
     * 默认保留的消息对数窗口大小，超出后会自动裁剪最旧的上下文。
     */
    public static final int DEFAULT_MAX_WINDOW_SIZE = 6;

    /**
     * 会话 ID。
     */
    private final String sessionId;

    /**
     * 最大保留的消息对数，避免上下文无限增长导致提示词过长。
     */
    private final int maxWindowSize;

    /**
     * 会话消息历史，按 user/assistant 成对存储。
     */
    private final List<ChatMessage> messageHistory;

    /**
     * 会话创建时间戳（毫秒）。
     */
    private final long createTime;

    /**
     * 用于保护消息历史的读写锁，保证并发下历史数据一致性。
     */
    private final ReentrantLock lock;

    /**
     * 使用默认窗口大小创建会话。
     *
     * @param sessionId 会话 ID
     */
    public ChatSession(String sessionId) {
        this(sessionId, DEFAULT_MAX_WINDOW_SIZE);
    }

    /**
     * 创建会话并指定消息窗口大小。
     *
     * @param sessionId 会话 ID
     * @param maxWindowSize 最大保留消息对数
     */
    public ChatSession(String sessionId, int maxWindowSize) {
        this.sessionId = sessionId;
        this.maxWindowSize = maxWindowSize;
        this.messageHistory = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
        this.lock = new ReentrantLock();
    }

    /**
     * 获取会话 ID。
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取会话创建时间戳。
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * 追加一轮用户问题与模型回答，并在超出窗口后裁剪最旧上下文。
     *
     * <p>采用 user/assistant 两条消息为一组的方式存储，便于后续构造上下文时保持对话轮次一致。</p>
     *
     * @param userQuestion 用户问题
     * @param aiAnswer 模型回答
     */
    public void addMessage(String userQuestion, String aiAnswer) {
        lock.lock();
        try {
            messageHistory.add(ChatMessage.user(userQuestion));
            messageHistory.add(ChatMessage.assistant(aiAnswer));

            int maxMessages = maxWindowSize * 2;
            while (messageHistory.size() > maxMessages) {
                messageHistory.remove(0);
                if (!messageHistory.isEmpty()) {
                    messageHistory.remove(0);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前会话历史的安全副本。
     *
     * @return 历史消息拷贝，调用方可安全遍历但不应修改原始会话状态
     */
    public List<ChatMessage> getHistory() {
        lock.lock();
        try {
            return new ArrayList<>(messageHistory.stream()
                    .map(ChatMessage::new)
                    .toList());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清空会话历史。
     */
    public void clearHistory() {
        lock.lock();
        try {
            messageHistory.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前保存的消息对数量。
     *
     * @return 消息对数
     */
    public int getMessagePairCount() {
        lock.lock();
        try {
            return messageHistory.size() / 2;
        } finally {
            lock.unlock();
        }
    }
}
