-- Database Initialization Script for YAWL + Teradata
-- This script creates the required databases and users

-- =============================================================================
-- Create YAWL Production Database
-- =============================================================================

CREATE DATABASE ${yawl_db_name}
    AS PERMANENT = ${permanent_space_gb}e9,
       SPOOL = ${spool_space_gb}e9,
       DEFAULT MAP = TD_MAP1;

COMMENT ON DATABASE ${yawl_db_name} AS 'YAWL Workflow Engine Production Database';

-- =============================================================================
-- Create YAWL Staging Database
-- =============================================================================

CREATE DATABASE ${staging_db_name}
    AS PERMANENT = ${permanent_space_gb}e9 / 2,
       SPOOL = ${spool_space_gb}e9 / 2,
       DEFAULT MAP = TD_MAP1;

COMMENT ON DATABASE ${staging_db_name} AS 'YAWL Workflow Engine Staging Database';

-- =============================================================================
-- Create YAWL Analytics Database
-- =============================================================================

CREATE DATABASE ${analytics_db_name}
    AS PERMANENT = ${permanent_space_gb}e9 * 0.75,
       SPOOL = ${spool_space_gb}e9 * 0.75,
       DEFAULT MAP = TD_MAP1;

COMMENT ON DATABASE ${analytics_db_name} AS 'YAWL Workflow Engine Analytics Database';

-- =============================================================================
-- Create Application User
-- =============================================================================

CREATE USER ${yawl_db_name}_app
    AS PERMANENT = ${permanent_space_gb}e9 / 10,
       SPOOL = ${spool_space_gb}e9 / 10,
       PASSWORD = (EXPIRE_PASSWORD = 0);

COMMENT ON USER ${yawl_db_name}_app AS 'YAWL Application Service Account';

-- =============================================================================
-- Create Roles
-- =============================================================================

-- Admin role - full access
CREATE ROLE ${yawl_db_name}_admin;

-- Operator role - operational access
CREATE ROLE ${yawl_db_name}_operator;

-- Analyst role - read-only analytics
CREATE ROLE ${yawl_db_name}_analyst;

-- Readonly role - minimal read access
CREATE ROLE ${yawl_db_name}_readonly;

-- =============================================================================
-- Grant Privileges to Roles
-- =============================================================================

-- Admin privileges
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER
    ON ${yawl_db_name} TO ${yawl_db_name}_admin;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER
    ON ${staging_db_name} TO ${yawl_db_name}_admin;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER
    ON ${analytics_db_name} TO ${yawl_db_name}_admin;

-- Operator privileges
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ${yawl_db_name} TO ${yawl_db_name}_operator;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON ${staging_db_name} TO ${yawl_db_name}_operator;

-- Analyst privileges
GRANT SELECT
    ON ${yawl_db_name} TO ${yawl_db_name}_analyst;

GRANT SELECT, CREATE TABLE, CREATE VIEW
    ON ${analytics_db_name} TO ${yawl_db_name}_analyst;

-- Readonly privileges
GRANT SELECT
    ON ${analytics_db_name} TO ${yawl_db_name}_readonly;

-- =============================================================================
-- Assign Roles to Application User
-- =============================================================================

GRANT ${yawl_db_name}_operator TO ${yawl_db_name}_app;

-- =============================================================================
-- Create Additional Users (Optional)
-- =============================================================================

-- Analytics user
CREATE USER ${yawl_db_name}_analyst_user
    AS PERMANENT = 1e9,
       SPOOL = 2e9,
       PASSWORD = (EXPIRE_PASSWORD = 90);

GRANT ${yawl_db_name}_analyst TO ${yawl_db_name}_analyst_user;

-- Report user
CREATE USER ${yawl_db_name}_report_user
    AS PERMANENT = 500e6,
       SPOOL = 1e9,
       PASSWORD = (EXPIRE_PASSWORD = 90);

GRANT ${yawl_db_name}_readonly TO ${yawl_db_name}_report_user;
