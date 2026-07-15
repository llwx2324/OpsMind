package org.example.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证长期记忆候选类型、长度及敏感信息拒绝规则。 */
class LongMemorySensitiveFilterTest {
    private final LongMemoryService service = new LongMemoryService(null, null, null, null, null, null, null, null, null);

    @Test
    void acceptsLowRiskMemory() {
        assertTrue(service.isAllowedCandidate("user_preference", "用户偏好先给结论再给步骤"));
        assertTrue(service.isAllowedCandidate("ops_fact", "测试环境名称为 lab-blue"));
    }

    @Test
    void rejectsSecretsAndPersonalData() {
        assertFalse(service.isAllowedCandidate("ops_fact", "password=SuperSecret123"));
        assertFalse(service.isAllowedCandidate("ops_fact", "Authorization: Bearer abc.def.ghi"));
        assertFalse(service.isAllowedCandidate("ops_fact", "api_key=sk-1234567890abcdef"));
        assertFalse(service.isAllowedCandidate("user_preference", "邮箱 user@example.com"));
        assertFalse(service.isAllowedCandidate("user_preference", "手机号 13812345678"));
        assertFalse(service.isAllowedCandidate("ops_fact", "身份证 110101199001011234"));
    }

    @Test
    void rejectsUnknownTypesAndOversizedContent() {
        assertFalse(service.isAllowedCandidate("raw_log", "harmless"));
        assertFalse(service.isAllowedCandidate("ops_fact", "x".repeat(1001)));
    }
}
