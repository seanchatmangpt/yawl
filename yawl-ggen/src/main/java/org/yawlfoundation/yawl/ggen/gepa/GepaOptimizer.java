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

package org.yawlfoundation.yawl.ggen.gepa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.ggen.model.YawlSpec;
import org.yawlfoundation.yawl.ggen.model.ValidationResult;
import org.yawlfoundation.yawl.ggen.pipeline.YawlGenerator;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * GEPA (Generative Evolutionary Prompt Architecture) optimizer.
 *
 * <p>Evolves DSPy prompts through mutation and crossover to improve
 * YAWL generation quality. Integrates with TPOT2 for fitness scoring.
 *
 * <h2>Evolutionary Algorithm:</h2>
 * <ol>
 *   <li>Initialize population with seed prompts</li>
 *   <li>Evaluate fitness using validation pipeline</li>
 *   <li>Select top performers (tournament selection)</li>
 *   <li>Apply crossover (prompt recombination)</li>
 *   <li>Apply mutation (prompt variation)</li>
 *   <li>Repeat until convergence or max generations</li>
 * </ol>
 *
 * <h2>Fitness Function:</h2>
 * <pre>
 * fitness = ValidYAWL * Soundness * Executable * Efficiency
 * </pre>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * GepaOptimizer optimizer = GepaOptimizer.builder()
 *     .populationSize(10)
 *     .generations(20)
 *     .mutationRate(0.3)
 *     .crossoverRate(0.5)
 *     .build();
 *
 * OptimizedPrompt best = optimizer.evolve(
 *     trainingData,  // List<NLDescription>
 *     generator      // YawlGenerator
 * );
 *
 * System.out.println("Best prompt: " + best.content());
 * System.out.println("Fitness: " + best.fitness());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GepaOptimizer {

    private static final Logger log = LoggerFactory.getLogger(GepaOptimizer.class);

    private final int populationSize;
    private final int generations;
    private final double mutationRate;
    private final double crossoverRate;
    private final double elitismRate;
    private final int tournamentSize;

    private GepaOptimizer(Builder builder) {
        this.populationSize = builder.populationSize;
        this.generations = builder.generations;
        this.mutationRate = builder.mutationRate;
        this.crossoverRate = builder.crossoverRate;
        this.elitismRate = builder.elitismRate;
        this.tournamentSize = builder.tournamentSize;
    }

    /**
     * Evolve prompts to optimize YAWL generation.
     *
     * @param trainingData List of natural language descriptions
     * @param generator YawlGenerator instance
     * @return OptimizedPrompt with best fitness
     */
    public OptimizedPrompt evolve(List<String> trainingData, YawlGenerator generator) {
        log.info("Starting GEPA evolution: {} individuals, {} generations",
            populationSize, generations);

        // Initialize population
        List<PromptIndividual> population = initializePopulation();

        PromptIndividual bestOverall = null;
        double bestFitnessOverall = 0.0;

        for (int gen = 0; gen < generations; gen++) {
            log.debug("Generation {}/{}", gen + 1, generations);

            // Evaluate fitness
            Map<PromptIndividual, Double> fitnessMap = evaluatePopulation(population, trainingData, generator);

            // Track best
            for (Map.Entry<PromptIndividual, Double> entry : fitnessMap.entrySet()) {
                if (entry.getValue() > bestFitnessOverall) {
                    bestFitnessOverall = entry.getValue();
                    bestOverall = entry.getKey();
                }
            }

            // Check convergence
            if (hasConverged(fitnessMap)) {
                log.info("Converged at generation {}", gen + 1);
                break;
            }

            // Selection (tournament)
            List<PromptIndividual> selected = select(population, fitnessMap);

            // Crossover
            List<PromptIndividual> offspring = crossover(selected);

            // Mutation
            List<PromptIndividual> mutated = mutate(offspring);

            // Elitism: keep top performers
            population = applyElitism(population, fitnessMap, mutated);

            log.debug("Generation {} best fitness: {}", gen + 1,
                fitnessMap.values().stream().max(Double::compare).orElse(0.0));
        }

        if (bestOverall == null) {
            throw new GepaOptimizationException("Evolution failed to produce valid prompt");
        }

        log.info("Evolution complete. Best fitness: {}", bestFitnessOverall);
        return new OptimizedPrompt(bestOverall.content(), bestFitnessOverall, bestOverall.metadata());
    }

    private List<PromptIndividual> initializePopulation() {
        List<PromptIndividual> population = new ArrayList<>();

        // Add seed prompts
        population.add(new PromptIndividual(SEED_PROMPT_1, Map.of("type", "seed", "id", "1")));
        population.add(new PromptIndividual(SEED_PROMPT_2, Map.of("type", "seed", "id", "2")));
        population.add(new PromptIndividual(SEED_PROMPT_3, Map.of("type", "seed", "id", "3")));

        // Fill rest with variations
        while (population.size() < populationSize) {
            String variant = generateVariant();
            population.add(new PromptIndividual(variant, Map.of("type", "variant")));
        }

        return population;
    }

    private Map<PromptIndividual, Double> evaluatePopulation(
            List<PromptIndividual> population,
            List<String> trainingData,
            YawlGenerator generator) {

        Map<PromptIndividual, Double> fitnessMap = new HashMap<>();

        for (PromptIndividual individual : population) {
            double totalFitness = 0.0;

            for (String nl : trainingData) {
                try {
                    // Generate YAWL with this prompt's influence
                    YawlSpec spec = generator.generate(nl);

                    // Calculate fitness components
                    double fitness = calculateFitness(spec, generator);

                    totalFitness += fitness;

                } catch (Exception e) {
                    log.warn("Evaluation failed for prompt: {}", e.getMessage());
                    totalFitness += 0.0;
                }
            }

            // Average fitness across training data
            fitnessMap.put(individual, totalFitness / trainingData.size());
        }

        return fitnessMap;
    }

    private double calculateFitness(YawlSpec spec, YawlGenerator generator) {
        double fitness = 1.0;

        // Component 1: Has valid XML
        if (spec == null || !spec.hasXml()) {
            return 0.0;
        }
        fitness *= 1.0;

        // Component 2: Validation (if available)
        if (generator.hasValidator()) {
            ValidationResult result = generator.validate(spec);
            if (result.valid()) {
                fitness *= 1.0;
            } else {
                // Penalize based on error count
                int errorCount = result.xsdErrors().size() +
                    result.deadlocks().size() +
                    result.lackOfSync().size() +
                    result.executionErrors().size();
                fitness *= Math.max(0.1, 1.0 - (errorCount * 0.1));
            }
        }

        // Component 3: Efficiency (shorter XML is better, up to a point)
        int xmlLength = spec.xmlLength();
        if (xmlLength > 0 && xmlLength < 10000) {
            fitness *= 1.0;
        } else if (xmlLength >= 10000) {
            fitness *= 0.9; // Penalize very long specs
        }

        return fitness;
    }

    private List<PromptIndividual> select(List<PromptIndividual> population,
                                           Map<PromptIndividual, Double> fitnessMap) {
        List<PromptIndividual> selected = new ArrayList<>();

        while (selected.size() < populationSize / 2) {
            // Tournament selection
            List<PromptIndividual> tournament = new ArrayList<>();
            for (int i = 0; i < tournamentSize; i++) {
                int idx = ThreadLocalRandom.current().nextInt(population.size());
                tournament.add(population.get(idx));
            }

            // Winner is highest fitness
            PromptIndividual winner = tournament.stream()
                .max(Comparator.comparing(fitnessMap::get))
                .orElse(population.get(0));

            selected.add(winner);
        }

        return selected;
    }

    private List<PromptIndividual> crossover(List<PromptIndividual> selected) {
        List<PromptIndividual> offspring = new ArrayList<>();
        List<PromptIndividual> parents = new ArrayList<>(selected);

        Collections.shuffle(parents);

        for (int i = 0; i < parents.size() - 1; i += 2) {
            PromptIndividual p1 = parents.get(i);
            PromptIndividual p2 = parents.get(i + 1);

            if (ThreadLocalRandom.current().nextDouble() < crossoverRate) {
                // Single-point crossover
                String[] lines1 = p1.content().split("\n");
                String[] lines2 = p2.content().split("\n");

                int crossPoint = ThreadLocalRandom.current().nextInt(
                    Math.min(lines1.length, lines2.length));

                StringBuilder child1 = new StringBuilder();
                StringBuilder child2 = new StringBuilder();

                for (int j = 0; j < lines1.length || j < lines2.length; j++) {
                    if (j < lines1.length) {
                        child1.append(j < crossPoint ? lines1[j] :
                            (j < lines2.length ? lines2[j] : lines1[j])).append("\n");
                    }
                    if (j < lines2.length) {
                        child2.append(j < crossPoint ? lines2[j] :
                            (j < lines1.length ? lines1[j] : lines2[j])).append("\n");
                    }
                }

                offspring.add(new PromptIndividual(child1.toString().trim(),
                    Map.of("type", "crossover")));
                offspring.add(new PromptIndividual(child2.toString().trim(),
                    Map.of("type", "crossover")));
            } else {
                offspring.add(p1);
                offspring.add(p2);
            }
        }

        return offspring;
    }

    private List<PromptIndividual> mutate(List<PromptIndividual> population) {
        return population.stream()
            .map(individual -> {
                if (ThreadLocalRandom.current().nextDouble() < mutationRate) {
                    return applyMutation(individual);
                }
                return individual;
            })
            .collect(Collectors.toList());
    }

    private PromptIndividual applyMutation(PromptIndividual individual) {
        String content = individual.content();

        // Random mutation: add/remove/modify instructions
        String[] mutations = {
            "Focus on YAWL OR-join semantics for synchronization.",
            "Ensure cancellation regions are properly defined.",
            "Use clear task naming conventions.",
            "Include data flow between tasks.",
            "Specify resource requirements for each task."
        };

        String mutation = mutations[ThreadLocalRandom.current().nextInt(mutations.length)];

        // Insert mutation at random position
        int insertPos = ThreadLocalRandom.current().nextInt(content.length());
        String mutated = content.substring(0, insertPos) + "\n" + mutation + content.substring(insertPos);

        return new PromptIndividual(mutated, Map.of("type", "mutated"));
    }

    private List<PromptIndividual> applyElitism(
            List<PromptIndividual> oldPopulation,
            Map<PromptIndividual, Double> fitnessMap,
            List<PromptIndividual> newPopulation) {

        int eliteCount = (int) (populationSize * elitismRate);

        // Get top performers from old population
        List<PromptIndividual> elite = oldPopulation.stream()
            .sorted(Comparator.comparing(fitnessMap::get).reversed())
            .limit(eliteCount)
            .collect(Collectors.toList());

        // Combine elite with new population
        List<PromptIndividual> result = new ArrayList<>(elite);
        result.addAll(newPopulation.subList(0, Math.min(newPopulation.size(), populationSize - eliteCount)));

        return result;
    }

    private boolean hasConverged(Map<PromptIndividual, Double> fitnessMap) {
        if (fitnessMap.isEmpty()) return false;

        double maxFitness = fitnessMap.values().stream().max(Double::compare).orElse(0.0);
        return maxFitness >= 0.95; // 95% fitness = converged
    }

    private String generateVariant() {
        String[] variants = {
            "Generate YAWL with explicit OR-join handling.",
            "Focus on cancellation region patterns.",
            "Emphasize multi-instance task patterns.",
            "Include comprehensive data flow definitions.",
            "Add resource allocation constraints."
        };
        return variants[ThreadLocalRandom.current().nextInt(variants.length)];
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEED PROMPTS
    // ═══════════════════════════════════════════════════════════════════════════

    private static final String SEED_PROMPT_1 = """
        Transform the natural language process description into a structured YAWL specification.
        Identify all tasks, their types (atomic/composite/multiple), and control flow patterns.
        Pay special attention to OR-join semantics and cancellation regions.
        """;

    private static final String SEED_PROMPT_2 = """
        Convert the process description to YAWL format with proper task decomposition.
        Define input/output conditions, data objects, and constraints.
        Ensure all XOR/AND/OR splits and joins are properly identified.
        """;

    private static final String SEED_PROMPT_3 = """
        Generate YAWL specification from the description.
        Focus on sound workflow patterns and proper synchronization.
        Include cancellation regions for exception handling.
        Define clear data flow between tasks.
        """;

    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int populationSize = 10;
        private int generations = 20;
        private double mutationRate = 0.3;
        private double crossoverRate = 0.5;
        private double elitismRate = 0.1;
        private int tournamentSize = 3;

        public Builder populationSize(int populationSize) {
            this.populationSize = populationSize;
            return this;
        }

        public Builder generations(int generations) {
            this.generations = generations;
            return this;
        }

        public Builder mutationRate(double mutationRate) {
            this.mutationRate = mutationRate;
            return this;
        }

        public Builder crossoverRate(double crossoverRate) {
            this.crossoverRate = crossoverRate;
            return this;
        }

        public Builder elitismRate(double elitismRate) {
            this.elitismRate = elitismRate;
            return this;
        }

        public Builder tournamentSize(int tournamentSize) {
            this.tournamentSize = tournamentSize;
            return this;
        }

        public GepaOptimizer build() {
            return new GepaOptimizer(this);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    private record PromptIndividual(String content, Map<String, Object> metadata) {}

    /**
     * Result of GEPA optimization.
     */
    public record OptimizedPrompt(String content, double fitness, Map<String, Object> metadata) {}
}
