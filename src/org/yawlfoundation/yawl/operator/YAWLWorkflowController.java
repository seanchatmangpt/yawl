/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * Licensed under the GNU Lesser General Public License.
 */

package org.yawlfoundation.yawl.operator;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Kubernetes Operator controller for YAWLWorkflow custom resources.
 * <p>
 * Bridges the Kubernetes API (CRD watch/reconcile loop) with the YAWL engine
 * via Interface A (spec upload) and Interface B (case management). Each
 * YAWLWorkflow CR maps to a YAWL specification, and each YAWLWorkflowRun CR
 * maps to a YAWL case instance.
 * <p>
 * The controller implements the Kubernetes operator pattern:
 * <ol>
 *   <li>Watch for YAWLWorkflow CR creation → upload spec via Interface A</li>
 *   <li>Watch for YAWLWorkflowRun CR creation → launch case via Interface B</li>
 *   <li>Poll active cases → update YAWLWorkflowRun status</li>
 *   <li>Watch K8s pod events → trigger worklet exceptions for self-healing</li>
 *   <li>Handle CR deletion → cancel cases, unload specs</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class YAWLWorkflowController {

    private static final Logger LOG = Logger.getLogger(YAWLWorkflowController.class.getName());

    private final InterfaceA_EnvironmentBasedClient interfaceA;
    private final InterfaceB_EnvironmentBasedClient interfaceB;
    private final HttpClient kubeClient;
    private final String engineUrl;
    private final ConcurrentHashMap<String, String> activeSessionHandles;
    private final ConcurrentHashMap<String, String> specIdToCaseId;

    /**
     * Constructs the operator controller with engine connection details.
     *
     * @param engineBaseUrl YAWL engine base URL (e.g., "http://yawl-engine.yawl.svc.cluster.local:8080")
     * @param interfaceAPath Interface A URL path (e.g., "/yawl/ia")
     * @param interfaceBPath Interface B URL path (e.g., "/yawl/ib")
     */
    public YAWLWorkflowController(String engineBaseUrl, String interfaceAPath, String interfaceBPath) {
        this.engineUrl = engineBaseUrl;
        this.interfaceA = new InterfaceA_EnvironmentBasedClient(engineBaseUrl + interfaceAPath);
        this.interfaceB = new InterfaceB_EnvironmentBasedClient(engineBaseUrl + interfaceBPath);
        this.kubeClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.activeSessionHandles = new ConcurrentHashMap<>();
        this.specIdToCaseId = new ConcurrentHashMap<>();
    }

    /**
     * Reconciles a YAWLWorkflow custom resource by uploading its specification
     * to the YAWL engine via Interface A.
     *
     * @param workflowName the CR metadata.name
     * @param specXml the YAWL specification XML content
     * @param version the specification version
     * @return the engine-assigned specification ID
     * @throws IOException if the engine is unreachable
     */
    public String reconcileWorkflow(String workflowName, String specXml, String version) throws IOException {
        LOG.info("Reconciling YAWLWorkflow: " + workflowName);

        String sessionHandle = getOrCreateSession();

        // Upload the specification via Interface A
        String result = interfaceA.uploadSpecification(specXml, sessionHandle);

        if (result == null || result.contains("<failure>")) {
            throw new IOException("Failed to upload specification for workflow '"
                    + workflowName + "': " + result);
        }

        LOG.info("Workflow '" + workflowName + "' uploaded successfully: " + result);
        return result;
    }

    /**
     * Launches a new case (YAWLWorkflowRun) for the given specification.
     *
     * @param specUri the specification URI
     * @param version the specification version
     * @param caseData initial case data as XML, or null
     * @return the engine case ID
     * @throws IOException if the engine is unreachable
     */
    public String launchCase(String specUri, String version, String caseData) throws IOException {
        LOG.info("Launching case for spec: " + specUri + " v" + version);

        String sessionHandle = getOrCreateSession();
        YSpecificationID specId = new YSpecificationID(specUri, version, specUri);

        String caseId = interfaceB.launchCase(
                specId,
                caseData != null ? caseData : "",
                sessionHandle
        );

        if (caseId == null || caseId.contains("<failure>")) {
            throw new IOException("Failed to launch case for spec '"
                    + specUri + "': " + caseId);
        }

        specIdToCaseId.put(specUri + ":" + version, caseId);
        LOG.info("Case launched: " + caseId);
        return caseId;
    }

    /**
     * Cancels a running case by its engine case ID.
     *
     * @param caseId the YAWL engine case ID
     * @throws IOException if the engine is unreachable
     */
    public void cancelCase(String caseId) throws IOException {
        LOG.info("Cancelling case: " + caseId);

        String sessionHandle = getOrCreateSession();
        String result = interfaceB.cancelCase(caseId, sessionHandle);

        if (result != null && result.contains("<failure>")) {
            LOG.warning("Failed to cancel case " + caseId + ": " + result);
        }
    }

    /**
     * Retrieves the current state of a running case.
     *
     * @param caseId the YAWL engine case ID
     * @return XML describing the case state
     * @throws IOException if the engine is unreachable
     */
    public String getCaseState(String caseId) throws IOException {
        String sessionHandle = getOrCreateSession();
        return interfaceB.getCaseState(caseId, sessionHandle);
    }

    /**
     * Triggers a worklet exception for self-healing. Called when the operator
     * detects a Kubernetes pod failure event that corresponds to a running
     * workflow task.
     *
     * @param caseId the YAWL case ID affected by the pod failure
     * @param workItemId the work item corresponding to the failed pod
     * @param podName the name of the failed Kubernetes pod
     * @param failureReason the Kubernetes-reported failure reason
     * @throws IOException if the engine is unreachable
     */
    public void triggerSelfHealing(String caseId, String workItemId,
                                    String podName, String failureReason) throws IOException {
        LOG.warning("Self-healing triggered for case " + caseId
                + " work item " + workItemId
                + " due to pod " + podName + " failure: " + failureReason);

        String sessionHandle = getOrCreateSession();

        // Raise an external exception via the worklet service
        // The worklet exception service uses RDR rules to select the
        // appropriate compensatory worklet based on the failure type
        String exceptionXml = "<externalException>"
                + "<caseId>" + caseId + "</caseId>"
                + "<workItemId>" + workItemId + "</workItemId>"
                + "<trigger>KubernetesPodFailure</trigger>"
                + "<podName>" + podName + "</podName>"
                + "<reason>" + failureReason + "</reason>"
                + "</externalException>";

        // Post to worklet service exception endpoint
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(engineUrl + "/workletService/ix"))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(exceptionXml))
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            HttpResponse<String> response = kubeClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Worklet exception service returned "
                        + response.statusCode() + ": " + response.body());
            }
            LOG.info("Self-healing exception raised successfully for case " + caseId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while triggering self-healing", e);
        }
    }

    /**
     * Gets an existing session handle or creates a new one by connecting
     * to the YAWL engine via Interface B.
     */
    private String getOrCreateSession() throws IOException {
        String cached = activeSessionHandles.get("default");
        if (cached != null) {
            // Verify session is still valid
            String check = interfaceB.checkConnection(cached);
            if (check != null && !check.contains("<failure>")) {
                return cached;
            }
        }

        // Connect with operator credentials
        String adminUser = System.getenv("YAWL_ADMIN_USER");
        String adminPassword = System.getenv("YAWL_ADMIN_PASSWORD");

        if (adminUser == null || adminUser.isEmpty()) {
            throw new IOException("YAWL_ADMIN_USER environment variable required.\n"
                    + "Set via K8s Secret: yawl-operator-credentials");
        }
        if (adminPassword == null || adminPassword.isEmpty()) {
            throw new IOException("YAWL_ADMIN_PASSWORD environment variable required.\n"
                    + "Set via K8s Secret: yawl-operator-credentials");
        }

        String handle = interfaceB.connect(adminUser, adminPassword);
        if (handle == null || handle.contains("<failure>")) {
            throw new IOException("Failed to connect to YAWL engine at "
                    + engineUrl + ": " + handle);
        }

        activeSessionHandles.put("default", handle);
        LOG.info("Connected to YAWL engine, session: " + handle.substring(0, 8) + "...");
        return handle;
    }

    /**
     * Health check: verifies the operator can reach the YAWL engine.
     *
     * @return true if the engine is reachable
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(engineUrl + "/"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = kubeClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
        } catch (IOException | InterruptedException e) {
            LOG.log(Level.WARNING, "Engine health check failed", e);
            return false;
        }
    }

    /**
     * Graceful shutdown: disconnect from engine.
     */
    public void shutdown() {
        LOG.info("Shutting down YAWL operator controller");
        activeSessionHandles.forEach((key, handle) -> {
            try {
                interfaceB.disconnect(handle);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to disconnect session " + key, e);
            }
        });
        activeSessionHandles.clear();
        specIdToCaseId.clear();
    }
}
