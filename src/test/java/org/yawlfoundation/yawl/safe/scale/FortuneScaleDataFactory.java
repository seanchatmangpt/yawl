/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.safe.scale;

import org.yawlfoundation.yawl.safe.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Factory for generating realistic Fortune 5 SAFe test data at scale.
 *
 * Generates:
 * - Business units (5 fixed)
 * - Value streams (12 fixed)
 * - ARTs (variable, 6-7 teams each)
 * - Teams (variable capacity)
 * - User stories (with realistic dependencies)
 * - Themes and portfolio data
 */
public class FortuneScaleDataFactory {

    private final Random random = new Random(System.currentTimeMillis());
    private int storyCounter = 0;
    private int depCounter = 0;

    /**
     * Create 5 business units.
     */
    public List<BusinessUnit> createBusinessUnits(int count) {
        return List.of(
            new BusinessUnit("BU-1", "Enterprise", 100, null),
            new BusinessUnit("BU-2", "Platform", 80, null),
            new BusinessUnit("BU-3", "Healthcare", 120, null),
            new BusinessUnit("BU-4", "Finance", 90, null),
            new BusinessUnit("BU-5", "Cloud", 110, null)
        );
    }

    /**
     * Create 12 value streams distributed across 5 business units.
     */
    public List<ValueStream> createValueStreams(int count) {
        List<ValueStream> streams = new ArrayList<>();

        String[][] buVsMapping = {
            {"BU-1", "VS-1-1", "VS-1-2", "VS-1-3"},           // Enterprise: 3 value streams
            {"BU-2", "VS-2-1", "VS-2-2", "VS-2-3"},           // Platform: 3
            {"BU-3", "VS-3-1", "VS-3-2"},                     // Healthcare: 2
            {"BU-4", "VS-4-1", "VS-4-2"},                     // Finance: 2
            {"BU-5", "VS-5-1", "VS-5-2"}                      // Cloud: 2
        };

        for (String[] buVs : buVsMapping) {
            String buId = buVs[0];
            for (int i = 1; i < buVs.length; i++) {
                streams.add(new ValueStream(
                    buVs[i],
                    "Value Stream " + buVs[i],
                    new ArrayList<>(),
                    "Strategic focus for " + buId
                ));
            }
        }

        return streams;
    }

    /**
     * Create a business unit with realistic capacity.
     */
    public BusinessUnit createBusinessUnit(String name, int capacity) {
        return new BusinessUnit(
            "BU-" + name,
            name,
            capacity,
            new ArrayList<>()
        );
    }

    /**
     * Create a value stream.
     */
    public ValueStream createValueStream(String name, List<BusinessUnit> dus) {
        return new ValueStream(
            "VS-" + name,
            "Value Stream " + name,
            new ArrayList<>(),
            "Strategic focus"
        );
    }

    /**
     * Create an ART with N teams.
     */
    public ART createART(String id, int teamCount, ValueStream valueStream) {
        List<Team> teams = new ArrayList<>();
        int totalCapacity = 0;

        for (int i = 1; i <= teamCount; i++) {
            Team team = createTeam(id + "-Team-" + i, i);
            teams.add(team);
            totalCapacity += team.capacityPersonDays();
        }

        return new ART(
            id,
            "Agile Release Train " + id,
            teams,
            valueStream,
            totalCapacity,
            generateSkillSet()
        );
    }

    /**
     * Create a team.
     */
    private Team createTeam(String id, int index) {
        return new Team(
            id,
            "Team " + id,
            "SM-" + id,
            "PO-" + id,
            generateDeveloperIds(5 + random.nextInt(3)),  // 5-7 developers
            15 + random.nextInt(5),  // 15-20 person-days capacity
            generateSkillSet()
        );
    }

    /**
     * Create a user story.
     */
    public UserStory createUserStory(String id, String title, int points, List<String> dependencies) {
        return new UserStory(
            id,
            title,
            "Description for " + title,
            List.of("AC1: feature works", "AC2: tested", "AC3: documented"),
            points,
            random.nextInt(1, 4),  // Priority 1-3
            "BACKLOG",
            dependencies,
            null  // assignee assigned during planning
        );
    }

    /**
     * Generate stories with realistic dependencies.
     *
     * dependencyRate: percentage of stories that have cross-ART dependencies (0-100)
     */
    public List<UserStory> generateStoriesWithDependencies(int count, int dependencyRate) {
        List<UserStory> stories = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String storyId = "US-" + (++storyCounter);
            List<String> dependencies = new ArrayList<>();

            // Some stories depend on other stories
            if (random.nextInt(100) < dependencyRate && i > 0) {
                int depIndex = random.nextInt(i);  // Depend on earlier story
                dependencies.add("US-" + (storyCounter - i + depIndex));
            }

            UserStory story = createUserStory(
                storyId,
                "User Story " + storyId,
                3 + random.nextInt(5),  // 3-8 points
                dependencies
            );
            stories.add(story);
        }

        return stories;
    }

    /**
     * Generate cross-ART dependencies.
     *
     * @param arts List of ARTs
     * @param count Total number of dependencies to create
     */
    public List<Dependency> generateCrossARTDependencies(List<ART> arts, int count) {
        List<Dependency> dependencies = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String depId = "DEP-" + (++depCounter);

            // Random consumer and provider ARTs (must be different)
            ART consumer = arts.get(random.nextInt(arts.size()));
            ART provider;
            do {
                provider = arts.get(random.nextInt(arts.size()));
            } while (provider.id().equals(consumer.id()));

            Dependency dependency = new Dependency(
                depId,
                consumer.id(),
                provider.id(),
                "US-" + storyCounter,  // Reference some story
                "SUBMITTED",
                Instant.now(),
                null
            );
            dependencies.add(dependency);
        }

        return dependencies;
    }

    /**
     * Create portfolio themes.
     */
    public List<Theme> createThemes(String... names) {
        return Arrays.stream(names)
            .map(name -> new Theme(
                name,
                "Theme: " + name,
                50 + random.nextInt(100),  // 50-150 person-days demand
                random.nextInt(10)  // Arbitrary business value score
            ))
            .collect(Collectors.toList());
    }

    // ==================== PRIVATE HELPERS ====================

    private List<String> generateDeveloperIds(int count) {
        return IntStream.range(1, count + 1)
            .mapToObj(i -> "DEV-" + random.nextInt(1000))
            .collect(Collectors.toList());
    }

    private Set<String> generateSkillSet() {
        String[] allSkills = {
            "Java", "Python", "JavaScript", "React", "Spring",
            "Kubernetes", "AWS", "GCP", "Azure",
            "PostgreSQL", "MongoDB", "Redis",
            "Architecture", "DevOps", "Security", "ML/AI"
        };

        Set<String> skills = new HashSet<>();
        int skillCount = 3 + random.nextInt(5);  // 3-7 skills per team/ART
        for (int i = 0; i < skillCount; i++) {
            skills.add(allSkills[random.nextInt(allSkills.length)]);
        }
        return skills;
    }
}
