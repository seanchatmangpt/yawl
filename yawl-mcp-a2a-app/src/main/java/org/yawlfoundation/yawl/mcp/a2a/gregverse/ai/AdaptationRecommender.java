/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai;

import org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for recommending adaptations to therapy plans.
 */
public class AdaptationRecommender {

    /**
     * Recommends adaptations based on progress data.
     *
     * @param patientProfile the patient's profile
     * @param currentTherapy current therapy interventions
     * @param progressData the patient's progress data
     * @return adaptation recommendations
     */
    public AdaptationRecommendations recommendAdaptations(
            PatientProfile patientProfile,
            List<TherapyIntervention> currentTherapy,
            ProgressData progressData) {

        List<String> recommendedChanges = new ArrayList<>();
        Map<String, String> rationale = new HashMap<>();
        List<String> priorityChanges = new ArrayList<>();
        Map<String, Double> expectedImpact = new HashMap<>();

        // Analyze progress and generate recommendations
        if (progressData.overallProgress() < 30) {
            recommendedChanges.add("Consider increasing session frequency");
            rationale.put("Consider increasing session frequency",
                "Progress below 30% indicates need for more intensive intervention");
            priorityChanges.add("Review current intervention approach");
            expectedImpact.put("Consider increasing session frequency", 0.7);
        }

        if (progressData.challenges().size() > 3) {
            recommendedChanges.add("Address multiple barriers simultaneously");
            rationale.put("Address multiple barriers simultaneously",
                "Multiple challenges indicate need for comprehensive approach");
            priorityChanges.add("Prioritize barrier resolution");
            expectedImpact.put("Address multiple barriers simultaneously", 0.6);
        }

        if (progressData.overallProgress() > 70) {
            recommendedChanges.add("Begin transition to maintenance phase");
            rationale.put("Begin transition to maintenance phase",
                "Progress above 70% suggests readiness for reduced frequency");
            expectedImpact.put("Begin transition to maintenance phase", 0.8);
        }

        return new AdaptationRecommendations(
            patientProfile.patientId(),
            recommendedChanges,
            rationale,
            priorityChanges,
            expectedImpact,
            Instant.now()
        );
    }
}
