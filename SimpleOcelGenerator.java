import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

/**
 * Simple OCEL 2.0 generator for testing
 */
public class SimpleOcelGenerator {

    public static void main(String[] args) throws IOException {
        // Ensure sim-output directory exists
        Path outputDir = Paths.get("sim-output");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // Generate Sprint OCEL
        generateSprintOcel();

        // Generate PI OCEL
        generatePIOcel();

        // Generate Portfolio Sync OCEL
        generatePortfolioSyncOcel();

        // Generate Self-Assessment OCEL
        generateSelfAssessmentOcel();

        System.out.println("All OCEL files generated successfully!");
    }

    private static void generateSprintOcel() throws IOException {
        String ocel = """
        {
          "ocel:version": "2.0",
          "ocel:ordering": "timestamp",
          "ocel:attribute-names": ["org:resource"],
          "ocel:object-types": ["Feature"],
          "ocel:objects": {
            "FeatureA": {
              "ocel:type": "Feature",
              "ocel:ovmap": {
                "feature:id": "FeatureA"
              }
            }
          },
          "ocel:events": {
            "evt-sprint-1-start": {
              "ocel:activity": "sprint_started",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Feature": ["FeatureA"]
              },
              "ocel:vmap": {
                "org:resource": "TeamAlpha"
              }
            },
            "evt-sprint-1-story1": {
              "ocel:activity": "story_completed",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Feature": ["FeatureA"]
              },
              "ocel:vmap": {
                "org:resource": "TeamAlpha",
                "story:points": 5
              }
            },
            "evt-sprint-1-story2": {
              "ocel:activity": "story_completed",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Feature": ["FeatureA"]
              },
              "ocel:vmap": {
                "org:resource": "TeamAlpha",
                "story:points": 3
              }
            },
            "evt-sprint-1-end": {
              "ocel:activity": "sprint_completed",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Feature": ["FeatureA"]
              },
              "ocel:vmap": {
                "org:resource": "TeamAlpha",
                "sprint:velocity": 8
              }
            }
          }
        }
        """;

        // Generate with different timestamps
        String now = Instant.now().toString();
        String formatted = String.format(ocel, now, now, now, now);

        Path sprintFile = Paths.get("sim-output", "sprint-1-FeatureA.json");
        try (FileWriter writer = new FileWriter(sprintFile.toFile())) {
            writer.write(formatted);
        }
        System.out.println("Generated Sprint OCEL: " + sprintFile);
    }

    private static void generatePIOcel() throws IOException {
        StringBuilder ocel = new StringBuilder();
        ocel.append("{\n");
        ocel.append("  \"ocel:version\": \"2.0\",\n");
        ocel.append("  \"ocel:ordering\": \"timestamp\",\n");
        ocel.append("  \"ocel:attribute-names\": [\"org:resource\"],\n");
        ocel.append("  \"ocel:object-types\": [\"Feature\", \"Team\", \"PI\", \"ART\"],\n");
        ocel.append("  \"ocel:objects\": {\n");

        // Add objects
        String[] features = {"PI1-Feature1", "PI1-Feature2", "PI1-Feature3", "PI1-Feature4"};
        for (String feature : features) {
            ocel.append(String.format(
                "    \"%s\": {\n      \"ocel:type\": \"Feature\",\n      \"ocel:ovmap\": {\n        \"feature:id\": \"%s\"\n      }\n    },\n",
                feature, feature
            ));
        }

        ocel.append("    \"PI1\": {\n      \"ocel:type\": \"PI\",\n      \"ocel:ovmap\": {\n        \"pi:id\": \"PI1\"\n      }\n    },\n");
        ocel.append("    \"DefaultART\": {\n      \"ocel:type\": \"ART\",\n      \"ocel:ovmap\": {\n        \"art:id\": \"DefaultART\"\n      }\n    }\n");
        ocel.append("  },\n");
        ocel.append("  \"ocel:events\": {\n");

        // Generate events for 4 sprints
        for (int sprint = 1; sprint <= 4; sprint++) {
            String sprintStart = String.format("\"evt-pi1-s%d-start\"", sprint);
            String sprintEnd = String.format("\"evt-pi1-s%d-end\"", sprint);

            ocel.append(String.format(
                "    %s: {\n      \"ocel:activity\": \"sprint_started\",\n      \"ocel:timestamp\": \"%s\",\n      \"ocel:omap\": {\n        \"Feature\": [\"PI1-Feature%d\"],\n        \"Team\": [\"DefaultART\"]\n      },\n      \"ocel:vmap\": {\n        \"org:resource\": \"DefaultART\"\n      }\n    },\n",
                sprintStart, Instant.now(), sprint
            ));

            ocel.append(String.format(
                "    %s: {\n      \"ocel:activity\": \"sprint_completed\",\n      \"ocel:timestamp\": \"%s\",\n      \"ocel:omap\": {\n        \"Feature\": [\"PI1-Feature%d\"],\n        \"Team\": [\"DefaultART\"]\n      },\n      \"ocel:vmap\": {\n        \"org:resource\": \"DefaultART\",\n        \"sprint:velocity\": %d\n      }\n    },\n",
                sprintEnd, Instant.now(), sprint, sprint * 3 + (int)(Math.random() * 5)
            ));
        }

        // Add PI events
        ocel.append("    \"evt-pi1-planning\": {\n");
        ocel.append("      \"ocel:activity\": \"pi_planning\",\n");
        ocel.append("      \"ocel:timestamp\": \"" + Instant.now() + "\",\n");
        ocel.append("      \"ocel:omap\": {\n");
        ocel.append("        \"PI\": [\"PI1\"]\n");
        ocel.append("      },\n");
        ocel.append("      \"ocel:vmap\": {\n");
        ocel.append("        \"org:resource\": \"PIPlanner\"\n");
        ocel.append("      }\n");
        ocel.append("    },\n");

        ocel.append("    \"evt-pi1-inspect-adapt\": {\n");
        ocel.append("      \"ocel:activity\": \"inspect_adapt\",\n");
        ocel.append("      \"ocel:timestamp\": \"" + Instant.now() + "\",\n");
        ocel.append("      \"ocel:omap\": {\n");
        ocel.append("        \"PI\": [\"PI1\"]\n");
        ocel.append("      },\n");
        ocel.append("      \"ocel:vmap\": {\n");
        ocel.append("        \"org:resource\": \"PIPlanner\"\n");
        ocel.append("      }\n");
        ocel.append("    }\n");
        ocel.append("  }\n");
        ocel.append("}");

        Path piFile = Paths.get("sim-output", "pi-1.json");
        try (FileWriter writer = new FileWriter(piFile.toFile())) {
            writer.write(ocel.toString());
        }
        System.out.println("Generated PI OCEL: " + piFile);
    }

    private static void generatePortfolioSyncOcel() throws IOException {
        String ocel = """
        {
          "ocel:version": "2.0",
          "ocel:ordering": "timestamp",
          "ocel:attribute-names": ["org:resource"],
          "ocel:object-types": ["Portfolio"],
          "ocel:objects": {
            "Portfolio1": {
              "ocel:type": "Portfolio",
              "ocel:ovmap": {
                "portfolio:id": "Portfolio1"
              }
            }
          },
          "ocel:events": {
            "evt-portfolio-sync-start": {
              "ocel:activity": "portfolio_sync_started",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Portfolio": ["Portfolio1"]
              },
              "ocel:vmap": {
                "org:resource": "PortfolioManager"
              }
            },
            "evt-epic1-ranking": {
              "ocel:activity": "wsjf_ranking",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Portfolio": ["Portfolio1"]
              },
              "ocel:vmap": {
                "org:resource": "PortfolioManager",
                "epic:id": "Epic1",
                "wsjf:score": 85
              }
            },
            "evt-epic2-ranking": {
              "ocel:activity": "wsjf_ranking",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Portfolio": ["Portfolio1"]
              },
              "ocel:vmap": {
                "org:resource": "PortfolioManager",
                "epic:id": "Epic2",
                "wsjf:score": 72
              }
            },
            "evt-portfolio-sync-complete": {
              "ocel:activity": "portfolio_sync_completed",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Portfolio": ["Portfolio1"]
              },
              "ocel:vmap": {
                "org:resource": "PortfolioManager"
              }
            }
          }
        }
        """;

        String now = Instant.now().toString();
        String formatted = String.format(ocel, now, now, now, now);

        Path portfolioFile = Paths.get("sim-output", "portfoliosync.json");
        try (FileWriter writer = new FileWriter(portfolioFile.toFile())) {
            writer.write(formatted);
        }
        System.out.println("Generated Portfolio Sync OCEL: " + portfolioFile);
    }

    private static void generateSelfAssessmentOcel() throws IOException {
        String ocel = """
        {
          "ocel:version": "2.0",
          "ocel:ordering": "timestamp",
          "ocel:attribute-names": ["org:resource"],
          "ocel:object-types": ["Assessment"],
          "ocel:objects": {
            "Assessment1": {
              "ocel:type": "Assessment",
              "ocel:ovmap": {
                "assessment:id": "Assessment1"
              }
            }
          },
          "ocel:events": {
            "evt-assessment-started": {
              "ocel:activity": "assessment_started",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Assessment": ["Assessment1"]
              },
              "ocel:vmap": {
                "org:resource": "SelfAssessmentAgent"
              }
            },
            "evt-gap-discovery": {
              "ocel:activity": "gap_discovery",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Assessment": ["Assessment1"]
              },
              "ocel:vmap": {
                "org:resource": "SelfAssessmentAgent"
              }
            },
            "evt-construct-query": {
              "ocel:activity": "construct_query_run",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Assessment": ["Assessment1"]
              },
              "ocel:vmap": {
                "org:resource": "SelfAssessmentAgent",
                "query:complexity": "high"
              }
            },
            "evt-gap-discovered": {
              "ocel:activity": "gap_discovered",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Assessment": ["Assessment1"]
              },
              "ocel:vmap": {
                "org:resource": "SelfAssessmentAgent",
                "gap:count": 3
              }
            },
            "evt-gap-closed": {
              "ocel:activity": "gap_closed",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Assessment": ["Assessment1"]
              },
              "ocel:vmap": {
                "org:resource": "SelfAssessmentAgent",
                "gap:closed": 1
              }
            },
            "evt-conformance-updated": {
              "ocel:activity": "conformance_updated",
              "ocel:timestamp": "%s",
              "ocel:omap": {
                "Assessment": ["Assessment1"]
              },
              "ocel:vmap": {
                "org:resource": "SelfAssessmentAgent",
                "conformance:score": 0.92
              }
            }
          }
        }
        """;

        String now = Instant.now().toString();
        String formatted = String.format(ocel, now, now, now, now, now, now);

        Path assessmentFile = Paths.get("sim-output", "selfassessment.json");
        try (FileWriter writer = new FileWriter(assessmentFile.toFile())) {
            writer.write(formatted);
        }
        System.out.println("Generated Self-Assessment OCEL: " + assessmentFile);
    }
}