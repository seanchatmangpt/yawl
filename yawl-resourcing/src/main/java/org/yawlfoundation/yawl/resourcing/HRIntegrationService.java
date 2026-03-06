/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or/modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.resourcing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * HR system integration service.
 *
 * <p>Provides integration with external HR systems for participant management,
 * organizational hierarchy, and employee data synchronization.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Employee profile synchronization</li>
 *   <li>Organizational hierarchy management</li>
 *   <li>Employee availability tracking</li>
 *   <li>Skills and capabilities management</li>
 *   <li>Leave and vacation tracking</li>
 *   <li>Performance metrics integration</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All operations are thread-safe and can be called concurrently from multiple
 * virtual threads.</p>
 *
 * @since YAWL 6.0
 */
@Service
public class HRIntegrationService {

    private static final Logger _logger = LogManager.getLogger(HRIntegrationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final CloseableHttpClient httpClient;
    private final String hrApiUrl;
    private final String hrApiToken;
    private final ParticipantRepository participantRepository;

    /**
     * Creates a new HR integration service.
     *
     * @param participantRepository the participant repository
     */
    public HRIntegrationService(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
        this.httpClient = HttpClients.createDefault();

        // Get HR API configuration from environment
        this.hrApiUrl = System.getenv("YAWL_HR_API_URL");
        this.hrApiToken = System.getenv("YAWL_HR_API_TOKEN");

        if (hrApiUrl == null || hrApiUrl.isEmpty()) {
            _logger.warn("HR API URL not configured, HR integration disabled");
        }
        if (hrApiToken == null || hrApiToken.isEmpty()) {
            _logger.warn("HR API token not configured, HR integration disabled");
        }
    }

    /**
     * Synchronizes employee profiles from HR system.
     *
     * @return the synchronization result
     */
    public CompletableFuture<HRSyncResult> synchronizeEmployeeProfiles() {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(
                HRSyncResult.failed("HR integration not configured")
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                _logger.info("Starting employee profile synchronization from HR system");

                // Get all employees from HR system
                List<HREmployee> hrEmployees = fetchAllEmployees();
                _logger.info("Retrieved {} employees from HR system", hrEmployees.size());

                // Get existing participants
                List<Participant> existingParticipants = participantRepository.findAll();

                // Synchronize participants
                int created = 0;
                int updated = 0;
                int deactivated = 0;

                for (HREmployee hrEmployee : hrEmployees) {
                    Participant existing = existingParticipants.stream()
                        .filter(p -> hrEmployee.getEmployeeId().equals(p.getUserId()))
                        .findFirst()
                        .orElse(null);

                    if (existing == null) {
                        // Create new participant
                        Participant newParticipant = createParticipantFromHREmployee(hrEmployee);
                        participantRepository.save(newParticipant);
                        created++;
                        _logger.info("Created new participant from HR employee: {}", hrEmployee.getEmployeeId());
                    } else {
                        // Update existing participant
                        updateParticipantFromHREmployee(existing, hrEmployee);
                        participantRepository.save(existing);
                        updated++;
                        _logger.info("Updated participant from HR employee: {}", hrEmployee.getEmployeeId());
                    }
                }

                // Deactivate participants not in HR system
                Set<String> hrEmployeeIds = new HashSet<>(hrEmployees.stream()
                    .map(HREmployee::getEmployeeId)
                    .toList());

                for (Participant existing : existingParticipants) {
                    if (!hrEmployeeIds.contains(existing.getUserId())) {
                        existing.setActive(false);
                        participantRepository.save(existing);
                        deactivated++;
                    }
                }

                _logger.info("Employee profile synchronization complete: " +
                    "Created: {}, Updated: {}, Deactivated: {}", created, updated, deactivated);

                return HRSyncResult.success(created, updated, deactivated);

            } catch (Exception e) {
                _logger.error("Employee profile synchronization failed: {}", e.getMessage(), e);
                return HRSyncResult.failed("Synchronization failed: " + e.getMessage());
            }
        });
    }

    /**
     * Updates employee availability in HR system.
     *
     * @param participantId the participant ID
     * @param available whether the employee is available
     * @return true if update was successful
     */
    public CompletableFuture<Boolean> updateEmployeeAvailability(String participantId, boolean available) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = hrApiUrl + "/employees/" + participantId + "/availability";

                Map<String, Object> payload = new HashMap<>();
                payload.put("available", available);
                payload.put("updatedBy", "YAWL-Resourcing");
                payload.put("updatedTime", Instant.now().toString());

                HttpPut httpPut = new HttpPut(apiUrl);
                httpPut.setHeader("Authorization", "Bearer " + hrApiToken);
                httpPut.setHeader("Content-Type", "application/json");
                httpPut.setEntity(new StringEntity(objectMapper.writeValueAsString(payload)));

                HttpResponse response = httpClient.execute(httpPut);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    _logger.info("Updated availability for employee {}: {}", participantId, available);
                    return true;
                } else {
                    _logger.error("Failed to update availability for employee {}: {}",
                        participantId, statusCode);
                    return false;
                }

            } catch (Exception e) {
                _logger.error("Error updating employee availability: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * Gets employee leave information from HR system.
     *
     * @param participantId the participant ID
     * @return leave information
     */
    public CompletableFuture<HRLeaveInfo> getEmployeeLeaveInfo(String participantId) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = hrApiUrl + "/employees/" + participantId + "/leave";

                HttpGet httpGet = new HttpGet(apiUrl);
                httpGet.setHeader("Authorization", "Bearer " + hrApiToken);

                HttpResponse response = httpClient.execute(httpGet);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    HttpEntity entity = response.getEntity();
                    String json = EntityUtils.toString(entity);
                    return objectMapper.readValue(json, HRLeaveInfo.class);
                } else {
                    _logger.error("Failed to get leave info for employee {}: {}",
                        participantId, statusCode);
                    return null;
                }

            } catch (Exception e) {
                _logger.error("Error getting employee leave info: {}", e.getMessage(), e);
                return null;
            }
        });
    }

    /**
     * Pushes work assignment data to HR system.
     *
     * @param participantId the participant ID
     * @param workload the current workload
     * @return true if push was successful
     */
    public CompletableFuture<Boolean> pushWorkAssignment(String participantId, int workload) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = hrApiUrl + "/employees/" + participantId + "/workload";

                Map<String, Object> payload = new HashMap<>();
                payload.put("workload", workload);
                payload.put("updatedBy", "YAWL-Resourcing");
                payload.put("updatedTime", Instant.now().toString());

                HttpPost httpPost = new HttpPost(apiUrl);
                httpPost.setHeader("Authorization", "Bearer " + hrApiToken);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(payload)));

                HttpResponse response = httpClient.execute(httpPost);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    _logger.info("Pushed workload data for employee {}: {}", participantId, workload);
                    return true;
                } else {
                    _logger.error("Failed to push workload for employee {}: {}",
                        participantId, statusCode);
                    return false;
                }

            } catch (Exception e) {
                _logger.error("Error pushing work assignment: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * Fetches all employees from HR system.
     *
     * @return list of HR employees
     * @throws IOException if request fails
     */
    private List<HREmployee> fetchAllEmployees() throws IOException {
        String apiUrl = hrApiUrl + "/employees";

        HttpGet httpGet = new HttpGet(apiUrl);
        httpGet.setHeader("Authorization", "Bearer " + hrApiToken);

        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        String json = EntityUtils.toString(entity);

        HRResponse<HREmployee> responseObj = objectMapper.readValue(json,
            objectMapper.getTypeFactory().constructParametricType(HRResponse.class, HREmployee.class));

        return responseObj.getData();
    }

    /**
     * Creates a participant from HR employee data.
     *
     * @param hrEmployee the HR employee
     * @return the new participant
     */
    private Participant createParticipantFromHREmployee(HREmployee hrEmployee) {
        Set<String> capabilities = new HashSet<>(hrEmployee.getSkills());
        capabilities.addAll(hrEmployee.getCertifications());

        Participant participant = new Participant(
            hrEmployee.getFullName(),
            hrEmployee.getJobTitle(),
            capabilities
        );

        participant.setUserId(hrEmployee.getEmployeeId());
        participant.setEmail(hrEmployee.getEmail());
        participant.setDepartment(hrEmployee.getDepartment());
        participant.setTitle(hrEmployee.getJobTitle());
        participant.setActive(hrEmployee.isActive());
        participant.setCreatedTime(Instant.now());

        return participant;
    }

    /**
     * Updates a participant from HR employee data.
     *
     * @param participant the existing participant
     * @param hrEmployee the HR employee
     */
    private void updateParticipantFromHREmployee(Participant participant, HREmployee hrEmployee) {
        participant.setName(hrEmployee.getFullName());
        participant.setRole(hrEmployee.getJobTitle());
        participant.setEmail(hrEmployee.getEmail());
        participant.setDepartment(hrEmployee.getDepartment());
        participant.setActive(hrEmployee.isActive());

        // Update capabilities
        Set<String> capabilities = new HashSet<>(hrEmployee.getSkills());
        capabilities.addAll(hrEmployee.getCertifications());
        participant.setCapabilities(capabilities);
    }

    /**
     * Checks if HR integration is configured.
     *
     * @return true if configured
     */
    private boolean isConfigured() {
        return hrApiUrl != null && !hrApiUrl.isEmpty() &&
               hrApiToken != null && !hrApiToken.isEmpty();
    }

    /**
     * Close the HTTP client.
     *
     * @throws IOException if close fails
     */
    public void close() throws IOException {
        httpClient.close();
    }
}

// DTO classes for HR system integration

class HRResponse<T> {
    private List<T> data;
    private Meta meta;

    public List<T> getData() { return data; }
    public void setData(List<T> data) { this.data = data; }
    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }
}

class Meta {
    private int total;
    private int page;
    private int limit;

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
}

class HREmployee {
    private String employeeId;
    private String fullName;
    private String email;
    private String jobTitle;
    private String department;
    private boolean active;
    private List<String> skills;
    private List<String> certifications;

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public List<String> getCertifications() { return certifications; }
    public void setCertifications(List<String> certifications) { this.certifications = certifications; }
}

class HRLeaveInfo {
    private String employeeId;
    private int vacationDaysUsed;
    private int vacationDaysRemaining;
    private int sickDaysUsed;
    private int sickDaysRemaining;
    private boolean onLeave;
    private Instant leaveStartDate;
    private Instant leaveEndDate;

    // Getters and setters
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public int getVacationDaysUsed() { return vacationDaysUsed; }
    public void setVacationDaysUsed(int vacationDaysUsed) { this.vacationDaysUsed = vacationDaysUsed; }
    public int getVacationDaysRemaining() { return vacationDaysRemaining; }
    public void setVacationDaysRemaining(int vacationDaysRemaining) { this.vacationDaysRemaining = vacationDaysRemaining; }
    public int getSickDaysUsed() { return sickDaysUsed; }
    public void setSickDaysUsed(int sickDaysUsed) { this.sickDaysUsed = sickDaysUsed; }
    public int getSickDaysRemaining() { return sickDaysRemaining; }
    public void setSickDaysRemaining(int sickDaysRemaining) { this.sickDaysRemaining = sickDaysRemaining; }
    public boolean isOnLeave() { return onLeave; }
    public void setOnLeave(boolean onLeave) { this.onLeave = onLeave; }
    public Instant getLeaveStartDate() { return leaveStartDate; }
    public void setLeaveStartDate(Instant leaveStartDate) { this.leaveStartDate = leaveStartDate; }
    public Instant getLeaveEndDate() { return leaveEndDate; }
    public void setLeaveEndDate(Instant leaveEndDate) { this.leaveEndDate = leaveEndDate; }
}

class HRSyncResult {
    private boolean success;
    private String message;
    private int created;
    private int updated;
    private int deactivated;

    private HRSyncResult(boolean success, String message, int created, int updated, int deactivated) {
        this.success = success;
        this.message = message;
        this.created = created;
        this.updated = updated;
        this.deactivated = deactivated;
    }

    public static HRSyncResult success(int created, int updated, int deactivated) {
        return new HRSyncResult(true, "Synchronization successful", created, updated, deactivated);
    }

    public static HRSyncResult failed(String message) {
        return new HRSyncResult(false, message, 0, 0, 0);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public int getCreated() { return created; }
    public int getUpdated() { return updated; }
    public int getDeactivated() { return deactivated; }
}