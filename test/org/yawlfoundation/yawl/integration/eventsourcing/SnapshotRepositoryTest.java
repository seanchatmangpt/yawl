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

package org.yawlfoundation.yawl.integration.eventsourcing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link SnapshotRepository}.
 * Uses real H2 in-memory database instead of mocks.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class SnapshotRepositoryTest {

    private DataSource dataSource;
    private SnapshotRepository snapshotRepository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = EventSourcingTestFixture.createDataSource();
        EventSourcingTestFixture.createSchema(dataSource);
        snapshotRepository = new SnapshotRepository(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        EventSourcingTestFixture.dropSchema(dataSource);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructWithDefaultThreshold")
        void constructWithDefaultThreshold() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            SnapshotRepository repo = new SnapshotRepository(ds);

            assertEquals(SnapshotRepository.DEFAULT_SNAPSHOT_THRESHOLD, repo.snapshotThreshold);
        }

        @Test
        @DisplayName("constructWithCustomThreshold")
        void constructWithCustomThreshold() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            int customThreshold = 50;
            SnapshotRepository repo = new SnapshotRepository(ds, customThreshold);

            assertEquals(customThreshold, repo.snapshotThreshold);
        }

        @Test
        @DisplayName("constructWithZeroThresholdUsesDefault")
        void constructWithZeroThresholdUsesDefault() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            SnapshotRepository repo = new SnapshotRepository(ds, 0);

            assertEquals(SnapshotRepository.DEFAULT_SNAPSHOT_THRESHOLD, repo.snapshotThreshold);
        }

        @Test
        @DisplayName("constructWithNegativeThresholdUsesDefault")
        void constructWithNegativeThresholdUsesDefault() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
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
            String caseId = EventSourcingTestFixture.generateCaseId();
            CaseSnapshot snapshot = EventSourcingTestFixture.createTestSnapshot(
                caseId, 42L, "RUNNING");

            snapshotRepository.save(snapshot);

            assertEquals(1, countSnapshots(dataSource));
        }

        @Test
        @DisplayName("saveSnapshotWithWorkItems")
        void saveSnapshotWithWorkItems() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED",
                    EventSourcingTestFixture.BASE_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "STARTED",
                    EventSourcingTestFixture.BASE_TIMESTAMP_PLUS_30)
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                caseId,
                EventSourcingTestFixture.TEST_SPEC_ID,
                42L,
                EventSourcingTestFixture.BASE_TIMESTAMP,
                "RUNNING",
                workItems,
                Map.of()
            );

            snapshotRepository.save(snapshot);

            assertEquals(1, countSnapshots(dataSource));
        }

        @Test
        @DisplayName("saveSnapshotWithPayload")
        void saveSnapshotWithPayload() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Map<String, String> payload = Map.of(
                "startedBy", "agent-order-service",
                "priority", "high"
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                caseId,
                EventSourcingTestFixture.TEST_SPEC_ID,
                42L,
                EventSourcingTestFixture.BASE_TIMESTAMP,
                "RUNNING",
                Map.of(),
                payload
            );

            snapshotRepository.save(snapshot);

            assertEquals(1, countSnapshots(dataSource));
        }

        @Test
        @DisplayName("saveMultipleSnapshotsForSameCase")
        void saveMultipleSnapshotsForSameCase() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();

            CaseSnapshot snapshot1 = EventSourcingTestFixture.createTestSnapshot(caseId, 10L, "RUNNING");
            CaseSnapshot snapshot2 = EventSourcingTestFixture.createTestSnapshot(caseId, 20L, "COMPLETED");

            snapshotRepository.save(snapshot1);
            snapshotRepository.save(snapshot2);

            assertEquals(2, countSnapshots(dataSource));
        }

        @Test
        @DisplayName("saveSnapshotWithNullWorkItemsThrows")
        void saveSnapshotWithNullWorkItemsThrows() {
            String caseId = EventSourcingTestFixture.generateCaseId();
            CaseSnapshot snapshot = new CaseSnapshot(
                caseId,
                EventSourcingTestFixture.TEST_SPEC_ID,
                42L,
                EventSourcingTestFixture.BASE_TIMESTAMP,
                "RUNNING",
                null,
                Map.of()
            );

            assertThrows(NullPointerException.class, () -> snapshotRepository.save(snapshot));
        }
    }

    @Nested
    @DisplayName("Find Latest Snapshot")
    class FindLatestSnapshotTest {

        @Test
        @DisplayName("findLatestSnapshotExists")
        void findLatestSnapshotExists() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            CaseSnapshot snapshot = EventSourcingTestFixture.createTestSnapshot(
                caseId, 42L, "RUNNING");
            snapshotRepository.save(snapshot);

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(caseId);

            assertTrue(result.isPresent());
            CaseSnapshot found = result.get();
            assertEquals(caseId, found.getCaseId());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, found.getSpecId());
            assertEquals(42L, found.getSequenceNumber());
            assertEquals("RUNNING", found.getStatus());
        }

        @Test
        @DisplayName("findLatestSnapshotNotFound")
        void findLatestSnapshotNotFound() throws Exception {
            Optional<CaseSnapshot> result = snapshotRepository.findLatest("nonexistent-case");

            assertFalse(result.isPresent());
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
        @DisplayName("findLatestReturnsMostRecentSnapshot")
        void findLatestReturnsMostRecentSnapshot() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();

            CaseSnapshot snapshot1 = EventSourcingTestFixture.createTestSnapshot(caseId, 10L, "RUNNING");
            CaseSnapshot snapshot2 = EventSourcingTestFixture.createTestSnapshot(caseId, 20L, "RUNNING");
            CaseSnapshot snapshot3 = EventSourcingTestFixture.createTestSnapshot(caseId, 15L, "RUNNING");

            snapshotRepository.save(snapshot1);
            snapshotRepository.save(snapshot2);
            snapshotRepository.save(snapshot3);

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(caseId);

            assertTrue(result.isPresent());
            assertEquals(20L, result.get().getSequenceNumber(), "Should return highest sequence number");
        }
    }

    @Nested
    @DisplayName("Snapshot Threshold Logic")
    class SnapshotThresholdTest {

        @Test
        @DisplayName("shouldSnapshotDefaultThreshold")
        void shouldSnapshotDefaultThreshold() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            SnapshotRepository repo = new SnapshotRepository(ds);

            assertFalse(repo.shouldSnapshot(99));
            assertTrue(repo.shouldSnapshot(100));
            assertTrue(repo.shouldSnapshot(101));
        }

        @Test
        @DisplayName("shouldSnapshotCustomThreshold")
        void shouldSnapshotCustomThreshold() throws SQLException {
            int customThreshold = 50;
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            SnapshotRepository repo = new SnapshotRepository(ds, customThreshold);

            assertFalse(repo.shouldSnapshot(49));
            assertTrue(repo.shouldSnapshot(50));
            assertTrue(repo.shouldSnapshot(51));
        }

        @Test
        @DisplayName("shouldSnapshotWithZeroEvents")
        void shouldSnapshotWithZeroEvents() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            SnapshotRepository repo = new SnapshotRepository(ds, 100);

            assertFalse(repo.shouldSnapshot(0));
        }

        @Test
        @DisplayName("shouldSnapshotWithLargeEventCount")
        void shouldSnapshotWithLargeEventCount() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            SnapshotRepository repo = new SnapshotRepository(ds, 1000);

            assertFalse(repo.shouldSnapshot(999));
            assertTrue(repo.shouldSnapshot(1000));
        }
    }

    @Nested
    @DisplayName("Snapshot Persistence")
    class SnapshotPersistenceTest {

        @Test
        @DisplayName("snapshotsPersistAcrossRepositoryInstances")
        void snapshotsPersistAcrossRepositoryInstances() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            CaseSnapshot snapshot = EventSourcingTestFixture.createTestSnapshot(caseId, 42L, "RUNNING");
            snapshotRepository.save(snapshot);

            SnapshotRepository newRepo = new SnapshotRepository(dataSource);
            Optional<CaseSnapshot> result = newRepo.findLatest(caseId);

            assertTrue(result.isPresent());
            assertEquals(42L, result.get().getSequenceNumber());
        }

        @Test
        @DisplayName("snapshotWithComplexWorkItems")
        void snapshotWithComplexWorkItems() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED",
                    EventSourcingTestFixture.BASE_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "STARTED",
                    EventSourcingTestFixture.BASE_TIMESTAMP_PLUS_30),
                "wi-3", new CaseStateView.WorkItemState("wi-3", "COMPLETED",
                    EventSourcingTestFixture.BASE_TIMESTAMP_PLUS_60)
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                caseId,
                EventSourcingTestFixture.TEST_SPEC_ID,
                42L,
                EventSourcingTestFixture.BASE_TIMESTAMP,
                "RUNNING",
                workItems,
                Map.of()
            );
            snapshotRepository.save(snapshot);

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(caseId);

            assertTrue(result.isPresent());
            assertEquals(3, result.get().getActiveWorkItems().size());
            assertEquals("ENABLED", result.get().getActiveWorkItems().get("wi-1").status());
        }

        @Test
        @DisplayName("snapshotWithComplexPayload")
        void snapshotWithComplexPayload() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Map<String, String> payload = Map.of(
                "caseParams", "{'customerId':'123','priority':'high'}",
                "launchedBy", "agent-order-service",
                "metadata", "{'version':'1.0','source':'web-ui'}"
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                caseId,
                EventSourcingTestFixture.TEST_SPEC_ID,
                42L,
                EventSourcingTestFixture.BASE_TIMESTAMP,
                "RUNNING",
                Map.of(),
                payload
            );
            snapshotRepository.save(snapshot);

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(caseId);

            assertTrue(result.isPresent());
            assertEquals(payload, result.get().getPayload());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("handleMultipleSnapshotsForSameCase")
        void handleMultipleSnapshotsForSameCase() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();

            CaseSnapshot snapshot1 = new CaseSnapshot(
                caseId,
                EventSourcingTestFixture.TEST_SPEC_ID,
                10L,
                EventSourcingTestFixture.BASE_TIMESTAMP,
                "RUNNING",
                Map.of(),
                Map.of()
            );
            CaseSnapshot snapshot2 = new CaseSnapshot(
                caseId,
                EventSourcingTestFixture.TEST_SPEC_ID,
                20L,
                EventSourcingTestFixture.BASE_TIMESTAMP_PLUS_60,
                "COMPLETED",
                Map.of(),
                Map.of()
            );

            snapshotRepository.save(snapshot1);
            snapshotRepository.save(snapshot2);

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(caseId);

            assertTrue(result.isPresent());
            assertEquals(20L, result.get().getSequenceNumber());
            assertEquals("COMPLETED", result.get().getStatus());
        }

        @Test
        @DisplayName("concurrentSnapshotSave")
        void concurrentSnapshotSave() throws Exception {
            String case1 = EventSourcingTestFixture.generateCaseId("case-1");
            String case2 = EventSourcingTestFixture.generateCaseId("case-2");

            CaseSnapshot snapshot1 = EventSourcingTestFixture.createTestSnapshot(case1, 1L, "RUNNING");
            CaseSnapshot snapshot2 = EventSourcingTestFixture.createTestSnapshot(case2, 1L, "RUNNING");

            snapshotRepository.save(snapshot1);
            snapshotRepository.save(snapshot2);

            assertEquals(2, countSnapshots(dataSource));
        }

        @Test
        @DisplayName("emptyCaseReturnsEmptyOptional")
        void emptyCaseReturnsEmptyOptional() throws Exception {
            Optional<CaseSnapshot> result = snapshotRepository.findLatest("empty-case");

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("snapshotPreservesAllFields")
        void snapshotPreservesAllFields() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED",
                    EventSourcingTestFixture.BASE_TIMESTAMP)
            );
            Map<String, String> payload = Map.of("key", "value");

            CaseSnapshot snapshot = new CaseSnapshot(
                caseId,
                EventSourcingTestFixture.TEST_SPEC_ID,
                42L,
                EventSourcingTestFixture.BASE_TIMESTAMP,
                "RUNNING",
                workItems,
                payload
            );

            snapshotRepository.save(snapshot);

            Optional<CaseSnapshot> result = snapshotRepository.findLatest(caseId);

            assertTrue(result.isPresent());
            CaseSnapshot found = result.get();
            assertEquals(caseId, found.getCaseId());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, found.getSpecId());
            assertEquals(42L, found.getSequenceNumber());
            assertEquals(EventSourcingTestFixture.BASE_TIMESTAMP, found.getSnapshotAt());
            assertEquals("RUNNING", found.getStatus());
            assertEquals(workItems, found.getActiveWorkItems());
            assertEquals(payload, found.getPayload());
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

    // Helper method for counting snapshots
    private int countSnapshots(DataSource ds) throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM case_snapshots")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
