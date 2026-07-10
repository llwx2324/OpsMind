package org.example.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOpsServiceTest {

    @Test
    void plannerSystemPromptDoesNotContainStateTemplateVariables() {
        AiOpsService service = new AiOpsService();

        String systemPrompt = service.buildPlannerSystemPrompt();

        assertThat(systemPrompt)
                .doesNotContain("{input}")
                .doesNotContain("{executor_feedback}");
    }

    @Test
    void plannerInstructionUsesOnlyInputTemplateVariable() {
        AiOpsService service = new AiOpsService();

        String instruction = service.buildPlannerInstruction();

        assertThat(instruction)
                .contains("{input}")
                .doesNotContain("{executor_feedback}")
                .doesNotContain("{\"");
    }

    @Test
    void executorSystemPromptDoesNotContainPlannerPlanTemplateVariable() {
        AiOpsService service = new AiOpsService();

        String systemPrompt = service.buildExecutorSystemPrompt();

        assertThat(systemPrompt).doesNotContain("{planner_plan}");
    }

    @Test
    void executorInstructionUsesPlannerPlanTemplateVariable() {
        AiOpsService service = new AiOpsService();

        String instruction = service.buildExecutorInstruction();

        assertThat(instruction)
                .contains("{planner_plan}")
                .doesNotContain("{\"");
    }
}
