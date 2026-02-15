# YAWL Generic Agent Configuration - Quick Reference

**Created:** 2026-02-15
**Phase:** 3 - Configuration Files & Validation

---

## File Locations

```
/home/user/yawl/config/agents/
├── orderfulfillment/          # OrderFulfillment domain agents
│   ├── ordering-agent.yaml    # Port 8091, ZAI+ZAI
│   ├── carrier-agent.yaml     # Port 8092, ZAI+ZAI
│   ├── payment-agent.yaml     # Port 8093, Static+ZAI
│   ├── freight-agent.yaml     # Port 8094, ZAI+Template
│   └── delivered-agent.yaml   # Port 8095, ZAI+ZAI
├── notification/              # Notification domain agents
│   ├── email-agent.yaml       # Port 8096, ZAI+Template
│   ├── sms-agent.yaml         # Port 8097, ZAI+Template
│   └── alert-agent.yaml       # Port 8098, Static+Template
├── mappings/                  # Static mapping files
│   ├── orderfulfillment-static.json
│   └── notification-static.json
├── templates/                 # Output templates
│   ├── freight-output.xml
│   ├── notification-output.xml
│   └── generic-success.xml
├── schema.yaml                # Configuration schema documentation
├── VALIDATION_REPORT.md       # Detailed validation report
└── QUICK_REFERENCE.md         # This file
```

---

## Agent Port Assignments

| Port | Agent | Domain | Reasoning |
|------|-------|--------|-----------|
| 8091 | ordering-agent | Ordering | ZAI + ZAI |
| 8092 | carrier-agent | Carrier | ZAI + ZAI |
| 8093 | payment-agent | Payment | Static + ZAI |
| 8094 | freight-agent | Freight | ZAI + Template |
| 8095 | delivered-agent | Delivered | ZAI + ZAI |
| 8096 | email-agent | Email | ZAI + Template |
| 8097 | sms-agent | SMS | ZAI + Template |
| 8098 | alert-agent | Alert | Static + Template |

---

## Reasoning Engine Combinations

### ZAI + ZAI (Full AI)
- **Agents:** ordering, carrier, delivered, (payment decision)
- **Use case:** Complex decisions requiring semantic understanding
- **Requires:** ZAI_API_KEY environment variable

### ZAI + Template (AI Eligibility, Structured Output)
- **Agents:** freight, email, sms
- **Use case:** AI determines eligibility, output follows fixed structure
- **Requires:** ZAI_API_KEY + template file

### Static + ZAI (Rule-based Eligibility, AI Decision)
- **Agents:** payment
- **Use case:** Known task names, complex decision making
- **Requires:** ZAI_API_KEY + mapping file

### Static + Template (Fully Deterministic)
- **Agents:** alert
- **Use case:** Fixed routing and output structure
- **Requires:** mapping file + template file
- **Benefit:** No ZAI API calls needed

---

## Environment Variables

### Required (for ZAI-based agents)
```bash
export ZAI_API_KEY="your-api-key-here"
```

### Optional (with defaults)
```bash
export YAWL_ENGINE_URL="http://localhost:8080/yawl"  # YAWL Engine URL
export YAWL_USERNAME="admin"                          # YAWL username
export YAWL_PASSWORD="YAWL"                           # YAWL password
export ZAI_MODEL="GLM-4-Flash"                        # ZAI model
export ZAI_BASE_URL="https://open.bigmodel.cn/api/paas/v4/chat/completions"
```

---

## Quick Start

### 1. Set Environment Variables
```bash
export ZAI_API_KEY="your-key"
export YAWL_ENGINE_URL="http://localhost:8080/yawl"
```

### 2. Load Configuration
```java
// Example: Load ordering agent config
AgentConfig config = AgentConfigLoader.load(
    "config/agents/orderfulfillment/ordering-agent.yaml"
);
```

### 3. Start Agent
```java
GenericAgent agent = new GenericAgent(config);
agent.start();
```

---

## Static Mapping Files

### OrderFulfillment Mappings (30+ tasks)
**File:** `config/agents/mappings/orderfulfillment-static.json`

**Sample Mappings:**
- `Approve_Purchase_Order` → Ordering
- `Select_Carrier` → Carrier
- `Process_Payment` → Payment
- `Calculate_Freight` → Freight
- `Confirm_Delivery` → Delivered

### Notification Mappings (18+ tasks)
**File:** `config/agents/mappings/notification-static.json`

**Sample Mappings:**
- `Send_Email_Notification` → Email
- `Send_SMS` → SMS
- `Critical_Alert` → Alert

---

## Template Files

### Freight Output Template
**File:** `config/agents/templates/freight-output.xml`

**Fields (17):**
- FreightCharge, ShippingCost, TotalCost, Currency
- Weight, WeightUnit, Dimensions
- Origin, Destination
- CustomsRequired, CustomsDuty
- EstimatedTransitDays
- etc.

### Notification Output Template
**File:** `config/agents/templates/notification-output.xml`

**Fields (16):**
- NotificationID, NotificationType
- Recipient, RecipientEmail, RecipientPhone
- Subject, Message
- SentDate, SentTime, Status
- etc.

### Generic Success Template
**File:** `config/agents/templates/generic-success.xml`

**Fields (16):**
- TaskID, TaskName, Status
- CompletionDate, CompletionTime
- Result, ResultCode, ResultMessage
- Duration, Notes
- etc.

---

## Configuration Schema

### Agent Section
```yaml
agent:
  name: "Agent Name"
  capability:
    domain: "DomainName"          # MUST be unique
    description: "keywords..."     # For semantic matching
  discovery:
    strategy: "polling"
    interval_ms: 3000
  reasoning:
    eligibility_engine: "zai|static"
    decision_engine: "zai|template"
    # ... prompts/files as needed
  output:
    format: "xml"
  server:
    port: 8091                     # MUST be unique
```

### YAWL Section
```yaml
yawl:
  engine_url: "${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
  username: "${YAWL_USERNAME:-admin}"
  password: "${YAWL_PASSWORD:-YAWL}"
```

### ZAI Section
```yaml
zai:
  api_key: "${ZAI_API_KEY}"
  model: "${ZAI_MODEL:-GLM-4-Flash}"
  base_url: "${ZAI_BASE_URL:-https://...}"
  timeout_ms: 30000
  max_retries: 3
```

---

## Validation Checklist

Before deploying a new agent configuration:

- [ ] Unique port number assigned
- [ ] Unique domain name assigned
- [ ] All required fields present
- [ ] ZAI config present if using ZAI engines
- [ ] Referenced mapping/template files exist
- [ ] Environment variables documented
- [ ] No TODO/FIXME/XXX markers
- [ ] No mock/stub/fake implementations
- [ ] Prompts tested with real data
- [ ] Static mappings match YAWL specs

---

## Common Tasks

### Add New Agent
1. Copy similar agent config from appropriate domain
2. Assign next available port
3. Assign unique domain name
4. Customize capability description
5. Update prompts or file references
6. Add to static mappings if needed
7. Validate and test

### Modify Prompts
1. Edit YAML config file
2. Locate `eligibility_prompt` or `decision_prompt`
3. Update prompt text
4. Maintain JSON output format requirement
5. Test with sample workitems

### Add Task Mapping
1. Open appropriate static mapping JSON
2. Add `"Task_Name": "AgentDomain"` entry
3. Save file
4. Restart agent using that mapping

### Create New Template
1. Create XML file in `templates/` directory
2. Add `<data>` root element
3. Add field elements with `${var:-default}` syntax
4. Reference in agent config `template_file`

---

## File Counts

- **YAML configs:** 9 (8 agents + 1 schema)
- **JSON mappings:** 2
- **XML templates:** 3
- **Documentation:** 2 (VALIDATION_REPORT.md, QUICK_REFERENCE.md)
- **Total:** 14 files + 1 existing (orderfulfillment-permutations.json)

---

## Next Implementation Steps

1. **Parse YAML configs** - Load configurations into Java objects
2. **Validate configs** - Check required fields, unique constraints
3. **Create engines** - Factory for ZAI/Static/Template engines
4. **Connect to YAWL** - InterfaceB_EnvironmentBasedClient integration
5. **Start HTTP servers** - One per agent on configured port
6. **Poll for workitems** - Implement discovery strategy
7. **Check eligibility** - Use configured eligibility engine
8. **Make decisions** - Use configured decision engine
9. **Return output** - Format as XML/JSON
10. **Check out/in workitems** - Complete YAWL workflow cycle

---

## Support

- **Full Documentation:** See `schema.yaml` for complete field reference
- **Validation Report:** See `VALIDATION_REPORT.md` for detailed analysis
- **Example Configs:** See `orderfulfillment/*.yaml` and `notification/*.yaml`
- **Mapping Examples:** See `mappings/*.json`
- **Template Examples:** See `templates/*.xml`

---

*Quick Reference Guide - YAWL Generic Agent Framework v1.0*
