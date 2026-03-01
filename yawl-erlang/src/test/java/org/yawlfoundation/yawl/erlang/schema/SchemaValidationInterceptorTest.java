/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.erlang.schema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.workflow.TaskSchemaViolation;
import org.yawlfoundation.yawl.erlang.workflow.WorkflowEventBus;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SchemaValidationInterceptor} and {@link SchemaContractRegistry}.
 *
 * <p>Test fixtures use {@link TaskSchemaContract}-annotated inner classes that
 * reference YAML files in {@code src/test/resources/schemas/}.</p>
 */
class SchemaValidationInterceptorTest {

    // -----------------------------------------------------------------------
    // Task fixture classes — annotated with @TaskSchemaContract
    // -----------------------------------------------------------------------

    @TaskSchemaContract(
            input = "schemas/test-order-input.yaml",
            output = "schemas/test-order-output.yaml",
            inputFallback = "schemas/test-order-fallback.yaml")
    static class OrderTask {}

    @TaskSchemaContract(input = "schemas/test-payment-input.yaml")
    static class PaymentTask {}

    // No annotation — used to verify pass-through
    static class UnschematizedTask {}

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    private WorkflowEventBus eventBus;
    private SchemaContractRegistry registry;
    private SchemaValidationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        eventBus = new WorkflowEventBus();
        registry = new SchemaContractRegistry(
                List.of(OrderTask.class, PaymentTask.class));
        interceptor = new SchemaValidationInterceptor(registry, eventBus);
    }

    @AfterEach
    void tearDown() throws Exception {
        eventBus.close();
    }

    // -----------------------------------------------------------------------
    // validateInput tests
    // -----------------------------------------------------------------------

    @Test
    void validateInput_passesWhenAllRequiredFieldsPresent() {
        assertDoesNotThrow(() ->
                interceptor.validateInput("OrderTask",
                        "{\"orderId\": \"ORD-001\", \"quantity\": 5}"),
                "Valid JSON with all required fields must pass");
    }

    @Test
    void validateInput_throwsWhenRequiredFieldMissing() {
        TaskSchemaViolationException ex = assertThrows(
                TaskSchemaViolationException.class,
                () -> interceptor.validateInput("PaymentTask",
                        "{\"amount\": 99.99}"),  // missing 'currency'
                "Missing required field 'currency' must throw");

        assertEquals("PaymentTask", ex.getTaskId());
        assertTrue(ex.getDiff().contains("currency"),
                "Exception diff must name the missing field");
    }

    @Test
    void validateInput_publishesTaskSchemaViolationEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean received = new AtomicBoolean(false);

        eventBus.subscribe(TaskSchemaViolation.class, event -> {
            if ("PaymentTask".equals(event.taskId())) {
                received.set(true);
                latch.countDown();
            }
        });

        assertThrows(TaskSchemaViolationException.class,
                () -> interceptor.validateInput("PaymentTask", "{}"));

        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "TaskSchemaViolation event must be published within 2s");
        assertTrue(received.get());
    }

    @Test
    void validateInput_passesThroughWhenNoSchemaRegistered() {
        // UnschematizedTask has no @TaskSchemaContract, so no schema in registry
        assertDoesNotThrow(() ->
                interceptor.validateInput("UnschematizedTask", "{}"),
                "Task with no registered schema must pass through without validation");
    }

    @Test
    void validateInput_acceptsFallbackSchemaWhenPrimaryFails() {
        // OrderTask primary requires 'orderId'+'quantity'; fallback requires 'legacyOrderRef'
        // JSON has only 'legacyOrderRef' — should pass via fallback
        assertDoesNotThrow(() ->
                interceptor.validateInput("OrderTask",
                        "{\"legacyOrderRef\": \"LEGACY-42\"}"),
                "Fallback schema must be tried when primary fails");
    }

    @Test
    void validateInput_throwsWhenBothPrimaryAndFallbackFail() {
        // JSON has neither required fields from primary nor fallback
        assertThrows(TaskSchemaViolationException.class,
                () -> interceptor.validateInput("OrderTask",
                        "{\"irrelevant\": \"value\"}"),
                "Must throw when both primary and fallback schemas fail");
    }

    @Test
    void validateInput_typeChecking_rejectsStringWhereIntegerExpected() {
        // 'quantity' is declared as integer in test-order-input.yaml
        TaskSchemaViolationException ex = assertThrows(
                TaskSchemaViolationException.class,
                () -> interceptor.validateInput("OrderTask",
                        "{\"orderId\": \"ORD-001\", \"quantity\": \"not-a-number\"}"));

        assertTrue(ex.getDiff().contains("quantity"),
                "Violation diff must reference 'quantity' as the wrongly typed field");
    }

    @Test
    void validateInput_optionalFieldAbsenceIsAllowed() {
        // 'notes' is optional in test-order-input.yaml
        assertDoesNotThrow(() ->
                interceptor.validateInput("OrderTask",
                        "{\"orderId\": \"ORD-002\", \"quantity\": 10}"),
                "Optional field 'notes' absence must not cause validation failure");
    }

    // -----------------------------------------------------------------------
    // validateOutput tests
    // -----------------------------------------------------------------------

    @Test
    void validateOutput_passesWhenOutputSchemaMatched() {
        // OrderTask has output schema requiring 'confirmationId' + 'status'
        assertDoesNotThrow(() ->
                interceptor.validateOutput("OrderTask",
                        "{\"confirmationId\": \"CONF-99\", \"status\": \"accepted\"}"));
    }

    @Test
    void validateOutput_throwsOnMissingRequiredOutputField() {
        assertThrows(TaskSchemaViolationException.class,
                () -> interceptor.validateOutput("OrderTask",
                        "{\"confirmationId\": \"CONF-99\"}"));  // missing 'status'
    }

    @Test
    void validateOutput_passesThroughWhenNoOutputSchemaRegistered() {
        // PaymentTask only has input schema, no output schema
        assertDoesNotThrow(() ->
                interceptor.validateOutput("PaymentTask", "{}"),
                "Task with no output schema must pass through");
    }

    // -----------------------------------------------------------------------
    // Guard checks
    // -----------------------------------------------------------------------

    @Test
    void validateInput_rejectsBlankTaskId() {
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.validateInput("", "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.validateInput("  ", "{}"));
    }

    @Test
    void validateInput_rejectsNullJson() {
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.validateInput("OrderTask", null));
    }

    @Test
    void constructor_rejectsNullRegistry() {
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaValidationInterceptor(null, eventBus));
    }

    @Test
    void constructor_rejectsNullEventBus() {
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaValidationInterceptor(registry, null));
    }

    // -----------------------------------------------------------------------
    // SchemaContractRegistry parser tests
    // -----------------------------------------------------------------------

    @Test
    void parseOdcsYaml_extractsNameVersionAndFields() {
        String yaml = """
                name: OrderSchema
                version: "1.0"
                properties:
                  orderId:
                    type: string
                    required: true
                  quantity:
                    type: integer
                    required: false
                """;

        ParsedSchema parsed = SchemaContractRegistry.parseOdcsYaml(yaml, "test://inline");

        assertEquals("OrderSchema", parsed.name());
        assertEquals("1.0", parsed.version());
        assertEquals(2, parsed.fields().size());

        SchemaField first = parsed.fields().get(0);
        assertEquals("orderId", first.name());
        assertEquals("string", first.type());
        assertTrue(first.required());

        SchemaField second = parsed.fields().get(1);
        assertEquals("quantity", second.name());
        assertEquals("integer", second.type());
        assertFalse(second.required());
    }

    @Test
    void parseOdcsYaml_usesResourcePathAsFallbackName() {
        String yaml = "version: \"1.0\"\nproperties:\n";
        ParsedSchema parsed = SchemaContractRegistry.parseOdcsYaml(yaml, "schemas/order-v1.yaml");

        assertEquals("order-v1", parsed.name());
    }

    @Test
    void parseOdcsYaml_handlesEmptyProperties() {
        String yaml = "name: Empty\nversion: \"2.0\"\n";
        ParsedSchema parsed = SchemaContractRegistry.parseOdcsYaml(yaml, "empty.yaml");

        assertEquals("Empty", parsed.name());
        assertTrue(parsed.fields().isEmpty());
    }

    @Test
    void registry_loadsSchemaFromClasspath() {
        // OrderTask's input schema is loaded from classpath at construction time
        assertTrue(registry.getInputSchema("OrderTask").isPresent(),
                "Input schema for OrderTask must be loaded from classpath");
        assertTrue(registry.getOutputSchema("OrderTask").isPresent(),
                "Output schema for OrderTask must be loaded from classpath");
        assertTrue(registry.getInputFallbackSchema("OrderTask").isPresent(),
                "Fallback input schema for OrderTask must be loaded from classpath");
    }

    @Test
    void registry_inputSchemaHasCorrectFields() {
        ParsedSchema schema = registry.getInputSchema("OrderTask")
                .orElseThrow(() -> new AssertionError("schema must be present"));

        assertEquals("OrderInput", schema.name());
        assertEquals("1.0", schema.version());

        List<SchemaField> required = schema.requiredFields();
        assertEquals(2, required.size(), "OrderInput must have 2 required fields");
        assertTrue(required.stream().anyMatch(f -> f.name().equals("orderId")));
        assertTrue(required.stream().anyMatch(f -> f.name().equals("quantity")));
    }
}
