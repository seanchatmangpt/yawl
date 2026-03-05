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

package org.yawlfoundation.yawl.integration.blueocean.lineage;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RDF-based data lineage store with Lucene indexing for fast queries.
 *
 * <p>Tracks data flow through workflows by recording:</p>
 * <ul>
 *   <li>Data access events (table reads/writes)</li>
 *   <li>Column-level lineage</li>
 *   <li>Task completion with output data snapshots</li>
 *   <li>Case-level data provenance</li>
 * </ul>
 *
 * <p>Thread-safe via internal synchronization. Supports concurrent case execution.</p>
 *
 * <h2>Data Model</h2>
 * <pre>
 * Resource Types:
 *   - DataAccess (with properties: table, columns, operation, timestamp)
 *   - TableSchema (with properties: name, columns, owner)
 *   - TaskExecution (with properties: task_id, case_id, status, duration)
 *   - Column (with properties: name, type, nullable)
 * </pre>
 *
 * <h2>Query Examples</h2>
 * <pre>
 * List<LineagePath> paths = store.queryLineage("customers", 3);
 * RDFGraph graph = store.queryCaseLineage("case-123");
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class RdfLineageStore implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RdfLineageStore.class);

    private static final String LINEAGE_NS = "http://yawl.org/lineage#";
    private static final String TASK_NS = "http://yawl.org/task#";
    private static final String TABLE_NS = "http://yawl.org/table#";

    private final Dataset dataset;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final IndexWriter luceneWriter;
    private final Directory luceneDir;
    private final Map<String, Instant> lastAccessCache;
    private final ExecutorService asyncExecutor;

    /**
     * Creates a new RDF lineage store backed by TDB2 and Lucene.
     *
     * @param tdb2Path Path to TDB2 database directory
     * @param luceneIndexPath Path to Lucene index directory
     * @throws IllegalArgumentException if paths are invalid
     */
    public RdfLineageStore(@NonNull String tdb2Path, @NonNull String luceneIndexPath) {
        try {
            this.dataset = TDB2Factory.connectDataset(tdb2Path);
            this.luceneDir = FSDirectory.open(java.nio.file.Paths.get(luceneIndexPath));
            this.luceneWriter = new IndexWriter(luceneDir,
                    new IndexWriterConfig(new StandardAnalyzer()));
            this.lastAccessCache = Collections.synchronizedMap(new LinkedHashMap<>());
            this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

            logger.info("Initialized RDF lineage store: tdb2={}, lucene={}", tdb2Path, luceneIndexPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to initialize lineage store", e);
        }
    }

    /**
     * Records a data access event (table read/write).
     *
     * @param caseId Workflow case identifier
     * @param taskId Task identifier
     * @param tableId Table identifier (must match schema)
     * @param columns Column names accessed (empty = all columns)
     * @param operation READ, WRITE, UPDATE, DELETE
     * @param timestamp Access timestamp
     * @throws IllegalArgumentException if operation or tableId invalid
     */
    public void recordDataAccess(
            @NonNull String caseId,
            @NonNull String taskId,
            @NonNull String tableId,
            @NonNull String[] columns,
            @NonNull String operation,
            @NonNull Instant timestamp) {

        validateOperation(operation);

        lock.writeLock().lock();
        try {
            Model model = dataset.getDefaultModel();
            Resource accessRes = model.createResource(
                    LINEAGE_NS + "access_" + UUID.randomUUID());

            Property operationProp = model.createProperty(LINEAGE_NS + "operation");
            Property tableProp = model.createProperty(LINEAGE_NS + "table");
            Property columnProp = model.createProperty(LINEAGE_NS + "column");
            Property timestampProp = model.createProperty(LINEAGE_NS + "timestamp");
            Property caseProp = model.createProperty(LINEAGE_NS + "case_id");
            Property taskProp = model.createProperty(LINEAGE_NS + "task_id");

            accessRes.addProperty(RDF.type,
                    model.createResource(LINEAGE_NS + "DataAccess"));
            accessRes.addProperty(operationProp, operation);
            accessRes.addProperty(tableProp, tableId);
            accessRes.addProperty(timestampProp, timestamp.toString());
            accessRes.addProperty(caseProp, caseId);
            accessRes.addProperty(taskProp, taskId);

            for (String column : columns) {
                accessRes.addProperty(columnProp, column);
            }

            dataset.commit();

            // Update cache and index
            lastAccessCache.put(tableId + ":" + caseId, timestamp);
            indexAccessEvent(caseId, taskId, tableId, operation, columns);

            logger.debug("Recorded data access: case={}, task={}, table={}, op={}, ts={}",
                    caseId, taskId, tableId, operation, timestamp);

        } catch (Exception e) {
            dataset.abort();
            logger.error("Failed to record data access", e);
            throw new RuntimeException("Data access recording failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Records a task completion with output data snapshot.
     *
     * @param caseId Workflow case identifier
     * @param taskId Task identifier
     * @param outputData Output data as JSON or XML serialization
     * @throws IllegalArgumentException if outputData is null or malformed
     */
    public void recordTaskCompletion(
            @NonNull String caseId,
            @NonNull String taskId,
            @NonNull String outputData) {

        if (outputData.trim().isEmpty()) {
            throw new IllegalArgumentException("Output data cannot be empty");
        }

        lock.writeLock().lock();
        try {
            Model model = dataset.getDefaultModel();
            Resource taskRes = model.createResource(
                    TASK_NS + taskId + "_" + caseId);

            Property statusProp = model.createProperty(LINEAGE_NS + "status");
            Property outputProp = model.createProperty(LINEAGE_NS + "output_data");
            Property completedAtProp = model.createProperty(LINEAGE_NS + "completed_at");

            taskRes.addProperty(RDF.type,
                    model.createResource(TASK_NS + "TaskExecution"));
            taskRes.addProperty(statusProp, "COMPLETE");
            taskRes.addProperty(outputProp, outputData);
            taskRes.addProperty(completedAtProp, Instant.now().toString());

            dataset.commit();

            logger.debug("Recorded task completion: case={}, task={}, dataSize={}",
                    caseId, taskId, outputData.length());

        } catch (Exception e) {
            dataset.abort();
            logger.error("Failed to record task completion", e);
            throw new RuntimeException("Task completion recording failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Queries backward data lineage for a table (what tables contribute to it).
     *
     * @param tableId Table identifier
     * @param maxDepth Maximum lineage chain depth (1-10)
     * @return List of lineage paths
     * @throws IllegalArgumentException if maxDepth is invalid
     */
    public List<LineagePath> queryLineage(@NonNull String tableId, int maxDepth) {
        if (maxDepth < 1 || maxDepth > 10) {
            throw new IllegalArgumentException("maxDepth must be 1-10");
        }

        lock.readLock().lock();
        try {
            String sparqlQuery = String.format("""
                    PREFIX l: <%s>
                    PREFIX t: <%s>

                    SELECT ?sourceTable ?operation ?timestamp
                    WHERE {
                        ?access l:table "%s" ;
                                l:operation ?operation ;
                                l:timestamp ?timestamp ;
                                a l:DataAccess .
                        OPTIONAL {
                            ?sourceAccess l:table ?sourceTable ;
                                         l:timestamp ?sourceTime .
                            FILTER (?sourceTime < ?timestamp)
                        }
                    }
                    ORDER BY DESC(?timestamp)
                    LIMIT 1000
                    """, LINEAGE_NS, TASK_NS, tableId);

            QueryExecution qExec = QueryExecutionFactory.create(sparqlQuery, dataset);
            ResultSet results = qExec.execSelect();

            List<LineagePath> paths = new ArrayList<>();
            while (results.hasNext()) {
                QuerySolution soln = results.next();
                String sourceTable = soln.contains("sourceTable")
                        ? soln.getLiteral("sourceTable").getString()
                        : null;
                String operation = soln.getLiteral("operation").getString();
                String timestamp = soln.getLiteral("timestamp").getString();

                paths.add(new LineagePath(sourceTable, tableId, operation, timestamp));
            }
            qExec.close();

            return paths;

        } catch (Exception e) {
            logger.error("Failed to query lineage for table: {}", tableId, e);
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Queries the complete data lineage graph for a case (all data accesses and transformations).
     *
     * @param caseId Case identifier
     * @return RDF graph as N-Triples string
     * @throws RuntimeException if SPARQL query execution fails
     */
    public String queryCaseLineage(@NonNull String caseId) {
        lock.readLock().lock();
        try {
            String sparqlQuery = String.format("""
                    PREFIX l: <%s>
                    PREFIX t: <%s>

                    CONSTRUCT {
                        ?access ?p ?o .
                        ?task ?tp ?to .
                    }
                    WHERE {
                        {
                            ?access l:case_id "%s" ;
                                    ?p ?o .
                            ?access a l:DataAccess .
                        } UNION {
                            ?task l:case_id "%s" ;
                                  ?tp ?to .
                            ?task a t:TaskExecution .
                        }
                    }
                    """, LINEAGE_NS, TASK_NS, caseId, caseId);

            QueryExecution qExec = QueryExecutionFactory.create(sparqlQuery, dataset);
            Model resultModel = qExec.execConstruct();

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            RDFDataMgr.write(out, resultModel, Lang.NTRIPLES);
            qExec.close();

            return out.toString();

        } catch (Exception e) {
            String errorMsg = String.format(
                    "Failed to execute SPARQL CONSTRUCT for case '%s': %s. " +
                    "Verify case exists in RDF store and SPARQL syntax is valid.",
                    caseId, e.getMessage());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Searches for data accesses matching criteria (Lucene-backed).
     *
     * @param criteria Query criteria (e.g., "table:orders AND operation:WRITE")
     * @param limit Maximum results
     * @return List of matching case IDs
     * @throws IllegalArgumentException if criteria is invalid
     */
    public List<String> searchLineage(@NonNull String criteria, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be >= 1");
        }

        try {
            Query query = new QueryParser("criteria", new StandardAnalyzer()).parse(criteria);
            IndexSearcher searcher = new IndexSearcher(
                    org.apache.lucene.index.DirectoryReader.open(luceneDir));

            TopDocs topDocs = searcher.search(query, limit);
            List<String> results = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                results.add(doc.get("case_id"));
            }

            searcher.getIndexReader().close();
            return results;

        } catch (Exception e) {
            logger.error("Search failed for criteria: {}", criteria, e);
            return Collections.emptyList();
        }
    }

    /**
     * Gets the lineage impact: tables affected by changes to a source table.
     *
     * @param sourceTableId Source table
     * @param maxDepth Maximum impact depth
     * @return Map of affected tables to operation type
     */
    public Map<String, String> getLineageImpact(@NonNull String sourceTableId, int maxDepth) {
        lock.readLock().lock();
        try {
            String sparqlQuery = String.format("""
                    PREFIX l: <%s>

                    SELECT ?affectedTable ?operation
                    WHERE {
                        ?access1 l:table "%s" ;
                                 l:operation "WRITE" ;
                                 l:timestamp ?ts1 .
                        ?access2 l:table ?affectedTable ;
                                 l:timestamp ?ts2 .
                        FILTER (?ts2 > ?ts1)
                    }
                    """, LINEAGE_NS, sourceTableId);

            QueryExecution qExec = QueryExecutionFactory.create(sparqlQuery, dataset);
            ResultSet results = qExec.execSelect();

            Map<String, String> impact = new LinkedHashMap<>();
            while (results.hasNext()) {
                QuerySolution soln = results.next();
                String affectedTable = soln.getLiteral("affectedTable").getString();
                String operation = soln.contains("operation")
                        ? soln.getLiteral("operation").getString()
                        : "UNKNOWN";
                impact.put(affectedTable, operation);
            }
            qExec.close();

            return impact;

        } catch (Exception e) {
            logger.error("Failed to compute lineage impact", e);
            return Collections.emptyMap();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Exports lineage graph in RDF format.
     *
     * @param format RDF format (RDF/XML, TTL, JSONLD)
     * @return Serialized RDF graph
     * @throws RuntimeException if RDF export fails
     */
    public String exportLineageGraph(@NonNull String format) {
        lock.readLock().lock();
        try {
            Lang lang = switch (format.toUpperCase()) {
                case "TTL" -> Lang.TURTLE;
                case "JSONLD" -> Lang.JSONLD;
                default -> Lang.RDFXML;
            };

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            RDFDataMgr.write(out, dataset.getDefaultModel(), lang);

            return out.toString();

        } catch (Exception e) {
            String errorMsg = String.format(
                    "Failed to export RDF lineage graph in format '%s': %s. " +
                    "Verify RDF store is initialized and format is valid (RDF/XML, TTL, JSONLD).",
                    format, e.getMessage());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    // === Private Helpers ===

    private void validateOperation(@NonNull String operation) {
        if (!Set.of("READ", "WRITE", "UPDATE", "DELETE").contains(operation.toUpperCase())) {
            throw new IllegalArgumentException("Invalid operation: " + operation);
        }
    }

    private void indexAccessEvent(String caseId, String taskId, String tableId,
                                   String operation, String[] columns) {
        asyncExecutor.submit(() -> {
            try {
                Document doc = new Document();
                doc.add(new StringField("case_id", caseId, Field.Store.YES));
                doc.add(new StringField("task_id", taskId, Field.Store.YES));
                doc.add(new StringField("table_id", tableId, Field.Store.YES));
                doc.add(new TextField("operation", operation, Field.Store.YES));
                doc.add(new TextField("columns", String.join(",", columns), Field.Store.YES));
                doc.add(new StringField("timestamp", Instant.now().toEpochMilli() + "", Field.Store.YES));

                synchronized (luceneWriter) {
                    luceneWriter.addDocument(doc);
                    luceneWriter.commit();
                }
            } catch (Exception e) {
                logger.warn("Failed to index access event", e);
            }
        });
    }

    @Override
    public void close() {
        try {
            asyncExecutor.shutdown();
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }

            synchronized (luceneWriter) {
                luceneWriter.close();
            }
            luceneDir.close();
            dataset.close();

            logger.info("Closed RDF lineage store");
        } catch (Exception e) {
            logger.error("Error closing lineage store", e);
        }
    }

    /**
     * Immutable record representing a data lineage path.
     */
    public record LineagePath(
            @Nullable String sourceTable,
            @NonNull String targetTable,
            @NonNull String operation,
            @NonNull String timestamp
    ) {}
}
