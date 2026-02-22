package org.yawlfoundation.yawl.ggen.api;

import java.util.UUID;

/**
 * Represents a single conversion job in the queue.
 * Tracks the request, status, result, and error information for async job processing.
 */
public class ConversionJob {

    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETE,
        FAILED
    }

    private String jobId;
    private ProcessConversionRequest request;
    private Status status;
    private String result;
    private String error;
    private long createdAt;

    public ConversionJob() {
        this.jobId = UUID.randomUUID().toString();
        this.status = Status.QUEUED;
        this.createdAt = System.currentTimeMillis();
    }

    public ConversionJob(ProcessConversionRequest request) {
        this();
        this.request = request;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public ProcessConversionRequest getRequest() {
        return request;
    }

    public void setRequest(ProcessConversionRequest request) {
        this.request = request;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "ConversionJob{" +
                "jobId='" + jobId + '\'' +
                ", status=" + status +
                ", processId='" + (request != null ? request.getProcessId() : null) + '\'' +
                '}';
    }
}
