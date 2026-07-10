package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.domain.po.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {

    @Test
    void createChatModelUsesConfiguredModelName() throws Exception {
        ChatService chatService = new ChatService();
        setField(chatService, "chatModelName", "glm-5.1");

        DashScopeChatModel chatModel = chatService.createChatModel(
                DashScopeApi.builder().apiKey("test-key").build(),
                0.7,
                2000,
                0.9);

        assertThat(chatModel.getDashScopeChatOptions().getModel()).isEqualTo("glm-5.1");
    }

    @Test
    void chatModelNameRequiresExplicitConfiguration() throws Exception {
        Field field = ChatService.class.getDeclaredField("chatModelName");

        assertThat(field.getAnnotation(Value.class).value())
                .isEqualTo("${spring.ai.dashscope.chat.options.model}");
    }

    @Test
    void systemPromptIdentifiesOpsMindAssistant() {
        ChatService chatService = new ChatService();

        String systemPrompt = chatService.buildSystemPrompt(List.of());

        assertThat(systemPrompt).contains("OpsMind 智能运维助手");
    }

    @Test
    void systemPromptIncludesTypedChatHistory() {
        ChatService chatService = new ChatService();

        String systemPrompt = chatService.buildSystemPrompt(List.of(
                ChatMessage.user("CPU 告警怎么处理"),
                ChatMessage.assistant("先确认负载来源")
        ));

        assertThat(systemPrompt)
                .contains("CPU 告警怎么处理")
                .contains("先确认负载来源");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
