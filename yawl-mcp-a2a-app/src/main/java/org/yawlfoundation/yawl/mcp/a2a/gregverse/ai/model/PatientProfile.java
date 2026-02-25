/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Patient profile containing clinical and demographic information.
 *
 * @param patientId unique patient identifier
 * @param firstName patient's first name
 * @param lastName patient's last name
 * @param dateOfBirth patient's date of birth
 * @param gender patient's gender
 * @param primaryCondition primary diagnosis or condition
 * @param secondaryConditions secondary conditions
 * @param functionalLevel functional ability assessment
 * @param mobilityLevel mobility assessment
 * @param cognitiveStatus cognitive status assessment
 * @param previousTherapy history of previous therapy
 * @param comorbidities medical comorbidities
 * @param medications current medications
 * @param allergies known allergies
 * @param emergencyContact emergency contact information
 * @param insuranceInsurance insurance information
 */
public record PatientProfile(
    String patientId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Gender gender,
    String primaryCondition,
    List<String> secondaryConditions,
    FunctionalLevel functionalLevel,
    MobilityLevel mobilityLevel,
    CognitiveStatus cognitiveStatus,
    String previousTherapy,
    Map<String, String> comorbidities,
    Map<String, String> medications,
    List<String> allergies,
    ContactInfo emergencyContact,
    String insuranceInsurance
) {

    /**
     * Creates a new patient profile with required fields.
     */
    public PatientProfile {
        Objects.requireNonNull(patientId, "Patient ID cannot be null");
        Objects.requireNonNull(firstName, "First name cannot be null");
        Objects.requireNonNull(lastName, "Last name cannot be null");
        Objects.requireNonNull(dateOfBirth, "Date of birth cannot be null");
        Objects.requireNonNull(gender, "Gender cannot be null");
        Objects.requireNonNull(primaryCondition, "Primary condition cannot be null");
    }

    /**
     * Gets patient's age in years.
     */
    public int getAge() {
        return LocalDate.now().getYear() - dateOfBirth.getYear();
    }

    /**
     * Gets patient's full name.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Checks if patient has any comorbidities.
     */
    public boolean hasComorbidities() {
        return comorbidities != null && !comorbidities.isEmpty();
    }

    /**
     * Checks if patient is on any medications.
     */
    public boolean hasMedications() {
        return medications != null && !medications.isEmpty();
    }

    /**
     * Checks if patient has any allergies.
     */
    public boolean hasAllergies() {
        return allergies != null && !allergies.isEmpty();
    }
}