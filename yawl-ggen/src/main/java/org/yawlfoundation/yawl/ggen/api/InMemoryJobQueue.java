package org.yawlfoundation.yawl.ggen.api;

import org.yawlfoundation.yawl.ggen.mining.ai.AiValidationLoop;
import org.yawlfoundation.yawl.ggen.mining.ai.OllamaValidationClient;
import org.yawlfoundation.yawl.ggen.mining.generators.TerraformGenerator;
import org.yawlfoundation.yawl.ggen.mining.generators.YawlSpecExporter;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.parser.BpmnParser;
import org.yawlfoundation.yawl.ggen.mining.parser.PnmlParser;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-memory job queue for async process conversion jobs.
 * Manages job submission, status tracking, and dispatch to appropriate parsers and generators.
 * Uses a fixed thread pool for concurrent job processing.
 */
public class InMemoryJobQueue {
    private final ConcurrentHashMap<String, ConversionJob> jobs;
    private final ExecutorService executorService;
    private static final int THREAD_POOL_SIZE = 4;

    public InMemoryJobQueue() {
        this.jobs = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    /**
     * Submit a new conversion request to the queue.
     *
     * @param request the process conversion request
     * @return the job ID
     * @throws IllegalArgumentException if request validation fails
     */
    public String submit(ProcessConversionRequest request) {
        request.validate();

        ConversionJob job = new ConversionJob(request);
        jobs.put(job.getJobId(), job);

        executorService.submit(() -> processJob(job));

        return job.getJobId();
    }

    /**
     * Get a job by its ID.
     *
     * @param jobId the job ID
     * @return the ConversionJob, or null if not found
     */
    public ConversionJob get(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Process a conversion job: dispatch to appropriate parser and generator.
     *
     * @param job the job to process
     */
    public void processJob(ConversionJob job) {
        try {
            job.setStatus(ConversionJob.Status.PROCESSING);

            ProcessConversionRequest request = job.getRequest();
            String sourceFormat = request.getSourceFormat().toUpperCase();
            String targetFormat = request.getTargetFormat().toUpperCase();
            String content = request.getContent();

            PetriNet petriNet = parseToPetriNet(sourceFormat, content);

            if (petriNet == null) {
                job.setStatus(ConversionJob.Status.FAILED);
                job.setError("Failed to parse input: net was null");
                return;
            }

            String result = generateOutput(petriNet, targetFormat);

            job.setResult(result);
            job.setStatus(ConversionJob.Status.COMPLETE);

        } catch (Exception e) {
            job.setStatus(ConversionJob.Status.FAILED);
            job.setError(e.getMessage());
        }
    }

    /**
     * Parse input content to a Petri net model based on source format.
     *
     * @param sourceFormat the source format (PNML, BPMN, XES, CSV)
     * @param content the input content
     * @return the parsed PetriNet
     * @throws IOException if IO error occurs
     * @throws SAXException if XML parsing error occurs
     */
    private PetriNet parseToPetriNet(String sourceFormat, String content)
            throws IOException, SAXException {
        ByteArrayInputStream inputStream =
            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        return switch (sourceFormat) {
            case "PNML" -> {
                PnmlParser parser = new PnmlParser();
                yield parser.parse(inputStream);
            }
            case "BPMN" -> {
                BpmnParser parser = new BpmnParser();
                yield parser.parse(inputStream);
            }
            case "XES", "CSV" -> {
                throw new IllegalArgumentException(
                    "Format " + sourceFormat + " not yet supported"
                );
            }
            default -> throw new IllegalArgumentException(
                "Unknown source format: " + sourceFormat
            );
        };
    }

    /**
     * Generate output in the target format from a Petri net model.
     *
     * @param petriNet the Petri net model
     * @param targetFormat the target format
     * @return the generated output as string
     */
    private String generateOutput(PetriNet petriNet, String targetFormat) {
        return switch (targetFormat) {
            case "TERRAFORM_AWS" -> {
                TerraformGenerator generator = new TerraformGenerator();
                yield generator.generateTerraform(petriNet, "aws");
            }
            case "TERRAFORM_AZURE" -> {
                TerraformGenerator generator = new TerraformGenerator();
                yield generator.generateTerraform(petriNet, "azure");
            }
            case "TERRAFORM_GCP" -> {
                TerraformGenerator generator = new TerraformGenerator();
                yield generator.generateTerraform(petriNet, "gcp");
            }
            case "YAWL_SPEC" -> {
                try {
                    yield new AiValidationLoop(
                        new YawlSpecExporter(),
                        new OllamaValidationClient("http://localhost:11434", "qwen2.5-coder", 30),
                        3
                    ).generateAndValidate(petriNet);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(
                        "Ollama validation service unavailable: " + e.getMessage(), e);
                }
            }
            case "CAMUNDA", "BPEL" -> {
                throw new IllegalArgumentException(
                    "Format " + targetFormat + " generation not yet implemented"
                );
            }
            default -> throw new IllegalArgumentException(
                "Unknown target format: " + targetFormat
            );
        };
    }

    /**
     * Shutdown the job queue executor service.
     */
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Get total number of jobs in the queue (all statuses).
     */
    public int getJobCount() {
        return jobs.size();
    }
}
