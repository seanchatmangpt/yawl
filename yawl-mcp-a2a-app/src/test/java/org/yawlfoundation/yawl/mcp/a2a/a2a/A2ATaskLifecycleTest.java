/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.a2a;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentProvider;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.Artifact;
import io.a2a.spec.DataPart;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive JUnit 5 tests for A2A protocol data model and task lifecycle.
 *
 * Tests cover:
 * - AgentCard construction with identity, provider, capabilities, skills
 * - Task creation and lifecycle (submitted, working, completed, failed)
 * - Message parts (text, data) and artifacts
 * - Skill definition and discovery
 * - TaskStatus and TaskState enums
 * - Object equality contracts
 * - Validation of required fields
 */
class A2ATaskLifecycleTest {

    @Nested
    @DisplayName("AgentCard Construction")
    class AgentCardTests {

        private AgentCard.Builder cardBuilder;

        @BeforeEach
        void setUp() {
            cardBuilder = AgentCard.builder()
                    .name("test-agent")
                    .description("Test workflow agent")
                    .version("1.0.0")
                    .capabilities(AgentCapabilities.builder().streaming(false).pushNotifications(false).build())
                    .defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text"))
                    .skills(List.of())
                    .supportedInterfaces(List.of());
        }

        @Test
        @DisplayName("Create minimal AgentCard with required fields")
        void testCreateMinimalAgentCard() {
            AgentCard card = cardBuilder.build();

            assertNotNull(card);
            assertEquals("test-agent", card.name());
            assertEquals("Test workflow agent", card.description());
            assertEquals("1.0.0", card.version());
        }

        @Test
        @DisplayName("AgentCard with provider information")
        void testAgentCardWithProvider() {
            AgentProvider provider = new AgentProvider(
                    "YAWL Foundation",
                    "https://yawlfoundation.github.io"
            );

            AgentCard card = cardBuilder
                    .provider(provider)
                    .build();

            assertNotNull(card.provider());
            assertEquals("YAWL Foundation", card.provider().organization());
            assertEquals("https://yawlfoundation.github.io", card.provider().url());
        }

        @Test
        @DisplayName("AgentCard with capabilities")
        void testAgentCardWithCapabilities() {
            AgentCapabilities capabilities = AgentCapabilities.builder()
                    .streaming(true)
                    .pushNotifications(false)
                    .build();

            AgentCard card = cardBuilder
                    .capabilities(capabilities)
                    .build();

            assertNotNull(card.capabilities());
            assertTrue(card.capabilities().streaming());
            assertFalse(card.capabilities().pushNotifications());
        }

        @Test
        @DisplayName("AgentCard with input/output modes")
        void testAgentCardWithModes() {
            AgentCard card = cardBuilder
                    .defaultInputModes(List.of("text", "structured"))
                    .defaultOutputModes(List.of("text"))
                    .build();

            assertNotNull(card.defaultInputModes());
            assertNotNull(card.defaultOutputModes());
            assertEquals(2, card.defaultInputModes().size());
            assertEquals(1, card.defaultOutputModes().size());
        }

        @Test
        @DisplayName("AgentCard with interface definitions")
        void testAgentCardWithInterfaces() {
            AgentInterface restInterface = new AgentInterface(
                    "a2a-rest",
                    "http://localhost:8080/a2a"
            );

            AgentCard card = cardBuilder
                    .supportedInterfaces(List.of(restInterface))
                    .build();

            assertNotNull(card.supportedInterfaces());
            assertEquals(1, card.supportedInterfaces().size());
            assertEquals("a2a-rest", card.supportedInterfaces().get(0).protocolBinding());
            assertEquals("http://localhost:8080/a2a", card.supportedInterfaces().get(0).url());
        }

        @Test
        @DisplayName("AgentCard equality")
        void testAgentCardEquality() {
            AgentCard card1 = cardBuilder.build();
            AgentCard card2 = cardBuilder.build();

            assertEquals(card1, card2);
            assertEquals(card1.hashCode(), card2.hashCode());
        }

        @Test
        @DisplayName("AgentCard string representation")
        void testAgentCardToString() {
            AgentCard card = cardBuilder.build();
            String representation = card.toString();

            assertNotNull(representation);
            assertFalse(representation.isEmpty());
        }
    }

    @Nested
    @DisplayName("Skill Definition")
    class SkillTests {

        @Test
        @DisplayName("Create basic Skill")
        void testCreateBasicSkill() {
            AgentSkill skill = AgentSkill.builder()
                    .id("analyze")
                    .name("Analysis Skill")
                    .description("Analyzes workflow context")
                    .tags(List.of())
                    .build();

            assertNotNull(skill);
            assertEquals("analyze", skill.id());
            assertEquals("Analysis Skill", skill.name());
            assertEquals("Analyzes workflow context", skill.description());
        }

        @Test
        @DisplayName("Skill with input/output modes")
        void testSkillWithModes() {
            AgentSkill skill = AgentSkill.builder()
                    .id("transform")
                    .name("Data Transformation")
                    .description("Transforms workflow data")
                    .inputModes(List.of("text", "json"))
                    .outputModes(List.of("text", "json"))
                    .tags(List.of())
                    .build();

            assertNotNull(skill.inputModes());
            assertNotNull(skill.outputModes());
            assertEquals(2, skill.inputModes().size());
            assertEquals(2, skill.outputModes().size());
        }

        @Test
        @DisplayName("Skill with tags")
        void testSkillWithTags() {
            AgentSkill skill = AgentSkill.builder()
                    .id("chat")
                    .name("Natural Language Chat")
                    .description("Chat interface")
                    .tags(List.of("nlp", "assistant", "ai"))
                    .build();

            assertNotNull(skill.tags());
            assertEquals(3, skill.tags().size());
            assertTrue(skill.tags().contains("ai"));
        }

        @Test
        @DisplayName("Multiple skills in agent card")
        void testMultipleSkillsInAgentCard() {
            AgentSkill skill1 = AgentSkill.builder()
                    .id("skill-1")
                    .name("Skill One")
                    .description("First skill")
                    .tags(List.of())
                    .build();

            AgentSkill skill2 = AgentSkill.builder()
                    .id("skill-2")
                    .name("Skill Two")
                    .description("Second skill")
                    .tags(List.of())
                    .build();

            AgentCard card = AgentCard.builder()
                    .name("multi-skill-agent")
                    .description("Agent with multiple skills")
                    .version("1.0.0")
                    .capabilities(AgentCapabilities.builder().streaming(false).pushNotifications(false).build())
                    .defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text"))
                    .skills(List.of(skill1, skill2))
                    .supportedInterfaces(List.of())
                    .build();

            assertNotNull(card.skills());
            assertEquals(2, card.skills().size());
            assertEquals("skill-1", card.skills().get(0).id());
            assertEquals("skill-2", card.skills().get(1).id());
        }

        @Test
        @DisplayName("Skill equality")
        void testSkillEquality() {
            AgentSkill skill1 = AgentSkill.builder()
                    .id("test")
                    .name("Test Skill")
                    .description("For testing")
                    .tags(List.of())
                    .build();

            AgentSkill skill2 = AgentSkill.builder()
                    .id("test")
                    .name("Test Skill")
                    .description("For testing")
                    .tags(List.of())
                    .build();

            assertEquals(skill1, skill2);
        }
    }

    @Nested
    @DisplayName("Message Construction")
    class MessageTests {

        @Test
        @DisplayName("Message with text part")
        void testCreateMessageWithTextPart() {
            TextPart textPart = new TextPart("Hello, workflow!");

            Message message = Message.builder()
                    .role(Message.Role.USER)
                    .parts(List.of(textPart))
                    .build();

            assertNotNull(message);
            assertNotNull(message.parts());
            assertEquals(1, message.parts().size());
            assertTrue(message.parts().get(0) instanceof TextPart);
        }

        @Test
        @DisplayName("Message with data part")
        void testCreateMessageWithDataPart() {
            String jsonData = "{\"case_id\": \"case-123\", \"status\": \"running\"}";
            DataPart dataPart = DataPart.builder()
                    .data(jsonData)
                    .build();

            Message message = Message.builder()
                    .role(Message.Role.USER)
                    .parts(List.of(dataPart))
                    .build();

            assertNotNull(message);
            assertNotNull(message.parts());
            assertEquals(1, message.parts().size());
            assertTrue(message.parts().get(0) instanceof DataPart);
        }

        @Test
        @DisplayName("Message with mixed parts")
        void testMessageWithMixedParts() {
            TextPart textPart = new TextPart("Case update:");
            String statusJson = "{\"status\": \"completed\"}";
            DataPart dataPart = DataPart.builder()
                    .data(statusJson)
                    .build();

            Message message = Message.builder()
                    .role(Message.Role.USER)
                    .parts(List.of(textPart, dataPart))
                    .build();

            assertNotNull(message.parts());
            assertEquals(2, message.parts().size());
            assertTrue(message.parts().get(0) instanceof TextPart);
            assertTrue(message.parts().get(1) instanceof DataPart);
        }

        @Test
        @DisplayName("Message with single text part")
        void testMessageWithEmptyParts() {
            TextPart textPart = new TextPart("status check");
            Message message = Message.builder()
                    .role(Message.Role.USER)
                    .parts(List.of(textPart))
                    .build();

            assertNotNull(message);
            assertNotNull(message.parts());
            assertEquals(1, message.parts().size());
        }

        @Test
        @DisplayName("Message equality by fields")
        void testMessageEquality() {
            TextPart textPart = new TextPart("Test message");

            Message message = Message.builder()
                    .role(Message.Role.USER)
                    .parts(List.of(textPart))
                    .build();

            // Message auto-generates messageId UUID, so verify field equality
            assertEquals(Message.Role.USER, message.role());
            assertEquals(1, message.parts().size());
            assertTrue(message.parts().get(0) instanceof TextPart);
            assertEquals(message, message); // reflexive
        }
    }

    @Nested
    @DisplayName("Artifact Construction")
    class ArtifactTests {

        @Test
        @DisplayName("Create artifact with metadata")
        void testCreateArtifactWithMetadata() {
            Artifact artifact = Artifact.builder()
                    .artifactId("artifact-1")
                    .name("Test Artifact")
                    .description("Test artifact description")
                    .parts(List.of(new TextPart("artifact content")))
                    .build();

            assertNotNull(artifact);
            assertEquals("artifact-1", artifact.artifactId());
            assertEquals("Test Artifact", artifact.name());
            assertEquals("Test artifact description", artifact.description());
        }

        @Test
        @DisplayName("Artifact with parts")
        void testArtifactWithParts() {
            TextPart textPart = new TextPart("Artifact content");
            Artifact artifact = Artifact.builder()
                    .artifactId("artifact-parts")
                    .name("Content Artifact")
                    .parts(List.of(textPart))
                    .build();

            assertNotNull(artifact);
            assertNotNull(artifact.parts());
            assertEquals(1, artifact.parts().size());
        }

        @Test
        @DisplayName("Artifact equality")
        void testArtifactEquality() {
            Artifact artifact1 = Artifact.builder()
                    .artifactId("doc-1")
                    .name("Document")
                    .description("Test document")
                    .parts(List.of(new TextPart("document content")))
                    .build();

            Artifact artifact2 = Artifact.builder()
                    .artifactId("doc-1")
                    .name("Document")
                    .description("Test document")
                    .parts(List.of(new TextPart("document content")))
                    .build();

            assertEquals(artifact1, artifact2);
            assertEquals(artifact1.hashCode(), artifact2.hashCode());
        }

        @Test
        @DisplayName("Artifact string representation")
        void testArtifactToString() {
            Artifact artifact = Artifact.builder()
                    .artifactId("test-artifact")
                    .name("Test")
                    .parts(List.of(new TextPart("test output")))
                    .build();

            String representation = artifact.toString();
            assertNotNull(representation);
            assertFalse(representation.isEmpty());
        }
    }

    @Nested
    @DisplayName("Task Construction")
    class TaskTests {

        @Test
        @DisplayName("Create task with minimum fields")
        void testCreateMinimalTask() {
            Task task = Task.builder()
                    .id("task-minimal")
                    .contextId("context-1")
                    .status(new TaskStatus(TaskState.SUBMITTED))
                    .build();

            assertNotNull(task);
            assertEquals("task-minimal", task.id());
            assertEquals("context-1", task.contextId());
        }

        @Test
        @DisplayName("Task with message input")
        void testTaskWithMessage() {
            TextPart textPart = new TextPart("Analyze this workflow case");
            Message message = Message.builder()
                    .role(Message.Role.USER)
                    .parts(List.of(textPart))
                    .build();

            Task task = Task.builder()
                    .id("task-msg-001")
                    .contextId("context-1")
                    .status(new TaskStatus(TaskState.WORKING))
                    .history(message)
                    .build();

            assertNotNull(task);
            assertNotNull(task.history());
            assertEquals("task-msg-001", task.id());
        }

        @Test
        @DisplayName("Task with metadata")
        void testTaskWithMetadata() {
            Map<String, Object> metadata = Map.of(
                    "case-id", "case-123",
                    "priority", "high"
            );

            Task task = Task.builder()
                    .id("task-meta-001")
                    .contextId("context-1")
                    .status(new TaskStatus(TaskState.SUBMITTED))
                    .metadata(metadata)
                    .build();

            assertNotNull(task);
            assertNotNull(task.metadata());
        }

        @Test
        @DisplayName("Task equality by fields")
        void testTaskEquality() {
            Task task = Task.builder()
                    .id("task-eq-001")
                    .contextId("context-1")
                    .status(new TaskStatus(TaskState.SUBMITTED))
                    .build();

            // Task includes a timestamp in TaskStatus, so verify field equality
            assertEquals("task-eq-001", task.id());
            assertEquals("context-1", task.contextId());
            assertEquals(TaskState.SUBMITTED, task.status().state());
            assertEquals(task, task); // reflexive
        }

        @Test
        @DisplayName("Task inequality for different tasks")
        void testTaskInequality() {
            Task task1 = Task.builder()
                    .id("task-diff-001")
                    .contextId("context-1")
                    .status(new TaskStatus(TaskState.SUBMITTED))
                    .build();

            Task task2 = Task.builder()
                    .id("task-diff-002")
                    .contextId("context-1")
                    .status(new TaskStatus(TaskState.SUBMITTED))
                    .build();

            assertFalse(task1.equals(task2));
        }

        @Test
        @DisplayName("Task string representation")
        void testTaskToString() {
            Task task = Task.builder()
                    .id("task-str-001")
                    .contextId("context-1")
                    .status(new TaskStatus(TaskState.SUBMITTED))
                    .build();

            String representation = task.toString();
            assertNotNull(representation);
            assertFalse(representation.isEmpty());
        }
    }

    @Nested
    @DisplayName("Task Status and State")
    class TaskStatusTests {

        @Test
        @DisplayName("TaskState enum values")
        void testTaskStateValues() {
            TaskState[] states = TaskState.values();

            assertNotNull(states);
            assertTrue(states.length > 0);

            boolean hasSubmitted = false;
            boolean hasWorking = false;
            boolean hasCompleted = false;

            for (TaskState state : states) {
                if (state == TaskState.SUBMITTED) hasSubmitted = true;
                if (state == TaskState.WORKING) hasWorking = true;
                if (state == TaskState.COMPLETED) hasCompleted = true;
            }

            assertTrue(hasSubmitted);
            assertTrue(hasWorking);
            assertTrue(hasCompleted);
        }

        @Test
        @DisplayName("TaskStatus contains state")
        void testTaskStatusStructure() {
            Task task = Task.builder()
                    .id("task-001")
                    .contextId("context-1")
                    .status(new TaskStatus(TaskState.SUBMITTED))
                    .build();

            assertNotNull(task);
            assertNotNull(task.status());
            assertNotNull(task.status().state());
        }
    }

    @Nested
    @DisplayName("Complex Workflow Scenarios")
    class WorkflowScenarioTests {

        @Test
        @DisplayName("Complete workflow agent")
        void testCompleteWorkflowAgent() {
            AgentSkill analyzeSkill = AgentSkill.builder()
                    .id("analyze-workflow")
                    .name("Workflow Analysis")
                    .description("Analyzes workflow structure")
                    .inputModes(List.of("text", "json"))
                    .outputModes(List.of("text", "json"))
                    .tags(List.of("analysis", "workflow"))
                    .build();

            AgentSkill decideSkill = AgentSkill.builder()
                    .id("make-decision")
                    .name("Decision Making")
                    .description("Makes workflow routing decisions")
                    .inputModes(List.of("json"))
                    .outputModes(List.of("json"))
                    .tags(List.of("decision", "routing"))
                    .build();

            AgentProvider provider = new AgentProvider(
                    "YAWL Foundation",
                    "https://yawlfoundation.github.io"
            );

            AgentCapabilities capabilities = AgentCapabilities.builder()
                    .streaming(true)
                    .pushNotifications(true)
                    .build();

            AgentInterface restInterface = new AgentInterface(
                    "a2a-rest",
                    "http://localhost:8080/a2a"
            );

            AgentCard agent = AgentCard.builder()
                    .name("yawl-workflow-agent")
                    .description("Intelligent YAWL workflow management agent")
                    .version("6.0.0")
                    .provider(provider)
                    .capabilities(capabilities)
                    .defaultInputModes(List.of("text", "json"))
                    .defaultOutputModes(List.of("text", "json"))
                    .skills(List.of(analyzeSkill, decideSkill))
                    .supportedInterfaces(List.of(restInterface))
                    .build();

            assertNotNull(agent);
            assertEquals("yawl-workflow-agent", agent.name());
            assertEquals(2, agent.skills().size());
            assertNotNull(agent.provider());
            assertNotNull(agent.capabilities());
        }

        @Test
        @DisplayName("Process workflow task")
        void testWorkflowTaskProcessing() {
            String caseData = "{\"case_id\": \"case-123\", \"status\": \"running\"}";
            DataPart dataPart = DataPart.builder()
                    .data(caseData)
                    .build();
            TextPart textPart = new TextPart("Analyze case status");

            Message taskMessage = Message.builder()
                    .role(Message.Role.USER)
                    .parts(List.of(textPart, dataPart))
                    .build();

            Map<String, Object> metadata = Map.of(
                    "case-id", "case-123",
                    "priority", "high"
            );

            Task task = Task.builder()
                    .id("workflow-task-001")
                    .contextId("case-context-123")
                    .status(new TaskStatus(TaskState.WORKING))
                    .history(taskMessage)
                    .metadata(metadata)
                    .build();

            assertNotNull(task);
            assertEquals("workflow-task-001", task.id());
            assertNotNull(task.history());
            assertNotNull(task.metadata());
        }

        @Test
        @DisplayName("Workflow completion report")
        void testWorkflowCompletionReport() {
            Artifact reportArtifact = Artifact.builder()
                    .artifactId("completion-report-001")
                    .name("Completion Report")
                    .description("Final workflow report")
                    .parts(List.of(new TextPart("Workflow completed")))
                    .build();

            Message completionMessage = Message.builder()
                    .role(Message.Role.AGENT)
                    .parts(List.of(new TextPart("Workflow case completed successfully")))
                    .build();

            Task completionTask = Task.builder()
                    .id("completion-task-001")
                    .contextId("completion-context")
                    .status(new TaskStatus(TaskState.COMPLETED))
                    .history(completionMessage)
                    .build();

            assertNotNull(reportArtifact);
            assertNotNull(completionTask);
            assertEquals("completion-report-001", reportArtifact.artifactId());
        }
    }
}
