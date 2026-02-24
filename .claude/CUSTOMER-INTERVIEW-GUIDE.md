# YAWL ggen Customer Discovery Interview Guide

**Purpose**: Validate market need, product positioning, and buying signals with process excellence teams.

**Duration**: 45-60 minutes
**Format**: Structured conversation (not a sales pitch)
**Target Audience**: VP Process Excellence, Process Mining Director, Automation Manager
**Outcome**: Qualification (hot/warm/cold), product feedback, next steps

---

## Pre-Interview Checklist

**Research Before Call**:
- [ ] Company: Industry, size, process mining tools used
- [ ] Contact: Title, LinkedIn profile, prior relationship
- [ ] MEDDIC qualification:
  - Metrics: Process volumes, timelines, cost targets
  - Economic buyer: Who controls $50K+ budget?
  - Decision criteria: Technical, financial, organizational
  - Decision process: Committee? Procurement timeline?
  - Identify pain: Process discovery → deployment gap
  - Champion: Executive sponsor in company?

**Technical Preparation**:
- [ ] Access to live YAWL ggen demo (sandboxed customer data)
- [ ] Case study from similar industry/company size
- [ ] Sample BPMN process (order-to-cash or procure-to-pay)
- [ ] ROI calculator (time + cost savings)

---

## Opening: Build Rapport & Frame Conversation

**Duration**: 3-5 minutes

**Opening Script**:
"Thanks for taking time today. I'm excited to chat about your process discovery and automation initiatives. Before we dive in, I want to be transparent: this isn't a sales pitch. My goal is to understand how you're currently discovering and deploying processes, where the bottlenecks are, and whether YAWL ggen might be useful down the road. We'll share what we're building, get your feedback, and see if there's a fit. Sound good?"

**Rapport Builders**:
- Mention their company's public press (process automation, AI investments)
- Reference their LinkedIn posts on digital transformation
- Acknowledge their industry-specific challenges (FSI compliance, manufacturing uptime)

---

## Section 1: Current State — Discovery & Deployment Today

### Question 1.1: Process Discovery Approach
**Primary Question**:
"Walk me through how you're currently discovering processes. What tools are you using, and how many processes have you discovered in the past 12 months?"

**What to Listen For**:
- Tool names: Celonis, UiPath, SAP SolManager, in-house tools
- Volume: 5 processes vs. 50+ processes (scale signal)
- Data source: Transaction logs, event logs, surveys, workshops
- Ownership: IT, process excellence team, business units

**Follow-Up Probes**:
- "How much manual effort goes into each discovery?" (measurement of pain)
- "What formats do those tools output?" (BPMN, proprietary, visualization-only)
- "Who are the main users of the discovery output?" (business analysts, IT, operations)

**Red Flags** (suggest tool-only, limited deployment):
- "We use Celonis to build dashboards" (discovery but not action)
- "The output is mostly for visualization/reporting" (no deployment intent)
- "Only the IT team sees the discoveries" (isolated from operations)

**Green Flags** (suggest deployment readiness):
- "We've discovered 50+ processes and need to deploy them" (pain is acute)
- "We have 20+ Camunda instances waiting for process definitions" (infrastructure ready)
- "Business teams are pushing to deploy discovered models" (demand signal)

---

### Question 1.2: Current Deployment Process
**Primary Question**:
"After discovery, what happens next? Walk me through how you get a discovered process into production workflow?"

**What to Listen For**:
- Current state: Manual rework, months-long cycles, multiple handoffs
- People involved: Business analysts, process architects, IT developers, operators
- Tools used: Camunda, custom Java/Python, RPA bots, manual worksheet-to-code
- Time & cost: 8 weeks per process, $100K+ spend, 60+ rework iterations

**Follow-Up Probes**:
- "What's the biggest bottleneck in taking a discovered model to production?" (core pain)
- "How many people touch a process before it's deployed?" (handoff complexity)
- "What percentage of a discovered model makes it into production unchanged?" (effort to change)
- "Who are the decision-makers on whether a discovered model is deployment-ready?" (stakeholder map)

**Red Flags** (not a fit):
- "We mostly just analyze processes, deployment is not our focus" (no pain)
- "We already have a tool that does this" (build vs. buy decided)
- "Our IT organization won't let us use new tools" (organizational constraint)

**Green Flags** (hot prospect):
- "We're currently doing this manually and it takes 6+ months per process" (pain + timeline)
- "We have 30+ processes waiting to deploy and we're understaffed" (scale + volume)
- "Camunda is our target platform but we need an automated bridge from discovery to deployment" (platform fit)
- "Our CFO is pushing us to cut implementation costs by 50%" (budget pressure/ROI motive)

---

### Question 1.3: Current Pain Points
**Primary Question**:
"What are your top 3 frustrations with the current process discovery-to-deployment pipeline?"

**What to Listen For**:
- Technical pain: Format incompatibility, manual format conversion, no validation
- Organizational pain: Handoffs, rework loops, knowledge loss, long cycles
- Financial pain: High cost per process, staff constraints, slow ROI
- Compliance pain: No audit trail, informal verification, regulatory risk

**Follow-Up Probes**:
- "What percentage of deployed processes fail in production?" (quality signal)
- "Have you ever had a deployed process break a business rule?" (compliance risk)
- "If you could magic-wand one pain away, what would it be?" (prioritization)
- "What's costing you the most right now—time, money, or risk?" (motivation)

**Red Flags**:
- "We're pretty happy with the current process" (no urgency)
- "Our bottleneck is elsewhere (not discovery-to-deployment)" (wrong problem)

**Green Flags**:
- "We're losing 3-4 months per process on rework and validation" (time pain)
- "We're spending $150K per process on manual implementation and we have 20 waiting" (money pain = $3M+)
- "We need formal verification before deployment for compliance reasons" (compliance pain + premium feature)
- "We have 5 different Camunda instances and deploying across them is a nightmare" (multi-target pain)

---

## Section 2: Motivation & Budget — Why They'd Buy

### Question 2.1: Ideal Outcome
**Primary Question**:
"If you could snap your fingers and have the perfect process discovery-to-deployment workflow, what would that look like?"

**What to Listen For**:
- Timeline: "3 weeks instead of 12 weeks per process"
- Cost: "Reduce cost per process from $150K to $20K"
- Quality: "95%+ of discovered models deployable with <10% rework"
- Scale: "Deploy 50+ processes per year instead of 5"
- Compliance: "Formal verification + audit trail for regulatory sign-off"

**Follow-Up Probes**:
- "What would success look like 6 months from now?" (milestone/timeline)
- "What KPIs would you use to measure improvement?" (metric orientation)
- "Who would you need to convince to adopt this?" (decision complexity)

**Green Flags**:
- "We need to go from 6-month cycle to 4-week cycle" (aggressive timeline = urgency)
- "We want one integrated platform instead of 5 different tools" (integration pain + buying signal)
- "Compliance verification is non-negotiable for us" (premium willingness)

---

### Question 2.2: Budget Authority & Timeline
**Primary Question**:
"If we showed you could reduce time-to-deployment by 80% and cost per process by 75%, what would it take to get budget approved for a 3-month pilot?"

**What to Listen For**:
- Budget owner: "VP of Operations," "CFO," "myself (director level)"
- Approval threshold: "$50K is director-level approval, $150K needs CFO"
- Procurement timeline: "4 weeks standard," "2 weeks expedited if strategic"
- Cost justification: "ROI-based," "operational efficiency," "risk reduction"
- Competitive evaluation: "Will need to compare with other solutions"

**Follow-Up Probes**:
- "What's the approval process for a $50K software pilot?" (procurement path)
- "Is budget already allocated for process automation in FY2026?" (timing)
- "Would you need multiple vendor evaluations or are you ready to move fast?" (sales cycle)
- "What ROI threshold would justify the investment?" (financial hurdle)

**Red Flags**:
- "Budget is locked until Q3" (timing constraint, revisit later)
- "We'd need 6+ month evaluation cycle with 5 vendors" (long sales process)
- "We can only do this as a no-cost beta" (not a serious buyer)

**Green Flags**:
- "We have $50-100K in discretionary budget and can move quickly" (ready to buy)
- "ROI of 2:1 or better in year 1 would get approved" (financially motivated)
- "We could make a decision within 30 days with a successful PoC" (fast deal possible)
- "Our VP of Operations is already expecting us to find a solution" (executive sponsorship)

---

### Question 2.3: Competitive Awareness
**Primary Question**:
"Have you looked at any other solutions for this problem? How does your current process compare to what you see in the market?"

**What to Listen For**:
- Competitive tools known: Celonis (usually), UiPath, ProM, SAP, others
- Perception of solutions: "Mostly dashboards, not deployment," "expensive," "not integrated"
- Decision criteria: "We need formal verification," "multi-cloud capability," "open source preferred"
- Switching costs: "Locked into Celonis," "invested in Camunda"

**Follow-Up Probes**:
- "What would make you switch from your current tools?" (decision driver)
- "How important is open source to your organization?" (licensing)
- "Would multi-cloud deployment capability be valuable?" (feature fit)
- "How important is formal process verification?" (product fit)

**Green Flags**:
- "We haven't found anything that does discovery + deployment in one place" (YAWL ggen is unique)
- "We like that YAWL is open source and we can deploy on-prem" (positioning match)
- "Formal verification is a must-have for us" (premium feature resonates)
- "We want to stay tool-agnostic and deploy to multiple targets" (multi-cloud need)

---

## Section 3: Buying Signals & Qualification

### Question 3.1: Technical Fit Assessment
**Primary Question**:
"Let me show you a sample—we take a discovered BPMN process and automatically deploy it to Camunda with formal verification. Does that match what you're trying to achieve?"

**What to Listen For**:
- Immediate reactions: Interest level, questions, objections
- Technical feasibility: "Can it handle our custom rules?" "Does it work with our event logs?"
- Platform fit: "We use Camunda," "We also use Zeebe," "We use custom Java"
- Format compatibility: "Our Celonis output is JSON," "SAP outputs XPDL"

**Follow-Up Probes**:
- "What formats would you need to import?" (technical requirement)
- "What deployment targets do you use?" (platform diversity)
- "Do you have custom process logic we'd need to handle?" (complexity)

**Green Flags**:
- "Yes, this is exactly what we need" (product-market fit)
- "Can you also handle [specific format/target]?" (engaged, problem-solving)
- "This would save us months of work" (value recognition)

**Red Flags**:
- "This doesn't match our technical stack" (misfit)
- "We need something more custom/specialized" (not a fit)

---

### Question 3.2: Organizational Fit Assessment
**Primary Question**:
"Who else in your organization would need to be involved in evaluating and implementing something like this?"

**What to Listen For**:
- Decision committee: IT, business process, operations, compliance
- Champions: Who's pushing for process automation?
- Blockers: Who might resist?
- Approval hierarchy: Director → VP → CFO chain

**Follow-Up Probes**:
- "Is your IT organization supportive of new process automation tools?" (org constraint)
- "Who would be the primary users?" (adoption signal)
- "Do you have internal champions pushing this initiative?" (sponsor confirmation)

**Green Flags**:
- "We have an executive sponsor pushing this initiative" (top-down support)
- "The team is hungry for tools to accelerate deployment" (bottom-up demand)
- "IT is supportive and looking for integrated solutions" (no org friction)

**Red Flags**:
- "IT is very conservative and skeptical of new tools" (adoption risk)
- "Different teams have conflicting priorities" (org misalignment)
- "No executive sponsor is pushing process automation" (no air cover)

---

### Question 3.3: Success Criteria
**Primary Question**:
"If we did a 3-month pilot with you, what would need to happen for you to say this was successful?"

**What to Listen For**:
- Metrics: Specific time/cost/quality targets
- Scope: Number of processes, deployment targets, team size
- Risk tolerance: What could go wrong?
- Next steps: Path from pilot to production

**Follow-Up Probes**:
- "What would make you say 'this is worth paying for'?" (value threshold)
- "What's the minimum you'd need to see in a pilot?" (scope negotiation)
- "What are your biggest concerns about implementing something new?" (risk assessment)

**Green Flags**:
- "If you deploy 2-3 processes in 4 weeks with <15% rework, we'd be very interested" (achievable pilot scope)
- "We'd want to see formal verification reports showing the deployed processes are correct" (feature validation)
- "Cost reduction to $20K per process would justify the investment" (financial threshold)

**Red Flags**:
- "We'd need to deploy 50 processes in the pilot" (scope creep, unrealistic)
- "We need 100% of our discovered processes to work unchanged" (perfectionism)
- "We need a 12-month evaluation before deciding" (too long)

---

## Section 4: Closing & Next Steps

### Question 4.1: Closing Question
**Primary Question**:
"Based on what we've discussed, how much would a solution like this help with your process deployment challenges—a little, somewhat, or a lot?"

**Scoring Responses**:
- **A lot** = Hot prospect (move to pilot proposal)
- **Somewhat** = Warm prospect (stay in touch, follow up monthly)
- **A little** = Cold prospect (not a fit right now, add to nurture list)

---

### Question 4.2: Next Steps
**Depending on Prospect Temperature**:

**HOT Prospect** ("A lot"):
- [ ] Schedule technical architecture review (1 hour, your IT + our architects)
- [ ] Offer free PoC (no cost, 4-week scope, 1-2 representative processes)
- [ ] Send case study from similar company (same industry, same scale)
- [ ] Follow up within 3 days with formal PoC proposal

**WARM Prospect** ("Somewhat"):
- [ ] Stay in touch (quarterly webinar invitations, relevant content)
- [ ] Share case studies matching their use case
- [ ] Set up 30-minute followup in 2 months ("checking in to see if priorities have changed")
- [ ] Offer free trial account (self-serve, no commitment)

**COLD Prospect** ("A little"):
- [ ] Thank them for their time, ask permission to stay in touch
- [ ] Add to marketing nurture list (webinars, whitepapers, thought leadership)
- [ ] Note in CRM: "Not a fit right now, revisit Q3 when process deployment initiatives mature"
- [ ] No aggressive follow-up

---

### Question 4.3: Permission to Reference
**If Hot or Warm**:
"Would you be open to us chatting with one or two other people in your organization to understand the bigger picture better?"

**Follow-up Actions**:
- [ ] Ask for contact names and titles (process architect, IT director, operations)
- [ ] Request 15-minute intro call with each (not a full discovery, just introduction)
- [ ] Track all stakeholders in CRM (decision committee mapping)

---

## Post-Interview Debrief

**Within 24 Hours, Capture**:

### Interview Summary Template

```
PROSPECT: [Company Name]
DATE: [MM/DD/YYYY]
INTERVIEWER: [Your Name]
CONTACT: [Name, Title, Email, Phone]

COMPANY PROFILE:
├─ Industry: [E.g., Financial Services]
├─ Size: [$500M - $50B revenue]
├─ Process Mining Tool: [Celonis, UiPath, etc.]
├─ Discovered Processes: [0-10, 11-50, 50+]
└─ Deployment Infrastructure: [Camunda, custom, other]

DISCOVERY ASSESSMENT:
├─ Volume: [1-5, 6-20, 20+ processes discovered]
├─ Maturity: [Tool-only, basic, advanced]
├─ Pain Level: [Low, Medium, High]
└─ Deployment Readiness: [Not ready, somewhat ready, very ready]

DEPLOYMENT PAIN:
├─ Current Cycle Time: [X weeks/process]
├─ Cost Per Process: [$X]
├─ Manual Rework %: [X%]
├─ Compliance Requirements: [Yes/No, describe]
└─ Multi-Target Deployment: [Yes/No, describe]

FINANCIAL MOTIVATION:
├─ Annual Process Deployment Volume: [X processes]
├─ Estimated Cost Savings (YAWL): [$X annually]
├─ Budget Available: [$0, $0-50K, $50-150K, $150K+]
├─ Approval Timeline: [Months to decision]
└─ ROI Threshold: [Break-even period: X months]

ORGANIZATIONAL:
├─ Executive Sponsor: [Name, Title, Confidence level]
├─ Primary User: [Name, Title]
├─ Decision Committee Size: [1-3 people, 4-6, 7+]
├─ Procurement Process: [Fast-track, standard, long-tail]
└─ Org Barriers: [None, IT resistance, budget constraints, other]

PRODUCT FIT:
├─ Format Requirements: [BPMN, PNML, other]
├─ Deployment Targets: [Camunda, Zeebe, custom]
├─ Formal Verification Need: [Must-have, nice-to-have, not important]
├─ Multi-Cloud: [Critical, important, not needed]
└─ Open Source Preference: [Yes, no opinion, required]

COMPETITIVE LANDSCAPE:
├─ Known Competitors: [List tools they've considered]
├─ Perceived Gaps: [What current solutions don't do]
├─ YAWL ggen Fit: [Unique, better, different]
└─ Switching Costs: [High, medium, low]

QUALIFICATION SCORE:
├─ Pain Level: [1-10] __
├─ Budget Authority: [1-10] __
├─ Timeline: [1-10] __
├─ Product Fit: [1-10] __
├─ Org Readiness: [1-10] __
└─ OVERALL: [Avg score: 1-10] __

PROSPECT TEMPERATURE:
[ ] HOT (8-10/10): Move to pilot proposal immediately
[ ] WARM (5-7/10): Stay in touch, follow up in 60 days
[ ] COLD (1-4/10): Add to nurture list, minimal follow-up

NEXT STEPS:
[ ] Technical architecture review (date/time: ________)
[ ] Send PoC proposal (target send date: ________)
[ ] Intro call with stakeholder: ____________
[ ] Schedule followup call (date/time: ________)
[ ] Send case study / reference (date: ________)
[ ] Add to marketing nurture sequence
[ ] Other: ____________________

NOTES:
[Any insights, obstacles, opportunities, personal details]

ATTACHMENTS:
├─ Recording: [link or file]
├─ Notes: [shared doc link]
├─ Whiteboard: [photo link]
└─ Follow-up email: [copy/paste]
```

---

## Objection Handling Guide

**If They Say...**  → **You Say...**

| Objection | Response |
|---|---|
| "We're happy with our current process" | "That's great. I'm curious—how long does it take to deploy each process, and are there any places where the handoffs slow you down? Even happy customers sometimes find efficiency gains." |
| "We don't have budget right now" | "Understood. If you did have budget, what would need to happen in your process discovery/deployment pipeline to make this a priority? Let's stay in touch when priorities shift." |
| "We'd need to evaluate multiple vendors" | "Completely reasonable. What's your timeline for evaluation? We're happy to be part of that process, and we can start with a lightweight PoC to show value quickly." |
| "Our IT team would never allow a new tool" | "What's their typical concern with new tools? Is it integration complexity, security, cost? Understanding that helps us work with them effectively." |
| "We need formal verification but we don't have a use case yet" | "We see that a lot in regulated industries. Formal verification is built-in to YAWL ggen. When you do need it, it's ready. For now, the deployment automation alone usually saves 60-70% of implementation time." |
| "Our deployment target is custom (not Camunda)" | "What's your custom framework built on? [Java/Python/other] We can often build connectors to custom systems. Let's have a technical conversation with your architects to see if it's feasible." |
| "We need on-premise installation" | "We support Docker-based on-prem deployment. For a PoC, we can set it up in your environment within a week. For production, it's a standard installation on your infrastructure." |
| "How is YAWL different from Celonis + manual Camunda development?" | "Celonis discovers processes beautifully. YAWL ggen automates the journey from discovery to deployed, verified Camunda instance. You can keep using Celonis for discovery—we pick up where they leave off with deployment." |

---

## Interview Metrics (Track Across All Interviews)

**After 10 Interviews**, Calculate:

| Metric | Calculation | Target |
|---|---|---|
| **Avg Prospect Temperature** | Sum of scores ÷ 10 | 6+/10 (product-market fit) |
| **Hot Prospects %** | # HOT ÷ 10 | 30%+ |
| **Warm Prospects %** | # WARM ÷ 10 | 50%+ |
| **Cold Prospects %** | # COLD ÷ 10 | <20% |
| **Pain Recognition %** | # Prospects with high pain ÷ 10 | 80%+ |
| **Budget Authority %** | # With clear budget owner ÷ 10 | 80%+ |
| **Timeline Clarity %** | # With clear approval timeline ÷ 10 | 70%+ |
| **NPS Intent Score** | Avg willingness to recommend ÷ 10 | 50+ |

---

**Final Note**: The goal is not to sell, but to deeply understand the customer's world. The best interviews feel like a conversation, not an interrogation. Listen 70% of the time, talk 30%.
