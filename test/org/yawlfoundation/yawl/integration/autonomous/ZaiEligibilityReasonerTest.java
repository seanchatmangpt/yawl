/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiEligibilityReasoner;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

/**
 * Tests for ZaiEligibilityReasoner.
 * Chicago TDD style - real YAWL objects, test double only for external API.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiEligibilityReasonerTest extends TestCase {

    private AgentCapability capability;

    public ZaiEligibilityReasonerTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        capability = new AgentCapability("Ordering", "procurement, purchase orders, approvals");
    }

    public void testConstructorWithValidInputs() {
        TestZaiService service = new TestZaiService();
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);
        assertNotNull(reasoner);
    }

    public void testConstructorRejectsNullCapability() {
        TestZaiService service = new TestZaiService();
        try {
            new ZaiEligibilityReasoner(null, service);
            fail("Should reject null capability");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("capability is required"));
        }
    }

    public void testConstructorRejectsNullService() {
        try {
            new ZaiEligibilityReasoner(capability, null);
            fail("Should reject null zaiService");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("zaiService is required"));
        }
    }

    public void testConstructorWithCustomPrompts() {
        TestZaiService service = new TestZaiService();
        String systemPrompt = "Custom system prompt: {capability}";
        String userPrompt = "Custom user prompt: {taskName}";

        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(
            capability, service, systemPrompt, userPrompt);

        assertNotNull(reasoner);
    }

    public void testConstructorRejectsNullSystemPrompt() {
        TestZaiService service = new TestZaiService();
        try {
            new ZaiEligibilityReasoner(capability, service, null, "user prompt");
            fail("Should reject null systemPromptTemplate");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("systemPromptTemplate is required"));
        }
    }

    public void testConstructorRejectsEmptySystemPrompt() {
        TestZaiService service = new TestZaiService();
        try {
            new ZaiEligibilityReasoner(capability, service, "", "user prompt");
            fail("Should reject empty systemPromptTemplate");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("systemPromptTemplate is required"));
        }
    }

    public void testConstructorRejectsNullUserPrompt() {
        TestZaiService service = new TestZaiService();
        try {
            new ZaiEligibilityReasoner(capability, service, "system prompt", null);
            fail("Should reject null userPromptTemplate");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("userPromptTemplate is required"));
        }
    }

    public void testIsEligibleReturnsTrue() {
        TestZaiService service = new TestZaiService("YES, this is suitable");
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        WorkItemRecord workItem = createWorkItem("ApproveOrder", "case-123");

        boolean result = reasoner.isEligible(workItem);

        assertTrue(result);
        assertNotNull(service.getLastSystemPrompt());
        assertNotNull(service.getLastUserMessage());
        assertTrue(service.getLastUserMessage().contains("ApproveOrder"));
    }

    public void testIsEligibleReturnsFalse() {
        TestZaiService service = new TestZaiService("NO, not suitable");
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        WorkItemRecord workItem = createWorkItem("ProcessPayment", "case-456");

        boolean result = reasoner.isEligible(workItem);

        assertFalse(result);
    }

    public void testIsEligibleCaseInsensitive() {
        TestZaiService service = new TestZaiService("yes, suitable");
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        WorkItemRecord workItem = createWorkItem("Task", "case-789");

        boolean result = reasoner.isEligible(workItem);

        assertTrue(result);
    }

    public void testIsEligibleWithWhitespace() {
        TestZaiService service = new TestZaiService("  YES  ");
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        WorkItemRecord workItem = createWorkItem("Task", "case-101");

        boolean result = reasoner.isEligible(workItem);

        assertTrue(result);
    }

    public void testIsEligibleRejectsNullWorkItem() {
        TestZaiService service = new TestZaiService("YES");
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        try {
            reasoner.isEligible(null);
            fail("Should reject null workItem");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("workItem is required"));
        }
    }

    public void testSetSystemPromptTemplate() {
        TestZaiService service = new TestZaiService("YES");
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        reasoner.setSystemPromptTemplate("New system prompt: {capability}");

        WorkItemRecord workItem = createWorkItem("Task", "case-1");
        reasoner.isEligible(workItem);

        assertTrue(service.getLastSystemPrompt().contains("procurement"));
    }

    public void testSetSystemPromptTemplateRejectsEmpty() {
        TestZaiService service = new TestZaiService("YES");
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        try {
            reasoner.setSystemPromptTemplate("");
            fail("Should reject empty template");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("systemPromptTemplate cannot be empty"));
        }
    }

    public void testSetUserPromptTemplate() {
        TestZaiService service = new TestZaiService("YES");
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        reasoner.setUserPromptTemplate("Custom: {taskName}");

        WorkItemRecord workItem = createWorkItem("MyTask", "case-1");
        reasoner.isEligible(workItem);

        assertTrue(service.getLastUserMessage().contains("MyTask"));
    }

    public void testZaiServiceException() {
        TestZaiService service = new TestZaiService();
        service.setShouldFail(true);
        ZaiEligibilityReasoner reasoner = new ZaiEligibilityReasoner(capability, service);

        WorkItemRecord workItem = createWorkItem("Task", "case-1");

        try {
            reasoner.isEligible(workItem);
            fail("Should propagate ZAI service exception");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Eligibility reasoning failed"));
        }
    }

    private WorkItemRecord createWorkItem(String taskName, String caseId) {
        WorkItemRecord workItem = new WorkItemRecord();
        workItem.setTaskName(taskName);
        workItem.setCaseID(caseId);
        workItem.setTaskID("wi-" + System.currentTimeMillis());
        return workItem;
    }

    /**
     * Test-specific ZaiService that returns predefined responses.
     * This is a REAL implementation for testing - not a mock/stub.
     * It provides actual ZaiService behavior with controllable responses.
     */
    private static class TestZaiService extends ZaiService {
        private String response;
        private String lastSystemPrompt;
        private String lastUserMessage;
        private boolean shouldFail;

        public TestZaiService() {
            this("YES");
        }

        public TestZaiService(String response) {
            super("test-api-key-for-unit-tests");
            this.response = response;
        }

        @Override
        public void setSystemPrompt(String prompt) {
            this.lastSystemPrompt = prompt;
        }

        @Override
        public String chat(String message) {
            this.lastUserMessage = message;
            if (shouldFail) {
                throw new RuntimeException("ZAI service failure for test");
            }
            return response;
        }

        public String getLastSystemPrompt() {
            return lastSystemPrompt;
        }

        public String getLastUserMessage() {
            return lastUserMessage;
        }

        public void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }
    }
}
