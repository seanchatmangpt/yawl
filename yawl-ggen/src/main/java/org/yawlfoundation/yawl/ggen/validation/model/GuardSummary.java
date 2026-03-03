package org.yawlfoundation.yawl.ggen.validation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

/**
 * Summary counts of guard violations by pattern type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GuardSummary {

    @JsonProperty("h_todo_count")
    private int hTodoCount;

    @JsonProperty("h_mock_count")
    private int hMockViolationCount;

    @JsonProperty("h_stub_count")
    private int hStubViolationCount;

    @JsonProperty("h_empty_count")
    private int hEmptyCount;

    @JsonProperty("h_fallback_count")
    private int hFallbackCount;

    @JsonProperty("h_lie_count")
    private int hLieCount;

    @JsonProperty("h_silent_count")
    private int hSilentCount;

    @JsonProperty("total_violations")
    private int totalViolations;

    public GuardSummary() {
        this.hTodoCount = 0;
        this.hMockViolationCount = 0;
        this.hStubViolationCount = 0;
        this.hEmptyCount = 0;
        this.hFallbackCount = 0;
        this.hLieCount = 0;
        this.hSilentCount = 0;
        this.totalViolations = 0;
    }

    public GuardSummary(List<GuardViolation> violations) {
        if (violations == null) {
            throw new NullPointerException("Violations list cannot be null");
        }
        for (GuardViolation violation : violations) {
            increment(violation.getPattern());
        }
    }

    public void increment(String pattern) {
        switch (pattern) {
            case "H_TODO" -> hTodoCount++;
            case "H_MOCK" -> hMockViolationCount++;
            case "H_STUB" -> hStubViolationCount++;
            case "H_EMPTY" -> hEmptyCount++;
            case "H_FALLBACK" -> hFallbackCount++;
            case "H_LIE" -> hLieCount++;
            case "H_SILENT" -> hSilentCount++;
        }
        totalViolations++;
    }

    // CamelCase getters
    public int getHTodoCount() { return hTodoCount; }
    public int getHMockViolationCount() { return hMockViolationCount; }
    public int getHStubViolationCount() { return hStubViolationCount; }
    public int getHEmptyCount() { return hEmptyCount; }
    public int getHFallbackCount() { return hFallbackCount; }
    public int getHLieCount() { return hLieCount; }
    public int getHSilentCount() { return hSilentCount; }
    public int getTotalViolations() { return totalViolations; }

    // Snake-case getters for HyperStandardsValidator compatibility
    public int getH_todo_count() { return hTodoCount; }
    public int getH_mock_violation_count() { return hMockViolationCount; }
    public int getH_stub_violation_count() { return hStubViolationCount; }
    public int getH_empty_count() { return hEmptyCount; }
    public int getH_fallback_count() { return hFallbackCount; }
    public int getH_lie_count() { return hLieCount; }
    public int getH_silent_count() { return hSilentCount; }
    public int getTotal_violations() { return totalViolations; }

    // Setters
    public void setHTodoCount(int hTodoCount) { this.hTodoCount = hTodoCount; }
    public void setHMockCount(int hMockViolationCount) { this.hMockViolationCount = hMockViolationCount; }
    public void setHStubCount(int hStubViolationCount) { this.hStubViolationCount = hStubViolationCount; }
    public void setHEmptyCount(int hEmptyCount) { this.hEmptyCount = hEmptyCount; }
    public void setHFallbackCount(int hFallbackCount) { this.hFallbackCount = hFallbackCount; }
    public void setHLieCount(int hLieCount) { this.hLieCount = hLieCount; }
    public void setHSilentCount(int hSilentCount) { this.hSilentCount = hSilentCount; }
    public void setTotalViolations(int totalViolations) { this.totalViolations = totalViolations; }
}
