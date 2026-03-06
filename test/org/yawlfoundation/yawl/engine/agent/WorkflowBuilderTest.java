package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD tests for WorkflowBuilder fluent API.
 */
@DisplayName("WorkflowBuilder Fluent API Tests")
class WorkflowBuilderTest {

    @Test
    @DisplayName("builds simple workflow with places and transitions")
    void buildsSimpleWorkflow() {
        WorkflowDef workflow = WorkflowBuilder.named("Order Process")
            .description("Handles customer orders")
            .place("start", "Start", 1)
            .place("end", "End")
            .transition("review", "Review Order", "manual")
            .finalTransition("complete", "Complete Order", "automatic")
            .startAt("start")
            .build();

        assertThat(workflow.name()).isEqualTo("Order Process");
        assertThat(workflow.description()).isEqualTo("Handles customer orders");
        assertThat(workflow.placeCount()).isEqualTo(2);
        assertThat(workflow.transitionCount()).isEqualTo(2);
        assertThat(workflow.getInitialPlace().id()).isEqualTo("start");
        assertThat(workflow.getInitialPlace().getTokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("builds workflow with custom UUID")
    void buildsWorkflowWithCustomUUID() {
        UUID customId = UUID.fromString("12345678-1234-1234-1234-123456789abc");

        WorkflowDef workflow = WorkflowBuilder.named("Custom ID")
            .id(customId)
            .place("p0", "Start", 1)
            .transition("t0", "Task", "auto")
            .startAt("p0")
            .build();

        assertThat(workflow.workflowId()).isEqualTo(customId);
    }

    @Test
    @DisplayName("builds workflow with multi-instance transition")
    void buildsMultiInstanceWorkflow() {
        WorkflowDef workflow = WorkflowBuilder.named("Parallel Process")
            .place("p0", "Start", 1)
            .place("p1", "Middle")
            .place("p2", "End")
            .transition("t0", "Init", "automatic")
            .multiInstanceTransition("t1", "Parallel Task", "service")
            .finalTransition("t2", "Finish", "automatic")
            .startAt("p0")
            .build();

        assertThat(workflow.transitionCount()).isEqualTo(3);
        assertThat(workflow.transitions().get(1).isMultiInstance()).isTrue();
    }

    @Test
    @DisplayName("rejects build without places")
    void rejectsBuildWithoutPlaces() {
        assertThatThrownBy(() ->
            WorkflowBuilder.named("Empty")
                .transition("t0", "Task", "auto")
                .startAt("p0")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("place");
    }

    @Test
    @DisplayName("rejects build without transitions")
    void rejectsBuildWithoutTransitions() {
        assertThatThrownBy(() ->
            WorkflowBuilder.named("No Transitions")
                .place("p0", "Start", 1)
                .startAt("p0")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("transition");
    }

    @Test
    @DisplayName("rejects build without initial place")
    void rejectsBuildWithoutInitialPlace() {
        assertThatThrownBy(() ->
            WorkflowBuilder.named("No Start")
                .place("p0", "Start", 1)
                .transition("t0", "Task", "auto")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Initial place");
    }

    @Test
    @DisplayName("rejects build with invalid initial place ID")
    void rejectsInvalidInitialPlaceId() {
        assertThatThrownBy(() ->
            WorkflowBuilder.named("Bad Start")
                .place("p0", "Start", 1)
                .transition("t0", "Task", "auto")
                .startAt("nonexistent")
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("generated workflow has valid structure")
    void generatedWorkflowIsValid() {
        WorkflowDef workflow = WorkflowBuilder.named("Valid Process")
            .place("p0", "Start", 1)
            .place("p1", "End")
            .transition("t0", "Task", "auto")
            .startAt("p0")
            .build();

        assertThat(workflow.isValid()).isTrue();
    }

    @Test
    @DisplayName("places with zero initial tokens by default")
    void placesHaveZeroTokensByDefault() {
        WorkflowDef workflow = WorkflowBuilder.named("Zero Tokens")
            .place("p0", "Start", 1)
            .place("p1", "Middle")
            .transition("t0", "Task", "auto")
            .startAt("p0")
            .build();

        assertThat(workflow.findPlace("p1").getTokenCount()).isZero();
    }
}
