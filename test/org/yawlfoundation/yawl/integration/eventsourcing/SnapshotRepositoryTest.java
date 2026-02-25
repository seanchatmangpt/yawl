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
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.eventsourcing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SnapshotRepository}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@ExtendWith(MockitoExtension.class)
class SnapshotRepositoryTest {

    @Mock
    private DataSource mockDataSource;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    private SnapshotRepository snapshotRepository;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");
    private static final long TEST_SEQUENCE_NUMBER = 42;

    @BeforeEach
    void setUp() throws SQLException {
        snapshotRepository = new SnapshotRepository(mockDataSource);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructWithDefaultThreshold")
        void constructWithDefaultThreshold() {
            DataSource ds = mock(DataSource.class);
            SnapshotRepository repo = new SnapshotRepository(ds);

            assertEquals(SnapshotRepository.DEFAULT_SNAPSHOT_THRESHOLD, repo.snapshotThreshold);
        }

        @Test
        @DisplayName("constructWithCustomThreshold")
        void constructWithCustomThreshold() {
            DataSource ds = mock(DataSource.class);
            int customThreshold = 50;
            SnapshotRepository repo = new SnapshotRepository(ds, customThreshold);

            assertEquals(customThreshold, repo.snapshotThreshold);
        }

        @Test
        @DisplayName("constructWithZeroThresholdUsesDefault")
        void constructWithZeroThresholdUsesDefault() {
            DataSource ds = mock(DataSource.class);
            SnapshotRepository repo = new SnapshotRepository(ds, 0);

            assertEquals(SnapshotRepository.DEFAULT_SNAPSHOT_THRESHOLD, repo.snapshotThreshold);
        }

        @Test
        @DisplayName("constructWithNegativeThresholdUsesDefault")
        void constructWithNegativeThresholdUsesDefault() {
            DataSource ds = mock(DataSource.class);
            SnapshotRepository repo = new SnapshotRepository(ds, -10);

            assertEquals(SnapshotRepository.DEFAULT_SNAPSHOT_THRESHOLD, repo.snapshotThreshold);
        }

        @Test
        @DisplayName("constructWithNullDataSourceThrows")
        void constructWithNullDataSourceThrows() {
            assertThrows(NullPointerException.class, () -> new SnapshotRepository(null));
        }
    }

    @Nested
    @DisplayName("Save Snapshot")
    class SaveSnapshotTest {

        @Test
        @DisplayName("saveSnapshotSuccess")
        void saveSnapshotSuccess() throws Exception {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED", BASE_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "STARTED", BASE_TIMESTAMP.plusSeconds(30))
            );

            Map<String, String> payload = Map.of(
                "startedBy", "agent-order-service",
                "priority", "high"
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, BASE_TIMESTAMP,
                "RUNNING", workItems, payload);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).close();

            snapshotRepository.save(snapshot);

            verify(mockPreparedStatement, times(1)).executeUpdate();
            verify(mockConnection, times(1)).close();
        }

        @Test
        @DisplayName("saveSnapshotWithNullWorkItems")
        void saveSnapshotWithNullWorkItems() throws Exception {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, BASE_TIMESTAMP,
                "COMPLETED", null, Map.of("finalStatus", "success"));

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).close();

            snapshotRepository.save(snapshot);

            verify(mockPreparedStatement, times(1)).executeUpdate();
            verify(mockConnection, times(1)).close();
        }

        @Test
        @DisplayName("saveSnapshotWithNullPayload")
        void saveSnapshotWithNullPayload() throws Exception {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "COMPLETED", BASE_TIMESTAMP)
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, BASE_TIMESTAMP,
                "COMPLETED", workItems, null);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).close();

            snapshotRepository.save(snapshot);

            verify(mockPreparedStatement, times(1)).executeUpdate();
            verify(mockConnection, times(1)).close();
        }

        @Test
        @DisplayName("saveSnapshotThrowsOnNullSnapshot")
        void saveSnapshotThrowsOnNullSnapshot() {
            assertThrows(NullPointerException.class, () -> snapshotRepository.save(null));
        }

        @Test
        @DisplayName("saveSnapshotThrowsOnSQLException")
        void saveSnapshotThrowsOnSQLException() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
            when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Write failed"));
            doNothing().when(mockConnection).close();

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, BASE_TIMESTAMP,
                "RUNNING", Map.of(), Map.of());

            assertThrows(SnapshotRepository.SnapshotException.class, () -> snapshotRepository.save(snapshot));
        }

        @Test
        @DisplayName("saveSnapshotWithSQLExceptionWrapsCause")
        void saveSnapshotWithSQLExceptionWrapsCause() throws Exception {
            SQLException cause = new SQLException("Write failed");
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
            when(mockPreparedStatement.executeUpdate()).thenThrow(cause);
            doNothing().when(mockConnection).close();

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, BASE_TIMESTAMP,
                "RUNNING", Map.of(), Map.of());

            try {
                snapshotRepository.save(snapshot);
                fail("Should have thrown exception");
            } catch (SnapshotRepository.SnapshotException e) {
                assertEquals("Failed to save snapshot for case test-case-123", e.getMessage());
                assertEquals(cause, e.getCause());
            }
        }
    }

    @Nested
    @DisplayName("Find Latest Snapshot")
    class FindLatestSnapshotTest {

        @Test
        @DisplayName("findLatestSnapshotExists")
        void findLatestSnapshotExists() throws Exception {
            Instant snapshotTime = BASE_TIMESTAMP.plusSeconds(60);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getLong("seq_num")).thenReturn(TEST_SEQUENCE_NUMBER);
            when(mockResultSet.getTimestamp("snapshot_ts")).thenReturn(Timestamp.from(snapshotTime));
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("status")).thenReturn("RUNNING");
            when(mockResultSet.getString("active_items_json")).thenReturn("{}");
            when(mockResultSet.getString("payload_json")).thenReturn("{}");
            doNothing().when(mockConnection).close();

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(TEST_CASE_ID);

            assertTrue(result.isPresent());
            CaseSnapshot snapshot = result.get();
            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
            assertEquals(TEST_SEQUENCE_NUMBER, snapshot.getSequenceNumber());
            assertEquals(snapshotTime, snapshot.getSnapshotAt());
            assertEquals("RUNNING", snapshot.getStatus());
            assertTrue(snapshot.getActiveWorkItems().isEmpty());
            assertTrue(snapshot.getPayload().isEmpty());
            verify(mockConnection, times(1)).close();
        }

        @Test
        @DisplayName("findLatestSnapshotNotFound")
        void findLatestSnapshotNotFound() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(false);
            doNothing().when(mockConnection).close();

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(TEST_CASE_ID);

            assertFalse(result.isPresent());
            verify(mockConnection, times(1)).close();
        }

        @Test
        @DisplayName("findLatestSnapshotThrowsOnBlankCaseId")
        void findLatestSnapshotThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () -> snapshotRepository.findLatest(""));
        }

        @Test
        @DisplayName("findLatestSnapshotThrowsOnNullCaseId")
        void findLatestSnapshotThrowsOnNullCaseId() {
            assertThrows(IllegalArgumentException.class, () -> snapshotRepository.findLatest(null));
        }

        @Test
        @DisplayName("findLatestSnapshotThrowsOnSQLException")
        void findLatestSnapshotThrowsOnSQLException() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("Read failed"));
            doNothing().when(mockConnection).close();

            assertThrows(SnapshotRepository.SnapshotException.class, () ->
                snapshotRepository.findLatest(TEST_CASE_ID));
        }

        @Test
        @DisplayName("findLatestSnapshotWithSQLExceptionWrapsCause")
        void findLatestSnapshotWithSQLExceptionWrapsCause() throws Exception {
            SQLException cause = new SQLException("Read failed");
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenThrow(cause);
            doNothing().when(mockConnection).close();

            try {
                snapshotRepository.findLatest(TEST_CASE_ID);
                fail("Should have thrown exception");
            } catch (SnapshotRepository.SnapshotException e) {
                assertEquals("Failed to find latest snapshot for case test-case-123", e.getMessage());
                assertEquals(cause, e.getCause());
            }
        }
    }

    @Nested
    @DisplayName("Snapshot Threshold Logic")
    class SnapshotThresholdTest {

        @Test
        @DisplayName("shouldSnapshotDefaultThreshold")
        void shouldSnapshotDefaultThreshold() {
            SnapshotRepository repo = new SnapshotRepository(mockDataSource);

            assertFalse(repo.shouldSnapshot(99)); // Below threshold
            assertTrue(repo.shouldSnapshot(100));  // At threshold
            assertTrue(repo.shouldSnapshot(101));  // Above threshold
        }

        @Test
        @DisplayName("shouldSnapshotCustomThreshold")
        void shouldSnapshotCustomThreshold() {
            int customThreshold = 50;
            SnapshotRepository repo = new SnapshotRepository(mockDataSource, customThreshold);

            assertFalse(repo.shouldSnapshot(49));  // Below threshold
            assertTrue(repo.shouldSnapshot(50));   // At threshold
            assertTrue(repo.shouldSnapshot(51));   // Above threshold
        }

        @Test
        @DisplayName("shouldSnapshotWithZeroEvents")
        void shouldSnapshotWithZeroEvents() {
            SnapshotRepository repo = new SnapshotRepository(mockDataSource, 100);
            assertFalse(repo.shouldSnapshot(0));
        }

        @Test
        @DisplayName("shouldSnapshotWithLargeEventCount")
        void shouldSnapshotWithLargeEventCount() {
            SnapshotRepository repo = new SnapshotRepository(mockDataSource, 1000);
            assertFalse(repo.shouldSnapshot(999));
            assertTrue(repo.shouldSnapshot(1000));
            assertTrue(repo.shouldSnapshot(1001));
        }
    }

    @Nested
    @DisplayName("JSON Deserialization")
    class JsonDeserializationTest {

        @Test
        @DisplayName("deserializesComplexWorkItems")
        void deserializesComplexWorkItems() throws Exception {
            String workItemsJson = "{\"wi-1\":{\"workItemId\":\"wi-1\",\"status\":\"STARTED\",\"stateAt\":\"2026-02-17T10:00:30Z\"},\"wi-2\":{\"workItemId\":\"wi-2\",\"status\":\"ENABLED\",\"stateAt\":\"2026-02-17T10:01:00Z\"}}";
            String payloadJson = "{\"startedBy\":\"agent-order-service\",\"priority\":\"high\"}";

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getLong("seq_num")).thenReturn(TEST_SEQUENCE_NUMBER);
            when(mockResultSet.getTimestamp("snapshot_ts")).thenReturn(Timestamp.from(BASE_TIMESTAMP));
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("status")).thenReturn("RUNNING");
            when(mockResultSet.getString("active_items_json")).thenReturn(workItemsJson);
            when(mockResultSet.getString("payload_json")).thenReturn(payloadJson);
            doNothing().when(mockConnection).close();

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(TEST_CASE_ID);

            assertTrue(result.isPresent());
            CaseSnapshot snapshot = result.get();
            assertEquals(2, snapshot.getActiveWorkItems().size());
            assertEquals("STARTED", snapshot.getActiveWorkItems().get("wi-1").status());
            assertEquals("ENABLED", snapshot.getActiveWorkItems().get("wi-2").status());
            assertEquals("high", snapshot.getPayload().get("priority"));
        }

        @Test
        @DisplayName("deserializesEmptyWorkItems")
        void deserializesEmptyWorkItems() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getLong("seq_num")).thenReturn(TEST_SEQUENCE_NUMBER);
            when(mockResultSet.getTimestamp("snapshot_ts")).thenReturn(Timestamp.from(BASE_TIMESTAMP));
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("status")).thenReturn("RUNNING");
            when(mockResultSet.getString("active_items_json")).thenReturn("{}");
            when(mockResultSet.getString("payload_json")).thenReturn("{}");
            doNothing().when(mockConnection).close();

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(TEST_CASE_ID);

            assertTrue(result.isPresent());
            CaseSnapshot snapshot = result.get();
            assertTrue(snapshot.getActiveWorkItems().isEmpty());
            assertTrue(snapshot.getPayload().isEmpty());
        }

        @Test
        @DisplayName("deserializationFailureThrowsSnapshotException")
        void deserializationFailureThrowsSnapshotException() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getLong("seq_num")).thenReturn(TEST_SEQUENCE_NUMBER);
            when(mockResultSet.getTimestamp("snapshot_ts")).thenReturn(Timestamp.from(BASE_TIMESTAMP));
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("status")).thenReturn("RUNNING");
            when(mockResultSet.getString("active_items_json")).thenReturn("invalid-json");
            doNothing().when(mockConnection).close();

            assertThrows(SnapshotRepository.SnapshotException.class, () ->
                snapshotRepository.findLatest(TEST_CASE_ID));
        }

        @Test
        @DisplayName("deserializationExceptionIncludesCaseId")
        void deserializationExceptionIncludesCaseId() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getLong("seq_num")).thenReturn(TEST_SEQUENCE_NUMBER);
            when(mockResultSet.getTimestamp("snapshot_ts")).thenReturn(Timestamp.from(BASE_TIMESTAMP));
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("status")).thenReturn("RUNNING");
            when(mockResultSet.getString("active_items_json")).thenReturn("invalid-json");
            doNothing().when(mockConnection).close();

            try {
                snapshotRepository.findLatest(TEST_CASE_ID);
                fail("Should have thrown exception");
            } catch (SnapshotRepository.SnapshotException e) {
                assertTrue(e.getMessage().contains("Failed to parse snapshot data for case test-case-123"));
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("handleMultipleSnapshotsForSameCase")
        void handleMultipleSnapshotsForSameCase() throws Exception {
            // This test would normally require database setup, but we're mocking the DB
            // In a real scenario, the findLatest query would return the most recent snapshot
            Instant snapshot1Time = BASE_TIMESTAMP;
            Instant snapshot2Time = BASE_TIMESTAMP.plusSeconds(60);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getLong("seq_num")).thenReturn(TEST_SEQUENCE_NUMBER);
            when(mockResultSet.getTimestamp("snapshot_ts")).thenReturn(Timestamp.from(snapshot2Time)); // Later snapshot
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("status")).thenReturn("RUNNING");
            when(mockResultSet.getString("active_items_json")).thenReturn("{}");
            when(mockResultSet.getString("payload_json")).thenReturn("{}");
            doNothing().when(mockConnection).close();

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(TEST_CASE_ID);

            assertTrue(result.isPresent());
            CaseSnapshot snapshot = result.get();
            assertEquals(snapshot2Time, snapshot.getSnapshotAt());
            verify(mockConnection, times(1)).close();
        }

        @Test
        @DisplayName("concurrentSnapshotSave")
        void concurrentSnapshotSave() throws Exception {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, BASE_TIMESTAMP,
                "RUNNING", Map.of(), Map.of());

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString);
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong);
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any);
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).close();

            // Save multiple snapshots concurrently
            snapshotRepository.save(snapshot);
            snapshotRepository.save(snapshot);

            verify(mockPreparedStatement, times(2)).executeUpdate();
        }
    }

    @Nested
    @DisplayName("Snapshot Exception")
    class SnapshotExceptionTest {

        @Test
        @DisplayName("snapshotExceptionWithMessageOnly")
        void snapshotExceptionWithMessageOnly() {
            SnapshotRepository.SnapshotException e =
                new SnapshotRepository.SnapshotException("Test error");

            assertEquals("Test error", e.getMessage());
            assertNull(e.getCause());
        }

        @Test
        @DisplayName("snapshotExceptionWithMessageAndCause")
        void snapshotExceptionWithMessageAndCause() {
            Throwable cause = new RuntimeException("Root cause");
            SnapshotRepository.SnapshotException e =
                new SnapshotRepository.SnapshotException("Test error", cause);

            assertEquals("Test error", e.getMessage());
            assertEquals(cause, e.getCause());
        }
    }
}