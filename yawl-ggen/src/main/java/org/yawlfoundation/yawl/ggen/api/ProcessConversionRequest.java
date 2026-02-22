package org.yawlfoundation.yawl.ggen.api;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a request to convert a process model from one format to another.
 * Supports multiple source and target formats for process mining and code generation.
 */
public class ProcessConversionRequest {
    private String sourceFormat;
    private String targetFormat;
    private String processId;
    private String content;
    private Map<String, String> options;

    public ProcessConversionRequest() {
        this.options = new HashMap<>();
    }

    public ProcessConversionRequest(String sourceFormat, String targetFormat,
                                    String processId, String content) {
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
        this.processId = processId;
        this.content = content;
        this.options = new HashMap<>();
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options != null ? options : new HashMap<>();
    }

    /**
     * Validate that all required fields are present and valid.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() throws IllegalArgumentException {
        if (sourceFormat == null || sourceFormat.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceFormat is required");
        }
        if (targetFormat == null || targetFormat.trim().isEmpty()) {
            throw new IllegalArgumentException("targetFormat is required");
        }
        if (processId == null || processId.trim().isEmpty()) {
            throw new IllegalArgumentException("processId is required");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("content is required");
        }

        String source = sourceFormat.toUpperCase();
        String target = targetFormat.toUpperCase();

        if (!isValidSourceFormat(source)) {
            throw new IllegalArgumentException(
                "Invalid sourceFormat: " + sourceFormat +
                ". Supported: PNML, BPMN, XES, CSV"
            );
        }

        if (!isValidTargetFormat(target)) {
            throw new IllegalArgumentException(
                "Invalid targetFormat: " + targetFormat +
                ". Supported: YAWL, CAMUNDA, TERRAFORM_AWS, TERRAFORM_AZURE, " +
                "TERRAFORM_GCP, BPEL"
            );
        }
    }

    private boolean isValidSourceFormat(String format) {
        return format.equals("PNML") || format.equals("BPMN") ||
               format.equals("XES") || format.equals("CSV");
    }

    private boolean isValidTargetFormat(String format) {
        return format.equals("YAWL") || format.equals("CAMUNDA") ||
               format.equals("TERRAFORM_AWS") || format.equals("TERRAFORM_AZURE") ||
               format.equals("TERRAFORM_GCP") || format.equals("BPEL");
    }

    /**
     * Deserialize from JSON using Gson.
     */
    public static ProcessConversionRequest fromJson(String json) {
        return new Gson().fromJson(json, ProcessConversionRequest.class);
    }

    /**
     * Serialize to JSON using Gson.
     */
    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        return "ProcessConversionRequest{" +
                "sourceFormat='" + sourceFormat + '\'' +
                ", targetFormat='" + targetFormat + '\'' +
                ", processId='" + processId + '\'' +
                ", optionCount=" + (options != null ? options.size() : 0) +
                '}';
    }
}
