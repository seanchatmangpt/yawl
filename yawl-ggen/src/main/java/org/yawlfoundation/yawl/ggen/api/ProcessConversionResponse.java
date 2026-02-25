package org.yawlfoundation.yawl.ggen.api;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the response to a process conversion request.
 * Contains the conversion job status, output, metrics, and any error information.
 */
public class ProcessConversionResponse {
    private String jobId;
    private String status;
    private String outputContent;
    private Map<String, Object> metrics;
    private String errorMessage;
    private long createdAt;
    private long completedAt;

    public ProcessConversionResponse() {
        this.metrics = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.completedAt = 0;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOutputContent() {
        return outputContent;
    }

    public void setOutputContent(String outputContent) {
        this.outputContent = outputContent;
    }

    public Map<String, Object> getMetrics() {
        return java.util.Collections.unmodifiableMap(metrics);
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics != null ? metrics : new HashMap<>();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Factory method: create a response for a queued job.
     */
    public static ProcessConversionResponse queued(String jobId) {
        ProcessConversionResponse response = new ProcessConversionResponse();
        response.setJobId(jobId);
        response.setStatus("QUEUED");
        return response;
    }

    /**
     * Factory method: create a response for a job currently processing.
     */
    public static ProcessConversionResponse processing(String jobId) {
        ProcessConversionResponse response = new ProcessConversionResponse();
        response.setJobId(jobId);
        response.setStatus("PROCESSING");
        return response;
    }

    /**
     * Factory method: create a response for a completed job.
     */
    public static ProcessConversionResponse complete(String jobId, String outputContent) {
        ProcessConversionResponse response = new ProcessConversionResponse();
        response.setJobId(jobId);
        response.setStatus("COMPLETE");
        response.setOutputContent(outputContent);
        response.setCompletedAt(System.currentTimeMillis());
        return response;
    }

    /**
     * Factory method: create a response for a failed job.
     */
    public static ProcessConversionResponse failed(String jobId, String errorMessage) {
        ProcessConversionResponse response = new ProcessConversionResponse();
        response.setJobId(jobId);
        response.setStatus("FAILED");
        response.setErrorMessage(errorMessage);
        response.setCompletedAt(System.currentTimeMillis());
        return response;
    }

    /**
     * Serialize to JSON using Gson.
     */
    public String toJson() {
        return new Gson().toJson(this);
    }

    /**
     * Deserialize from JSON using Gson.
     */
    public static ProcessConversionResponse fromJson(String json) {
        return new Gson().fromJson(json, ProcessConversionResponse.class);
    }

    @Override
    public String toString() {
        return "ProcessConversionResponse{" +
                "jobId='" + jobId + '\'' +
                ", status='" + status + '\'' +
                ", metricsCount=" + metrics.size() +
                ", hasError=" + (errorMessage != null) +
                '}';
    }
}
