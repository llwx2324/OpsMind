package org.example.memory;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.service.ChatService;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

@Component
/** 使用现有 DashScope ChatService 实现受控的非流式记忆模型调用。 */
public class ChatServiceMemoryLlmGateway implements MemoryLlmGateway {
    private final ChatService chatService;
    public ChatServiceMemoryLlmGateway(ChatService chatService) { this.chatService = chatService; }
    @Override
    /** {@inheritDoc} */
    public String complete(String systemPrompt, String userPrompt) {
        DashScopeApi api = chatService.createDashScopeApi();
        DashScopeChatModel model = chatService.createJsonChatModel(api, 1200);
        try {
            return model.call(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
        } catch (Exception e) {
            throw new IllegalStateException("memory model invocation failed", e);
        }
    }
}
