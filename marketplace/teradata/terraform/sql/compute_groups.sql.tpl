-- Compute Group Configuration for YAWL + Teradata
-- Creates compute groups and profiles for different workload types

-- =============================================================================
-- Drop Existing Compute Groups (if they exist)
-- =============================================================================

%FOREACH cluster IN (${compute_clusters})
DROP COMPUTE GROUP IF EXISTS CG_${cluster.name};
%ENDFOR;

-- =============================================================================
-- Create Compute Groups
-- =============================================================================

%FOREACH cluster IN (${compute_clusters})
-- Compute Group: ${cluster.name}
CREATE COMPUTE GROUP CG_${cluster.name}
    USING QUERY_STRATEGY('${cluster.type}');

COMMENT ON COMPUTE GROUP CG_${cluster.name} AS 'Compute group for ${cluster.name} workloads';

%ENDFOR;

-- =============================================================================
-- Create Compute Profiles
-- =============================================================================

%FOREACH cluster IN (${compute_clusters})
-- Compute Profile for ${cluster.name}

%IF ${cluster.start_time} != ""
-- Scheduled profile (business hours)
CREATE COMPUTE PROFILE CP_${cluster.name}_SCHEDULED
    IN COMPUTE GROUP CG_${cluster.name}
    ,INSTANCE = ${cluster.size}
    ,INSTANCE TYPE = ${cluster.type}
    USING
        MIN_COMPUTE_COUNT (${cluster.min_instances})
        MAX_COMPUTE_COUNT (${cluster.max_instances})
        SCALING_POLICY ('STANDARD')
        START_TIME ('${cluster.start_time}')
        END_TIME ('${cluster.end_time}')
        COOLDOWN_PERIOD (30);

COMMENT ON COMPUTE PROFILE CP_${cluster.name}_SCHEDULED AS 'Scheduled compute profile for ${cluster.name}';

%ELSE
-- Always-on profile
CREATE COMPUTE PROFILE CP_${cluster.name}_ALWAYSON
    IN COMPUTE GROUP CG_${cluster.name}
    ,INSTANCE = ${cluster.size}
    ,INSTANCE TYPE = ${cluster.type}
    USING
        MIN_COMPUTE_COUNT (${cluster.min_instances})
        MAX_COMPUTE_COUNT (${cluster.max_instances})
        SCALING_POLICY ('STANDARD')
        COOLDOWN_PERIOD (30);

COMMENT ON COMPUTE PROFILE CP_${cluster.name}_ALWAYSON AS 'Always-on compute profile for ${cluster.name}';

%ENDIF

%ENDFOR;

-- =============================================================================
-- Create Compute Group Roles
-- =============================================================================

%FOREACH cluster IN (${compute_clusters})
-- Role for ${cluster.name} compute group access
CREATE ROLE CR_${cluster.name};

GRANT COMPUTE GROUP CG_${cluster.name} TO CR_${cluster.name};

%ENDFOR;

-- =============================================================================
-- Grant Compute Group Access to Users
-- =============================================================================

%FOREACH cluster IN (${compute_clusters})
-- Grant ${cluster.name} compute group to application user
GRANT CR_${cluster.name} TO ${database_name}_app;

%ENDFOR;

-- =============================================================================
-- Verify Configuration
-- =============================================================================

-- List all compute groups
SELECT
    ComputeGroupName,
    QueryStrategy,
    CreateTimeStamp
FROM DBC.ComputeGroupsV
WHERE ComputeGroupName LIKE 'CG_%'
ORDER BY ComputeGroupName;

-- List all compute profiles
SELECT
    ComputeGroupName,
    ComputeProfileName,
    InstanceType,
    InstanceSize,
    MinComputeCount,
    MaxComputeCount
FROM DBC.ComputeProfilesV
WHERE ComputeProfileName LIKE 'CP_%'
ORDER BY ComputeGroupName, ComputeProfileName;
