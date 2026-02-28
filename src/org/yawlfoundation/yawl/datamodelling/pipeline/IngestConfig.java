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
 * Configuration for the INGEST stage of the data pipeline.
 *
 * <p>Specifies data source location, format, and ingestion parameters.</p>
 *
 * <p>Supported data sources:</p>
 * <ul>
 *   <li>JSON files or JSON arrays</li>
 *   <li>CSV files with configurable delimiters</li>
 *   <li>Database connections (PostgreSQL, MySQL, SQLite, Databricks)</li>
 *   <li>Apache Iceberg tables (REST, Unity Catalog, AWS Glue, S3)</li>
 *   <li>Parquet files</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IngestConfig {

    @JsonProperty("sourceType")
    private String sourceType; // json, csv, database, iceberg, parquet

    @JsonProperty("sourcePath")
    private String sourcePath; // file path or connection string

    @JsonProperty("format")
    private String format; // json, csv, parquet, etc.

    @JsonProperty("delimiter")
    private String delimiter; // for CSV: comma, semicolon, tab, etc.

    @JsonProperty("hasHeader")
    private Boolean hasHeader; // for CSV

    @JsonProperty("encoding")
    private String encoding; // UTF-8, ISO-8859-1, etc.

    @JsonProperty("sampleSize")
    private Integer sampleSize; // number of rows to sample for inference

    @JsonProperty("deduplicationStrategy")
    private String deduplicationStrategy; // none, exact, fuzzy, semantic

    @JsonProperty("batchSize")
    private Integer batchSize; // for large dataset ingestion

    @JsonProperty("checkpointEnabled")
    private Boolean checkpointEnabled; // enable resume on failure

    @JsonProperty("options")
    private Map<String, Object> options; // format-specific options (driver, table, query, etc.)

    /**
     * Constructs an empty IngestConfig.
     */
    public IngestConfig() {
        this.options = new HashMap<>();
    }

    // Getters and setters

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public Boolean getHasHeader() {
        return hasHeader;
    }

    public void setHasHeader(Boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Integer getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Integer sampleSize) {
        this.sampleSize = sampleSize;
    }

    public String getDeduplicationStrategy() {
        return deduplicationStrategy;
    }

    public void setDeduplicationStrategy(String deduplicationStrategy) {
        this.deduplicationStrategy = deduplicationStrategy;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Boolean getCheckpointEnabled() {
        return checkpointEnabled;
    }

    public void setCheckpointEnabled(Boolean checkpointEnabled) {
        this.checkpointEnabled = checkpointEnabled;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options != null ? options : new HashMap<>();
    }

    // Builder pattern

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sourceType;
        private String sourcePath;
        private String format;
        private String delimiter;
        private Boolean hasHeader;
        private String encoding;
        private Integer sampleSize;
        private String deduplicationStrategy;
        private Integer batchSize;
        private Boolean checkpointEnabled;
        private Map<String, Object> options = new HashMap<>();

        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder sourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder delimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public Builder hasHeader(Boolean hasHeader) {
            this.hasHeader = hasHeader;
            return this;
        }

        public Builder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder sampleSize(Integer sampleSize) {
            this.sampleSize = sampleSize;
            return this;
        }

        public Builder deduplicationStrategy(String deduplicationStrategy) {
            this.deduplicationStrategy = deduplicationStrategy;
            return this;
        }

        public Builder batchSize(Integer batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder checkpointEnabled(Boolean checkpointEnabled) {
            this.checkpointEnabled = checkpointEnabled;
            return this;
        }

        public Builder addOption(String key, Object value) {
            this.options.put(key, value);
            return this;
        }

        public Builder options(Map<String, Object> options) {
            this.options = options != null ? new HashMap<>(options) : new HashMap<>();
            return this;
        }

        public IngestConfig build() {
            IngestConfig config = new IngestConfig();
            config.sourceType = this.sourceType;
            config.sourcePath = this.sourcePath;
            config.format = this.format;
            config.delimiter = this.delimiter;
            config.hasHeader = this.hasHeader;
            config.encoding = this.encoding;
            config.sampleSize = this.sampleSize;
            config.deduplicationStrategy = this.deduplicationStrategy;
            config.batchSize = this.batchSize;
            config.checkpointEnabled = this.checkpointEnabled;
            config.options = this.options;
            return config;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IngestConfig that = (IngestConfig) o;
        return Objects.equals(sourceType, that.sourceType)
                && Objects.equals(sourcePath, that.sourcePath)
                && Objects.equals(format, that.format)
                && Objects.equals(delimiter, that.delimiter)
                && Objects.equals(hasHeader, that.hasHeader)
                && Objects.equals(encoding, that.encoding)
                && Objects.equals(sampleSize, that.sampleSize)
                && Objects.equals(deduplicationStrategy, that.deduplicationStrategy)
                && Objects.equals(batchSize, that.batchSize)
                && Objects.equals(checkpointEnabled, that.checkpointEnabled)
                && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceType, sourcePath, format, delimiter, hasHeader,
                encoding, sampleSize, deduplicationStrategy, batchSize,
                checkpointEnabled, options);
    }

    @Override
    public String toString() {
        return "IngestConfig{" +
                "sourceType='" + sourceType + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", format='" + format + '\'' +
                ", delimiter='" + delimiter + '\'' +
                ", hasHeader=" + hasHeader +
                ", encoding='" + encoding + '\'' +
                ", sampleSize=" + sampleSize +
                ", deduplicationStrategy='" + deduplicationStrategy + '\'' +
                ", batchSize=" + batchSize +
                ", checkpointEnabled=" + checkpointEnabled +
                ", options=" + options +
                '}';
    }
}
