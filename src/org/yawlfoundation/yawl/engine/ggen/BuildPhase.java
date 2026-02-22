/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Build phase orchestrator — invokes ggen-build.sh and consumes receipts.
 *
 * Λ (Build) phase circuit:
 *   1. Generate (ggen → YAWL)
 *   2. Compile (Maven, agent-dx profile)
 *   3. Test (JUnit 5)
 *   4. Validate (SpotBugs, PMD, Checkstyle)
 *
 * All phases emit receipts to .ggen/build-receipt.json (JSONL).
 *
 * Usage:
 * <pre>
 *   BuildPhase bp = new BuildPhase(Paths.get("/home/user/yawl"));
 *   if (bp.executeLambda()) {
 *       System.out.println(bp.getSummary());
 *       // Proceed to H (guards) phase
 *   } else {
 *       System.err.println("Build failed. See: " + bp.getFailureLog());
 *   }
 * </pre>
 */
public class BuildPhase {
    private final Path repoRoot;
    private final Path scriptDir;
    private final Path receiptFile;
    private final Path failureLog;

    /**
     * Create build orchestrator for repository.
     *
     * @param repoRoot path to YAWL repository root
     */
    public BuildPhase(Path repoRoot) {
        if (repoRoot == null) {
            throw new IllegalArgumentException("Repository root must not be null");
        }
        if (!Files.isDirectory(repoRoot)) {
            throw new IllegalArgumentException("Repository root must be a directory: " + repoRoot);
        }

        this.repoRoot = repoRoot;
        this.scriptDir = repoRoot.resolve("scripts");
        this.receiptFile = repoRoot.resolve(".ggen/build-receipt.json");
        this.failureLog = Paths.get("/tmp/ggen-build-failure.log");
    }

    /**
     * Execute full Λ pipeline (generate → compile → test → validate).
     *
     * @return true if all phases GREEN; false otherwise
     * @throws IOException if build script fails or receipt cannot be read
     * @throws InterruptedException if process interrupted
     */
    public boolean executeLambda() throws IOException, InterruptedException {
        return executePhase("lambda");
    }

    /**
     * Execute single phase by name.
     *
     * @param phaseName phase name: "lambda", "generate", "compile", "test", or "validate"
     * @return true if phase GREEN; false otherwise
     * @throws IOException if build script fails or receipt cannot be read
     * @throws InterruptedException if process interrupted
     */
    public boolean executePhase(String phaseName) throws IOException, InterruptedException {
        if (phaseName == null || phaseName.trim().isEmpty()) {
            throw new IllegalArgumentException("Phase name must not be empty");
        }

        Path buildScript = scriptDir.resolve("ggen-build.sh");
        if (!Files.exists(buildScript)) {
            throw new IOException("Build script not found: " + buildScript);
        }

        // Prepare receipt directory
        Files.createDirectories(receiptFile.getParent());

        // Invoke build script
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(buildScript.toString());
        command.add("--phase");
        command.add(phaseName);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoRoot.toFile());
        pb.inheritIO();  // Show output in console

        int exitCode = pb.start().waitFor();

        // Load receipt chain
        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);

        // Determine success
        return exitCode == 0 && BuildReceipt.isChainPass(chain);
    }

    /**
     * Get receipt for specific phase.
     *
     * @param phaseName phase name
     * @return BuildReceipt or null if phase not found in chain
     * @throws IOException if receipt file cannot be read
     */
    public BuildReceipt getReceipt(String phaseName) throws IOException {
        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);
        return chain.get(phaseName);
    }

    /**
     * Get all receipts from chain.
     *
     * @return map of phase name → BuildReceipt (empty if receipt file not found)
     * @throws IOException if receipt file cannot be read
     */
    public Map<String, BuildReceipt> getReceiptChain() throws IOException {
        return BuildReceipt.loadChain(receiptFile);
    }

    /**
     * Check if receipt file exists and is non-empty.
     */
    public boolean hasReceipts() {
        return Files.isRegularFile(receiptFile) && Files.isReadable(receiptFile);
    }

    /**
     * Validate that build is ready for H (guards) phase.
     *
     * Requirements:
     * - All required phases present in receipt
     * - All phases GREEN or WARN (no FAIL)
     * - Receipt file exists and is readable
     *
     * @return true if ready for H phase; false otherwise
     * @throws IOException if receipt file cannot be read
     */
    public boolean isReadyForGuardsPhase() throws IOException {
        if (!hasReceipts()) {
            return false;
        }

        Map<String, BuildReceipt> chain = getReceiptChain();

        // Check all required phases present
        List<String> requiredPhases = Arrays.asList("generate", "compile", "test", "validate");
        for (String phase : requiredPhases) {
            if (!chain.containsKey(phase)) {
                System.err.println("Missing phase receipt: " + phase);
                return false;
            }
            BuildReceipt receipt = chain.get(phase);
            if (!receipt.isPass()) {
                System.err.println("Phase failed: " + phase + " status=" + receipt.getStatus());
                return false;
            }
        }

        return true;
    }

    /**
     * Get path to failure log (if build failed).
     */
    public Path getFailureLog() {
        return failureLog;
    }

    /**
     * Emit summary of build execution (for logging/reporting).
     *
     * @return human-readable summary string
     * @throws IOException if receipt file cannot be read
     */
    public String getSummary() throws IOException {
        Map<String, BuildReceipt> chain = getReceiptChain();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Build Phase Summary ===\n");

        if (chain.isEmpty()) {
            sb.append("  (No receipts found)\n");
            return sb.toString();
        }

        long totalElapsed = 0;
        List<String> allPhases = Arrays.asList("generate", "compile", "test", "validate");

        for (String phase : allPhases) {
            BuildReceipt receipt = chain.get(phase);
            if (receipt != null) {
                totalElapsed += receipt.getElapsedMs();
                sb.append(String.format("  %s: %s (%dms)\n", phase, receipt.getStatus(), receipt.getElapsedMs()));

                Map<String, Object> details = receipt.getDetails();
                if (!details.isEmpty()) {
                    for (Map.Entry<String, Object> entry : details.entrySet()) {
                        sb.append(String.format("    - %s: %s\n", entry.getKey(), entry.getValue()));
                    }
                }
            }
        }

        sb.append(String.format("  Total: %dms\n", totalElapsed));

        return sb.toString();
    }

    /**
     * Write summary to System.out or System.err depending on status.
     *
     * @throws IOException if receipt file cannot be read
     */
    public void printSummary() throws IOException {
        Map<String, BuildReceipt> chain = getReceiptChain();
        boolean allPass = BuildReceipt.isChainPass(chain);

        if (allPass) {
            System.out.println(getSummary());
        } else {
            System.err.println(getSummary());
        }
    }

    /**
     * Main entry point for testing/debugging.
     *
     * Usage: java BuildPhase [repo-root]
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Path repoRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(System.getProperty("user.dir"));

        BuildPhase bp = new BuildPhase(repoRoot);

        if (bp.executeLambda()) {
            bp.printSummary();
            System.exit(0);
        } else {
            bp.printSummary();
            System.exit(1);
        }
    }
}
