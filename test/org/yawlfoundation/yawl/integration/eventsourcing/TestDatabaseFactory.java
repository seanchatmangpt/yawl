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

import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Delegate wrapper for EventSourcingTestFixture providing additional utilities.
 * This class delegates to EventSourcingTestFixture for schema setup and provides
 * additional helper methods for Chicago TDD tests.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see EventSourcingTestFixture
 * @deprecated Use {@link EventSourcingTestFixture} directly for new tests
 */
@Deprecated(since = "6.0.0", forRemoval = true)
public final class TestDatabaseFactory {

    private TestDatabaseFactory() {
        throw new UnsupportedOperationException("Use EventSourcingTestFixture directly");
    }

    /**
     * Creates an H2 in-memory data source with the workflow_events schema initialized.
     * Delegates to EventSourcingTestFixture.
     *
     * @return a configured H2 DataSource with event store schema
     * @throws SQLException if schema initialization fails
     */
    public static DataSource createEventStoreDataSource() throws SQLException {
        DataSource ds = EventSourcingTestFixture.createDataSource();
        EventSourcingTestFixture.createSchema(ds);
        return ds;
    }

    /**
     * Creates an H2 in-memory data source with the case_snapshots schema initialized.
     * Delegates to EventSourcingTestFixture.
     *
     * @return a configured H2 DataSource with snapshot repository schema
     * @throws SQLException if schema initialization fails
     */
    public static DataSource createSnapshotDataSource() throws SQLException {
        DataSource ds = EventSourcingTestFixture.createDataSource();
        EventSourcingTestFixture.createSchema(ds);
        return ds;
    }

    /**
     * Creates an H2 in-memory data source with both event store and snapshot schemas.
     * Delegates to EventSourcingTestFixture.
     *
     * @return a configured H2 DataSource with both schemas initialized
     * @throws SQLException if schema initialization fails
     */
    public static DataSource createCombinedDataSource() throws SQLException {
        DataSource ds = EventSourcingTestFixture.createDataSource();
        EventSourcingTestFixture.createSchema(ds);
        return ds;
    }

    /**
     * Clears all events from the workflow_events table.
     *
     * @param dataSource the data source to clear
     * @throws SQLException if the clear operation fails
     */
    public static void clearEventStore(DataSource dataSource) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM workflow_events");
        }
    }

    /**
     * Clears all snapshots from the case_snapshots table.
     *
     * @param dataSource the data source to clear
     * @throws SQLException if the clear operation fails
     */
    public static void clearSnapshots(DataSource dataSource) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM case_snapshots");
        }
    }

    /**
     * Counts events in the workflow_events table for verification.
     *
     * @param dataSource the data source to query
     * @return the number of events in the table
     * @throws SQLException if the query fails
     */
    public static int countEvents(DataSource dataSource) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM workflow_events")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Counts snapshots in the case_snapshots table for verification.
     *
     * @param dataSource the data source to query
     * @return the number of snapshots in the table
     * @throws SQLException if the query fails
     */
    public static int countSnapshots(DataSource dataSource) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM case_snapshots")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
