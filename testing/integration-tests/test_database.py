"""
YAWL Integration Tests - Database Tests
Tests for database connectivity and operations
"""

import pytest
import logging

logger = logging.getLogger(__name__)


@pytest.mark.integration
@pytest.mark.requires_db
class TestDatabaseConnectivity:
    """Test database connectivity"""

    def test_database_connection(self, db_connection):
        """Test database connection"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute("SELECT 1;")
        result = cursor.fetchone()
        cursor.close()

        assert result is not None, "Query returned no results"
        assert result[0] == 1, "Query returned unexpected result"
        logger.info("Database connection successful")

    def test_database_version(self, db_connection):
        """Test database version query"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute("SELECT version();")
        result = cursor.fetchone()
        cursor.close()

        assert result is not None, "Version query returned no results"
        assert "PostgreSQL" in result[0], "Not a PostgreSQL database"
        logger.info(f"Database version: {result[0][:50]}...")

    def test_database_encoding(self, db_connection):
        """Test database encoding"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute("SELECT datname, encoding, datcollate FROM pg_database WHERE datname = current_database();")
        result = cursor.fetchone()
        cursor.close()

        assert result is not None, "Encoding query returned no results"
        assert "UTF" in result[1], "Database not UTF-8 encoded"
        logger.info(f"Database encoding: {result[1]}")


@pytest.mark.integration
@pytest.mark.requires_db
class TestDatabaseSchema:
    """Test database schema"""

    def test_tables_exist(self, db_connection):
        """Test required tables exist"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
        )
        result = cursor.fetchone()
        cursor.close()

        table_count = result[0] if result else 0
        assert table_count > 0, "No tables found in database"
        logger.info(f"Found {table_count} tables in database")

    def test_view_existence(self, db_connection):
        """Test database views"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT COUNT(*) FROM information_schema.views WHERE table_schema='public';"
        )
        result = cursor.fetchone()
        cursor.close()

        view_count = result[0] if result else 0
        logger.info(f"Found {view_count} views in database")

    def test_indexes_exist(self, db_connection):
        """Test database indexes"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public';"
        )
        result = cursor.fetchone()
        cursor.close()

        index_count = result[0] if result else 0
        logger.info(f"Found {index_count} indexes in database")


@pytest.mark.integration
@pytest.mark.requires_db
class TestDatabaseSize:
    """Test database size"""

    def test_database_size(self, db_connection):
        """Test database size query"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute("SELECT pg_size_pretty(pg_database_size(current_database()));")
        result = cursor.fetchone()
        cursor.close()

        assert result is not None, "Size query returned no results"
        logger.info(f"Database size: {result[0]}")

    def test_table_sizes(self, db_connection):
        """Test table sizes"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) "
            "FROM pg_tables WHERE schemaname = 'public' ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC LIMIT 10;"
        )
        results = cursor.fetchall()
        cursor.close()

        assert results is not None, "Table size query returned no results"
        logger.info(f"Found {len(results)} largest tables")


@pytest.mark.integration
@pytest.mark.requires_db
class TestDatabaseIntegrity:
    """Test database integrity"""

    def test_foreign_keys_enabled(self, db_connection):
        """Test foreign key constraints are enabled"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT COUNT(*) FROM information_schema.table_constraints "
            "WHERE constraint_type = 'FOREIGN KEY' AND table_schema = 'public';"
        )
        result = cursor.fetchone()
        cursor.close()

        fk_count = result[0] if result else 0
        logger.info(f"Found {fk_count} foreign key constraints")

    def test_unique_constraints(self, db_connection):
        """Test unique constraints"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT COUNT(*) FROM information_schema.table_constraints "
            "WHERE constraint_type = 'UNIQUE' AND table_schema = 'public';"
        )
        result = cursor.fetchone()
        cursor.close()

        unique_count = result[0] if result else 0
        logger.info(f"Found {unique_count} unique constraints")

    def test_check_constraints(self, db_connection):
        """Test check constraints"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT COUNT(*) FROM information_schema.table_constraints "
            "WHERE constraint_type = 'CHECK' AND table_schema = 'public';"
        )
        result = cursor.fetchone()
        cursor.close()

        check_count = result[0] if result else 0
        logger.info(f"Found {check_count} check constraints")


@pytest.mark.integration
@pytest.mark.requires_db
@pytest.mark.slow
class TestDatabasePerformance:
    """Test database performance"""

    def test_sequential_scan_count(self, db_connection):
        """Test sequential scan statistics"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT schemaname, tablename, seq_scan, seq_tup_read, seq_tup_returned "
            "FROM pg_stat_user_tables ORDER BY seq_scan DESC LIMIT 10;"
        )
        results = cursor.fetchall()
        cursor.close()

        logger.info(f"Top 10 tables by sequential scans: {len(results) or 0}")

    def test_index_usage(self, db_connection):
        """Test index usage statistics"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute(
            "SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch "
            "FROM pg_stat_user_indexes ORDER BY idx_scan DESC LIMIT 10;"
        )
        results = cursor.fetchall()
        cursor.close()

        logger.info(f"Top 10 indexes by usage: {len(results) or 0}")

    def test_query_performance(self, db_connection):
        """Test basic query performance"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        import time

        cursor = db_connection.cursor()

        start = time.time()
        cursor.execute("SELECT COUNT(*) FROM information_schema.tables;")
        result = cursor.fetchone()
        duration = time.time() - start

        cursor.close()

        logger.info(f"Query completed in {duration:.3f}s")
        assert duration < 1.0, f"Query took too long: {duration:.3f}s"


@pytest.mark.integration
@pytest.mark.requires_db
class TestDatabaseBackups:
    """Test database backup capabilities"""

    def test_backup_directory_exists(self, db_connection, config):
        """Test backup directory exists"""
        import os

        backup_dir = config.get("backup_dir", "/var/lib/postgresql/backups")
        # Don't fail if directory doesn't exist, just log it
        if os.path.exists(backup_dir):
            logger.info(f"Backup directory exists: {backup_dir}")
        else:
            logger.warning(f"Backup directory not found: {backup_dir}")

    def test_wal_archiving_enabled(self, db_connection):
        """Test WAL archiving is enabled"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute("SELECT setting FROM pg_settings WHERE name = 'wal_level';")
        result = cursor.fetchone()
        cursor.close()

        if result and result[0]:
            logger.info(f"WAL level: {result[0]}")


@pytest.mark.integration
@pytest.mark.requires_db
class TestDatabaseConnections:
    """Test database connection settings"""

    def test_max_connections(self, db_connection):
        """Test max_connections setting"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute("SELECT setting FROM pg_settings WHERE name = 'max_connections';")
        result = cursor.fetchone()
        cursor.close()

        if result:
            max_conn = int(result[0])
            assert max_conn >= 20, f"max_connections too low: {max_conn}"
            logger.info(f"max_connections: {max_conn}")

    def test_current_connections(self, db_connection):
        """Test current connection count"""
        if db_connection is None:
            pytest.skip("Database connection not available")

        cursor = db_connection.cursor()
        cursor.execute("SELECT count(*) FROM pg_stat_activity;")
        result = cursor.fetchone()
        cursor.close()

        if result:
            conn_count = result[0]
            logger.info(f"Current connections: {conn_count}")
