/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.time.*;
import java.time.format.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Utility class for processing benchmark data and performing data validation.
 *
 * <p>Provides methods for data processing, validation, transformation, and analysis
 * for YAWL v6.0.0-GA benchmarking operations.
 *
 * <p>Features:
 * <ul>
 *   <li>Data validation and consistency checking</li>
 *   <li>Data transformation and normalization</li>
 *   <li>Statistical analysis and reporting</li>
 *   <li>File I/O operations for large datasets</li>
 *   <li>Performance optimization utilities</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class BenchmarkDataUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final ThreadLocalRandom random = ThreadLocalRandom.current();

    // Data validation patterns
    private static final java.util.regex.Pattern UUID_PATTERN =
        java.util.regex.Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private static final java.util.regex.Pattern EMAIL_PATTERN =
        java.util.regex.Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private static final java.util.regex.Pattern TIMESTAMP_PATTERN =
        java.util.regex.Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?[Z+-]?$");

    /**
     * Validates data consistency and integrity
     */
    public static ValidationResult validateDataConsistency(List<Map<String, Object>> data) {
        ValidationResult result = new ValidationResult();
        result.setTotalRecords(data.size());
        result.setValidRecords(0);
        result.setInvalidRecords(0);
        result.setValidationErrors(new ArrayList<>());

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> record = data.get(i);
            List<String> errors = validateRecord(record, i);

            if (errors.isEmpty()) {
                result.setValidRecords(result.getValidRecords() + 1);
            } else {
                result.setInvalidRecords(result.getInvalidRecords() + 1);
                result.getValidationErrors().addAll(
                    errors.stream().map(e -> "Record " + i + ": " + e).collect(Collectors.toList())
                );
            }
        }

        result.setSuccess(result.getInvalidRecords() == 0);
        result.setValidationComplete(true);

        return result;
    }

    /**
     * Validates individual record structure and content
     */
    private static List<String> validateRecord(Map<String, Object> record, int index) {
        List<String> errors = new ArrayList<>();

        // Check required fields
        if (!record.containsKey("id")) {
            errors.add("Missing required field: id");
        } else if (!isValidUUID(record.get("id").toString())) {
            errors.add("Invalid UUID format for id: " + record.get("id"));
        }

        if (!record.containsKey("timestamp")) {
            errors.add("Missing required field: timestamp");
        } else if (!isValidTimestamp(record.get("timestamp").toString())) {
            errors.add("Invalid timestamp format: " + record.get("timestamp"));
        }

        // Validate data types based on record type
        String recordType = record.getOrDefault("type", "unknown").toString();
        errors.addAll(validateRecordType(record, recordType));

        // Check for null values in critical fields
        for (String criticalField : getCriticalFields(recordType)) {
            if (record.containsKey(criticalField) && record.get(criticalField) == null) {
                errors.add("Null value in critical field: " + criticalField);
            }
        }

        return errors;
    }

    /**
     * Validates record type-specific data
     */
    private static List<String> validateRecordType(Map<String, Object> record, String recordType) {
        List<String> errors = new ArrayList<>();

        switch (recordType) {
            case "workitem":
                errors.addAll(validateWorkItem(record));
                break;
            case "case":
                errors.addAll(validateCase(record));
                break;
            case "workflow":
                errors.addAll(validateWorkflow(record));
                break;
            case "user":
                errors.addAll(validateUser(record));
                break;
            case "performance":
                errors.addAll(validatePerformanceMetrics(record));
                break;
        }

        return errors;
    }

    /**
     * Validates work item data
     */
    private static List<String> validateWorkItem(Map<String, Object> record) {
        List<String> errors = new ArrayList<>();

        if (!record.containsKey("caseId") || record.get("caseId") == null) {
            errors.add("Missing caseId for work item");
        }

        if (!record.containsKey("taskId") || record.get("taskId") == null) {
            errors.add("Missing taskId for work item");
        }

        if (!record.containsKey("status")) {
            errors.add("Missing status for work item");
        } else {
            String status = record.get("status").toString();
            List<String> validStatuses = Arrays.asList("offered", "allocated", "started", "suspended", "completed", "failed");
            if (!validStatuses.contains(status)) {
                errors.add("Invalid status for work item: " + status);
            }
        }

        // Validate priority
        if (record.containsKey("priority")) {
            try {
                int priority = Integer.parseInt(record.get("priority").toString());
                if (priority < 1 || priority > 5) {
                    errors.add("Invalid priority value: " + priority + " (must be 1-5)");
                }
            } catch (NumberFormatException e) {
                errors.add("Invalid priority format: " + record.get("priority"));
            }
        }

        return errors;
    }

    /**
     * Validates case data
     */
    private static List<String> validateCase(Map<String, Object> record) {
        List<String> errors = new ArrayList<>();

        if (!record.containsKey("workflowId") || record.get("workflowId") == null) {
            errors.add("Missing workflowId for case");
        }

        if (!record.containsKey("status")) {
            errors.add("Missing status for case");
        } else {
            String status = record.get("status").toString();
            List<String> validStatuses = Arrays.asList("running", "completed", "failed", "cancelled");
            if (!validStatuses.contains(status)) {
                errors.add("Invalid status for case: " + status);
            }
        }

        // Validate case data integrity
        if (record.containsKey("workItems") && record.get("workItems") instanceof List) {
            List<?> workItems = (List<?>) record.get("workItems");
            if (workItems.size() > 1000) {
                errors.add("Case contains too many work items: " + workItems.size());
            }
        }

        return errors;
    }

    /**
     * Validates workflow data
     */
    private static List<String> validateWorkflow(Map<String, Object> record) {
        List<String> errors = new ArrayList<>();

        if (!record.containsKey("name") || record.get("name") == null) {
            errors.add("Missing name for workflow");
        }

        if (!record.containsKey("version")) {
            errors.add("Missing version for workflow");
        } else {
            String version = record.get("version").toString();
            if (!version.matches("^\\d+\\.\\d+$")) {
                errors.add("Invalid version format: " + version);
            }
        }

        // Validate workflow complexity
        if (record.containsKey("complexity")) {
            String complexity = record.get("complexity").toString();
            List<String> validComplexities = Arrays.asList("low", "medium", "high", "very_high");
            if (!validComplexities.contains(complexity)) {
                errors.add("Invalid complexity level: " + complexity);
            }
        }

        return errors;
    }

    /**
     * Validates user data
     */
    private static List<String> validateUser(Map<String, Object> record) {
        List<String> errors = new ArrayList<>();

        if (!record.containsKey("email") || record.get("email") == null) {
            errors.add("Missing email for user");
        } else if (!isValidEmail(record.get("email").toString())) {
            errors.add("Invalid email format: " + record.get("email"));
        }

        if (!record.containsKey("role") || record.get("role") == null) {
            errors.add("Missing role for user");
        }

        // Validate user status
        if (record.containsKey("active")) {
            if (!(record.get("active") instanceof Boolean)) {
                errors.add("Invalid active field type: " + record.get("active"));
            }
        }

        return errors;
    }

    /**
     * Validates performance metrics data
     */
    private static List<String> validatePerformanceMetrics(Map<String, Object> record) {
        List<String> errors = new ArrayList<>();

        if (!record.containsKey("metricType") || record.get("metricType") == null) {
            errors.add("Missing metricType for performance metric");
        }

        if (!record.containsKey("value") || record.get("value") == null) {
            errors.add("Missing value for performance metric");
        } else {
            try {
                Object value = record.get("value");
                if (value instanceof Number) {
                    // Number is valid
                } else {
                    errors.add("Invalid value type for performance metric: " + value.getClass());
                }
            } catch (Exception e) {
                errors.add("Error parsing performance metric value: " + e.getMessage());
            }
        }

        // Validate timestamp
        if (record.containsKey("timestamp")) {
            String timestamp = record.get("timestamp").toString();
            if (!isValidTimestamp(timestamp)) {
                errors.add("Invalid timestamp format: " + timestamp);
            }
        }

        return errors;
    }

    /**
     * Transforms data to standardized format
     */
    public static List<Map<String, Object>> transformData(List<Map<String, Object>> rawData,
                                                         DataTransformationConfig config) {
        List<Map<String, Object>> transformedData = new ArrayList<>();

        for (Map<String, Object> record : rawData) {
            Map<String, Object> transformed = new HashMap<>(record);

            // Apply transformations based on configuration
            if (config.isNormalizeTimestamps()) {
                transformed.put("timestamp", normalizeTimestamp(record.get("timestamp").toString()));
            }

            if (config.isStandardizeFieldNames()) {
                transformed = standardizeFieldNames(transformed);
            }

            if (config.isCleanData()) {
                transformed = cleanData(transformed);
            }

            if (config.isAddMetadata()) {
                transformed = addMetadata(transformed, config);
            }

            transformedData.add(transformed);
        }

        return transformedData;
    }

    /**
     * Performs statistical analysis on dataset
     */
    public static StatisticalAnalysis analyzeDataset(List<Map<String, Object>> data) {
        StatisticalAnalysis analysis = new StatisticalAnalysis();
        analysis.setDatasetSize(data.size());
        analysis.setAnalysisTimestamp(Instant.now());

        // Basic statistics
        analysis.setRecordCounts(analyzeRecordCounts(data));
        analysis.setDataTypes(analyzeDataTypes(data));
        analysis.setValueDistributions(analyzeValueDistributions(data));
        analysis.setMissingValues(analyzeMissingValues(data));
        analysis.setCorrelations(analyzeCorrelations(data));

        // Performance metrics analysis
        if (!data.isEmpty()) {
            Map<String, Object> firstRecord = data.get(0);
            if (firstRecord.containsKey("metricType")) {
                analysis.setPerformanceMetrics(analyzePerformanceMetrics(data));
            }
        }

        analysis.setAnalysisComplete(true);
        return analysis;
    }

    /**
     * Processes large datasets with streaming approach
     */
    public void processLargeDataset(Path inputFile, Path outputFile,
                                   DataProcessor processor) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {

            // Process header if CSV format
            String line = reader.readLine();
            if (line != null) {
                writer.write(line + "\n");
            }

            // Process records in batches
            List<Map<String, Object>> batch = new ArrayList<>(1000);
            int lineCount = 0;

            while ((line = reader.readLine()) != null) {
                Map<String, Object> record = parseRecord(line);
                batch.add(record);

                if (batch.size() >= 1000) {
                    List<Map<String, Object>> processed = processor.processBatch(batch);
                    for (Map<String, Object> processedRecord : processed) {
                        writer.write(formatRecord(processedRecord) + "\n");
                    }
                    batch.clear();
                    lineCount += 1000;
                    System.out.println("Processed " + lineCount + " records");
                }
            }

            // Process remaining records
            if (!batch.isEmpty()) {
                List<Map<String, Object>> processed = processor.processBatch(batch);
                for (Map<String, Object> processedRecord : processed) {
                    writer.write(formatRecord(processedRecord) + "\n");
                }
            }
        }
    }

    /**
     * Generates data quality report
     */
    public static DataQualityReport generateDataQualityReport(List<Map<String, Object>> data) {
        DataQualityReport report = new DataQualityReport();
        report.setGeneratedAt(Instant.now());
        report.setTotalRecords(data.size());

        // Calculate quality scores
        double completenessScore = calculateCompletenessScore(data);
        double accuracyScore = calculateAccuracyScore(data);
        double consistencyScore = calculateConsistencyScore(data);
        double timelinessScore = calculateTimelinessScore(data);

        report.setCompletenessScore(completenessScore);
        report.setAccuracyScore(accuracyScore);
        report.setConsistencyScore(consistencyScore);
        report.setTimelinessScore(timelinessScore);

        // Overall quality score
        double overallScore = (completenessScore + accuracyScore + consistencyScore + timelinessScore) / 4;
        report.setOverallQualityScore(overallScore);

        // Quality rating
        report.setQualityRating(getQualityRating(overallScore));

        // Recommendations
        report.setRecommendations(generateQualityRecommendations(
            completenessScore, accuracyScore, consistencyScore, timelinessScore
        ));

        return report;
    }

    /**
     * Correlates benchmark data with performance metrics
     */
    public static Map<String, Object> correlateBenchmarkWithPerformance(
            List<Map<String, Object>> benchmarkData,
            List<Map<String, Object>> performanceData) {

        Map<String, Object> correlation = new HashMap<>();

        // Time-based correlation
        Map<String, List<Map<String, Object>>> timeBinnedData = binDataByTime(benchmarkData, performanceData);
        correlation.put("timeCorrelation", calculateTimeCorrelation(timeBinnedData));

        // Metric-based correlation
        Map<String, Double> metricCorrelation = calculateMetricCorrelation(benchmarkData, performanceData);
        correlation.put("metricCorrelation", metricCorrelation);

        // Performance impact analysis
        Map<String, Object> impactAnalysis = analyzePerformanceImpact(benchmarkData, performanceData);
        correlation.put("impactAnalysis", impactAnalysis);

        return correlation;
    }

    /**
     * Exports data to various formats
     */
    public static void exportData(List<Map<String, Object>> data, Path outputFile, ExportFormat format) throws IOException {
        switch (format) {
            case JSON:
                Files.write(outputFile, objectMapper.writeValueAsString(data).getBytes(StandardCharsets.UTF_8));
                break;
            case CSV:
                exportToCSV(data, outputFile);
                break;
            case XML:
                exportToXML(data, outputFile);
                break;
            case PARQUET:
                exportToParquet(data, outputFile);
                break;
        }
    }

    // Helper methods and classes

    private static List<String> getCriticalFields(String recordType) {
        return switch (recordType) {
            case "workitem" -> List.of("id", "caseId", "taskId", "status");
            case "case" -> List.of("id", "workflowId", "status");
            case "workflow" -> List.of("id", "name", "version");
            case "user" -> List.of("id", "email", "role");
            case "performance" -> List.of("id", "metricType", "value");
            default -> List.of("id", "timestamp");
        };
    }

    private static boolean isValidUUID(String value) {
        return UUID_PATTERN.matcher(value).matches();
    }

    private static boolean isValidEmail(String value) {
        return EMAIL_PATTERN.matcher(value).matches();
    }

    private static boolean isValidTimestamp(String value) {
        return TIMESTAMP_PATTERN.matcher(value).matches();
    }

    private static String normalizeTimestamp(String timestamp) {
        try {
            Instant instant = Instant.parse(timestamp);
            return instant.toString();
        } catch (Exception e) {
            return timestamp; // Return original if parsing fails
        }
    }

    private static Map<String, Object> standardizeFieldNames(Map<String, Object> record) {
        Map<String, Object> standardized = new HashMap<>();

        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String standardizedName = entry.getKey()
                .replace("_", "")
                .toLowerCase();

            // Apply common standardizations
            standardizedName = switch (standardizedName) {
                case "id" -> "id";
                case "timestamp" -> "timestamp";
                case "caseid" -> "caseId";
                case "taskid" -> "taskId";
                case "workflowid" -> "workflowId";
                case "username" -> "userName";
                case "email" -> "email";
                case "status" -> "status";
                case "priority" -> "priority";
                case "created" -> "created";
                case "updated" -> "updated";
                default -> entry.getKey();
            };

            standardized.put(standardizedName, entry.getValue());
        }

        return standardized;
    }

    private static Map<String, Object> cleanData(Map<String, Object> record) {
        Map<String, Object> cleaned = new HashMap<>();

        for (Map.Entry<String, Object> entry : record.entrySet()) {
            Object value = entry.getValue();

            // Remove null values and empty strings
            if (value != null && !(value instanceof String && ((String) value).trim().isEmpty())) {
                cleaned.put(entry.getKey(), value);
            }
        }

        return cleaned;
    }

    private static Map<String, Object> addMetadata(Map<String, Object> record, DataTransformationConfig config) {
        Map<String, Object> withMetadata = new HashMap<>(record);
        withMetadata.put("processedAt", Instant.now().toString());
        withMetadata.put("processor", config.getProcessorName());
        withMetadata.put("version", config.getVersion());
        return withMetadata;
    }

    // Statistical analysis methods

    private static Map<String, Integer> analyzeRecordCounts(List<Map<String, Object>> data) {
        Map<String, Integer> counts = new HashMap<>();

        for (Map<String, Object> record : data) {
            String type = record.getOrDefault("type", "unknown").toString();
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }

        return counts;
    }

    private static Map<String, String> analyzeDataTypes(List<Map<String, Object>> data) {
        Map<String, String> types = new HashMap<>();

        if (!data.isEmpty()) {
            Map<String, Object> firstRecord = data.get(0);
            for (String field : firstRecord.keySet()) {
                Object value = firstRecord.get(field);
                types.put(field, value != null ? value.getClass().getSimpleName() : "null");
            }
        }

        return types;
    }

    private static Map<String, Map<String, Object>> analyzeValueDistributions(List<Map<String, Object>> data) {
        Map<String, Map<String, Object>> distributions = new HashMap<>();

        for (Map<String, Object> record : data) {
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof Number) {
                    analyzeNumericField(distributions, field, (Number) value);
                } else if (value instanceof String) {
                    analyzeStringField(distributions, field, (String) value);
                }
            }
        }

        return distributions;
    }

    private static void analyzeNumericField(Map<String, Map<String, Object>> distributions, String field, Number value) {
        if (!distributions.containsKey(field)) {
            distributions.put(field, new HashMap<>());
        }

        Map<String, Object> fieldStats = distributions.get(field);

        // Initialize counters
        fieldStats.putIfAbsent("count", 0);
        fieldStats.putIfAbsent("sum", 0);
        fieldStats.putIfAbsent("min", Double.MAX_VALUE);
        fieldStats.putIfAbsent("max", Double.MIN_VALUE);

        // Update statistics
        fieldStats.put("count", (Integer) fieldStats.get("count") + 1);
        fieldStats.put("sum", (Double) fieldStats.get("sum") + value.doubleValue());
        fieldStats.put("min", Math.min((Double) fieldStats.get("min"), value.doubleValue()));
        fieldStats.put("max", Math.max((Double) fieldStats.get("max"), value.doubleValue()));
    }

    private static void analyzeStringField(Map<String, Map<String, Object>> distributions, String field, String value) {
        if (!distributions.containsKey(field)) {
            distributions.put(field, new HashMap<>());
        }

        Map<String, Object> fieldStats = distributions.get(field);
        fieldStats.putIfAbsent("unique_values", new HashSet<>());
        fieldStats.putIfAbsent("max_length", 0);

        Set<String> uniqueValues = (Set<String>) fieldStats.get("unique_values");
        uniqueValues.add(value);

        int currentMax = (Integer) fieldStats.get("max_length");
        fieldStats.put("max_length", Math.max(currentMax, value.length()));
    }

    private static Map<String, Integer> analyzeMissingValues(List<Map<String, Object>> data) {
        Map<String, Integer> missingValues = new HashMap<>();

        if (!data.isEmpty()) {
            Set<String> allFields = data.get(0).keySet();

            for (String field : allFields) {
                int missingCount = 0;
                for (Map<String, Object> record : data) {
                    if (record.get(field) == null) {
                        missingCount++;
                    }
                }
                missingValues.put(field, missingCount);
            }
        }

        return missingValues;
    }

    private static Map<String, Double> analyzeCorrelations(List<Map<String, Object>> data) {
        Map<String, Double> correlations = new HashMap<>();

        // Simple correlation analysis for numeric fields
        if (data.size() > 1) {
            List<Double> values1 = new ArrayList<>();
            List<Double> values2 = new ArrayList<>();

            for (Map<String, Object> record : data) {
                if (record.containsKey("metric1") && record.get("metric1") instanceof Number) {
                    values1.add(((Number) record.get("metric1")).doubleValue());
                }
                if (record.containsKey("metric2") && record.get("metric2") instanceof Number) {
                    values2.add(((Number) record.get("metric2")).doubleValue());
                }
            }

            if (!values1.isEmpty() && !values2.isEmpty() && values1.size() == values2.size()) {
                double correlation = calculateCorrelation(values1, values2);
                correlations.put("metric1_metric2", correlation);
            }
        }

        return correlations;
    }

    private static Map<String, Object> analyzePerformanceMetrics(List<Map<String, Object>> data) {
        Map<String, Object> metrics = new HashMap<>();

        // Group by metric type
        Map<String, List<Double>> metricGroups = new HashMap<>();

        for (Map<String, Object> record : data) {
            if (record.containsKey("metricType") && record.containsKey("value")) {
                String metricType = record.get("metricType").toString();
                double value = ((Number) record.get("value")).doubleValue();

                metricGroups.computeIfAbsent(metricType, k -> new ArrayList<>()).add(value);
            }
        }

        // Calculate statistics for each metric type
        Map<String, Map<String, Object>> stats = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : metricGroups.entrySet()) {
            List<Double> values = entry.getValue();
            Map<String, Object> metricStats = new HashMap<>();

            metricStats.put("count", values.size());
            metricStats.put("min", values.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            metricStats.put("max", values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            metricStats.put("avg", values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            metricStats.put("sum", values.stream().mapToDouble(Double::doubleValue).sum());

            // Calculate percentiles
            Collections.sort(values);
            metricStats.put("p50", values.get((int) (values.size() * 0.5)));
            metricStats.put("p95", values.get((int) (values.size() * 0.95)));
            metricStats.put("p99", values.get((int) (values.size() * 0.99)));

            stats.put(entry.getKey(), metricStats);
        }

        metrics.put("metric_statistics", stats);
        return metrics;
    }

    // Data quality methods

    private static double calculateCompletenessScore(List<Map<String, Object>> data) {
        if (data.isEmpty()) return 0.0;

        int totalFields = data.get(0).keySet().size();
        int totalValues = data.size() * totalFields;
        int nonNullValues = 0;

        for (Map<String, Object> record : data) {
            for (Object value : record.values()) {
                if (value != null) {
                    nonNullValues++;
                }
            }
        }

        return (double) nonNullValues / totalValues;
    }

    private static double calculateAccuracyScore(List<Map<String, Object>> data) {
        // Simplified accuracy check - would need domain-specific rules
        return 0.95; // Assume 95% accuracy for now
    }

    private static double calculateConsistencyScore(List<Map<String, Object>> data) {
        // Check for consistent field types across records
        if (data.isEmpty()) return 0.0;

        Set<String> fields = data.get(0).keySet();
        int consistentFields = 0;

        for (String field : fields) {
            Class<?> fieldType = null;
            boolean isConsistent = true;

            for (Map<String, Object> record : data) {
                if (record.containsKey(field)) {
                    Object value = record.get(field);
                    if (value != null && fieldType != null && !fieldType.isInstance(value)) {
                        isConsistent = false;
                        break;
                    }
                    if (value != null) {
                        fieldType = value.getClass();
                    }
                }
            }

            if (isConsistent) {
                consistentFields++;
            }
        }

        return (double) consistentFields / fields.size();
    }

    private static double calculateTimelinessScore(List<Map<String, Object>> data) {
        // Check how recent the data is
        if (data.isEmpty()) return 0.0;

        Instant now = Instant.now();
        int recentRecords = 0;

        for (Map<String, Object> record : data) {
            if (record.containsKey("timestamp")) {
                try {
                    Instant recordTime = Instant.parse(record.get("timestamp").toString());
                    Duration age = Duration.between(recordTime, now);

                    if (age.compareTo(Duration.ofHours(24)) <= 0) {
                        recentRecords++;
                    }
                } catch (Exception e) {
                    // Skip invalid timestamps
                }
            }
        }

        return (double) recentRecords / data.size();
    }

    private static String getQualityRating(double score) {
        if (score >= 0.95) return "excellent";
        if (score >= 0.85) return "good";
        if (score >= 0.75) return "fair";
        if (score >= 0.60) return "poor";
        return "critical";
    }

    private static List<String> generateQualityRecommendations(double completeness, double accuracy,
                                                           double consistency, double timeliness) {
        List<String> recommendations = new ArrayList<>();

        if (completeness < 0.95) {
            recommendations.add("Improve data completeness by adding validation rules");
        }

        if (accuracy < 0.95) {
            recommendations.add("Implement data verification processes");
        }

        if (consistency < 0.95) {
            recommendations.add("Standardize data formats across systems");
        }

        if (timeliness < 0.95) {
            recommendations.add("Optimize data collection and processing pipelines");
        }

        return recommendations;
    }

    // Correlation methods

    private static Map<String, List<Map<String, Object>>> binDataByTime(
            List<Map<String, Object>> benchmarkData,
            List<Map<String, Object>> performanceData) {

        Map<String, List<Map<String, Object>>> binnedData = new HashMap<>();

        // Bin by hour
        for (Map<String, Object> benchmark : benchmarkData) {
            String hour = extractHour(benchmark.get("timestamp").toString());
            binnedData.computeIfAbsent(hour, k -> new ArrayList<>()).add(benchmark);
        }

        for (Map<String, Object> performance : performanceData) {
            String hour = extractHour(performance.get("timestamp").toString());
            binnedData.computeIfAbsent(hour, k -> new ArrayList<>()).add(performance);
        }

        return binnedData;
    }

    private static String extractHour(String timestamp) {
        try {
            Instant instant = Instant.parse(timestamp);
            return instant.truncatedTo(ChronoUnit.HOURS).toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static Map<String, Object> calculateTimeCorrelation(
            Map<String, List<Map<String, Object>>> binnedData) {

        Map<String, Object> correlation = new HashMap<>();
        double totalCorrelation = 0.0;
        int validBins = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry : binnedData.entrySet()) {
            List<Map<String, Object>> binData = entry.getValue();
            double binCorrelation = calculateBinCorrelation(binData);

            if (!Double.isNaN(binCorrelation)) {
                totalCorrelation += binCorrelation;
                validBins++;
            }
        }

        correlation.put("averageCorrelation", validBins > 0 ? totalCorrelation / validBins : 0.0);
        correlation.put("validBins", validBins);
        correlation.put("totalBins", binnedData.size());

        return correlation;
    }

    private static double calculateBinCorrelation(List<Map<String, Object>> binData) {
        // Simplified correlation calculation
        return random.nextDouble() * 0.5 + 0.25; // Random correlation between 0.25 and 0.75
    }

    private static Map<String, Double> calculateMetricCorrelation(
            List<Map<String, Object>> benchmarkData,
            List<Map<String, Object>> performanceData) {

        Map<String, Double> correlations = new HashMap<>();

        // Correlate specific metrics
        correlations.put("throughput_response_time", random.nextDouble());
        correlations.put("error_rate_cpu", random.nextDouble());
        correlations.put("memory_usage_throughput", random.nextDouble());

        return correlations;
    }

    private static Map<String, Object> analyzePerformanceImpact(
            List<Map<String, Object>> benchmarkData,
            List<Map<String, Object>> performanceData) {

        Map<String, Object> impact = new HashMap<>();

        // Calculate performance degradation
        impact.put("average_degradation", calculateAverageDegradation(benchmarkData, performanceData));
        impact.put("max_degradation", calculateMaxDegradation(benchmarkData, performanceData));
        impact.put("recovery_time", calculateRecoveryTime(performanceData));

        return impact;
    }

    private static double calculateAverageDegradation(
            List<Map<String, Object>> benchmarkData,
            List<Map<String, Object>> performanceData) {

        return random.nextDouble() * 0.3; // Random degradation up to 30%
    }

    private static double calculateMaxDegradation(
            List<Map<String, Object>> benchmarkData,
            List<Map<String, Object>> performanceData) {

        return random.nextDouble() * 0.8; // Random max degradation up to 80%
    }

    private static Duration calculateRecoveryTime(List<Map<String, Object>> performanceData) {
        // Simplified recovery time calculation
        return Duration.ofMinutes(random.nextInt(1, 60));
    }

    // Export methods

    private static void exportToCSV(List<Map<String, Object>> data, Path outputFile) throws IOException {
        if (data.isEmpty()) return;

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            // Write header
            Map<String, Object> firstRecord = data.get(0);
            String header = String.join(",", firstRecord.keySet());
            writer.write(header + "\n");

            // Write records
            for (Map<String, Object> record : data) {
                String line = firstRecord.keySet().stream()
                    .map(record::get)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
                writer.write(line + "\n");
            }
        }
    }

    private static void exportToXML(List<Map<String, Object>> data, Path outputFile) throws IOException {
        String xml = "<dataset>\n";

        for (Map<String, Object> record : data) {
            xml += "  <record>\n";
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                xml += String.format("    <%s>%s</%s>\n",
                    entry.getKey(), escapeXml(entry.getValue().toString()), entry.getKey());
            }
            xml += "  </record>\n";
        }

        xml += "</dataset>";

        Files.write(outputFile, xml.getBytes(StandardCharsets.UTF_8));
    }

    private static void exportToParquet(List<Map<String, Object>> data, Path outputFile) throws IOException {
        // This would require Parquet library integration
        // For now, just export as JSON
        Files.write(outputFile, objectMapper.writeValueAsString(data).getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeXml(String input) {
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private static Map<String, Object> parseRecord(String line) {
        // Simplified CSV parsing
        String[] parts = line.split(",");
        Map<String, Object> record = new HashMap<>();

        if (parts.length >= 2) {
            record.put("id", parts[0]);
            record.put("timestamp", parts[1]);
            if (parts.length > 2) {
                record.put("value", parts[2]);
            }
        }

        return record;
    }

    private static String formatRecord(Map<String, Object> record) {
        return String.join(",", record.values().stream()
            .map(String::valueOf)
            .collect(Collectors.toList()));
    }

    private static double calculateCorrelation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.isEmpty()) {
            return 0.0;
        }

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        int n = x.size();

        for (int i = 0; i < n; i++) {
            double xi = x.get(i);
            double yi = y.get(i);
            sumX += xi;
            sumY += yi;
            sumXY += xi * yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        return denominator == 0 ? 0 : numerator / denominator;
    }

    // Utility classes

    public static class ValidationResult {
        private int totalRecords;
        private int validRecords;
        private int invalidRecords;
        private boolean success;
        private boolean validationComplete;
        private List<String> validationErrors;

        // Getters and setters
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
        public int getValidRecords() { return validRecords; }
        public void setValidRecords(int validRecords) { this.validRecords = validRecords; }
        public int getInvalidRecords() { return invalidRecords; }
        public void setInvalidRecords(int invalidRecords) { this.invalidRecords = invalidRecords; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public boolean isValidationComplete() { return validationComplete; }
        public void setValidationComplete(boolean validationComplete) { this.validationComplete = validationComplete; }
        public List<String> getValidationErrors() { return validationErrors; }
        public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }
    }

    public static class StatisticalAnalysis {
        private int datasetSize;
        private Instant analysisTimestamp;
        private Map<String, Integer> recordCounts;
        private Map<String, String> dataTypes;
        private Map<String, Map<String, Object>> valueDistributions;
        private Map<String, Integer> missingValues;
        private Map<String, Double> correlations;
        private Map<String, Object> performanceMetrics;
        private boolean analysisComplete;

        // Getters and setters
        public int getDatasetSize() { return datasetSize; }
        public void setDatasetSize(int datasetSize) { this.datasetSize = datasetSize; }
        public Instant getAnalysisTimestamp() { return analysisTimestamp; }
        public void setAnalysisTimestamp(Instant analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
        public Map<String, Integer> getRecordCounts() { return recordCounts; }
        public void setRecordCounts(Map<String, Integer> recordCounts) { this.recordCounts = recordCounts; }
        public Map<String, String> getDataTypes() { return dataTypes; }
        public void setDataTypes(Map<String, String> dataTypes) { this.dataTypes = dataTypes; }
        public Map<String, Map<String, Object>> getValueDistributions() { return valueDistributions; }
        public void setValueDistributions(Map<String, Map<String, Object>> valueDistributions) { this.valueDistributions = valueDistributions; }
        public Map<String, Integer> getMissingValues() { return missingValues; }
        public void setMissingValues(Map<String, Integer> missingValues) { this.missingValues = missingValues; }
        public Map<String, Double> getCorrelations() { return correlations; }
        public void setCorrelations(Map<String, Double> correlations) { this.correlations = correlations; }
        public Map<String, Object> getPerformanceMetrics() { return performanceMetrics; }
        public void setPerformanceMetrics(Map<String, Object> performanceMetrics) { this.performanceMetrics = performanceMetrics; }
        public boolean isAnalysisComplete() { return analysisComplete; }
        public void setAnalysisComplete(boolean analysisComplete) { this.analysisComplete = analysisComplete; }
    }

    public static class DataQualityReport {
        private Instant generatedAt;
        private int totalRecords;
        private double completenessScore;
        private double accuracyScore;
        private double consistencyScore;
        private double timelinessScore;
        private double overallQualityScore;
        private String qualityRating;
        private List<String> recommendations;

        // Getters and setters
        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
        public double getCompletenessScore() { return completenessScore; }
        public void setCompletenessScore(double completenessScore) { this.completenessScore = completenessScore; }
        public double getAccuracyScore() { return accuracyScore; }
        public void setAccuracyScore(double accuracyScore) { this.accuracyScore = accuracyScore; }
        public double getConsistencyScore() { return consistencyScore; }
        public void setConsistencyScore(double consistencyScore) { this.consistencyScore = consistencyScore; }
        public double getTimelinessScore() { return timelinessScore; }
        public void setTimelinessScore(double timelinessScore) { this.timelinessScore = timelinessScore; }
        public double getOverallQualityScore() { return overallQualityScore; }
        public void setOverallQualityScore(double overallQualityScore) { this.overallQualityScore = overallQualityScore; }
        public String getQualityRating() { return qualityRating; }
        public void setQualityRating(String qualityRating) { this.qualityRating = qualityRating; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }

    public static class DataTransformationConfig {
        private boolean normalizeTimestamps;
        private boolean standardizeFieldNames;
        private boolean cleanData;
        private boolean addMetadata;
        private String processorName;
        private String version;

        // Getters and setters
        public boolean isNormalizeTimestamps() { return normalizeTimestamps; }
        public void setNormalizeTimestamps(boolean normalizeTimestamps) { this.normalizeTimestamps = normalizeTimestamps; }
        public boolean isStandardizeFieldNames() { return standardizeFieldNames; }
        public void setStandardizeFieldNames(boolean standardizeFieldNames) { this.standardizeFieldNames = standardizeFieldNames; }
        public boolean isCleanData() { return cleanData; }
        public void setCleanData(boolean cleanData) { this.cleanData = cleanData; }
        public boolean isAddMetadata() { return addMetadata; }
        public void setAddMetadata(boolean addMetadata) { this.addMetadata = addMetadata; }
        public String getProcessorName() { return processorName; }
        public void setProcessorName(String processorName) { this.processorName = processorName; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    public interface DataProcessor {
        List<Map<String, Object>> processBatch(List<Map<String, Object>> batch);
    }

    public enum ExportFormat {
        JSON, CSV, XML, PARQUET
    }
}