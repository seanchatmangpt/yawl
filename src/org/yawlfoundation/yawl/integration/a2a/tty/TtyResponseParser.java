/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.tty;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Claude Code TTY responses supporting multiple output formats.
 *
 * <p>Parses responses from Claude Code CLI output in three formats:
 * <ul>
 *   <li><b>JSON</b> - Structured JSON output from --output-format json</li>
 *   <li><b>Text</b> - Plain text output with markdown sections</li>
 *   <li><b>Streaming</b> - Server-Sent Events (SSE) format for real-time updates</li>
 * </ul>
 *
 * <p><b>Response Types:</b>
 * <ul>
 *   <li>{@link TextResponse} - Plain text response with optional sections</li>
 *   <li>{@link JsonResponse} - Structured JSON response with parsed data</li>
 *   <li>{@link StreamingResponse} - Streaming response with event sequence</li>
 *   <li>{@link ErrorResponse} - Error response with code and message</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * TtyResponseParser parser = new TtyResponseParser();
 *
 * // Parse JSON response
 * TtyResponse response = parser.parse(jsonString, TtyResponseFormat.JSON);
 * if (response instanceof JsonResponse json) {
 *     Map<String, Object> data = json.data();
 * }
 *
 * // Parse streaming response
 * parser.startStreaming();
 * parser.feedChunk("event: token\n");
 * parser.feedChunk("data: {\"text\": \"Hello\"}\n\n");
 * StreamingResponse stream = parser.finishStreaming();
 * }</pre>
 *
 * @since YAWL 5.2
 */
public final class TtyResponseParser {

    private static final Logger _logger = LogManager.getLogger(TtyResponseParser.class);

    /**
     * Supported response formats.
     */
    public enum TtyResponseFormat {
        /** Plain text output */
        TEXT,
        /** JSON structured output */
        JSON,
        /** Server-Sent Events streaming format */
        STREAMING,
        /** Auto-detect format */
        AUTO
    }

    /**
     * Sealed interface for all response types.
     */
    public sealed interface TtyResponse permits
        TextResponse, JsonResponse, StreamingResponse, ErrorResponse {

        /**
         * Check if the response indicates success.
         *
         * @return true if successful
         */
        boolean isSuccess();

        /**
         * Get the raw response content.
         *
         * @return raw content
         */
        String rawContent();

        /**
         * Get the timestamp when response was received.
         *
         * @return response timestamp
         */
        Instant timestamp();
    }

    /**
     * Plain text response with optional sections.
     *
     * @param content the text content
     * @param sections parsed markdown sections
     * @param rawContent the raw response
     * @param timestamp when the response was received
     */
    public record TextResponse(
        String content,
        List<Section> sections,
        String rawContent,
        Instant timestamp
    ) implements TtyResponse {

        @Override
        public boolean isSuccess() {
            return true;
        }

        /**
         * Get a section by title.
         *
         * @param title the section title
         * @return the section, or empty if not found
         */
        public Optional<Section> getSection(String title) {
            return sections.stream()
                .filter(s -> s.title().equalsIgnoreCase(title))
                .findFirst();
        }

        /**
         * Get all code blocks from the response.
         *
         * @return list of code blocks
         */
        public List<CodeBlock> getCodeBlocks() {
            return sections.stream()
                .filter(s -> s instanceof CodeBlock)
                .map(s -> (CodeBlock) s)
                .toList();
        }
    }

    /**
     * Structured JSON response.
     *
     * @param data the parsed JSON data
     * @param rawContent the raw JSON string
     * @param timestamp when the response was received
     */
    public record JsonResponse(
        Map<String, Object> data,
        String rawContent,
        Instant timestamp
    ) implements TtyResponse {

        @Override
        public boolean isSuccess() {
            Object status = data.get("status");
            Object success = data.get("success");
            if (status != null) {
                return "success".equalsIgnoreCase(status.toString()) ||
                       "ok".equalsIgnoreCase(status.toString());
            }
            if (success != null) {
                return Boolean.TRUE.equals(success);
            }
            return !data.containsKey("error");
        }

        /**
         * Get a value from the JSON data.
         *
         * @param key the key
         * @return the value, or null if not present
         */
        public Object get(String key) {
            return data.get(key);
        }

        /**
         * Get a string value from the JSON data.
         *
         * @param key the key
         * @return the string value, or null if not present or not a string
         */
        public String getString(String key) {
            Object value = data.get(key);
            return value != null ? value.toString() : null;
        }

        /**
         * Get an integer value from the JSON data.
         *
         * @param key the key
         * @return the integer value, or null if not present or not an integer
         */
        public Integer getInteger(String key) {
            Object value = data.get(key);
            if (value instanceof Number num) {
                return num.intValue();
            }
            return null;
        }

        /**
         * Get a nested object from the JSON data.
         *
         * @param key the key
         * @return the nested map, or empty map if not present
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> getNested(String key) {
            Object value = data.get(key);
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
            return Map.of();
        }
    }

    /**
     * Streaming response with event sequence.
     *
     * @param events the sequence of events
     * @param rawContent the raw SSE content
     * @param timestamp when streaming completed
     * @param isComplete whether the stream completed successfully
     */
    public record StreamingResponse(
        List<SseEvent> events,
        String rawContent,
        Instant timestamp,
        boolean isComplete
    ) implements TtyResponse {

        @Override
        public boolean isSuccess() {
            return isComplete && events.stream()
                .noneMatch(e -> "error".equalsIgnoreCase(e.eventType()));
        }

        /**
         * Get all events of a specific type.
         *
         * @param eventType the event type
         * @return list of matching events
         */
        public List<SseEvent> getEvents(String eventType) {
            return events.stream()
                .filter(e -> e.eventType().equalsIgnoreCase(eventType))
                .toList();
        }

        /**
         * Get the combined text from all token events.
         *
         * @return combined text content
         */
        public String getCombinedText() {
            StringBuilder sb = new StringBuilder();
            for (SseEvent event : events) {
                if ("token".equalsIgnoreCase(event.eventType())) {
                    Object text = event.data().get("text");
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }
    }

    /**
     * Error response.
     *
     * @param errorCode the error code
     * @param message the error message
     * @param details additional error details
     * @param rawContent the raw error content
     * @param timestamp when the error was received
     */
    public record ErrorResponse(
        String errorCode,
        String message,
        Map<String, Object> details,
        String rawContent,
        Instant timestamp
    ) implements TtyResponse {

        @Override
        public boolean isSuccess() {
            return false;
        }

        /**
         * Create from exception.
         *
         * @param e the exception
         * @return error response
         */
        public static ErrorResponse fromException(Exception e) {
            return new ErrorResponse(
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(),
                e.toString(),
                Instant.now()
            );
        }
    }

    /**
     * Represents a markdown section in text output.
     *
     * @param title the section title
     * @param content the section content
     */
    public record Section(String title, String content) {}

    /**
     * Represents a code block in markdown.
     *
     * @param language the code language
     * @param code the code content
     * @param title the optional title
     * @param content the full content including markers
     */
    public record CodeBlock(
        String language,
        String code,
        String title,
        String content
    ) implements Section {}

    /**
     * Server-Sent Event.
     *
     * @param eventType the event type (event: line)
     * @param data the event data (data: lines)
     * @param id optional event ID
     * @param raw the raw event text
     */
    public record SseEvent(
        String eventType,
        Map<String, Object> data,
        String id,
        String raw
    ) {}

    // Pattern for markdown sections
    private static final Pattern SECTION_PATTERN = Pattern.compile(
        "^#+\\s+(.+?)\\s*$",
        Pattern.MULTILINE
    );

    // Pattern for code blocks
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```(\\w*)\\s*(?:\\[(.+?)\\])?\\s*\\n(.*?)```",
        Pattern.DOTALL
    );

    // Pattern for JSON detection
    private static final Pattern JSON_PATTERN = Pattern.compile(
        "^\\s*\\{.*\\}\\s*$",
        Pattern.DOTALL
    );

    // Pattern for SSE event line
    private static final Pattern SSE_EVENT_PATTERN = Pattern.compile(
        "^event:\\s*(.+)$",
        Pattern.MULTILINE
    );

    // Pattern for SSE data line
    private static final Pattern SSE_DATA_PATTERN = Pattern.compile(
        "^data:\\s*(.+)$",
        Pattern.MULTILINE
    );

    // Streaming state
    private final StringBuilder streamingBuffer = new StringBuilder();
    private final List<SseEvent> streamingEvents = new ArrayList<>();
    private boolean streamingActive = false;

    /**
     * Create a new response parser.
     */
    public TtyResponseParser() {
        _logger.debug("TtyResponseParser initialized");
    }

    /**
     * Parse a response string.
     *
     * @param content the response content
     * @param format the expected format (AUTO for auto-detection)
     * @return the parsed response
     */
    public TtyResponse parse(String content, TtyResponseFormat format) {
        Objects.requireNonNull(content, "content cannot be null");

        if (format == TtyResponseFormat.AUTO) {
            format = detectFormat(content);
        }

        _logger.debug("Parsing response with format: {}", format);

        return switch (format) {
            case JSON -> parseJson(content);
            case STREAMING -> parseStreaming(content);
            case TEXT -> parseText(content);
            case AUTO -> parseText(content); // Should never reach here
        };
    }

    /**
     * Parse a response with auto-detection.
     *
     * @param content the response content
     * @return the parsed response
     */
    public TtyResponse parse(String content) {
        return parse(content, TtyResponseFormat.AUTO);
    }

    /**
     * Start a streaming parse session.
     */
    public void startStreaming() {
        streamingBuffer.setLength(0);
        streamingEvents.clear();
        streamingActive = true;
        _logger.debug("Started streaming parse session");
    }

    /**
     * Feed a chunk to the streaming parser.
     *
     * @param chunk the chunk to process
     */
    public void feedChunk(String chunk) {
        if (!streamingActive) {
            throw new IllegalStateException("Streaming not active. Call startStreaming() first.");
        }

        streamingBuffer.append(chunk);

        // Try to parse complete events
        parseAvailableEvents();
    }

    /**
     * Finish streaming and get the final response.
     *
     * @return the complete streaming response
     */
    public StreamingResponse finishStreaming() {
        streamingActive = false;

        // Parse any remaining events
        parseAvailableEvents();

        String rawContent = streamingBuffer.toString();
        boolean isComplete = !rawContent.contains("event: error");

        _logger.debug("Finished streaming with {} events, complete={}",
            streamingEvents.size(), isComplete);

        return new StreamingResponse(
            new ArrayList<>(streamingEvents),
            rawContent,
            Instant.now(),
            isComplete
        );
    }

    /**
     * Check if streaming is active.
     *
     * @return true if streaming
     */
    public boolean isStreamingActive() {
        return streamingActive;
    }

    /**
     * Get the number of events parsed so far in streaming mode.
     *
     * @return event count
     */
    public int getStreamingEventCount() {
        return streamingEvents.size();
    }

    /**
     * Detect the format of a response.
     *
     * @param content the response content
     * @return the detected format
     */
    public TtyResponseFormat detectFormat(String content) {
        if (content == null || content.isBlank()) {
            return TtyResponseFormat.TEXT;
        }

        String trimmed = content.trim();

        // Check for JSON
        if (JSON_PATTERN.matcher(trimmed).matches()) {
            return TtyResponseFormat.JSON;
        }

        // Check for SSE format
        if (trimmed.startsWith("event:") || trimmed.startsWith("data:")) {
            return TtyResponseFormat.STREAMING;
        }

        // Default to text
        return TtyResponseFormat.TEXT;
    }

    /**
     * Extract code blocks from any response type.
     *
     * @param response the response
     * @return list of code blocks
     */
    public List<CodeBlock> extractCodeBlocks(TtyResponse response) {
        if (response instanceof TextResponse text) {
            return text.getCodeBlocks();
        }

        if (response instanceof JsonResponse json) {
            Object codeObj = json.get("code");
            if (codeObj instanceof String code) {
                return List.of(new CodeBlock("unknown", code, null, code));
            }
            Object blocksObj = json.get("code_blocks");
            if (blocksObj instanceof List<?> blocks) {
                return blocks.stream()
                    .filter(b -> b instanceof Map)
                    .map(b -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) b;
                        return new CodeBlock(
                            (String) map.getOrDefault("language", "unknown"),
                            (String) map.getOrDefault("code", ""),
                            (String) map.get("title"),
                            (String) map.getOrDefault("code", "")
                        );
                    })
                    .toList();
            }
        }

        if (response instanceof StreamingResponse stream) {
            String text = stream.getCombinedText();
            return parseCodeBlocks(text);
        }

        return List.of();
    }

    private TtyResponse parseJson(String content) {
        try {
            Map<String, Object> data = parseJsonToMap(content);

            // Check for error in JSON
            if (data.containsKey("error")) {
                Object errorObj = data.get("error");
                String errorCode = "JSON_ERROR";
                String message = "Unknown error";
                Map<String, Object> details = Map.of();

                if (errorObj instanceof String) {
                    message = (String) errorObj;
                } else if (errorObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                    errorCode = (String) errorMap.getOrDefault("code", errorCode);
                    message = (String) errorMap.getOrDefault("message", message);
                    details = errorMap;
                }

                return new ErrorResponse(errorCode, message, details, content, Instant.now());
            }

            return new JsonResponse(data, content, Instant.now());

        } catch (Exception e) {
            _logger.warn("Failed to parse JSON response: {}", e.getMessage());
            return new ErrorResponse(
                "PARSE_ERROR",
                "Failed to parse JSON: " + e.getMessage(),
                Map.of(),
                content,
                Instant.now()
            );
        }
    }

    private TtyResponse parseText(String content) {
        List<CodeBlock> codeBlocks = parseCodeBlocks(content);
        List<Section> sections = new ArrayList<>(codeBlocks);

        // Parse markdown sections
        Matcher sectionMatcher = SECTION_PATTERN.matcher(content);
        int lastEnd = 0;
        while (sectionMatcher.find()) {
            String title = sectionMatcher.group(1);
            int start = sectionMatcher.end();
            int end = content.length();

            // Find next section or end
            if (sectionMatcher.find()) {
                end = sectionMatcher.start();
                sectionMatcher.region(end, content.length());
            }

            String sectionContent = content.substring(start, end).trim();
            sections.add(new Section(title, sectionContent));
        }

        return new TextResponse(content, sections, content, Instant.now());
    }

    private TtyResponse parseStreaming(String content) {
        List<SseEvent> events = new ArrayList<>();
        String[] lines = content.split("\n\n");

        for (String block : lines) {
            if (block.isBlank()) {
                continue;
            }

            String eventType = "message";
            Map<String, Object> data = Map.of();
            String id = null;

            for (String line : block.split("\n")) {
                line = line.trim();
                if (line.startsWith("event:")) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    String dataStr = line.substring(5).trim();
                    try {
                        data = parseJsonToMap(dataStr);
                    } catch (Exception e) {
                        // Store as raw string if not JSON
                        data = Map.of("raw", dataStr);
                    }
                } else if (line.startsWith("id:")) {
                    id = line.substring(3).trim();
                }
            }

            events.add(new SseEvent(eventType, data, id, block));
        }

        boolean isComplete = events.stream()
            .anyMatch(e -> "done".equalsIgnoreCase(e.eventType()));

        return new StreamingResponse(events, content, Instant.now(), isComplete);
    }

    private List<CodeBlock> parseCodeBlocks(String content) {
        List<CodeBlock> blocks = new ArrayList<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);

        while (matcher.find()) {
            String language = matcher.group(1);
            String title = matcher.group(2);
            String code = matcher.group(3);

            blocks.add(new CodeBlock(
                language != null ? language : "unknown",
                code != null ? code : "",
                title,
                matcher.group(0)
            ));
        }

        return blocks;
    }

    private void parseAvailableEvents() {
        String buffer = streamingBuffer.toString();
        int lastEventEnd = 0;

        // Events are separated by double newlines
        String[] potentialEvents = buffer.split("\n\n");

        for (int i = 0; i < potentialEvents.length - 1; i++) {
            String eventBlock = potentialEvents[i];
            if (!eventBlock.isBlank()) {
                SseEvent event = parseSseEvent(eventBlock);
                if (event != null) {
                    streamingEvents.add(event);
                    lastEventEnd += eventBlock.length() + 2; // +2 for \n\n
                }
            }
        }

        // Remove processed events from buffer
        if (lastEventEnd > 0) {
            streamingBuffer.delete(0, lastEventEnd);
        }
    }

    private SseEvent parseSseEvent(String block) {
        String eventType = "message";
        Map<String, Object> data = Map.of();
        String id = null;

        for (String line : block.split("\n")) {
            line = line.trim();
            if (line.startsWith("event:")) {
                eventType = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                String dataStr = line.substring(5).trim();
                try {
                    data = parseJsonToMap(dataStr);
                } catch (Exception e) {
                    data = Map.of("raw", dataStr);
                }
            } else if (line.startsWith("id:")) {
                id = line.substring(3).trim();
            }
        }

        return new SseEvent(eventType, data, id, block);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        // Simple JSON parser for basic structures
        // For production, use Jackson or Gson
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object");
        }

        // Use a basic implementation - in production this would use Jackson/Gson
        return new SimpleJsonParser(json).parseObject();
    }

    /**
     * Simple JSON parser for basic structures.
     * In production, this should be replaced with Jackson or Gson.
     */
    private static final class SimpleJsonParser {
        private final String json;
        private int pos = 0;

        SimpleJsonParser(String json) {
            this.json = json;
        }

        Map<String, Object> parseObject() {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();

            if (peek() == '}') {
                pos++;
                return result;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                result.put(key, value);

                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    break;
                }
                expect(',');
            }

            return result;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();

            if (c == '"') {
                return parseString();
            } else if (c == '{') {
                return new SimpleJsonParser(json.substring(pos)).parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == 't' || c == 'f') {
                return parseBoolean();
            } else if (c == 'n') {
                return parseNull();
            } else if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            }

            throw new IllegalArgumentException("Unexpected character: " + c);
        }

        List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            expect('[');
            skipWhitespace();

            if (peek() == ']') {
                pos++;
                return result;
            }

            while (true) {
                skipWhitespace();
                result.add(parseValue());
                skipWhitespace();

                if (peek() == ']') {
                    pos++;
                    break;
                }
                expect(',');
            }

            return result;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();

            while (peek() != '"') {
                char c = next();
                if (c == '\\') {
                    c = next();
                    sb.append(switch (c) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'r' -> '\r';
                        case '"' -> '"';
                        case '\\' -> '\\';
                        default -> c;
                    });
                } else {
                    sb.append(c);
                }
            }
            expect('"');

            return sb.toString();
        }

        Number parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (Character.isDigit(peek())) {
                pos++;
            }
            if (peek() == '.') {
                pos++;
                while (Character.isDigit(peek())) {
                    pos++;
                }
                return Double.parseDouble(json.substring(start, pos));
            }
            return Long.parseLong(json.substring(start, pos));
        }

        Boolean parseBoolean() {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return true;
            }
            if (json.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new IllegalArgumentException("Expected boolean");
        }

        Object parseNull() {
            if (json.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Expected null");
        }

        char peek() {
            return pos < json.length() ? json.charAt(pos) : '\0';
        }

        char next() {
            return json.charAt(pos++);
        }

        void expect(char c) {
            if (peek() != c) {
                throw new IllegalArgumentException("Expected '" + c + "' but found '" + peek() + "'");
            }
            pos++;
        }

        void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }
    }
}
