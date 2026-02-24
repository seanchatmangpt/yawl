package org.yawlfoundation.yawl.ggen.api;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test suite for InMemoryJobQueue.
 * Tests job submission, retrieval, and processing with real parsers and generators.
 */
public class InMemoryJobQueueTest {
    private InMemoryJobQueue jobQueue;
    private String samplePnmlContent;
    private String sampleBpmnContent;

    @Before
    public void setUp() {
        jobQueue = new InMemoryJobQueue();

        samplePnmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml xmlns="http://www.pnml.org/version-2009-12-16">
              <net id="sample-net" name="Sample Net">
                <place id="p1">
                  <name><text>Place 1</text></name>
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p2">
                  <name><text>Place 2</text></name>
                </place>
                <transition id="t1">
                  <name><text>Transition 1</text></name>
                </transition>
                <arc id="a1" source="p1" target="t1"/>
                <arc id="a2" source="t1" target="p2"/>
              </net>
            </pnml>
            """;

        sampleBpmnContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                               id="sample-bpmn">
              <bpmn:process id="process1" name="Sample Process">
                <bpmn:startEvent id="start1" name="Start"/>
                <bpmn:task id="task1" name="Task 1"/>
                <bpmn:endEvent id="end1" name="End"/>
                <bpmn:sequenceFlow id="flow1" sourceRef="start1" targetRef="task1"/>
                <bpmn:sequenceFlow id="flow2" sourceRef="task1" targetRef="end1"/>
              </bpmn:process>
            </bpmn:definitions>
            """;
    }

    @Test
    public void testSubmitValidPnmlToTerraformAws() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "PNML",
            "TERRAFORM_AWS",
            "test-process-001",
            samplePnmlContent
        );

        String jobId = jobQueue.submit(request);
        assertNotNull(jobId);
        assertFalse(jobId.isEmpty());

        ConversionJob job = jobQueue.get(jobId);
        assertNotNull(job);
        assertEquals(jobId, job.getJobId());
        assertEquals("test-process-001", job.getRequest().getProcessId());
    }

    @Test
    public void testSubmitValidBpmnToTerraformAzure() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "BPMN",
            "TERRAFORM_AZURE",
            "test-process-002",
            sampleBpmnContent
        );

        String jobId = jobQueue.submit(request);
        assertNotNull(jobId);

        ConversionJob job = jobQueue.get(jobId);
        assertNotNull(job);
        assertEquals("BPMN", job.getRequest().getSourceFormat());
        assertEquals("TERRAFORM_AZURE", job.getRequest().getTargetFormat());
    }

    @Test
    public void testSubmitValidPnmlToTerraformGcp() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "PNML",
            "TERRAFORM_GCP",
            "test-process-003",
            samplePnmlContent
        );

        String jobId = jobQueue.submit(request);
        assertNotNull(jobId);

        ConversionJob job = jobQueue.get(jobId);
        assertNotNull(job);
        assertEquals("TERRAFORM_GCP", job.getRequest().getTargetFormat());
    }

    @Test
    public void testJobProcessingCompletes() throws InterruptedException {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "PNML",
            "TERRAFORM_AWS",
            "test-process-004",
            samplePnmlContent
        );

        String jobId = jobQueue.submit(request);
        ConversionJob job = jobQueue.get(jobId);

        Thread.sleep(2000);

        assertEquals(ConversionJob.Status.COMPLETE, job.getStatus());
        assertNotNull(job.getResult());
        assertTrue(job.getResult().contains("terraform"));
        assertTrue(job.getResult().contains("aws"));
    }

    @Test
    public void testInvalidSourceFormatThrowsException() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "INVALID_FORMAT",
            "TERRAFORM_AWS",
            "test-process-005",
            samplePnmlContent
        );

        assertThrows(IllegalArgumentException.class, () -> {
            jobQueue.submit(request);
        });
    }

    @Test
    public void testInvalidTargetFormatThrowsException() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "PNML",
            "INVALID_TARGET",
            "test-process-006",
            samplePnmlContent
        );

        assertThrows(IllegalArgumentException.class, () -> {
            jobQueue.submit(request);
        });
    }

    @Test
    public void testNullSourceFormatThrowsException() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            null,
            "TERRAFORM_AWS",
            "test-process-007",
            samplePnmlContent
        );

        assertThrows(IllegalArgumentException.class, () -> {
            jobQueue.submit(request);
        });
    }

    @Test
    public void testNullTargetFormatThrowsException() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "PNML",
            null,
            "test-process-008",
            samplePnmlContent
        );

        assertThrows(IllegalArgumentException.class, () -> {
            jobQueue.submit(request);
        });
    }

    @Test
    public void testNullProcessIdThrowsException() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "PNML",
            "TERRAFORM_AWS",
            null,
            samplePnmlContent
        );

        assertThrows(IllegalArgumentException.class, () -> {
            jobQueue.submit(request);
        });
    }

    @Test
    public void testNullContentThrowsException() {
        ProcessConversionRequest request = new ProcessConversionRequest(
            "PNML",
            "TERRAFORM_AWS",
            "test-process-009",
            null
        );

        assertThrows(IllegalArgumentException.class, () -> {
            jobQueue.submit(request);
        });
    }

    @Test
    public void testGetNonExistentJobReturnsNull() {
        ConversionJob job = jobQueue.get("non-existent-job-id");
        assertNull(job);
    }

    @Test
    public void testMultipleJobsProcessedIndependently() throws InterruptedException {
        ProcessConversionRequest request1 = new ProcessConversionRequest(
            "PNML",
            "TERRAFORM_AWS",
            "test-process-010",
            samplePnmlContent
        );

        ProcessConversionRequest request2 = new ProcessConversionRequest(
            "PNML",
            "TERRAFORM_AZURE",
            "test-process-011",
            samplePnmlContent
        );

        String jobId1 = jobQueue.submit(request1);
        String jobId2 = jobQueue.submit(request2);

        Thread.sleep(2000);

        ConversionJob job1 = jobQueue.get(jobId1);
        ConversionJob job2 = jobQueue.get(jobId2);

        assertEquals(ConversionJob.Status.COMPLETE, job1.getStatus());
        assertEquals(ConversionJob.Status.COMPLETE, job2.getStatus());

        assertTrue(job1.getResult().contains("aws"));
        assertTrue(job2.getResult().contains("azure"));

        assertNotEquals(job1.getResult(), job2.getResult());
    }

    @Test
    public void testJobCountIncreases() {
        int initialCount = jobQueue.getJobCount();

        ProcessConversionRequest request = new ProcessConversionRequest(
            "PNML",
            "TERRAFORM_AWS",
            "test-process-012",
            samplePnmlContent
        );

        jobQueue.submit(request);

        assertEquals(initialCount + 1, jobQueue.getJobCount());
    }
}
