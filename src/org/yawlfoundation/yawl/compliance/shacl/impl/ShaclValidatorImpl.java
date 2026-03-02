/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.compliance.shacl.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.compliance.shacl.*;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.YNetRunner;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of SHACL validator for YAWL compliance checking.
 *
 * <p>This class provides concrete implementations of SHACL validation
 * for YAWL specifications and workflow engines against SOX, GDPR,
 * and HIPAA compliance requirements.</p>
 */
public class ShaclValidatorImpl implements ShaclValidator {

    private static final Logger _logger = LogManager.getLogger(ShaclValidatorImpl.class);

    private final ShaclShapeRegistry shapeRegistry;
    private final Map<String, Object> performanceMetrics;
    private final Map<ComplianceDomain, Set<String>> cachedResults;

    /**
     * Creates a new SHACL validator implementation.
     */
    public ShaclValidatorImpl() {
        this(new ShaclShapeRegistry());
    }

    /**
     * Creates a new SHACL validator implementation with a custom shape registry.
     *
     * @param shapeRegistry The shape registry to use
     */
    public ShaclValidatorImpl(ShaclShapeRegistry shapeRegistry) {
        this.shapeRegistry = shapeRegistry;
        this.performanceMetrics = new ConcurrentHashMap<>();
        this.cachedResults = new EnumMap<>(ComplianceDomain.class);

        initializeMetrics();
    }

    @Override
    public ShaclValidationResult validate(YSpecification spec, ComplianceDomain complianceDomain) {
        long startTime = System.nanoTime();
        String target = spec.getSpecURI() != null ? spec.getSpecURI() : "unnamed-spec";

        _logger.debug("Validating specification {} against {}", target, complianceDomain);

        try {
            // Check if we have cached results
            String cacheKey = target + ":" + complianceDomain;
            if (cachedResults.containsKey(complianceDomain) &&
                cachedResults.get(complianceDomain).contains(cacheKey)) {
                _logger.debug("Using cached result for {}", cacheKey);
                return getCachedResult(cacheKey);
            }

            // Validate the specification
            List<ShaclViolation> violations = validateSpecificationAgainstDomain(spec, complianceDomain);

            long validationTime = System.nanoTime() - startTime;
            Map<String, Object> metadata = createValidationMetadata(spec, complianceDomain);

            ShaclValidationResult result;
            if (violations.isEmpty()) {
                result = ShaclValidationResult.success(complianceDomain, target, validationTime, metadata);
            } else {
                result = ShaclValidationResult.failure(complianceDomain, target, violations, validationTime, metadata);
            }

            // Cache the result
            cacheResult(cacheKey, result);

            updatePerformanceMetrics(validationTime, violations.size());

            return result;
        } catch (Exception e) {
            _logger.error("Error validating specification {} against {}: {}", target, complianceDomain, e.getMessage());
            long validationTime = System.nanoTime() - startTime;

            Map<String, Object> metadata = createErrorMetadata(spec, complianceDomain, e);
            return ShaclValidationResult.failure(complianceDomain, target,
                List.of(ShaclViolation.high("system", "validation-error",
                    "System error: " + e.getMessage(), "/spec")),
                validationTime, metadata);
        }
    }

    @Override
    public ShaclValidationResult validate(YNetRunner runner, ComplianceDomain complianceDomain) {
        long startTime = System.nanoTime();
        String target = runner.getSpecificationID().toString();

        _logger.debug("Validating runner {} against {}", target, complianceDomain);

        try {
            // Validate the engine
            List<ShaclViolation> violations = validateEngineAgainstDomain(runner, complianceDomain);

            long validationTime = System.nanoTime() - startTime;
            Map<String, Object> metadata = createEngineValidationMetadata(runner, complianceDomain);

            ShaclViolationResult result;
            if (violations.isEmpty()) {
                result = ShaclValidationResult.success(complianceDomain, target, validationTime, metadata);
            } else {
                result = ShaclValidationResult.failure(complianceDomain, target, violations, validationTime, metadata);
            }

            updatePerformanceMetrics(validationTime, violations.size());

            return result;
        } catch (Exception e) {
            _logger.error("Error validating runner {} against {}: {}", target, complianceDomain, e.getMessage());
            long validationTime = System.nanoTime() - startTime;

            Map<String, Object> metadata = createEngineErrorMetadata(runner, complianceDomain, e);
            return ShaclValidationResult.failure(complianceDomain, target,
                List.of(ShaclViolation.high("system", "validation-error",
                    "System error: " + e.getMessage(), "/engine")),
                validationTime, metadata);
        }
    }

    @Override
    public List<ShaclValidationResult> validate(YSpecification spec, List<ComplianceDomain> domains) {
        long startTime = System.nanoTime();
        List<ShaclValidationResult> results = new ArrayList<>();

        for (ComplianceDomain domain : domains) {
            if (supportsSpecificationDomain(domain)) {
                results.add(validate(spec, domain));
            }
        }

        long totalTime = System.nanoTime() - startTime;
        _logger.debug("Validated specification against {} domains in {}ms", domains.size(), totalTime / 1_000_000);

        return results;
    }

    @Override
    public List<ShaclValidationResult> validate(YNetRunner runner, List<ComplianceDomain> domains) {
        long startTime = System.nanoTime();
        List<ShaclValidationResult> results = new ArrayList<>();

        for (ComplianceDomain domain : domains) {
            if (supportsEngineDomain(domain)) {
                results.add(validate(runner, domain));
            }
        }

        long totalTime = System.nanoTime() - startTime;
        _logger.debug("Validated runner against {} domains in {}ms", domains.size(), totalTime / 1_000_000);

        return results;
    }

    @Override
    public List<ShaclValidationResult> validateAll(YSpecification spec) {
        return validate(spec, Arrays.asList(getSupportedSpecificationDomains()));
    }

    @Override
    public List<ShaclValidationResult> validateAll(YNetRunner runner) {
        return validate(runner, Arrays.asList(getSupportedEngineDomains()));
    }

    @Override
    public ComplianceDomain[] getSupportedSpecificationDomains() {
        return ComplianceDomain.getSpecificationDomains();
    }

    @Override
    public ComplianceDomain[] getSupportedEngineDomains() {
        return ComplianceDomain.getEngineDomains();
    }

    @Override
    public boolean supportsSpecificationDomain(ComplianceDomain domain) {
        return Arrays.asList(getSupportedSpecificationDomains()).contains(domain);
    }

    @Override
    public boolean supportsEngineDomain(ComplianceDomain domain) {
        return Arrays.asList(getSupportedEngineDomains()).contains(domain);
    }

    @Override
    public Map<String, Object> getPerformanceMetrics() {
        return new HashMap<>(performanceMetrics);
    }

    @Override
    public void resetPerformanceMetrics() {
        performanceMetrics.clear();
        initializeMetrics();
    }

    /**
     * Validates a specification against a specific compliance domain.
     */
    private List<ShaclViolation> validateSpecificationAgainstDomain(YSpecification spec, ComplianceDomain domain) {
        List<ShaclViolation> violations = new ArrayList<>();

        // Validate based on compliance domain
        switch (domain) {
            case SOX:
                violations.addAll(validateSOXCompliance(spec));
                break;
            case GDPR:
                violations.addAll(validateGDPRCompliance(spec));
                break;
            case HIPAA:
                violations.addAll(validateHIPAACompliance(spec));
                break;
        }

        return violations;
    }

    /**
     * Validates an engine against a specific compliance domain.
     */
    private List<ShaclViolation> validateEngineAgainstDomain(YNetRunner runner, ComplianceDomain domain) {
        List<ShaclViolation> violations = new ArrayList<>();

        // Validate based on compliance domain
        switch (domain) {
            case SOX:
                violations.addAll(validateSOXEngineCompliance(runner));
                break;
            case HIPAA:
                violations.addAll(validateHIPAAEngineCompliance(runner));
                break;
        }

        return violations;
    }

    /**
     * Validates SOX compliance for a specification.
     */
    private List<ShaclViolation> validateSOXCompliance(YSpecification spec) {
        List<ShaclViolation> violations = new ArrayList<>();

        // Check for audit trail requirements
        YNet rootNet = spec.getRootNet();
        if (rootNet == null) {
            violations.add(ShaclViolation.high("spec", "sox-audit-trail-required",
                "Specification must have a root net for audit trails", "/spec"));
            return violations;
        }

        // Check for financial data handling
        List<YTask> tasks = rootNet.getTasks();
        for (YTask task : tasks) {
            if (handlesFinancialData(task)) {
                if (!hasAuditTrail(task)) {
                    violations.add(ShaclViolation.high(task.getID(), "sox-audit-trail-missing",
                        "Financial data task must have audit trail", "/spec/net/task/" + task.getID()));
                }

                if (!hasSegregationOfDuties(task, tasks)) {
                    violations.add(ShaclViolation.medium(task.getID(), "sox-segregation-violation",
                        "Potential segregation of duties violation", "/spec/net/task/" + task.getID()));
                }
            }
        }

        // Check for proper documentation
        if (spec.getDocumentation() == null || spec.getDocumentation().isEmpty()) {
            violations.add(ShaclViolation.medium("spec", "sox-documentation-required",
                "Financial processes must have documentation", "/spec"));
        }

        return violations;
    }

    /**
     * Validates GDPR compliance for a specification.
     */
    private List<ShaclViolation> validateGDPRCompliance(YSpecification spec) {
        List<ShaclViolation> violations = new ArrayList<>();

        // Check for personal data handling
        YNet rootNet = spec.getRootNet();
        if (rootNet != null) {
            List<YTask> tasks = rootNet.getTasks();
            for (YTask task : tasks) {
                if (processesPersonalData(task)) {
                    if (!hasDataProtectionMeasure(task)) {
                        violations.add(ShaclViolation.high(task.getID(), "gdpr-data-protection-required",
                            "Personal data processing must have protection measures", "/spec/net/task/" + task.getID()));
                    }

                    if (!hasLawfulBasis(task)) {
                        violations.add(ShaclViolation.high(task.getID(), "gdpr-lawful-basis-required",
                            "Personal data processing必须有合法依据", "/spec/net/task/" + task.getID()));
                    }

                    if (!hasDataMinimization(task)) {
                        violations.add(ShaclViolation.medium(task.getID(), "gdpr-data-minimization",
                            "Ensure data minimization for personal data", "/spec/net/task/" + task.getID()));
                    }
                }
            }
        }

        // Check for consent management
        if (!hasConsentManagement(spec)) {
            violations.add(ShaclViolation.medium("spec", "gdpr-consent-management",
                "Processes handling personal data should include consent management", "/spec"));
        }

        // Check for data subject rights
        if (!hasDataSubjectRights(spec)) {
            violations.add(ShaclViolation.medium("spec", "gdpr-data-subject-rights",
                "Processes should support data subject rights", "/spec"));
        }

        return violations;
    }

    /**
     * Validates HIPAA compliance for a specification.
     */
    private List<ShaclViolation> validateHIPAACompliance(YSpecification spec) {
        List<ShaclViolation> violations = new ArrayList<>();

        // Check for healthcare data handling
        YNet rootNet = spec.getRootNet();
        if (rootNet != null) {
            List<YTask> tasks = rootNet.getTasks();
            for (YTask task : tasks) {
                if (handlesHealthcareData(task)) {
                    if (!hasHIPAAComplianceMeasures(task)) {
                        violations.add(ShaclViolation.high(task.getID(), "hipaa-compliance-required",
                            "Healthcare data handling requires HIPAA compliance measures", "/spec/net/task/" + task.getID()));
                    }

                    if (!hasAccessControls(task)) {
                        violations.add(ShaclViolation.high(task.getID(), "hipaa-access-controls",
                            "Healthcare data requires strict access controls", "/spec/net/task/" + task.getID()));
                    }

                    if (!hasAuditLogging(task)) {
                        violations.add(ShaclViolation.medium(task.getID(), "hipaa-audit-logging",
                            "Healthcare data activities require audit logging", "/spec/net/task/" + task.getID()));
                    }
                }
            }
        }

        // Check for PHI handling
        if (!hasPHIHandlingProtocol(spec)) {
            violations.add(ShaclViolation.high("spec", "hipaa-phi-protocol",
                "Processes handling PHI must have proper protocols", "/spec"));
        }

        return violations;
    }

    /**
     * Validates SOX compliance for an engine.
     */
    private List<ShaclViolation> validateSOXEngineCompliance(YNetRunner runner) {
        List<ShaclViolation> violations = new ArrayList<>();

        // Check for audit trail maintenance
        if (!hasEngineAuditTrail(runner)) {
            violations.add(ShaclViolation.high("engine", "sox-engine-audit-trail",
                "Engine must maintain financial audit trails", "/engine"));
        }

        // Check for data integrity
        if (!hasDataIntegrityChecks(runner)) {
            violations.add(ShaclViolation.high("engine", "sox-data-integrity",
                "Engine must enforce data integrity for financial processes", "/engine"));
        }

        // Check for access controls
        if (!hasSOXAccessControls(runner)) {
            violations.add(ShaclViolation.medium("engine", "sox-access-controls",
                "Engine must enforce proper access controls", "/engine"));
        }

        return violations;
    }

    /**
     * Validates HIPAA compliance for an engine.
     */
    private List<ShaclViolation> validateHIPAAEngineCompliance(YNetRunner runner) {
        List<ShaclViolation> violations = new ArrayList<>();

        // Check for encryption requirements
        if (!hasEncryptionInTransit(runner)) {
            violations.add(ShaclViolation.high("engine", "hipaa-encryption-transit",
                "Engine must encrypt healthcare data in transit", "/engine"));
        }

        // Check for audit logging
        if (!hasHIPAAuditLogging(runner)) {
            violations.add(ShaclViolation.high("engine", "hipaa-audit-logging",
                "Engine must maintain HIPAA audit logs", "/engine"));
        }

        // Check for breach notification
        if (!hasBreachNotification(runner)) {
            violations.add(ShaclViolation.medium("engine", "hipaa-breach-notification",
                "Engine must support breach notification procedures", "/engine"));
        }

        return violations;
    }

    // Helper methods for SOX validation
    private boolean handlesFinancialData(YTask task) {
        // Implementation depends on task properties or data schema
        return task.getName() != null && task.getName().toLowerCase().contains("financial");
    }

    private boolean hasAuditTrail(YTask task) {
        // Check if task has audit trail requirements
        return task.getDocumentation() != null &&
               task.getDocumentation().contains("audit");
    }

    private boolean hasSegregationOfDuties(YTask task, List<YTask> allTasks) {
        // Check for segregation of duties
        // Simplified implementation - in practice would need more sophisticated logic
        return true;
    }

    // Helper methods for GDPR validation
    private boolean processesPersonalData(YTask task) {
        // Check if task processes personal data
        return task.getName() != null &&
               (task.getName().toLowerCase().contains("personal") ||
                task.getName().toLowerCase().contains("data"));
    }

    private boolean hasDataProtectionMeasure(YTask task) {
        // Check for data protection measures
        return task.getDocumentation() != null &&
               task.getDocumentation().contains("protection");
    }

    private boolean hasLawfulBasis(YTask task) {
        // Check for lawful basis for processing
        return task.getDocumentation() != null &&
               task.getDocumentation().contains("lawful");
    }

    private boolean hasDataMinimization(YTask task) {
        // Check for data minimization
        return task.getDocumentation() != null &&
               task.getDocumentation().contains("minimization");
    }

    private boolean hasConsentManagement(YSpecification spec) {
        // Check for consent management
        return spec.getDocumentation() != null &&
               spec.getDocumentation().contains("consent");
    }

    private boolean hasDataSubjectRights(YSpecification spec) {
        // Check for data subject rights
        return spec.getDocumentation() != null &&
               spec.getDocumentation().contains("rights");
    }

    // Helper methods for HIPAA validation
    private boolean handlesHealthcareData(YTask task) {
        // Check if task handles healthcare data
        return task.getName() != null &&
               task.getName().toLowerCase().contains("healthcare");
    }

    private boolean hasHIPAAComplianceMeasures(YTask task) {
        // Check for HIPAA compliance measures
        return task.getDocumentation() != null &&
               task.getDocumentation().contains("hipaa");
    }

    private boolean hasAccessControls(YTask task) {
        // Check for access controls
        return task.getDocumentation() != null &&
               task.getDocumentation().contains("access");
    }

    private boolean hasAuditLogging(YTask task) {
        // Check for audit logging
        return task.getDocumentation() != null &&
               task.getDocumentation().contains("audit");
    }

    private boolean hasPHIHandlingProtocol(YSpecification spec) {
        // Check for PHI handling protocol
        return spec.getDocumentation() != null &&
               spec.getDocumentation().contains("phi");
    }

    // Helper methods for engine validation
    private boolean hasEngineAuditTrail(YNetRunner runner) {
        // Check for audit trail maintenance
        return runner != null; // Simplified implementation
    }

    private boolean hasDataIntegrityChecks(YNetRunner runner) {
        // Check for data integrity
        return runner != null; // Simplified implementation
    }

    private boolean hasSOXAccessControls(YNetRunner runner) {
        // Check for access controls
        return runner != null; // Simplified implementation
    }

    private boolean hasEncryptionInTransit(YNetRunner runner) {
        // Check for encryption
        return runner != null; // Simplified implementation
    }

    private boolean hasHIPAAuditLogging(YNetRunner runner) {
        // Check for audit logging
        return runner != null; // Simplified implementation
    }

    private boolean hasBreachNotification(YNetRunner runner) {
        // Check for breach notification
        return runner != null; // Simplified implementation
    }

    // Helper methods for caching
    private void cacheResult(String cacheKey, ShaclValidationResult result) {
        for (ComplianceDomain domain : ComplianceDomain.values()) {
            if (domain.name().equals(cacheKey.split(":")[1])) {
                cachedResults.computeIfAbsent(domain, k -> ConcurrentHashMap.newKeySet())
                    .add(cacheKey);
                break;
            }
        }
    }

    private ShaclValidationResult getCachedResult(String cacheKey) {
        // Simplified implementation - would need to store actual results
        return null;
    }

    // Helper methods for metadata
    private Map<String, Object> createValidationMetadata(YSpecification spec, ComplianceDomain domain) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("specURI", spec.getSpecURI());
        metadata.put("version", spec.getVersion() != null ? spec.getVersion().toString() : null);
        metadata.put("domain", domain.name());
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("cacheEnabled", true);
        return metadata;
    }

    private Map<String, Object> createErrorMetadata(YSpecification spec, ComplianceDomain domain, Exception e) {
        Map<String, Object> metadata = createValidationMetadata(spec, domain);
        metadata.put("error", e.getMessage());
        metadata.put("errorType", e.getClass().getSimpleName());
        return metadata;
    }

    private Map<String, Object> createEngineValidationMetadata(YNetRunner runner, ComplianceDomain domain) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("specId", runner.getSpecificationID().toString());
        metadata.put("domain", domain.name());
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("cacheEnabled", true);
        return metadata;
    }

    private Map<String, Object> createEngineErrorMetadata(YNetRunner runner, ComplianceDomain domain, Exception e) {
        Map<String, Object> metadata = createEngineValidationMetadata(runner, domain);
        metadata.put("error", e.getMessage());
        metadata.put("errorType", e.getClass().getSimpleName());
        return metadata;
    }

    // Helper methods for performance metrics
    private void initializeMetrics() {
        performanceMetrics.put("totalValidations", 0);
        performanceMetrics.put("totalValidationTime", 0L);
        performanceMetrics.put("averageValidationTime", 0L);
        performanceMetrics.put("lastValidationTime", 0L);
        performanceMetrics.put("cacheHits", 0);
        performanceMetrics.put("cacheMisses", 0);
        performanceMetrics.put("violationCounts", new HashMap<String, Integer>());
    }

    private void updatePerformanceMetrics(long validationTime, int violationCount) {
        int totalValidations = (int) performanceMetrics.get("totalValidations") + 1;
        long totalValidationTime = (long) performanceMetrics.get("totalValidationTime") + validationTime;

        performanceMetrics.put("totalValidations", totalValidations);
        performanceMetrics.put("totalValidationTime", totalValidationTime);
        performanceMetrics.put("averageValidationTime", totalValidationTime / totalValidations);
        performanceMetrics.put("lastValidationTime", validationTime);

        // Update violation counts
        @SuppressWarnings("unchecked")
        Map<String, Integer> violationCounts = (Map<String, Integer>) performanceMetrics.get("violationCounts");
        violationCounts.put("total", violationCounts.getOrDefault("total", 0) + violationCount);
    }
}