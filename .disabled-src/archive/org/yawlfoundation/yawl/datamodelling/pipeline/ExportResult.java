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

package org.yawlfoundation.yawl.datamodelling.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Result of the EXPORT stage of the data pipeline.
 *
 * <p>Contains exported schema, transformation scripts, documentation, and
 * metadata about the export operation.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ExportResult {

    @JsonProperty("exportedContent")
    private String exportedContent; // exported schema/script as string

    @JsonProperty("exportFormat")
    private String exportFormat; // odcs, sql, jsonschema, etc.

    @JsonProperty("outputPath")
    private String outputPath; // where output was written

    @JsonProperty("outputSize")
    private Long outputSize; // size of exported file in bytes

    @JsonProperty("timestamp")
    private Instant timestamp; // when export completed

    @JsonProperty("validationResult")
    private Boolean validationResult; // whether exported content validates

    @JsonProperty("validationErrors")
    private List<String> validationErrors; // validation error messages

    @JsonProperty("exportedArtifacts")
    private List<String> exportedArtifacts; // list of artifacts (schema, docs, transforms)

    @JsonProperty("exportNotes")
    private String exportNotes; // notes on export process

    /**
     * Constructs an empty ExportResult.
     */
    public ExportResult() {
        this.validationErrors = new ArrayList<>();
        this.exportedArtifacts = new ArrayList<>();
    }

    /**
     * Constructs an ExportResult with exported content and format.
     *
     * @param exportedContent exported schema/script; must not be null
     * @param exportFormat export format; must not be null
     * @param outputPath where output was written; may be null for in-memory exports
     */
    public ExportResult(String exportedContent, String exportFormat, String outputPath) {
        this.exportedContent = exportedContent;
        this.exportFormat = exportFormat;
        this.outputPath = outputPath;
        this.timestamp = Instant.now();
        this.validationErrors = new ArrayList<>();
        this.exportedArtifacts = new ArrayList<>();
        this.outputSize = exportedContent != null ? (long) exportedContent.length() : 0L;
    }

    // Getters and setters

    public String getExportedContent() {
        return exportedContent;
    }

    public void setExportedContent(String exportedContent) {
        this.exportedContent = exportedContent;
        this.outputSize = exportedContent != null ? (long) exportedContent.length() : 0L;
    }

    public String getExportFormat() {
        return exportFormat;
    }

    public void setExportFormat(String exportFormat) {
        this.exportFormat = exportFormat;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public Long getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(Long outputSize) {
        this.outputSize = outputSize;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(Boolean validationResult) {
        this.validationResult = validationResult;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public List<String> getExportedArtifacts() {
        return exportedArtifacts;
    }

    public void setExportedArtifacts(List<String> exportedArtifacts) {
        this.exportedArtifacts = exportedArtifacts;
    }

    public String getExportNotes() {
        return exportNotes;
    }

    public void setExportNotes(String exportNotes) {
        this.exportNotes = exportNotes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExportResult that = (ExportResult) o;
        return Objects.equals(exportedContent, that.exportedContent)
                && Objects.equals(exportFormat, that.exportFormat)
                && Objects.equals(outputPath, that.outputPath)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(validationResult, that.validationResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exportedContent, exportFormat, outputPath, timestamp, validationResult);
    }

    @Override
    public String toString() {
        return "ExportResult{" +
                "exportFormat='" + exportFormat + '\'' +
                ", outputPath='" + outputPath + '\'' +
                ", outputSize=" + outputSize +
                ", timestamp=" + timestamp +
                ", validationResult=" + validationResult +
                '}';
    }
}
