package org.yawlfoundation.yawl.mcp.a2a.benchmarks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Network Transport Performance Benchmarks.
 *
 * <p>Compares HTTP/SSE vs STDIO transport efficiency:</p>
 * <ul>
 *   <li>Message framing overhead</li>
 *   <li>Compression effectiveness</li>
 *   <li>Bandwidth utilization</li>
 *   <li>Protocol efficiency</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
public class NetworkTransportBenchmark {

    // =========================================================================
    // Transport State
    // =========================================================================

    @State(Scope.Benchmark)
    public static class NetworkState {
        ObjectMapper objectMapper;
        
        // Different payload sizes
        String smallPayload;
        String mediumPayload;
        String largePayload;
        
        // Pre-serialized bytes
        byte[] smallBytes;
        byte[] mediumBytes;
        byte[] largeBytes;
        
        // Pre-compressed bytes
        byte[] compressedSmall;
        byte[] compressedMedium;
        byte[] compressedLarge;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            objectMapper = new ObjectMapper();
            objectMapper.findAndRegisterModules();
            
            // Small payload: tool response
            smallPayload = createToolResponse("Case launched successfully", "case-42");
            
            // Medium payload: work item list
            mediumPayload = createWorkItemList(10);
            
            // Large payload: specification list
            largePayload = createSpecificationList(50);
            
            // Serialize to bytes
            smallBytes = smallPayload.getBytes(StandardCharsets.UTF_8);
            mediumBytes = mediumPayload.getBytes(StandardCharsets.UTF_8);
            largeBytes = largePayload.getBytes(StandardCharsets.UTF_8);
            
            // Pre-compress
            compressedSmall = compress(smallBytes);
            compressedMedium = compress(mediumBytes);
            compressedLarge = compress(largeBytes);
            
            System.out.println("Payload Sizes:");
            System.out.println("  Small: " + smallBytes.length + " bytes (compressed: " + compressedSmall.length + ")");
            System.out.println("  Medium: " + mediumBytes.length + " bytes (compressed: " + compressedMedium.length + ")");
            System.out.println("  Large: " + largeBytes.length + " bytes (compressed: " + compressedLarge.length + ")");
        }

        private String createToolResponse(String message, String caseId) throws IOException {
            Map<String, Object> response = new HashMap<>();
            response.put("result", Map.of(
                "content", java.util.List.of(Map.of("type", "text", "text", message)),
                "isError", false
            ));
            response.put("caseId", caseId);
            return objectMapper.writeValueAsString(response);
        }

        private String createWorkItemList(int count) throws IOException {
            Map<String, Object> response = new HashMap<>();
            java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", "WI-" + i);
                item.put("caseId", "case-" + (i % 5));
                item.put("taskId", "Task-" + (i % 3));
                item.put("status", "enabled");
                items.add(item);
            }
            response.put("workItems", items);
            return objectMapper.writeValueAsString(response);
        }

        private String createSpecificationList(int count) throws IOException {
            Map<String, Object> response = new HashMap<>();
            java.util.List<Map<String, Object>> specs = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, Object> spec = new HashMap<>();
                spec.put("id", "Spec-" + i);
                spec.put("uri", "http://example.com/spec" + i);
                spec.put("name", "Specification " + i);
                spec.put("version", "1." + i);
                spec.put("description", "A workflow specification for testing purposes");
                spec.put("documentation", "Extended documentation for specification " + i);
                specs.add(spec);
            }
            response.put("specifications", specs);
            return objectMapper.writeValueAsString(response);
        }

        private byte[] compress(byte[] data) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(data);
            }
            return baos.toByteArray();
        }

        private byte[] decompress(byte[] compressed) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
                gzip.transferTo(baos);
            }
            return baos.toByteArray();
        }
    }

    // =========================================================================
    // STDIO Transport Benchmarks
    // =========================================================================

    @Benchmark
    public void benchmarkStdio_framing(NetworkState state, Blackhole bh) {
        // STDIO uses Content-Length header framing
        String framed = "Content-Length: " + state.smallBytes.length + "\r\n\r\n";
        byte[] framedBytes = framed.getBytes(StandardCharsets.UTF_8);
        bh.consume(framedBytes);
    }

    @Benchmark
    public void benchmarkStdio_fullMessage(NetworkState state, Blackhole bh) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.printf("Content-Length: %d\r\n\r\n", state.mediumBytes.length);
        pw.flush();
        String header = sw.toString();
        byte[] fullMessage = new byte[header.length() + state.mediumBytes.length];
        System.arraycopy(header.getBytes(StandardCharsets.UTF_8), 0, fullMessage, 0, header.length());
        System.arraycopy(state.mediumBytes, 0, fullMessage, header.length(), state.mediumBytes.length);
        bh.consume(fullMessage);
    }

    // =========================================================================
    // HTTP/SSE Transport Benchmarks
    // =========================================================================

    @Benchmark
    public void benchmarkSse_framing(NetworkState state, Blackhole bh) {
        // SSE uses event and data fields
        String event = "event: message\r\n";
        String data = "data: " + state.smallPayload + "\r\n\r\n";
        bh.consume(event + data);
    }

    @Benchmark
    public void benchmarkSse_fullMessage(NetworkState state, Blackhole bh) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("event: message");
        pw.println("data: " + state.mediumPayload);
        pw.println();
        pw.flush();
        bh.consume(sw.toString());
    }

    @Benchmark
    public void benchmarkSse_multiLineData(NetworkState state, Blackhole bh) {
        // SSE requires each line to have "data: " prefix
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("event: message");
        // Split payload by lines
        for (String line : state.largePayload.split("\n")) {
            pw.println("data: " + line);
        }
        pw.println();
        pw.flush();
        bh.consume(sw.toString());
    }

    // =========================================================================
    // Compression Benchmarks
    // =========================================================================

    @Benchmark
    public byte[] benchmarkCompression_small(NetworkState state) throws IOException {
        return state.compress(state.smallBytes);
    }

    @Benchmark
    public byte[] benchmarkCompression_medium(NetworkState state) throws IOException {
        return state.compress(state.mediumBytes);
    }

    @Benchmark
    public byte[] benchmarkCompression_large(NetworkState state) throws IOException {
        return state.compress(state.largeBytes);
    }

    @Benchmark
    public byte[] benchmarkDecompression_small(NetworkState state) throws IOException {
        return state.decompress(state.compressedSmall);
    }

    @Benchmark
    public byte[] benchmarkDecompression_medium(NetworkState state) throws IOException {
        return state.decompress(state.compressedMedium);
    }

    @Benchmark
    public byte[] benchmarkDecompression_large(NetworkState state) throws IOException {
        return state.decompress(state.compressedLarge);
    }

    // =========================================================================
    // Bandwidth Analysis
    // =========================================================================

    @Benchmark
    public void benchmarkBandwidthOverhead_stdio(NetworkState state, Blackhole bh) {
        // Calculate framing overhead for STDIO
        int headerLength = ("Content-Length: " + state.mediumBytes.length + "\r\n\r\n").length();
        double overhead = (double) headerLength / state.mediumBytes.length * 100;
        bh.consume(overhead);
    }

    @Benchmark
    public void benchmarkBandwidthOverhead_sse(NetworkState state, Blackhole bh) {
        // Calculate framing overhead for SSE
        int sseOverhead = "event: message\r\ndata: \r\n\r\n".length();
        double overhead = (double) sseOverhead / state.mediumBytes.length * 100;
        bh.consume(overhead);
    }

    @Benchmark
    public void benchmarkCompressionRatio(NetworkState state, Blackhole bh) {
        // Calculate compression effectiveness
        double smallRatio = (double) state.compressedSmall.length / state.smallBytes.length;
        double mediumRatio = (double) state.compressedMedium.length / state.mediumBytes.length;
        double largeRatio = (double) state.compressedLarge.length / state.largeBytes.length;
        bh.consume(smallRatio);
        bh.consume(mediumRatio);
        bh.consume(largeRatio);
    }

    // =========================================================================
    // Protocol Efficiency
    // =========================================================================

    @Threads(4)
    @Benchmark
    public void benchmarkProtocolEfficiency_concurrentStdio(NetworkState state, Blackhole bh) {
        String framed = "Content-Length: " + state.smallBytes.length + "\r\n\r\n";
        bh.consume(framed);
    }

    @Threads(4)
    @Benchmark
    public void benchmarkProtocolEfficiency_concurrentSse(NetworkState state, Blackhole bh) {
        String event = "event: message\ndata: " + state.smallPayload + "\n\n";
        bh.consume(event);
    }
}
