package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.model.GuardReceipt;
import org.yawlfoundation.yawl.ggen.model.GuardViolation;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * HyperStandardsValidator - Orchestrates all guard pattern checks
 *
 * This validator implements the H (Guards) phase of the hyper-standards validation pipeline.
 * It coordinates 7 guard checkers to detect forbidden patterns in generated code:
 * - H_TODO: Deferred work markers
 * - H_MOCK: Mock implementations
 * - H_STUB: Empty/placeholder returns
 * - H_EMPTY: No-op method bodies
 * - H_FALLBACK: Silent catch-and-fake
 * - H_LIE: Code ≠ documentation
 * - H_SILENT: Log instead of throw
 */
public class HyperStandardsValidator {

    private final List<GuardChecker> checkers;
    private final ExecutorService executor;
    private final int maxThreads;

    /**
     * Constructor that initializes all guard checkers
     */
    public HyperStandardsValidator() {
        this.maxThreads = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(maxThreads);
        this.checkers = initializeCheckers();
    }

    /**
     * Constructor with custom thread pool size
     */
    public HyperStandardsValidator(int maxThreads) {
        this.maxThreads = maxThreads;
        this.executor = Executors.newFixedThreadPool(maxThreads);
        this.checkers = initializeCheckers();
    }

    /**
     * Initialize all guard checkers
     */
    private List<GuardChecker> initializeCheckers() {
        List<GuardChecker> checkers = new ArrayList<>();

        // Regex-based checkers (fast scanning)
        checkers.add(new RegexGuardChecker(
            "H_TODO",
            "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)"
        ));

        checkers.add(new RegexGuardChecker(
            "H_MOCK",
            "(mock|stub|fake|demo)[A-Z][a-zA-Z]*\\s*[=(]"
        ));

        checkers.add(new RegexGuardChecker(
            "H_SILENT",
            "log\\.(warn|error)\\([^)]*\"[^\"]*not\\s+implemented"
        ));

        // SPARQL-based checkers (semantic analysis)
        try {
            // Load SPARQL queries from resources
            String hStubQuery = loadSparqlQuery("guards-h-stub.sparql");
            String hEmptyQuery = loadSparqlQuery("guards-h-empty.sparql");
            String hFallbackQuery = loadSparqlQuery("guards-h-fallback.sparql");
            String hLieQuery = loadSparqlQuery("guards-h-lie.sparql");

            checkers.add(new SparqlGuardChecker("H_STUB", hStubQuery));
            checkers.add(new SparqlGuardChecker("H_EMPTY", hEmptyQuery));
            checkers.add(new SparqlGuardChecker("H_FALLBACK", hFallbackQuery));
            checkers.add(new SparqlGuardChecker("H_LIE", hLieQuery));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load SPARQL queries", e);
        }

        return Collections.unmodifiableList(checkers);
    }

    /**
     * Load SPARQL query from resources
     */
    private String loadSparqlQuery(String filename) throws IOException {
        Path queryPath = Paths.get("src/main/resources/sparql", filename);
        return Files.readString(queryPath);
    }

    /**
     * Main validation method for entire emit directory
     */
    public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
        return validateEmitDir(emitDir, null);
    }

    /**
     * Main validation method with progress callback
     */
    public GuardReceipt validateEmitDir(Path emitDir, Consumer<Progress> progressCallback) throws IOException {
        GuardReceipt receipt = GuardReceipt.builder()
            .phase("guards")
            .timestamp(Instant.now())
            .build();

        // Find all Java files
        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(emitDir)) {
            javaFiles = stream
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !Files.isDirectory(p))
                .toList();
        }

        receipt.setFilesScanned(javaFiles.size());

        if (progressCallback != null) {
            progressCallback.accept(new Progress(0, javaFiles.size(), "Scanning files..."));
        }

        // Process files in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger processedFiles = new AtomicInteger(0);

        for (Path javaFile : javaFiles) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    validateFile(javaFile);
                    int current = processedFiles.incrementAndGet();
                    if (progressCallback != null) {
                        progressCallback.accept(new Progress(
                            current,
                            javaFiles.size(),
                            "Validating " + javaFile.getFileName()
                        ));
                    }
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, executor));
        }

        // Wait for all files to be processed
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Validation failed", e);
        }

        // Build final status
        receipt.buildStatus();
        return receipt;
    }

    /**
     * Validate a single Java file
     */
    public GuardReceipt validateFile(Path javaFile) throws IOException {
        GuardReceipt fileReceipt = GuardReceipt.builder()
            .phase("guards")
            .timestamp(Instant.now())
            .build();

        for (GuardChecker checker : checkers) {
            try {
                List<GuardViolation> violations = checker.check(javaFile);
                for (GuardViolation violation : violations) {
                    violation.setFile(javaFile.toString());
                    fileReceipt.addViolation(violation);
                }
            } catch (IOException e) {
                // Add system violation for checker failure
                GuardViolation systemViolation = GuardViolation.builder()
                    .pattern("H_SYSTEM")
                    .severity(GuardViolation.Severity.FAIL)
                    .file(javaFile.toString())
                    .line(0)
                    .content("Failed to run " + checker.patternName() + " checker: " + e.getMessage())
                    .build();
                fileReceipt.addViolation(systemViolation);
            }
        }

        fileReceipt.buildStatus();
        return fileReceipt;
    }

    /**
     * Shutdown the validator and release resources
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Progress tracking for CLI integration
     */
    public static class Progress {
        private final int current;
        private final int total;
        private final String message;

        public Progress(int current, int total, String message) {
            this.current = current;
            this.total = total;
            this.message = message;
        }

        public int getCurrent() { return current; }
        public int getTotal() { return total; }
        public String getMessage() { return message; }
        public double getPercentage() {
            return total > 0 ? (double) current / total * 100 : 0;
        }
    }

    /**
     * Get statistics about the validator
     */
    public ValidatorStats getStats() {
        return new ValidatorStats(
            checkers.size(),
            maxThreads,
            checkers.stream()
                .mapToInt(c -> c instanceof RegexGuardChecker ? 1 : 0)
                .sum(),
            checkers.size() - (checkers.stream()
                .mapToInt(c -> c instanceof RegexGuardChecker ? 1 : 0)
                .sum())
        );
    }

    /**
     * Validator statistics
     */
    public static class ValidatorStats {
        private final int totalCheckers;
        private final int maxThreads;
        private final int regexCheckers;
        private final int sparqlCheckers;

        public ValidatorStats(int totalCheckers, int maxThreads, int regexCheckers, int sparqlCheckers) {
            this.totalCheckers = totalCheckers;
            this.maxThreads = maxThreads;
            this.regexCheckers = regexCheckers;
            this.sparqlCheckers = sparqlCheckers;
        }

        // Getters
        public int getTotalCheckers() { return totalCheckers; }
        public int getMaxThreads() { return maxThreads; }
        public int getRegexCheckers() { return regexCheckers; }
        public int getSparqlCheckers() { return sparqlCheckers; }
    }
}