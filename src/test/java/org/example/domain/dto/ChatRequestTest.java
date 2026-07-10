package org.example.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void supportsUpperCamelCaseJsonFields() throws Exception {
        ChatRequest request = objectMapper.readValue(
                "{\"Id\":\"session-1\",\"Question\":\"怎么处理 CPU 告警\"}",
                ChatRequest.class);

        assertThat(request.getId()).isEqualTo("session-1");
        assertThat(request.getQuestion()).isEqualTo("怎么处理 CPU 告警");
    }

    @Test
    void supportsLowerCamelCaseJsonAliases() throws Exception {
        ChatRequest request = objectMapper.readValue(
                "{\"id\":\"session-2\",\"question\":\"怎么处理内存告警\"}",
                ChatRequest.class);

        assertThat(request.getId()).isEqualTo("session-2");
        assertThat(request.getQuestion()).isEqualTo("怎么处理内存告警");
    }
}
