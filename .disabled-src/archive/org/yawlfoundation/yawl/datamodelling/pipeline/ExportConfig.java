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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the EXPORT stage of the data pipeline.
 *
 * <p>Specifies target format, output location, and export options for
 * pipeline results (schema, transformations, documentation).</p>
 *
 * <p>Supported export formats:</p>
 * <ul>
 *   <li>ODCS YAML (v2.x, v3.x)</li>
 *   <li>SQL DDL (PostgreSQL, MySQL, SQLite, Databricks)</li>
 *   <li>JSON Schema</li>
 *   <li>Avro</li>
 *   <li>Protobuf</li>
 *   <li>OpenAPI</li>
 *   <li>Markdown documentation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ExportConfig {

    @JsonProperty("exportFormat")
    private String exportFormat; // odcs, sql, jsonschema, avro, protobuf, openapi, markdown

    @JsonProperty("sqlDialect")
    private String sqlDialect; // postgres, mysql, sqlite, databricks (if format=sql)

    @JsonProperty("odcsVersion")
    private String odcsVersion; // v2.x or v3.x (if format=odcs)

    @JsonProperty("outputPath")
    private String outputPath; // where to write output file

    @JsonProperty("includeDocumentation")
    private Boolean includeDocumentation; // include comments and descriptions

    @JsonProperty("includeSamples")
    private Boolean includeSamples; // include example data

    @JsonProperty("prettyPrint")
    private Boolean prettyPrint; // format output for readability

    @JsonProperty("validateOutput")
    private Boolean validateOutput; // validate exported schema

    @JsonProperty("compressOutput")
    private Boolean compressOutput; // gzip output if applicable

    @JsonProperty("options")
    private Map<String, Object> options; // format-specific options

    /**
     * Constructs an empty ExportConfig.
     */
    public ExportConfig() {
        this.options = new HashMap<>();
    }

    /**
     * Constructs an ExportConfig with format and output path.
     *
     * @param exportFormat target export format; must not be null
     * @param outputPath output file path; must not be null
     */
    public ExportConfig(String exportFormat, String outputPath) {
        this.exportFormat = exportFormat;
        this.outputPath = outputPath;
        this.options = new HashMap<>();
    }

    // Getters and setters

    public String getExportFormat() {
        return exportFormat;
    }

    public void setExportFormat(String exportFormat) {
        this.exportFormat = exportFormat;
    }

    public String getSqlDialect() {
        return sqlDialect;
    }

    public void setSqlDialect(String sqlDialect) {
        this.sqlDialect = sqlDialect;
    }

    public String getOdcsVersion() {
        return odcsVersion;
    }

    public void setOdcsVersion(String odcsVersion) {
        this.odcsVersion = odcsVersion;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public Boolean getIncludeDocumentation() {
        return includeDocumentation;
    }

    public void setIncludeDocumentation(Boolean includeDocumentation) {
        this.includeDocumentation = includeDocumentation;
    }

    public Boolean getIncludeSamples() {
        return includeSamples;
    }

    public void setIncludeSamples(Boolean includeSamples) {
        this.includeSamples = includeSamples;
    }

    public Boolean getPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(Boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public Boolean getValidateOutput() {
        return validateOutput;
    }

    public void setValidateOutput(Boolean validateOutput) {
        this.validateOutput = validateOutput;
    }

    public Boolean getCompressOutput() {
        return compressOutput;
    }

    public void setCompressOutput(Boolean compressOutput) {
        this.compressOutput = compressOutput;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExportConfig that = (ExportConfig) o;
        return Objects.equals(exportFormat, that.exportFormat)
                && Objects.equals(sqlDialect, that.sqlDialect)
                && Objects.equals(odcsVersion, that.odcsVersion)
                && Objects.equals(outputPath, that.outputPath)
                && Objects.equals(includeDocumentation, that.includeDocumentation)
                && Objects.equals(includeSamples, that.includeSamples)
                && Objects.equals(prettyPrint, that.prettyPrint)
                && Objects.equals(validateOutput, that.validateOutput)
                && Objects.equals(compressOutput, that.compressOutput)
                && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exportFormat, sqlDialect, odcsVersion, outputPath,
                includeDocumentation, includeSamples, prettyPrint, validateOutput,
                compressOutput, options);
    }

    @Override
    public String toString() {
        return "ExportConfig{" +
                "exportFormat='" + exportFormat + '\'' +
                ", outputPath='" + outputPath + '\'' +
                ", prettyPrint=" + prettyPrint +
                '}';
    }
}
