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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.skills;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A2A skill that ingests Object-Centric Event Data (OCED) in CSV, JSON, or XML format.
 *
 * <p>This skill provides MCP/A2A access to OCED bridge functionality without requiring
 * a cross-module dependency on {@code yawl-pi}. Format detection is inline; parsing
 * extracts structural metadata (columns, record count, sample) without full OcedBridgeFactory.
 *
 * <p>Closes {@code V7Gap.BURIED_ENGINES_MCP_A2A_WIRING} for the OCED bridge component.
 *
 * <h2>Request parameters</h2>
 * <ul>
 *   <li>{@code data} — required, raw OCED data as string (CSV, JSON array, or XML)</li>
 *   <li>{@code format} — optional, one of {@code csv}, {@code json}, {@code xml};
 *       auto-detected if omitted</li>
 * </ul>
 *
 * <h2>Result data keys</h2>
 * <ul>
 *   <li>{@code format} — detected or specified format</li>
 *   <li>{@code recordCount} — number of records parsed</li>
 *   <li>{@code columns} — list of detected column/field names</li>
 *   <li>{@code sampleRows} — first 3 records as strings for preview</li>
 *   <li>{@code objectTypes} — object types detected in the OCED data</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OcedDataIngestionSkill implements A2ASkill {

    @Override
    public String getId() {
        return "oced_data_ingestion";
    }

    @Override
    public String getName() {
        return "OCED Data Ingestion";
    }

    @Override
    public String getDescription() {
        return "Ingests Object-Centric Event Data (OCED) in CSV, JSON, or XML format. "
            + "Auto-detects format, extracts structural metadata (columns, record count, object types). "
            + "Provides A2A access to OCED bridge functionality for process mining integration. "
            + "Closes V7Gap.BURIED_ENGINES_MCP_A2A_WIRING for the OCED bridge component.";
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("data:read");
    }

    @Override
    public List<String> getTags() {
        return List.of("oced", "process-mining", "data-ingestion", "no-llm", "bridge");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String data = request.getParameter("data");
        if (data == null || data.isBlank()) {
            return SkillResult.error(
                "Parameter 'data' is required. Provide raw OCED data as CSV, JSON array, or XML string.");
        }

        long start = System.currentTimeMillis();
        String format = request.getParameter("format");

        if (format == null || format.isBlank()) {
            format = detectFormat(data.trim());
        } else {
            format = format.toLowerCase().trim();
            if (!Set.of("csv", "json", "xml").contains(format)) {
                return SkillResult.error(
                    "Invalid format '" + format + "'. Must be one of: csv, json, xml");
            }
        }

        try {
            OcedParseResult result = switch (format) {
                case "csv" -> parseCsv(data);
                case "json" -> parseJson(data);
                case "xml" -> parseXml(data);
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            };

            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("format", format);
            resultData.put("recordCount", result.recordCount());
            resultData.put("columns", result.columns());
            resultData.put("sampleRows", result.sampleRows());
            resultData.put("objectTypes", result.objectTypes());
            resultData.put("engine", "OcedDataIngestionSkill (inline bridge, no yawl-pi dep)");
            return SkillResult.success(resultData, elapsed);
        } catch (Exception e) {
            return SkillResult.error("Failed to parse OCED data as " + format + ": " + e.getMessage());
        }
    }

    // =========================================================================
    // Format detection
    // =========================================================================

    private String detectFormat(String data) {
        if (data.startsWith("<")) return "xml";
        if (data.startsWith("{") || data.startsWith("[")) return "json";
        return "csv";
    }

    // =========================================================================
    // CSV parsing
    // =========================================================================

    private OcedParseResult parseCsv(String data) {
        String[] lines = data.split("\n");
        if (lines.length == 0) {
            return new OcedParseResult("csv", 0, List.of(), List.of(), List.of());
        }

        // First line is header
        List<String> columns = Arrays.stream(lines[0].split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        int recordCount = Math.max(0, lines.length - 1);

        List<String> sampleRows = new ArrayList<>();
        for (int i = 1; i <= Math.min(3, lines.length - 1); i++) {
            sampleRows.add(lines[i].trim());
        }

        // Detect object types from column names (OCED convention: column names contain object type)
        List<String> objectTypes = columns.stream()
            .filter(col -> col.contains("_") || col.equalsIgnoreCase("objectType")
                || col.equalsIgnoreCase("object_type") || col.equalsIgnoreCase("type"))
            .map(col -> col.replaceAll("_(id|ID|Id)$", ""))
            .distinct()
            .collect(Collectors.toList());

        if (objectTypes.isEmpty() && !columns.isEmpty()) {
            // Default: treat each unique prefix before underscore as object type
            objectTypes = columns.stream()
                .filter(col -> col.contains("_"))
                .map(col -> col.substring(0, col.indexOf('_')))
                .distinct()
                .collect(Collectors.toList());
        }

        return new OcedParseResult("csv", recordCount, columns, sampleRows, objectTypes);
    }

    // =========================================================================
    // JSON parsing (structural, no full JSON library needed)
    // =========================================================================

    private OcedParseResult parseJson(String data) {
        String trimmed = data.trim();
        boolean isArray = trimmed.startsWith("[");

        // Count records: count top-level { occurrences after the first one
        int recordCount = 0;
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    depth++;
                    if (depth == (isArray ? 2 : 1)) recordCount++;
                } else if (c == '}') {
                    depth--;
                }
            }
        }

        // Extract field names from first object (heuristic)
        List<String> columns = new ArrayList<>();
        int firstBrace = trimmed.indexOf('{');
        if (firstBrace >= 0) {
            int end = trimmed.indexOf('}', firstBrace);
            if (end > firstBrace) {
                String firstObj = trimmed.substring(firstBrace + 1, end);
                // Extract quoted key names: "key":
                int pos = 0;
                while (pos < firstObj.length()) {
                    int q1 = firstObj.indexOf('"', pos);
                    if (q1 < 0) break;
                    int q2 = firstObj.indexOf('"', q1 + 1);
                    if (q2 < 0) break;
                    String key = firstObj.substring(q1 + 1, q2);
                    // Check it's a key (followed by :)
                    int colon = firstObj.indexOf(':', q2);
                    if (colon >= 0 && firstObj.substring(q2 + 1, colon).isBlank()) {
                        columns.add(key);
                    }
                    pos = q2 + 1;
                }
            }
        }

        // Sample rows: extract first 3 objects as strings
        List<String> sampleRows = new ArrayList<>();
        int objStart = -1;
        depth = 0;
        inString = false;
        int objCount = 0;
        for (int i = 0; i < trimmed.length() && sampleRows.size() < 3; i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    if (depth == (isArray ? 1 : 0)) objStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == (isArray ? 1 : 0) && objStart >= 0) {
                        sampleRows.add(trimmed.substring(objStart, i + 1));
                        objStart = -1;
                        objCount++;
                    }
                }
            }
        }

        // Detect object types from field names
        List<String> objectTypes = columns.stream()
            .filter(col -> col.equalsIgnoreCase("ocel:type") || col.equalsIgnoreCase("objectType")
                || col.equalsIgnoreCase("type") || col.endsWith("Type") || col.endsWith("_type"))
            .collect(Collectors.toList());

        return new OcedParseResult("json", recordCount, columns, sampleRows, objectTypes);
    }

    // =========================================================================
    // XML parsing (structural, no XML library)
    // =========================================================================

    private OcedParseResult parseXml(String data) {
        // Count records: count <event>, <record>, <row>, or <item> elements
        String[] recordTags = {"<event ", "<event>", "<record ", "<record>", "<row ", "<row>", "<item ", "<item>"};
        int recordCount = 0;
        for (String tag : recordTags) {
            int pos = 0;
            while ((pos = data.indexOf(tag, pos)) >= 0) {
                recordCount++;
                pos += tag.length();
            }
        }

        // Extract attribute/element names from first record
        List<String> columns = new ArrayList<>();
        for (String tag : recordTags) {
            int start = data.indexOf(tag);
            if (start >= 0) {
                int end = data.indexOf('>', start);
                if (end > start) {
                    String tagContent = data.substring(start, end);
                    // Extract attribute names: word=
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(\\w+)=")
                        .matcher(tagContent);
                    while (m.find()) {
                        String attr = m.group(1);
                        if (!attr.equals("xmlns") && !columns.contains(attr)) {
                            columns.add(attr);
                        }
                    }
                }
                // Also look for child element names
                int childEnd = data.indexOf("</" + tag.substring(1).replace(">", "").trim(), start);
                if (childEnd > start) {
                    String inner = data.substring(end + 1, childEnd);
                    java.util.regex.Matcher childM = java.util.regex.Pattern
                        .compile("<(\\w+)[\\s>]")
                        .matcher(inner);
                    while (childM.find()) {
                        String elem = childM.group(1);
                        if (!columns.contains(elem)) columns.add(elem);
                    }
                }
                break;
            }
        }

        // Sample rows: first 3 records
        List<String> sampleRows = new ArrayList<>();
        for (String openTag : new String[]{"<event>", "<record>", "<row>", "<item>"}) {
            String closeTag = openTag.replace("<", "</");
            int pos = 0;
            while (sampleRows.size() < 3 && (pos = data.indexOf(openTag, pos)) >= 0) {
                int end = data.indexOf(closeTag, pos);
                if (end >= 0) {
                    sampleRows.add(data.substring(pos, end + closeTag.length()));
                    pos = end + closeTag.length();
                } else {
                    break;
                }
            }
            if (!sampleRows.isEmpty()) break;
        }

        // Detect object types from OCEL XML standard attribute names
        List<String> objectTypes = columns.stream()
            .filter(col -> col.equalsIgnoreCase("ocel:type") || col.equalsIgnoreCase("objectType")
                || col.equalsIgnoreCase("type") || col.equalsIgnoreCase("object-type"))
            .collect(Collectors.toList());

        return new OcedParseResult("xml", recordCount, columns, sampleRows, objectTypes);
    }

    // =========================================================================
    // Internal result record
    // =========================================================================

    private record OcedParseResult(
        String format,
        int recordCount,
        List<String> columns,
        List<String> sampleRows,
        List<String> objectTypes
    ) {}
}
