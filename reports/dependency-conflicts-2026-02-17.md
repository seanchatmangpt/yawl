# YAWL Dependency Conflict Analysis

**Date:** 2026-02-17
**Total conflicts requiring mediation:** 696
**Parsed structured entries:** 672

## Summary

Maven dependency mediation resolved **696** version conflicts across
13 modules. This report identifies which artifacts cause the most conflicts,
which modules are most affected, and where version spread is highest.

## Top Conflicted Artifacts

| Rank | Artifact | Conflicts | Versions Seen |
|------|----------|-----------|---------------|
| 1 | `jakarta.xml.bind:jakarta.xml.bind-api` | 62 | 4.0.0 4.0.2 4.0.5  |
| 2 | `org.slf4j:slf4j-api` | 51 | 2.0.16 2.0.17  |
| 3 | `org.jspecify:jspecify` | 50 | 1.0.0  |
| 4 | `org.apache.logging.log4j:log4j-api` | 49 | 2.25.3  |
| 5 | `jakarta.activation:jakarta.activation-api` | 34 | 2.1.1 2.1.4  |
| 6 | `org.apache.commons:commons-lang3` | 33 | 3.20.0  |
| 7 | `org.jdom:jdom2` | 32 | 2.0.6.1  |
| 8 | `org.eclipse.angus:angus-activation` | 22 | 2.0.0 2.0.2 2.0.3  |
| 9 | `jakarta.persistence:jakarta.persistence-api` | 21 | 3.1.0 3.2.0  |
| 10 | `com.sun.xml.bind:jaxb-impl` | 21 | 4.0.5  |
| 11 | `org.hibernate.orm:hibernate-core` | 20 | 6.6.42.Final  |
| 12 | `org.apache.logging.log4j:log4j-core` | 20 | 2.25.3  |
| 13 | `org.jboss.logging:jboss-logging` | 19 | 3.5.0.Final 3.6.1.Final  |
| 14 | `com.zaxxer:HikariCP` | 17 | 3.2.0 7.0.2  |
| 15 | `org.apache.commons:commons-collections4` | 13 | 4.5.0-M2  |
| 16 | `com.fasterxml.jackson.core:jackson-databind` | 13 | 2.18.3 2.19.2 2.19.4  |
| 17 | `org.hamcrest:hamcrest-core` | 12 | 3.0 compile  |
| 18 | `com.fasterxml.jackson.core:jackson-core` | 12 | 2.18.3 2.19.4  |
| 19 | `jaxen:jaxen` | 11 | 1.2.0  |
| 20 | `commons-io:commons-io` | 11 | 2.21.0  |
| 21 | `commons-codec:commons-codec` | 11 | 1.19.0  |
| 22 | `net.bytebuddy:byte-buddy` | 10 | 1.17.5 1.18.5  |
| 23 | `io.smallrye:jandex` | 10 | 3.2.0 3.3.1  |
| 24 | `com.sun.istack:istack-commons-runtime` | 10 | 4.1.1 4.2.0  |
| 25 | `com.fasterxml:classmate` | 10 | 1.5.1 1.7.3  |
| 26 | `org.apache.commons:commons-pool2` | 9 | 2.13.0 2.13.1  |
| 27 | `com.h2database:h2` | 9 | 2.4.240  |
| 28 | `com.fasterxml.jackson.core:jackson-annotations` | 9 | 2.19.2 2.19.4  |
| 29 | `org.postgresql:postgresql` | 8 | 42.7.7  |
| 30 | `org.hibernate.orm:hibernate-hikaricp` | 8 | 6.6.42.Final  |

## Conflicts by Module

| Module | Conflict Count | Primary Culprits |
|--------|---------------|-------------------|
| `yawl-utilities` | 531 | jakarta.xml.bind:jakarta.xml.bind-api,org.apache.logging.log4j:log4j-api,org.slf4j:slf4j-api |
| `yawl-engine` | 93 | com.fasterxml.jackson.core:jackson-databind,org.slf4j:slf4j-api,com.fasterxml.jackson.core:jackson-core |
| `yawl-elements` | 41 | org.slf4j:slf4j-api,org.hibernate.orm:hibernate-core,jakarta.xml.bind:jakarta.xml.bind-api |
| `yawl-security` | 7 | org.jspecify:jspecify,org.junit.jupiter:junit-jupiter-api,org.hamcrest:hamcrest-core |

## Conflict Type Distribution

| Type | Count | Description |
|------|-------|-------------|
| `conflict` | 0
0 | Transitive dependency pulled different version; Maven chose nearest/first |
| `managed` | 672 | `dependencyManagement` in parent POM forced a specific version |

## Remediation Priority

Artifacts should be addressed in order of conflict count. For each:

1. **Already managed** (type=managed): Parent POM is correctly pinning. No action needed.
2. **Not managed** (type=conflict): Add to parent `<dependencyManagement>` to pin version.
3. **High version spread**: Multiple incompatible versions pulled transitively.
   Consider adding `<exclusions>` on the dependency that pulls the wrong version.

## Files Generated

| File | Description |
|------|-------------|
| `reports/raw-dependency-tree-2026-02-17.txt` | Full verbose dependency tree |
| `reports/conflicts-2026-02-17.csv` | Structured conflict data (CSV) |
| `reports/dependency-conflicts-2026-02-17.json` | Machine-readable conflict data |
| `reports/dependency-conflicts-summary.txt` | Console-friendly summary |
