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
import java.util.Objects;

/**
 * Result of the INGEST stage of the data pipeline.
 *
 * <p>Contains metadata about ingested data: row count, record statistics, staging location,
 * and any deduplication metrics applied.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IngestResult {

    @JsonProperty("stagingLocation")
    private String stagingLocation; // DuckDB table name or file location where data is staged

    @JsonProperty("rowCount")
    private Long rowCount; // total rows ingested

    @JsonProperty("sampleRowCount")
    private Long sampleRowCount; // rows in sample used for inference

    @JsonProperty("deduplicatedRowCount")
    private Long deduplicatedRowCount; // rows after deduplication

    @JsonProperty("columnCount")
    private Integer columnCount; // detected column count

    @JsonProperty("timestamp")
    private Instant timestamp; // when ingestion completed

    @JsonProperty("checkpointId")
    private String checkpointId; // for resume on failure

    @JsonProperty("dataProfile")
    private Object dataProfile; // Statistics: null count, data distribution, type samples

    /**
     * Constructs an empty IngestResult.
     */
    public IngestResult() {
    }

    /**
     * Constructs an IngestResult with basic metadata.
     *
     * @param stagingLocation where data is staged; must not be null
     * @param rowCount total rows ingested; must be >= 0
     * @param columnCount detected column count; must be >= 0
     */
    public IngestResult(String stagingLocation, Long rowCount, Integer columnCount) {
        this.stagingLocation = stagingLocation;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.timestamp = Instant.now();
    }

    // Getters and setters

    public String getStagingLocation() {
        return stagingLocation;
    }

    public void setStagingLocation(String stagingLocation) {
        this.stagingLocation = stagingLocation;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public Long getSampleRowCount() {
        return sampleRowCount;
    }

    public void setSampleRowCount(Long sampleRowCount) {
        this.sampleRowCount = sampleRowCount;
    }

    public Long getDeduplicatedRowCount() {
        return deduplicatedRowCount;
    }

    public void setDeduplicatedRowCount(Long deduplicatedRowCount) {
        this.deduplicatedRowCount = deduplicatedRowCount;
    }

    public Integer getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(Integer columnCount) {
        this.columnCount = columnCount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public Object getDataProfile() {
        return dataProfile;
    }

    public void setDataProfile(Object dataProfile) {
        this.dataProfile = dataProfile;
    }

    // Builder pattern

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String stagingLocation;
        private Long rowCount;
        private Long sampleRowCount;
        private Long deduplicatedRowCount;
        private Integer columnCount;
        private Instant timestamp;
        private String checkpointId;
        private Object dataProfile;

        public Builder stagingLocation(String stagingLocation) {
            this.stagingLocation = stagingLocation;
            return this;
        }

        public Builder rowCount(Long rowCount) {
            this.rowCount = rowCount;
            return this;
        }

        public Builder sampleRowCount(Long sampleRowCount) {
            this.sampleRowCount = sampleRowCount;
            return this;
        }

        public Builder deduplicatedRowCount(Long deduplicatedRowCount) {
            this.deduplicatedRowCount = deduplicatedRowCount;
            return this;
        }

        public Builder columnCount(Integer columnCount) {
            this.columnCount = columnCount;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder checkpointId(String checkpointId) {
            this.checkpointId = checkpointId;
            return this;
        }

        public Builder dataProfile(Object dataProfile) {
            this.dataProfile = dataProfile;
            return this;
        }

        public IngestResult build() {
            IngestResult result = new IngestResult();
            result.stagingLocation = this.stagingLocation;
            result.rowCount = this.rowCount;
            result.sampleRowCount = this.sampleRowCount;
            result.deduplicatedRowCount = this.deduplicatedRowCount;
            result.columnCount = this.columnCount;
            result.timestamp = this.timestamp != null ? this.timestamp : Instant.now();
            result.checkpointId = this.checkpointId;
            result.dataProfile = this.dataProfile;
            return result;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IngestResult that = (IngestResult) o;
        return Objects.equals(stagingLocation, that.stagingLocation)
                && Objects.equals(rowCount, that.rowCount)
                && Objects.equals(sampleRowCount, that.sampleRowCount)
                && Objects.equals(deduplicatedRowCount, that.deduplicatedRowCount)
                && Objects.equals(columnCount, that.columnCount)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(checkpointId, that.checkpointId)
                && Objects.equals(dataProfile, that.dataProfile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stagingLocation, rowCount, sampleRowCount,
                deduplicatedRowCount, columnCount, timestamp, checkpointId, dataProfile);
    }

    @Override
    public String toString() {
        return "IngestResult{" +
                "stagingLocation='" + stagingLocation + '\'' +
                ", rowCount=" + rowCount +
                ", sampleRowCount=" + sampleRowCount +
                ", deduplicatedRowCount=" + deduplicatedRowCount +
                ", columnCount=" + columnCount +
                ", timestamp=" + timestamp +
                ", checkpointId='" + checkpointId + '\'' +
                ", dataProfile=" + dataProfile +
                '}';
    }
}
