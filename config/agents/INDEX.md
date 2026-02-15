# YAWL Generic Agent Framework - File Index

**Generated:** 2026-02-15
**Total Files:** 17

---

## Agent Configurations (8 files)

### OrderFulfillment Domain (5 agents)

| # | File | Path | Port | Domain | Eligibility | Decision | Size |
|---|------|------|------|--------|-------------|----------|------|
| 1 | ordering-agent.yaml | orderfulfillment/ | 8091 | Ordering | ZAI | ZAI | 2.2K |
| 2 | carrier-agent.yaml | orderfulfillment/ | 8092 | Carrier | ZAI | ZAI | 2.1K |
| 3 | payment-agent.yaml | orderfulfillment/ | 8093 | Payment | Static | ZAI | 1.7K |
| 4 | freight-agent.yaml | orderfulfillment/ | 8094 | Freight | ZAI | Template | 1.6K |
| 5 | delivered-agent.yaml | orderfulfillment/ | 8095 | Delivered | ZAI | ZAI | 2.2K |

### Notification Domain (3 agents)

| # | File | Path | Port | Domain | Eligibility | Decision | Size |
|---|------|------|------|--------|-------------|----------|------|
| 6 | email-agent.yaml | notification/ | 8096 | Email | ZAI | Template | 1.5K |
| 7 | sms-agent.yaml | notification/ | 8097 | SMS | ZAI | Template | 1.5K |
| 8 | alert-agent.yaml | notification/ | 8098 | Alert | Static | Template | 1.1K |

---

## Static Mappings (2 files)

| # | File | Path | Mappings | Domains | Size |
|---|------|------|----------|---------|------|
| 9 | orderfulfillment-static.json | mappings/ | 30+ | Ordering, Carrier, Payment, Freight, Delivered | 1.6K |
| 10 | notification-static.json | mappings/ | 18+ | Email, SMS, Alert | 1.1K |

---

## Templates (3 files)

| # | File | Path | Fields | Used By | Size |
|---|------|------|--------|---------|------|
| 11 | freight-output.xml | templates/ | 17 | freight-agent | 1.2K |
| 12 | notification-output.xml | templates/ | 16 | email, sms, alert agents | 1.1K |
| 13 | generic-success.xml | templates/ | 16 | (available for future use) | 1.1K |

---

## Documentation (4 files)

| # | File | Path | Purpose | Size |
|---|------|------|---------|------|
| 14 | schema.yaml | / | Complete configuration schema | 13K |
| 15 | VALIDATION_REPORT.md | / | Detailed validation results | 12K |
| 16 | QUICK_REFERENCE.md | / | Quick reference guide | 8.5K |
| 17 | README.md | / | Overview and getting started | (this session) |

---

## File Dependencies

### Agent → Mapping Dependencies

| Agent | References | File |
|-------|------------|------|
| payment-agent | mapping_file | orderfulfillment-static.json |
| alert-agent | mapping_file | notification-static.json |

### Agent → Template Dependencies

| Agent | References | File |
|-------|------------|------|
| freight-agent | template_file | freight-output.xml |
| email-agent | template_file | notification-output.xml |
| sms-agent | template_file | notification-output.xml |
| alert-agent | template_file | notification-output.xml |

### All Agents → Environment Variables

| Variable | Required For | Default |
|----------|--------------|---------|
| ZAI_API_KEY | All agents using ZAI engines | (none - MUST set) |
| YAWL_ENGINE_URL | All agents | http://localhost:8080/yawl |
| YAWL_USERNAME | All agents | admin |
| YAWL_PASSWORD | All agents | YAWL |
| ZAI_MODEL | All agents using ZAI | GLM-4-Flash |
| ZAI_BASE_URL | All agents using ZAI | https://open.bigmodel.cn/... |

---

## File Sizes Summary

| Category | Files | Total Size |
|----------|-------|------------|
| Agent Configs | 8 | ~14.9K |
| Mappings | 2 | ~2.7K |
| Templates | 3 | ~3.4K |
| Documentation | 4 | ~33.5K+ |
| **Total** | **17** | **~54.5K+** |

---

## Quick File Access

### For Configuration
- **Agent configs:** `config/agents/{orderfulfillment,notification}/*.yaml`
- **Mappings:** `config/agents/mappings/*.json`
- **Templates:** `config/agents/templates/*.xml`

### For Documentation
- **Schema reference:** `config/agents/schema.yaml`
- **Validation report:** `config/agents/VALIDATION_REPORT.md`
- **Quick guide:** `config/agents/QUICK_REFERENCE.md`
- **Getting started:** `config/agents/README.md`

### For Development
- **All YAML files:** `find config/agents -name "*.yaml"`
- **All JSON files:** `find config/agents -name "*.json"`
- **All XML files:** `find config/agents -name "*.xml"`

---

## Verification Commands

```bash
# Count all configuration files
find config/agents -type f \( -name "*.yaml" -o -name "*.json" -o -name "*.xml" \) | wc -l
# Expected: 14

# Verify no HYPER_STANDARDS violations
grep -rn "TODO\|FIXME\|XXX\|mock\|stub\|fake" config/agents/*.yaml config/agents/*.json config/agents/*.xml
# Expected: No matches (except schema documentation)

# Check YAML syntax
for f in config/agents/**/*.yaml; do yamllint "$f"; done

# Check JSON syntax
for f in config/agents/**/*.json; do jsonlint "$f"; done

# Check XML syntax
for f in config/agents/**/*.xml; do xmllint --noout "$f"; done
```

---

## File Creation Timeline

All files created on: **2026-02-15**

**Order of creation:**
1. Directory structure
2. OrderFulfillment agent configs (5)
3. Notification agent configs (3)
4. Static mapping files (2)
5. Template files (3)
6. Schema documentation (1)
7. Validation report (1)
8. Quick reference (1)
9. README (1)
10. This index (1)

---

## Completeness Checklist

### Task 1: Agent Configs ✅
- [x] 8+ YAML configurations created
- [x] OrderFulfillment domain (5 agents)
- [x] Notification domain (3 agents)
- [x] All required fields present
- [x] Unique ports assigned
- [x] Unique domains assigned

### Task 2: Static Mappings ✅
- [x] orderfulfillment-static.json (30+ mappings)
- [x] notification-static.json (18+ mappings)
- [x] Proper JSON format
- [x] Default agents specified

### Task 3: Templates ✅
- [x] freight-output.xml (17 fields)
- [x] notification-output.xml (16 fields)
- [x] generic-success.xml (16 fields)
- [x] Variable syntax: ${var:-default}

### Task 4: Schema Documentation ✅
- [x] schema.yaml created (13KB)
- [x] Complete field documentation
- [x] Environment variable reference
- [x] Validation rules
- [x] Best practices

### Additional Deliverables ✅
- [x] Validation report
- [x] Quick reference guide
- [x] README
- [x] File index (this file)

---

## Next Implementation Files

The following files need to be created in the implementation phase:

### Core Java Files
1. `src/org/yawlfoundation/yawl/integration/GenericAgentFramework.java`
2. `src/org/yawlfoundation/yawl/integration/AgentConfig.java`
3. `src/org/yawlfoundation/yawl/integration/AgentConfigLoader.java`
4. `src/org/yawlfoundation/yawl/integration/ReasoningEngineFactory.java`

### Engine Interfaces
5. `src/org/yawlfoundation/yawl/integration/EligibilityEngine.java`
6. `src/org/yawlfoundation/yawl/integration/DecisionEngine.java`

### Engine Implementations
7. `src/org/yawlfoundation/yawl/integration/ZAIEligibilityEngine.java`
8. `src/org/yawlfoundation/yawl/integration/StaticEligibilityEngine.java`
9. `src/org/yawlfoundation/yawl/integration/ZAIDecisionEngine.java`
10. `src/org/yawlfoundation/yawl/integration/TemplateDecisionEngine.java`

### Test Files
11. `test/org/yawlfoundation/yawl/integration/AgentConfigTest.java`
12. `test/org/yawlfoundation/yawl/integration/GenericAgentFrameworkTest.java`

---

*YAWL Generic Agent Framework - File Index v1.0*
