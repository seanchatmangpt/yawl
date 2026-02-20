# POM.xml Update Instructions

This document provides exact instructions for updating `/home/user/yawl/pom.xml` to add Flyway and Spring Cloud Config dependencies.

## 3 Changes Required

### 1. Add Spring Cloud Version Property

**Location**: After line 100 (`<jackson.version>2.19.4</jackson.version>`)

**Add this line**:
```xml
        <spring-cloud.version>4.2.0</spring-cloud.version>
```

**Result** (lines 99-102):
```xml
        <testcontainers.version>1.21.3</testcontainers.version>
        <jackson.version>2.19.4</jackson.version>
        <spring-cloud.version>4.2.0</spring-cloud.version>

        <!-- ================================================================ -->
```

### 2. Add Spring Cloud Config Starter Dependencies

**Location**: After line 439 (after `spring-boot-configuration-processor` closing `</dependency>`)

**Add these lines**:
```xml
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-config-server</artifactId>
                <version>${spring-cloud.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-starter-config</artifactId>
                <version>${spring-cloud.version}</version>
            </dependency>
```

**Result** (lines 434-445):
```xml
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-configuration-processor</artifactId>
                <version>${spring-boot.version}</version>
                <optional>true</optional>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-config-server</artifactId>
                <version>${spring-cloud.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-starter-config</artifactId>
                <version>${spring-cloud.version}</version>
            </dependency>

            <!-- ============================================================== -->
```

### 3. Add Flyway Database Driver Dependencies

**Location**: After line 634 (after `HikariCP` closing `</dependency>`)

**Add these lines**:
```xml
            <!-- Flyway Database Migration Tool -->
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-core</artifactId>
                <version>${flyway.version}</version>
            </dependency>
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-mysql</artifactId>
                <version>${flyway.version}</version>
            </dependency>
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-postgresql</artifactId>
                <version>${flyway.version}</version>
            </dependency>
```

**Result** (lines 630-648):
```xml
            <dependency>
                <groupId>com.zaxxer</groupId>
                <artifactId>HikariCP</artifactId>
                <version>${hikaricp.version}</version>
            </dependency>
            <!-- Flyway Database Migration Tool -->
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-core</artifactId>
                <version>${flyway.version}</version>
            </dependency>
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-mysql</artifactId>
                <version>${flyway.version}</version>
            </dependency>
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-postgresql</artifactId>
                <version>${flyway.version}</version>
            </dependency>

            <!-- ============================================================== -->
```

## Summary of Changes

| Change | Type | Location | Version |
|--------|------|----------|---------|
| Spring Cloud Config Server | Dependency | pom.xml | 4.2.0 |
| Spring Cloud Starter Config | Dependency | pom.xml | 4.2.0 |
| Flyway Core | Dependency | pom.xml | 10.20.1 |
| Flyway MySQL | Dependency | pom.xml | 10.20.1 |
| Flyway PostgreSQL | Dependency | pom.xml | 10.20.1 |
| Spring Cloud Version Property | Property | pom.xml | 4.2.0 |

Note: Flyway version (10.22.1) is already defined in pom.xml at line 247.

## Validation Steps

After updating pom.xml:

```bash
# Validate pom structure
mvn help:describe

# Verify no dependency conflicts
mvn dependency:tree | grep -E "spring-cloud|flyway"

# Compile to ensure dependencies resolve
mvn clean compile

# Run database migration tests
mvn test -Dtest=FlywayMigrationTest -Dmaven.test.skip=false

# Run config server tests
mvn test -Dtest=ConfigServerTest -Dmaven.test.skip=false
```

## Files Already Created

✅ Migration SQL Files:
- `/home/user/yawl/src/main/resources/db/migration/V3__Add_Resilience_Metrics.sql`
- `/home/user/yawl/src/main/resources/db/migration/V4__Add_Pact_Contract_Registry.sql`

✅ Java Configuration Classes:
- `/home/user/yawl/src/org/yawlfoundation/yawl/config/FlywayConfiguration.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/config/ConfigServerConfiguration.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/config/DynamicPropertiesRefresher.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/config/ConfigurationChangeListener.java`

✅ Test Classes:
- `/home/user/yawl/test/org/yawlfoundation/yawl/config/FlywayMigrationTest.java`
- `/home/user/yawl/test/org/yawlfoundation/yawl/config/ConfigServerTest.java`

✅ Configuration Files:
- `/home/user/yawl/.cloud/config/application.yml`
- `/home/user/yawl/.cloud/config/application-dev.yml`
- `/home/user/yawl/.cloud/config/application-prod.yml`
- `/home/user/yawl/.cloud/config/yawl-engine.yml`
- `/home/user/yawl/.cloud/config/yawl-mcp-a2a.yml`

✅ Documentation:
- `/home/user/yawl/src/main/resources/db/MIGRATION-GUIDE.md`

## Existing Migrations

V1 and V2 migrations already exist and remain unchanged:
- `/home/user/yawl/src/main/resources/db/migration/V1__Initial_Indexes.sql`
- `/home/user/yawl/src/main/resources/db/migration/V2__Partitioning_Setup.sql`
