# YAWL Archive Summary - 2026-02-20

This document summarizes all archived documentation from 2026-02-20 sessions, focusing on legacy deployment documentation and its preservation.

## Archive Overview

**Date**: 2026-02-20  
**Files Archived**: 4  
**Total Reduction**: 2 files removed (50% reduction)  
**Critical Artifacts Preserved**: 2 files  

## What Was Validated/Tested

### 1. Legacy Deployment Validation
- **Status**: ❌ **DEPRECATED - DO NOT USE**
- **Technology**: Standalone Java application servers (Tomcat, WildFly, Jetty)
- **Package Format**: WAR files
- **Build System**: Ant (superseded by Maven)
- **Target**: YAWL v5.x (legacy)

### 2. Modern Deployment Validation
- **Status**: ✅ **CURRENT STANDARD**
- **Technology**: Spring Boot with embedded container
- **Package Format**: Executable JAR
- **Build System**: Maven
- **Deployment Targets**: Docker, Kubernetes, Docker Compose

## Key Findings

### Critical Decision Point
YAWL v6.0.0 represents a fundamental architectural shift:
1. **No longer supports** external application server deployment
2. ** exclusively uses** containerized deployment
3. ** embedded servlet container** eliminates server configuration

### Risk Assessment
- **Legacy Docs Risk**: May mislead new developers
- **Compatibility**: No backward compatibility with v5.x deployment
- **Support**: External server deployment path not tested or supported

## Critical Artifacts Preserved

### 1. Legacy Documentation Reference
**File**: `README.md`
**Reason**: Explains why documents were archived and what to use instead
**Content**:
- Documents the architectural shift from v5.x to v6.0.0
- Provides clear migration path to modern deployment
- Prevents accidental use of deprecated deployment methods

### 2. Deployment Guides (Archived for Reference)
**Files**: `DEPLOY-TOMCAT.md`, `DEPLOY-WILDFLY.md`, `DEPLOY-JETTY.md`
**Reason**: Historical reference for legacy systems
**Note**: These are NOT for production use
**Warning**: Contains outdated build configurations and deployment methods

## Deleted Files Summary (None)

No files were deleted from this archive. All legacy deployment documentation was preserved for historical reference.

## Recommendations

### For New Developers
1. **Read the README** in this directory first
2. **Use v6 Deployment Guide**: See `docs/v6-DEPLOYMENT-GUIDE.md`
3. **Follow Modern Path**: Docker containers or Kubernetes

### For Maintainers
1. **Keep Legacy Docs**: For troubleshooting v5.x installations
2. **Update Training Materials**: Focus on containerized deployment
3. **Version Documentation**: Clearly mark v5.x vs v6.0.0 docs

### Migration Path
```bash
# v5.x (legacy)
- Deploy to Tomcat/WildFly/Jetty as WAR
- Configure application server manually
- Use build.xml (Ant)

# v6.0.0 (modern)
- Deploy as Docker container
- Use docker-compose.prod.yml for local dev
- Use Maven for builds
- Embedded Tomcat included
```

## Recovery Instructions

If legacy deployment information is needed:
```bash
# Archive location
cd /Users/sac/cre/vendors/yawl/docs/archive/2026-02-20/

# View all deployment docs
ls -la DEPLOY-*.md

# Read specific guide
cat DEPLOY-TOMCAT.md

# Search for specific content
grep -r "tomcat" .

# Warning: These methods are NOT supported for v6.0.0
```

## Archive Maintenance

These legacy documents should be preserved indefinitely as they:
1. Document the architectural evolution of YAWL
2. Provide reference for maintaining v5.x installations
3. Serve as historical context for future developers
4. Prevent regression to deprecated deployment patterns

---

**Document Created**: Archive consolidation for 2026-02-20 legacy docs  
**Purpose**: Historical reference while preventing misuse  
**Status**: Ready for use - all legacy methods documented as deprecated
