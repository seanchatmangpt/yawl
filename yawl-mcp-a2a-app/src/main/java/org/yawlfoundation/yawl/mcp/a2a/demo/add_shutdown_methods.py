#!/usr/bin/env python3

import re

# Read the file
with open('PatternDemoRunner.java', 'r') as f:
    content = f.read()

# Find the shutdown method and add our methods before it
shutdown_pattern = r'(    \/\*\*\n     \* Shutdown engine and resources\.\n     \*\/\n    private void shutdown\(\) \{)'
replacement = '''    /**
     * Register shutdown hook for graceful shutdown.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownRequested = true;
            LOGGER.info("Shutdown requested. Completed {}/{} patterns.",
                completedPatterns.get(), totalPatterns);

            // Wait briefly for in-flight patterns
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Generate partial report
            if (completedPatterns.get() > 0) {
                LOGGER.info("Generating partial report for {} completed patterns", completedPatterns.get());
                generatePartialReport();
            }
        }, "demo-shutdown-hook"));
    }

    /**
     * Generate partial report when shutdown occurs during execution.
     */
    private void generatePartialReport() {
        try {
            Path outputPath = Path.of(config.outputPath() + "-partial.json");
            String partialReport = buildReportJson(null, completedPatterns.get(), totalPatterns);
            Files.writeString(outputPath, partialReport);
            LOGGER.info("Partial report written to {}", outputPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write partial report", e);
        }
    }

    /**
     * Build JSON report for partial results.
     */
    private String buildReportJson(List<PatternResult> results, int completed, int total) {
        return String.format(
            "{\"partial\":true,\"completed\":%d,\"total\":%d,\"timestamp\":\"%s\"}",
            completed, total, java.time.Instant.now().toString()
        );
    }

    \1'''

# Replace the content
new_content = re.sub(shutdown_pattern, replacement, content)

# Also update the sequential execution method
seq_pattern = r'(  private List<PatternResult> executePatternsSequential\(List<PatternInfo> patterns\) \{\n        List<PatternResult> results = new ArrayList<>;\n        int total = patterns\.size\(\);)'
seq_replacement = '''  private List<PatternResult> executePatternsSequential(List<PatternInfo> patterns) {
        List<PatternResult> results = new ArrayList<>();
        int total = patterns.size();
        this.totalPatterns = total;

        // Check for shutdown request before starting
        if (shutdownRequested) {
            LOGGER.warn("Shutdown requested, skipping pattern execution");
            return List.of();
        }

        for (int i = 0; i < patterns.size(); i++) {
            // Check for shutdown request in each iteration
            if (shutdownRequested) {
                LOGGER.warn("Shutdown requested, skipping remaining patterns");
                generatePartialReport();
                break;
            }

            PatternInfo pattern = patterns.get(i);
            PatternResult result = executePattern(pattern);
            results.add(result);
            completedPatterns.incrementAndGet();
            printProgress(i + 1, total, pattern.id(), result.isSuccess(), result.getDuration());
        }

        return results;
    }'''

new_content = re.sub(seq_pattern, seq_replacement, new_content)

# Write the modified content
with open('PatternDemoRunner.java', 'w') as f:
    f.write(new_content)

print("Methods added successfully!")