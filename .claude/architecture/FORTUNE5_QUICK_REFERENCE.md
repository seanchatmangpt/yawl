# Fortune 5 SAFe Portfolio Quick Reference
## Executive Cheat Sheet for Portfolio Leaders

**Document**: Quick Reference Card (1-page printable)
**Date**: 2026-02-28
**Audience**: CPE, CFO, BU Presidents, RTEs, Portfolio Analysts

---

## PORTFOLIO AT A GLANCE

### Numbers That Matter
| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| **Annual Revenue** | $89.0B | Strategic allocation | — |
| **Operating Budget** | $13.5B | 15.1% of revenue | ✓ Healthy |
| **Employees** | 100,000+ | Agile-trained | — |
| **Engineers** | 12,900 | Active development | — |
| **Business Units** | 5 | Autonomous P&Ls | — |
| **Value Streams** | 49 | End-to-end outcomes | — |
| **ARTs** | 156 | 12-week PIs | — |
| **Solution Trains** | 11 | Cross-ART initiatives | — |
| **Portfolio Agents** | 200+ | Autonomous orchestration | — |

---

## STRATEGIC THEMES (Current PI)

```
1. Digital Transformation (20.5%) → $2.77B
2. AI/ML Capability (16.4%) → $2.21B
3. Cloud Migration (18.4%) → $2.48B
4. Customer Experience (15.2%) → $2.05B
5. Operational Efficiency (14.4%) → $1.94B
6. Security & Compliance (7.0%) → $0.95B
7. Emerging Markets (8.1%) → $1.09B
```

**Decision**: Which epics align with these themes?
**Tool**: WSJF scoring (AI-weighted)
**Update Frequency**: Quarterly

---

## QUICK ESCALATION GUIDE

### When to Escalate

| Situation | Escalate To | Response Time | Examples |
|-----------|------------|---------------|----------|
| **Blocked story (1 ART)** | RTE | 2 hours | Missing API, unclear requirements |
| **Blocked epic (2-3 ARTs)** | RTE + Chief Architect | 4 hours | Integration failure, design mismatch |
| **Blocked solution (5+ ARTs)** | CPE + BU President | 8 hours | Strategic redirect, market change |
| **Security vulnerability** | Chief Risk Officer + CTO | 1 hour | CRITICAL only |
| **Compliance gap** | Chief Compliance Officer | 4 hours | Audit finding |
| **Budget overrun** | CFO | Same week | >10% variance |
| **Attrition spike** | BU President + HR | Immediate | >3 resignations/month |

**Note**: Use decision playbooks in FORTUNE5_IMPLEMENTATION_PLAYBOOK.md

---

## WEEKLY DASHBOARD SNAPSHOT

### What to Check Every Monday Morning

**Portfolio Health** (5-minute scan)
```
✓ PI-1 Burned Points:        622,000 target, 405,000 complete (65% done)
✓ Velocity Trend:             +2.1% YoY (improving)
✓ Defect Rate:                0.82 defects/KLOC (on target)
✓ Flow Efficiency:            68% (healthy)
✓ Critical Dependencies Met:  44/48 (92%)
✗ Risk Exposure:              $85M (down from $340M, good mitigation)
✗ Employee Engagement:        73% (target 75%, slight decline)
```

**Red Flags to Investigate**
- Any ART velocity <70% of forecast
- Defect rate trending upward (3+ days)
- Critical dependencies slipping
- > 3 unresolved escalations
- Risk register adding >20 items/week

### Key Numbers to Know

**Financial**
- Weekly burn: $260M (operates daily)
- Contingency reserve: $270M (2% buffer)
- AI agent ROI: $25.9M/year
- Cost per story point: $15K

**Operational**
- ART average: 56 people, $2.8M budget
- Sprint velocity: 450 pts/ART (±50 variance)
- PI predictability: 90% (±10%)
- Cycle time: 8 weeks (target: <8)

**Quality**
- Defect escape: 0.8/KLOC (stable)
- Test coverage: 85% (industry: 70-90%)
- Security incidents: 0 CRITICAL (policy: immediate escalation)

---

## DECISION FRAMEWORK (1-PAGE)

### Daily Decisions (RTE Authority)

```
Scope Reduction (<150 pts)?
  → RTE approves, PO updates backlog

Resource Reallocation (<2 FTE)?
  → RTE approves, SM re-plans team

Dependency Mitigation (<$1M impact)?
  → RTE approves, escalate for monitoring
```

### Weekly Decisions (BU President + RTE)

```
Scope Reduction (150-500 pts)?
  → BU President reviews, approves, updates PI plan

Resource Reallocation (2-5 FTE)?
  → BU President approves, coordinate with other ARTs

Dependency Escalation ($1M-$10M)?
  → BU President + Chief Architect, follow decision playbook
```

### Monthly Decisions (CPE + CFO)

```
Scope Reduction (>500 pts, >1 ART)?
  → CPE reviews, approves, updates revenue forecast

Resource Reallocation (>5 FTE, >$500K)?
  → CPE + CFO, approve, reallocate budget

Risk Response (>$10M exposure)?
  → CPE + Chief Risk Officer, execute mitigation plan
```

---

## COMMON SCENARIOS & RESPONSES

### Scenario 1: ART Velocity Down 30%

**Questions**:
- Did we lose key people? (HR check)
- Technical impediment blocking work? (Architect review)
- Incorrect forecast? (Historical analysis)

**Response** (Choose one):
- [ ] Backfill (4-8 weeks, slow ramp)
- [ ] Borrow engineers (1 week ramp, fast)
- [ ] Reduce scope (immediate, affects delivery)
- [ ] Extend PI timeline (1-week delay per 5% velocity loss)

**Escalation**: If revenue impact >$5M, involve CFO

---

### Scenario 2: Critical Dependency Missed

**Questions**:
- Can we still integrate with workaround? (Architect assessment)
- How long to full fix? (RTE estimate)
- What's the delay impact? (Revenue calculation)

**Response** (Choose one):
- [ ] Accelerate upstream ART (add resources, increase risk)
- [ ] Build workaround (3-7 days, technical debt)
- [ ] Delay downstream ART (revenue impact, acceptable?)
- [ ] Reduce scope (avoid waiting, deliver partial functionality)

**Escalation**: If revenue impact >$10M, involve CPE + CFO

---

### Scenario 3: Attrition Crisis

**Questions**:
- Why are people leaving? (Exit interviews)
- Can we counter-offer? (Competitive analysis)
- What's the team impact? (Velocity, quality impact)

**Response** (Choose one):
- [ ] Backfill + retention bonus (6 months hiring delay)
- [ ] Temporary contractor surge (weeks 1-8)
- [ ] Borrow from peer ARTs (longer term, better quality)
- [ ] Reduce scope to fit capacity (immediate, revenue impact)

**Escalation**: If loss >5 engineers in 1 month, CPE + HR involved

---

## METRICS INTERPRETATION

### Velocity Metrics

| Metric | Green | Yellow | Red |
|--------|-------|--------|-----|
| **Variance** | ±10% | ±20% | >±20% |
| **Trend** | +2% YoY | 0% | <-2% |
| **Forecast Accuracy** | >90% | 80-90% | <80% |
| **Weeks Ahead/Behind** | On time | <1 week | >1 week |

### Quality Metrics

| Metric | Green | Yellow | Red |
|--------|-------|--------|-----|
| **Defects/KLOC** | <0.8 | 0.8-1.2 | >1.2 |
| **Escape Rate** | <0.5% | 0.5-1% | >1% |
| **Test Coverage** | >85% | 70-85% | <70% |
| **Cycle Time** | <8 weeks | 8-10 weeks | >10 weeks |

### Risk Metrics

| Risk Metric | Green | Yellow | Red |
|-------------|-------|--------|-----|
| **# Blockers** | 0-2 | 3-5 | >5 |
| **Avg Days to Resolve** | <1 day | 1-2 days | >2 days |
| **Critical Dependencies Met** | >95% | 90-95% | <90% |
| **Unresolved Escalations** | 0-1 | 2-3 | >3 |

---

## MONTHLY CHECKLIST

**First Monday of Month**: Portfolio Dashboard Review
- [ ] All 156 ART metrics loaded (or variance explained)
- [ ] Risk register updated (any new risks?)
- [ ] Dependency status reviewed (any cycles?)
- [ ] Quality trend analyzed (improving/declining?)
- [ ] Financial performance vs. budget (variance <5%?)
- [ ] Decisions recorded (who approved what?)

**Third Tuesday of Month**: Portfolio Governance Council
- [ ] Review top 10 risks (mitigations working?)
- [ ] Review financial variance (reallocation needed?)
- [ ] Review employee engagement (coaching plan updated?)
- [ ] Approve any scope/budget changes (>$5M impact)
- [ ] Confirm strategic themes still aligned

**Last Friday of Month**: BU President Sync
- [ ] Each BU reports: health, risks, blockers
- [ ] Cross-BU dependencies reviewed
- [ ] Resource requests approved/denied
- [ ] Next month plan finalized

---

## CONTACT REFERENCE

| Role | Name | Title | Phone | Email |
|------|------|-------|-------|-------|
| **CPE** | [Chief Portfolio Executive] | Portfolio Leader | +1-XXX | cpE@company.com |
| **LPM Lead** | [VP Portfolio Mgmt] | Portfolio Management | +1-XXX | lpm@company.com |
| **CFO** | [Chief Financial Officer] | Budget Authority | +1-XXX | cfo@company.com |
| **CTO** | [Chief Technology Officer] | Tech Authority | +1-XXX | cto@company.com |
| **Portfolio Analyst** | [Lead Analyst] | Metrics & Data | +1-XXX | analyst@company.com |

**Emergency Escalation**: CPE phone (keep in pocket)

---

## TOOLS & DASHBOARDS

| Tool | Purpose | URL/Access | Update Freq |
|------|---------|-----------|------------|
| **Jira Portfolio** | Backlog + epics | jira.company.com/portfolio | Real-time |
| **Dashboard** | Portfolio health | dashboard.company.com | 15 min |
| **Risk Register** | Risk tracking | confluence.company.com/risk | Weekly |
| **Dependency Map** | Cross-ART deps | agora.company.com/deps | Daily |
| **Revenue Forecast** | Financial tracking | finance.company.com | Weekly |

**Shortcut**: Bookmark dashboard.company.com (check every Monday)

---

## PI PLANNING CYCLE (12 WEEKS)

```
Week 1:  Executive theme definition + investment allocation
Week 2:  ART charter creation (all 156 ARTs) + Solution Train scoping
Week 3:  Dependency discovery + cycle detection + AI model training
Week 4:  ART-level PI Planning (parallel, all 156 ARTs)
Week 5:  Portfolio consolidation + risk review + final approval
Week 6:  PI launch + team kickoff + baseline metrics

Weeks 7-11:  PI Execution (6 sprints)
├─ Sprints 1-6: Daily standups, sprint ceremonies
├─ Weekly: Portfolio dashboard review, RTE sync
├─ Monthly: Governance council + BU president sync
└─ Ongoing: Dependency tracking, risk monitoring

Week 12: PI Review + Retrospective
├─ ART demos + business outcomes
├─ Portfolio metrics analysis (vs. forecast)
├─ Lessons learned (add to coaching plan)
└─ Next PI planning begins (Week 1 of next cycle)
```

**Key Dates**: Submit calendar holds 12 months out

---

## PHONE TREE (Escalation Contacts)

```
CRITICAL ESCALATION (Immediate):
  ├─ Security Breach? → Chief Risk Officer (phone 1)
  ├─ Regulatory Violation? → Chief Compliance Officer (phone 2)
  ├─ Revenue >$50M At Risk? → CPE (phone 3)
  └─ Customer Impact? → Chief Customer Officer (phone 4)

URGENT (Within 4 hours):
  ├─ Dependency Blocking Multiple ARTs? → Chief Architect
  ├─ ART Velocity Crisis? → BU President
  ├─ Attrition Spike? → Chief HR Officer
  └─ Budget Overrun? → CFO

ROUTINE (Same day):
  ├─ Single ART Blocked? → RTE
  ├─ Quality Issue? → Chief Quality Officer
  ├─ Backlog Question? → Product Director
  └─ Coaching Need? → SAFe Coach
```

---

## READING LIST

**Must-Read (Before Month 1)**:
1. FORTUNE5_SAFE_PORTFOLIO_ARCHITECTURE.md (3000 words, 45 min)
2. FORTUNE5_IMPLEMENTATION_PLAYBOOK.md (escalation playbooks, 30 min)

**Reference (Have Nearby)**:
3. This quick reference card (1 page, laminate it)
4. Decision framework (1 page, frame it for wall)
5. Portfolio risk register (updated weekly)

**Deep Dives (As Needed)**:
- SAFE-AI-NATIVE-SIMULATION.md (agent architecture)
- SAFE-IMPLEMENTATION-GUIDE.md (technical implementation)
- CLAUDE.md (project standards)

---

## ANNUAL CALENDAR

```
Q1 (Jan-Mar):   Strategic planning, PI-1 execution, coaching ramp
Q2 (Apr-Jun):   PI-2 execution, midyear review, adjustment decisions
Q3 (Jul-Sep):   PI-3 execution, planning for Q4, team development
Q4 (Oct-Dec):   PI-4 execution, annual review, strategy planning

Key Dates (Fixed):
├─ Week 1 of every month: Dashboard review
├─ 3rd Tuesday every month: Governance council
├─ Last Friday every month: BU president sync
├─ End of PI (Week 12): PI review + retrospective
├─ Start of next PI (Week 13): PI-N+1 planning begins
└─ Annual: Strategic portfolio review + budget planning
```

---

## THIS WEEK'S PRIORITIES

**Monday**:
- [ ] Check portfolio dashboard
- [ ] Review top 10 risks
- [ ] Note any RED metrics

**Tuesday-Thursday**:
- [ ] Address any escalations
- [ ] Approve decisions >$1M
- [ ] Respond to RTE requests

**Friday**:
- [ ] Weekly forecast update
- [ ] Prepare for next week
- [ ] Document lessons learned

---

## LAST-MINUTE REMINDERS

- **People > Process**: Invest in coaching, engagement scores matter
- **Data > Opinions**: Use metrics, not gut feeling
- **Execution > Perfection**: Ship regularly, improve iteratively
- **Risk Management**: Plan for failure, celebrate success
- **AI Agents**: Let them handle ops, focus you on strategy
- **Celebrate Wins**: Share good news with company monthly

---

**Print This | Laminate | Keep In Pocket**

For full documentation, see:
- `/home/user/yawl/.claude/architecture/FORTUNE5_SAFE_PORTFOLIO_ARCHITECTURE.md`
- `/home/user/yawl/.claude/architecture/FORTUNE5_IMPLEMENTATION_PLAYBOOK.md`

**Questions?** Contact: CPE (phone in contact reference section above)

---

**Version**: 1.0 | **Date**: 2026-02-28 | **Classification**: Internal Use
