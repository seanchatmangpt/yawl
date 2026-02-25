# YAWL Process Mining Go-to-Market Playbook

**Product**: YAWL ggen - Process Mining to Production Automation Pipeline
**Version**: 1.0
**Date**: February 2026
**Prepared for**: Enterprise GTM Launch

---

## Section 1: Ideal Customer Profile (ICP)

### Primary Segment: Process Excellence Teams at F500 Companies

**Definition**: Mid-market to enterprise organizations running Celonis or UiPath who have discovered process models but lack a path to production deployment.

**Company Profile**:
- Revenue: $500M - $50B+ (F500 focus)
- Employees: 5,000+
- Process Mining Maturity: Advanced (using commercial mining tools)
- Pain Point: "We have 50 discovered processes. How do we deploy them to Camunda without 6 months of rework?"

**Key Decision Makers**:
- VP of Process Excellence (budget owner, $100K-$500K threshold)
- Director of Automation (day-to-day user)
- IT Architecture (deployment and integration concerns)
- Business Process Manager (defines process requirements)

**Budget Authority**:
- $50K-$150K PoC: VP Process Excellence approves
- $300K+/year: Requires VP Operations or CFO sign-off
- Procurement cycle: 2-4 weeks for IT software

**Example Companies**:
- Banks running Celonis on transaction data (JPMorgan, Goldman Sachs, Deutsche Bank)
- Insurance companies automating claims (Allstate, Progressive, Axa)
- Telecom providers optimizing order-to-cash (Verizon, Deutsche Telekom, BT)
- Manufacturing/logistics mapping supply chain (BMW, Siemens, DHL)
- Healthcare systems improving patient workflows (UnitedHealth, CVS Health, HCA)

---

### Secondary Segment: SI Firms Building BPM Automation Practices

**Definition**: System integration and consulting firms (Accenture, Deloitte, EY, Capgemini, IBM) who deliver process mining + automation as a managed service.

**Company Profile**:
- Services Revenue: $1B - $200B+ (global SI firms)
- BPM Practice Size: 50-500 practitioners
- Process Mining Tools: Celonis, SAP SolManager, ProM (research partnerships)
- Current Gap: Deliver mining reports → customer manually implements

**Key Decision Makers**:
- Practice Leader / Partner (P&L owner, $5M+ practice)
- Engagement Manager (delivery efficiency)
- Capability Lead (technical differentiation)
- Sales Director (accelerator for pipeline)

**Business Model**:
- Time-and-materials: $200-500/hour for process implementation
- Desired transition: 30% faster delivery → 20-30% margin uplift
- Repeatable methodology: YAWL ggen becomes "secret sauce"

**Example Partners**:
- Accenture Process Mining Services
- Deloitte Process Intelligence
- EY Process Automation
- Capgemini Process Optimization
- IBM Business Process Services

---

### Tertiary Segment: Regulated Industries (Formal Verification Required)

**Definition**: Organizations in highly regulated sectors (FSI, healthcare, pharma) where process correctness is non-negotiable.

**Company Profile**:
- Regulatory Requirements: SOX, HIPAA, GDPR, FDA, PCI-DSS
- Compliance Cost: 5-15% of IT budget (risk management)
- Current Approach: Manual process design → auditor sign-off (6+ months)
- Desired State: Automated verification → audit trail → compliance reporting

**Key Decision Makers**:
- Chief Risk Officer (compliance owner)
- Audit & Compliance Director (regulatory relationship)
- Legal / Regulatory Affairs
- IT Risk Management

**Business Model**:
- Compliance-as-a-Service: $50K-$200K per verified process
- Long-term contracts: Multi-year framework agreements
- High switching costs (regulatory lock-in)

**Example Segments**:
- Financial Services (process validation for trading, lending, settlement)
- Healthcare (clinical workflow verification for FDA/CMS)
- Pharma (manufacturing process compliance for FDA/EMA)
- Insurance (claims processing, underwriting verification)

---

## Section 2: Competitive Positioning Matrix

| **Capability** | **YAWL ggen** | **Celonis** | **ProM** | **UiPath** | **SAP SolManager** |
|---|---|---|---|---|---|
| **Process Discovery** | ✓ (PNML/BPMN input) | ✓✓✓ (market leader) | ✓✓ (academic) | ✓ (shallow) | ✓ (legacy) |
| **Formal Verification** | ✓✓✓ (Petri net soundness) | ✗ (none) | ✓ (plugin-based) | ✗ (none) | ✗ (none) |
| **Multi-Format Input** | ✓✓✓ (PNML, BPMN, XPDL, logs) | ✗ (single tool) | ✓✓ (extensible) | ✗ (single tool) | ✓ (SAP only) |
| **Cloud API Integration** | ✓✓✓ (AWS, Azure, GCP native) | ✗ (on-prem focused) | ✗ (research tool) | ✓ (RPA-centric) | ✗ (enterprise only) |
| **IaC Deployment** | ✓✓✓ (Terraform + Helm) | ✗ (visualization only) | ✗ (no IaC) | ✓✓ (RPA bots) | ✗ (no IaC) |
| **Multi-Target Deploy** | ✓✓✓ (Camunda, Zeebe, custom) | ✗ (no deploy) | ✗ (no deploy) | ✓ (UiPath only) | ✗ (SAP only) |
| **Open Source** | ✓✓✓ (Apache 2.0, active) | ✗ (proprietary) | ✓✓✓ (academic) | ✗ (proprietary) | ✗ (proprietary) |
| **Pricing Model** | $15K-$50K/process (transparent) | $500K-$5M (enterprise seats) | Free (academic) | $5K-$50K/bot (variable) | License-based (opaque) |
| **Time to Value** | 2-6 weeks (ggen pipeline) | 3-6 months (manual) | 2-3 months (research) | 2-4 months (bot dev) | 4-8 months (custom) |
| **Compliance Support** | ✓✓✓ (audit trail, soundness proof) | ✗ (none) | ✗ (none) | ✗ (none) | ✓ (SAP certs) |

**Key Positioning Insight**: YAWL ggen is the only product that closes the "discovery-to-deployment" gap with formal verification AND cloud-native deployment in a single pipeline.

---

## Section 3: Sales Motion

### Land: SI Pilot Program ($50K, 3-month PoC)

**Objective**: Prove 1 discovered process can be deployed to Camunda in 4 weeks with <15% manual rework.

**Target Customer Profile**:
- Has used Celonis, UiPath, or SAP to discover 50+ processes
- Wants to deploy top 5-10 processes to Camunda
- Estimated manual implementation cost: $100K-$200K
- Budget ready: VP approval in hand

**Proposal Elements**:
- **Scope**: 1-2 representative processes (order-to-cash, procure-to-pay typical)
- **Duration**: 12 weeks (4-week pilot delivery, 8-week support)
- **Deliverables**:
  - Discovered model import (BPMN/PNML)
  - Automated verification report (soundness, conformance, coverage)
  - Terraform/Helm deployment package
  - Camunda instance (AWS/Azure/GCP) ready for testing
  - 2 days on-site training (deployment team)
- **Success Metrics**:
  - Process deployed within 4 weeks
  - <15% manual rework to production-ready
  - Team can deploy 3-5 additional processes without YAWL ggen support
- **Investment**: $50,000 (cost-plus model)
- **Economics**:
  - Customer saves: $75K in manual implementation (75% reduction)
  - ROI: 150% in first process alone
  - Payback: 8 weeks (from Camunda license savings)

**Sales Execution**:
1. Discovery call (30 min): "Where are your discovered processes?"
2. Technical assessment (2 hours): Import top 3 processes, show automated deployment
3. Proposal (1 week): Land statement, statement of work, SLAs
4. POC initiation (1 week): Kick-off meeting, tool access, baseline metrics
5. Mid-point review (6 weeks): Show deployed instance, gather feedback
6. Final demo (12 weeks): Live Camunda instance, process running in staging
7. Decision point: Expand or end

**Win Condition**: Customer commits to $150K/year subscription (Expand phase).

---

### Expand: 10+ Processes/Month Subscription ($15K/month)

**Objective**: Scale deployment from 1 pilot process to 10+ production processes per month.

**Target Timeline**: Months 4-12 after pilot completion

**Subscription Model**:
- **Pricing**: $15,000/month ($180,000/year)
- **Includes**:
  - Unlimited process imports (PNML, BPMN, XPDL, CSV logs)
  - Monthly deployment to Camunda/Zeebe/custom target
  - Automated verification reports (soundness, conformance, risk alerts)
  - 2 FTE equivalents of support (Slack, email, sprint cycles)
  - Quarterly business reviews (optimization, metrics, roadmap)
- **Overage**: $1,500 per additional process beyond 10/month

**Expansion Path**:
- **Month 3**: 1 process deployed (pilot)
- **Month 4-6**: 3-5 processes/month (pilot team learning curve)
- **Month 7-12**: 8-15 processes/month (scaled to second team, production runs)
- **Year 2**: 30+ processes deployed, new use cases (governance, conformance monitoring)

**Economics for Customer**:
- Manual implementation cost: $100K per process
- YAWL ggen cost: $1,500 per process (at 10/month rate)
- Savings: 98.5% per process after pilot ($98.5K/process)
- 10 processes/month = $985K savings/month = $11.8M/year
- 3-year savings: $35M+

**Upsell Opportunities**:
- Custom format integration (+$5K/mo): CSV, SQL, streaming logs
- Conformance monitoring (+$3K/mo): Ongoing model accuracy tracking
- Advanced governance (+$5K/mo): Audit trails, role-based deployment, change tracking
- Multi-cloud deployment (+$3K/mo): Deploy same process to Camunda + Zeebe + SAP

---

### Enterprise: Platform License ($150K/year + 20% annual maintenance)

**Objective**: Win entire organization with unrestricted deployment rights.

**Target Customer Profile**:
- 50+ processes in production
- 3+ deployment targets (Camunda, custom workflow, third-party)
- 20+ internal users (process designers, architects, analysts)
- Long-term partnership model preferred

**Licensing Model**:
- **Year 1**: $150,000 (platform license)
- **Year 2+**: $150,000 + 20% maintenance ($30,000) = $180,000/year
- **Includes**:
  - All previous features (unlimited imports, multi-target deploy)
  - On-premise installation option (Docker Compose)
  - Custom integration development (2 sprints/year)
  - White-label option (rebrand as internal tool)
  - 4 FTE equivalents of support
  - Quarterly executive business reviews
  - Priority feature development (2-3 custom features/year)
  - SLA: 99.5% uptime, 4-hour critical issue response

**Deal Structure**:
- **Negotiation Window**: 30-45 days
- **Typical Discount**: 15-25% off list price for multi-year commitments
- **Financing**: Lease option (60-month term, ~$3,500/month)
- **Contract Terms**: 3-year with auto-renewal, 90-day exit clause

**Enterprise Value Drivers**:
- Internal IT cost avoidance: 50+ processes × $100K manual = $5M
- Time-to-market acceleration: 4 weeks → 2 weeks average
- Operational risk reduction: Formal verification → audit-ready processes
- Strategic optionality: Multi-target deployment enables tool switching

**Use Cases That Justify Enterprise License**:
- Global financial services (500+ offices, multiple process systems)
- Pharmaceutical manufacturing (FDA compliance, 20+ plants)
- Insurance provider (30 product lines, 50+ claims workflows)
- Public sector agency (federal, state, local integration)

---

### Channel: Cloud Marketplace (AWS/Azure/GCP)

**Objective**: Achieve 30% of new customer acquisition via cloud marketplaces (zero CAC model).

**Marketplace Strategy**:

**AWS Marketplace**:
- Product listing: YAWL ggen - Process Mining to Deployment
- Pricing model: Hourly consumption + annual seat licenses
- Integration: CloudFormation templates for 1-click deployment
- Co-selling: AWS Partner Network (APN) Technology Partner tier
- Target: Finance/Banking APN competencies

**Azure Marketplace**:
- Product listing: YAWL ggen for Azure + Automation (AppSource)
- Pricing model: SaaS subscription + Azure consumption (storage, compute)
- Integration: Azure Resource Manager templates
- Co-selling: Microsoft Partner ecosystem (Gold tier)
- Target: Process Mining Services partners

**GCP Marketplace**:
- Product listing: YAWL ggen on Google Cloud
- Pricing model: GCP consumption-based billing
- Integration: Terraform GCP provider templates
- Co-selling: Google Cloud Partner (Technology Partner)
- Target: Process Intelligence partners

**Marketplace Economics**:
- Platform fee: 30% of customer invoice (AWS, Azure, GCP standard)
- Reduced CAC: $0 (organic discovery via marketplace search)
- Customer acquisition cost: ~$1,000 (support, onboarding, docs)
- LTV improvement: 3-5× (reduced sales cycle via self-serve)

**Marketplace Launch Timeline**:
- Month 1: AWS Marketplace submission (technical validation)
- Month 2: Azure Marketplace submission (Seller Dashboard)
- Month 3: GCP Marketplace submission
- Month 4: All three live, co-sell campaigns active

---

## Section 4: 90-Day Launch Plan

### Phase 1: Days 1-30 — Technical Validation & Design Partner PoCs

**Objective**: Validate product-market fit with 5 design partners, build case studies.

**Activities**:

**Week 1**:
- [ ] Select 5 design partners (mix: F500 + SI + regulated industry)
- [ ] Conduct intake calls: process discovery tool + deployment gap
- [ ] Secure executive sponsorship (C-level commitment)
- [ ] Set success metrics (deployment time, rework %, cost savings)

**Week 2-3**:
- [ ] Free PoC kickoff (no cost to customer, value-share agreement)
- [ ] Import customer's top 3 discovered processes
- [ ] Run automated verification pipeline
- [ ] Deploy to Camunda (test environment)
- [ ] Gather feedback on UX, feature gaps, deployment targets

**Week 4**:
- [ ] Mid-point review: "Is this solving your problem?"
- [ ] Iterate on feedback (product roadmap adjustments)
- [ ] Collect testimonials, quote for case study
- [ ] Negotiate transition to paid pilot ($50K PoC) or case study only

**Success Metrics**:
- 5/5 design partners engaged (100%)
- 3/5 proceed to paid pilot (60%)
- 2/5 become case study customers (40%)
- NPS feedback: >40 (product-market fit signal)

---

### Phase 2: Days 31-60 — SI Partnership Agreements

**Objective**: Secure 3-5 SI partnerships for co-go-to-market and joint delivery.

**Target Partners**:
- Accenture (process mining + automation practice)
- Deloitte (management consulting + implementation)
- EY (BPM + digital transformation)
- Capgemini (process services + cloud)
- IBM (enterprise consulting + delivery)

**Activities**:

**Week 5-6**:
- [ ] Develop SI partnership deck (1-page, joint value prop)
- [ ] Identify SI partner executive sponsor (practice leader)
- [ ] Schedule 30-min pitch with practice P&L owner
- [ ] Demo YAWL ggen in context of SI's current delivery model

**Week 7-8**:
- [ ] Negotiate partnership MOU (mutual non-exclusive)
- [ ] Define joint go-to-market: co-sell, co-deliver, revenue share
- [ ] Typical deal: YAWL ggen 30% of services revenue
- [ ] SI provides: lead generation, joint customer workshops, delivery team training

**Partnership Economics**:
- SI delivers process mining for $500K engagement
- Customer adopts YAWL ggen: 30% of services ($150K) paid to YAWL
- Gross margin: $300K (60% for SI, 40% for YAWL ggen)
- Volume: 1 partnership × $500K = $500K revenue in year 1

**Success Metrics**:
- 3/5 partnerships signed (60% close rate)
- 1 joint customer engagement by end of quarter
- SI roadmap includes YAWL ggen in future deals

---

### Phase 3: Days 61-90 — Cloud Marketplace Launch + First Paying Customers

**Objective**: Go live on 3 cloud marketplaces, secure 2-3 paying customers.

**Activities**:

**Week 9**:
- [ ] Submit AWS Marketplace listing (technical onboarding)
- [ ] Prepare listing copy, pricing tiers, demo video
- [ ] Secure AWS Partner Network badge (if available)

**Week 10**:
- [ ] AWS listing goes live (estimated 2-week review)
- [ ] Submit Azure Marketplace listing in parallel
- [ ] Activate cloud provider co-marketing programs

**Week 11-12**:
- [ ] GCP Marketplace submission
- [ ] Activate cloud marketplace SEO (keywords, meta tags)
- [ ] Launch cloud provider "process mining solutions" campaigns
- [ ] Announce in AWS/Azure/GCP community channels

**Paid Customer Acquisition**:
- [ ] Pilot customers convert: 3/5 design partners → $50K PoCs
- [ ] SI partnerships generate: 1 new deal (customer from SI network)
- [ ] Marketplace generates: 1-2 early adopters (self-serve signup)

**Target Revenue by Day 90**:
- 3 active pilots: $150K (3 × $50K)
- 1 SI partnership deal: $50K (YAWL ggen share)
- 1-2 early marketplace customers: $20K-$40K (partial month billing)
- **Total**: $220K-$240K in revenue or contracts signed

---

## Section 5: Key Performance Indicators (KPIs)

### Top-Line Metrics

| KPI | Q1 Target | Q2 Target | Q3 Target | Q4 Target | Year 1 Target |
|---|---|---|---|---|---|
| **Customers Acquired** | 3 (pilots) | 5 | 10 | 15 | 33 |
| **ARR (Annual Recurring)** | $150K | $300K | $700K | $1.2M | $1.5M |
| **Total Revenue** | $300K | $350K | $500K | $600K | $1.75M |
| **Marketplace Revenue %** | 0% | 5% | 10% | 20% | 12% |
| **SI Partnership Revenue** | $50K | $100K | $150K | $200K | $500K |

### Sales Funnel Metrics

| Stage | Q1 Target | Conversion % | Note |
|---|---|---|---|
| **MQLs (Marketing Qualified Leads)** | 20 | — | From design partners, webinars, content |
| **SQLs (Sales Qualified Leads)** | 10 | 50% | Discovery call + technical fit confirmed |
| **Pilots / PoCs** | 6 | 60% | Win design partners + SI partners |
| **Closed Deals** | 3 | 50% | Pilots convert to paid or case studies |
| **ARR Customers** | 2 | 33% | Expanded from pilots to annual subscriptions |

**Conversion Funnel**:
- MQL → SQL: 50%
- SQL → PoC: 60%
- PoC → Closed: 50%
- Closed → ARR: 33%
- **Overall**: 20 MQLs → 5 SQLs → 3 PoCs → 1.5 Closed → 0.5 ARR

### Unit Economics

| Metric | Target | Calculation |
|---|---|---|
| **CAC (Customer Acquisition Cost)** | $5,000 | $100K sales/marketing spend ÷ 20 customers |
| **LTV (Customer Lifetime Value)** | $60,000 | $15K annual × 4-year average lifetime |
| **LTV:CAC Ratio** | 12:1 | $60K ÷ $5K (healthy: >3:1) |
| **Payback Period** | 4 months | $5K CAC ÷ ($15K ÷ 12 months) |
| **Gross Margin** | 70% | $15K revenue - $4.5K delivery cost |
| **Magic Number** | 2.5+ | ($growth in ARR) ÷ (sales/marketing spend) |

### Customer Health & Expansion

| KPI | Target | Definition |
|---|---|---|
| **NPS (Net Promoter Score)** | 50+ | "Would you recommend YAWL ggen?" (0-10 scale) |
| **Customer Retention** | 90%+ | % of customers renewing at year 2 |
| **Net Revenue Retention** | 120%+ | (revenue from existing customers year 2) ÷ (revenue year 1) |
| **Expansion Revenue %** | 30%+ | % of new revenue from existing customers |
| **Support Tickets/Customer/Month** | <5 | Quality and UX signal |
| **Feature Requests/Customer/Quarter** | 2-3 | Engagement and usage signal |

### Product Metrics

| KPI | Target | Definition |
|---|---|---|
| **Process Import Success Rate** | 95%+ | % of BPMN/PNML files import without errors |
| **Verification Pass Rate** | 85%+ | % of imported processes pass soundness checks |
| **Deployment Success Rate** | 95%+ | % of processes deploy to Camunda without manual intervention |
| **Time to Deploy** | <2 weeks | Median time from discovery model to production |
| **Manual Rework %** | <15% | % of process logic requiring human adjustment |
| **Uptime** | 99.5%+ | API + dashboard availability |

### Market Penetration

| Metric | Year 1 | Year 2 | Year 3 | Target |
|---|---|---|---|---|
| **TAM Capture %** | 0.1% | 0.3% | 1.0% | $10M+ ARR |
| **SAM Capture %** | 0.4% | 1.2% | 4.0% | $16M ARR |
| **F500 Customers** | 2 | 5 | 15 | 50 by Y5 |
| **SI Partner Deals** | 5 | 20 | 60 | Self-sustaining channel |
| **Cloud Marketplace %** | 0% | 8% | 20%+ | Primary channel |

---

## Section 5B: Quarterly Business Review (QBR) Scorecard

**Format**: Executive dashboard updated monthly, reviewed with customers quarterly.

**Sample QBR for $15K/month Subscription Customer**:

```
CUSTOMER: Global Bank (sample)
CONTRACT VALUE: $15K/month ($180K/year)
MONTH: February 2026

DEPLOYMENT METRICS:
├─ Processes Deployed: 8/10 (80% of target)
├─ Average Deployment Time: 12 days (vs. 14-day target)
├─ Soundness Pass Rate: 94% (vs. 85% baseline)
└─ Manual Rework %: 8% (vs. 15% target) ✓ EXCEEDS

BUSINESS IMPACT:
├─ Cost Savings (vs. manual): $792K (8 processes × $99K each)
├─ Time-to-Market: 4 weeks → 2 weeks (50% faster)
├─ Process Volume: 42 processes in pipeline
└─ Projected Year 1 Savings: $4.2M

USAGE METRICS:
├─ Active Users: 12 (process designers + architects)
├─ Imports/Month: 15 (vs. 10 target) ✓ EXCEEDS
├─ Feature Adoption: 65% (conformance monitoring, governance)
└─ Support Tickets: 3/month (high quality signal)

CUSTOMER HEALTH:
├─ NPS Score: 52 (product-market fit achieved)
├─ Expansion Signals: Discussing conformance monitoring (+$3K/mo)
├─ Risk Signals: None
└─ Next Steps: Q2 roadmap review, conformance pilot

YAWL ggen COMMITMENT:
├─ Quarterly optimization review: Scheduled for March 15
├─ Dedicated technical lead: Assigned (Sarah Chen)
├─ Custom integration request: RFP due for Q2 (priority 1)
└─ Forecast: High confidence in renewal + expansion
```

---

## Success Criteria for 90-Day Plan

**Green Lights** (all must be achieved):
- ✓ 5/5 design partners engaged (100%)
- ✓ 3/5 proceed to paid pilot or case study (60%+)
- ✓ 3/5 SI partnerships signed (60%+)
- ✓ 3 cloud marketplaces live (100%)
- ✓ 2+ paying customers acquired ($50K+ ARR)
- ✓ $300K+ total revenue or contracts signed
- ✓ Product NPS >40 (product-market fit)
- ✓ <15% manual rework in all pilots

**Yellow Lights** (investigate, course correct):
- Design partners NPS < 40 (product gap)
- SI partnerships <2 signed (positioning issue)
- Marketplace go-live >14 days late (ops issue)
- Paying customers <2 by day 90 (sales issue)
- Manual rework >20% (product issue)

**Red Lights** (halt, pivot):
- <3 design partners engaged (market validation failure)
- 0 SI partnerships signed (channel doesn't work)
- Product cannot deploy 80%+ processes automatically (fatal flaw)
- >2 customers churn in year 1 (product-market mismatch)

---

## Summary: From Zero to $1.75M Revenue in Year 1

**Phase 1 (Days 1-30)**: Validate with design partners, secure executive sponsorships, start building case studies.

**Phase 2 (Days 31-60)**: Land SI partnerships as primary channel, negotiate co-go-to-market agreements, activate delivery teams.

**Phase 3 (Days 61-90)**: Launch cloud marketplaces for self-serve growth, convert pilots to paid customers, achieve $300K+ revenue.

**Months 4-12**: Scale to 33 customers, $1.5M ARR, 3 SI partnerships actively delivering, cloud marketplace driving 12%+ of new customer acquisition.

**Key Success Factors**:
1. **Design Partner Selection**: Mix of greenfield + brownfield, F500 + SI + regulated
2. **SI Channel**: Revenue share model (30% to partners) attracts premium partners
3. **Cloud Marketplace**: Zero CAC advantage for net-new customers
4. **Executive Alignment**: VP-level sponsors in each customer + SI
5. **Product Excellence**: <15% manual rework across all pilots (sets expectation)

---

**Next Steps**: Approve 90-day plan, allocate $500K sales/marketing budget, hire sales lead by Day 3, finalize design partner list by Day 5.
