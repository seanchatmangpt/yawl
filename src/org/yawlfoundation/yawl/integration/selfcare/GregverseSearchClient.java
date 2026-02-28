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

package org.yawlfoundation.yawl.integration.selfcare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Queries the Gregverse federated registry SPARQL endpoint for OT-aligned self-care
 * workflow specifications.
 *
 * <p>Uses the standard SPARQL 1.1 Protocol over HTTP POST. Results are parsed from
 * {@code application/sparql-results+json} into {@link WorkflowSpecSummary} records.
 * If the Gregverse endpoint is unavailable, methods return empty lists (graceful
 * degradation) so the rest of the autonomic pipeline continues operating.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * GregverseSearchClient client = new GregverseSearchClient(
 *     "https://registry.gregverse.io/sparql");
 *
 * List<WorkflowSpecSummary> specs = client.searchByDomain(OTDomain.SELF_CARE);
 * specs.forEach(s -> System.out.println(s.specName() + " — " + s.downloadCount() + " uses"));
 * }</pre>
 *
 * @since YAWL 6.0
 */
public final class GregverseSearchClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GregverseSearchClient.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String sparqlEndpoint;
    private final HttpClient httpClient;

    /**
     * Creates a client pointing at the given Gregverse SPARQL endpoint.
     *
     * @param sparqlEndpoint HTTP URL of the Gregverse SPARQL endpoint
     *                       (e.g. {@code https://registry.gregverse.io/sparql})
     */
    public GregverseSearchClient(String sparqlEndpoint) {
        this(sparqlEndpoint, HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build());
    }

    /**
     * Creates a client with a custom {@link HttpClient}. Use this constructor in
     * tests to inject a test server or mock transport.
     *
     * @param sparqlEndpoint HTTP URL of the Gregverse SPARQL endpoint
     * @param httpClient     custom HttpClient (e.g. backed by a test server)
     */
    public GregverseSearchClient(String sparqlEndpoint, HttpClient httpClient) {
        Objects.requireNonNull(sparqlEndpoint, "sparqlEndpoint must not be null");
        if (sparqlEndpoint.isBlank()) {
            throw new IllegalArgumentException("sparqlEndpoint must not be blank");
        }
        this.sparqlEndpoint = sparqlEndpoint;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * A YAWL workflow specification summary retrieved from the Gregverse registry.
     *
     * @param specId        Gregverse spec identifier (URI fragment)
     * @param specName      human-readable specification name
     * @param provider      organisation or individual who published the spec
     * @param domain        OT performance area
     * @param description   one-sentence description of the workflow
     * @param downloadCount number of times this spec has been instantiated
     */
    public record WorkflowSpecSummary(
            String specId,
            String specName,
            String provider,
            OTDomain domain,
            String description,
            int downloadCount
    ) {
        public WorkflowSpecSummary {
            Objects.requireNonNull(specId, "specId must not be null");
            Objects.requireNonNull(specName, "specName must not be null");
            Objects.requireNonNull(provider, "provider must not be null");
            Objects.requireNonNull(domain, "domain must not be null");
            Objects.requireNonNull(description, "description must not be null");
            if (downloadCount < 0) {
                throw new IllegalArgumentException("downloadCount must not be negative");
            }
        }
    }

    /**
     * Searches the Gregverse registry for workflow specs in the given OT domain.
     * Results are ordered by download count (most-proven first).
     *
     * @param domain the OT performance area to filter by
     * @return list of matching specs; empty if endpoint unreachable or no results
     */
    public List<WorkflowSpecSummary> searchByDomain(OTDomain domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        String query = buildDomainQuery(domain);
        return executeQuery(query, domain);
    }

    /**
     * Full-text keyword search across spec names and descriptions in Gregverse.
     * Each keyword is ANDed together (all keywords must appear).
     *
     * @param keywords one or more search terms (case-insensitive)
     * @return list of matching specs; empty if endpoint unreachable or no results
     */
    public List<WorkflowSpecSummary> searchByKeywords(String... keywords) {
        if (keywords == null || keywords.length == 0) {
            throw new IllegalArgumentException("at least one keyword is required");
        }
        String query = buildKeywordQuery(keywords);
        return executeQuery(query, null);
    }

    /**
     * Combined search: returns OT workflow specs across all three domains,
     * sorted by popularity. Useful for a curated "self-care starter" list.
     *
     * @return list of OT workflow specs across all domains; empty on failure
     */
    public List<WorkflowSpecSummary> searchOTWorkflows() {
        List<WorkflowSpecSummary> all = new ArrayList<>();
        for (OTDomain domain : OTDomain.values()) {
            all.addAll(searchByDomain(domain));
        }
        // Re-sort combined results by download count DESC
        all.sort((a, b) -> Integer.compare(b.downloadCount(), a.downloadCount()));
        return all;
    }

    // ─── Query building ───────────────────────────────────────────────────────

    private String buildDomainQuery(OTDomain domain) {
        // Substitutes the domain filter parameter into the SPARQL SELECT query
        return """
            PREFIX yawl: <http://yawlfoundation.org/yawl#>
            PREFIX greg: <http://gregverse.io/registry#>
            PREFIX dc:   <http://purl.org/dc/elements/1.1/>

            SELECT ?specId ?specName ?provider ?domain ?description ?downloadCount
            WHERE {
                ?spec a yawl:WorkflowSpecification ;
                      yawl:specId        ?specId ;
                      yawl:specName      ?specName ;
                      yawl:domain        ?domain ;
                      greg:provider      ?provider ;
                      greg:downloadCount ?downloadCount .
                OPTIONAL { ?spec dc:description ?description }
                FILTER(LCASE(STR(?domain)) = "%s")
                FILTER NOT EXISTS { ?spec greg:status "draft" }
            }
            ORDER BY DESC(?downloadCount)
            LIMIT 50
            """.formatted(domain.sparqlValue());
    }

    private String buildKeywordQuery(String[] keywords) {
        StringBuilder filters = new StringBuilder();
        for (String kw : keywords) {
            String safe = kw.replace("\"", "").replace("\\", "");
            filters.append("""
                FILTER(CONTAINS(LCASE(?specName), "%s") || CONTAINS(LCASE(?description), "%s"))
                """.formatted(safe.toLowerCase(), safe.toLowerCase()));
        }
        return """
            PREFIX yawl: <http://yawlfoundation.org/yawl#>
            PREFIX greg: <http://gregverse.io/registry#>
            PREFIX dc:   <http://purl.org/dc/elements/1.1/>

            SELECT ?specId ?specName ?provider ?domain ?description ?downloadCount
            WHERE {
                ?spec a yawl:WorkflowSpecification ;
                      yawl:specId        ?specId ;
                      yawl:specName      ?specName ;
                      yawl:domain        ?domain ;
                      greg:provider      ?provider ;
                      greg:downloadCount ?downloadCount .
                OPTIONAL { ?spec dc:description ?description }
                %s
                FILTER NOT EXISTS { ?spec greg:status "draft" }
            }
            ORDER BY DESC(?downloadCount)
            LIMIT 50
            """.formatted(filters.toString());
    }

    // ─── Query execution ──────────────────────────────────────────────────────

    private List<WorkflowSpecSummary> executeQuery(String sparqlQuery, OTDomain hintDomain) {
        try {
            String body = "query=" + URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sparqlEndpoint))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/sparql-results+json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warn("Gregverse SPARQL endpoint returned {}: {}",
                    response.statusCode(), sparqlEndpoint);
                return List.of();
            }

            return parseResults(response.body(), hintDomain);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn("Gregverse SPARQL query failed (endpoint: {}): {}",
                sparqlEndpoint, e.getMessage());
            return List.of();
        }
    }

    private List<WorkflowSpecSummary> parseResults(String json, OTDomain hintDomain) {
        List<WorkflowSpecSummary> results = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode bindings = root.path("results").path("bindings");

            for (JsonNode binding : bindings) {
                String specId        = value(binding, "specId");
                String specName      = value(binding, "specName");
                String provider      = value(binding, "provider");
                String domainStr     = value(binding, "domain");
                String description   = value(binding, "description");
                int downloadCount    = intValue(binding, "downloadCount");

                OTDomain domain;
                try {
                    domain = OTDomain.fromString(domainStr);
                } catch (IllegalArgumentException e) {
                    // If domain in registry doesn't match, use hint if available
                    domain = hintDomain != null ? hintDomain : OTDomain.SELF_CARE;
                }

                if (!specId.isEmpty() && !specName.isEmpty()) {
                    results.add(new WorkflowSpecSummary(
                        specId, specName, provider, domain,
                        description.isEmpty() ? specName : description,
                        downloadCount));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Gregverse SPARQL results: {}", e.getMessage());
        }
        return results;
    }

    private static String value(JsonNode binding, String field) {
        JsonNode node = binding.path(field).path("value");
        return node.isMissingNode() ? "" : node.asText("");
    }

    private static int intValue(JsonNode binding, String field) {
        JsonNode node = binding.path(field).path("value");
        if (node.isMissingNode()) return 0;
        try {
            return Integer.parseInt(node.asText("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
