# Infrastructure & Network Diagrams - YAWL Cloud Platform

**Detailed infrastructure, network, and data flow diagrams**

---

## 🌐 Network Architecture Diagram

### Multi-Cloud Network Topology

```plantuml
@startuml Network_Architecture
!theme plain

cloud "Internet" as internet {
    component "CDN (CloudFlare)" as cdn
    component "DDoS Protection" as ddos
}

cloud "AWS Region: us-east-1" {
    rectangle "VPC: 10.0.0.0/16" {
        rectangle "Public Subnet: 10.0.1.0/24" {
            component "ALB" as alb_aws
            component "NAT Gateway" as nat_aws
        }

        rectangle "Private Subnet: 10.0.2.0/24" {
            component "EKS Cluster" as eks
            component "App Pods" as app_aws
        }

        rectangle "Data Subnet: 10.0.3.0/24" {
            database "RDS PostgreSQL" as rds
            database "ElastiCache Redis" as redis_aws
        }
    }
}

cloud "Azure Region: eastus" {
    rectangle "VNet: 10.1.0.0/16" {
        rectangle "Public Subnet: 10.1.1.0/24" {
            component "App Gateway" as appgw
            component "NAT Gateway" as nat_azure
        }

        rectangle "Private Subnet: 10.1.2.0/24" {
            component "AKS Cluster" as aks
            component "App Pods" as app_azure
        }

        rectangle "Data Subnet: 10.1.3.0/24" {
            database "PostgreSQL Flexible" as pg_azure
            database "Azure Cache for Redis" as redis_azure
        }
    }
}

cloud "GCP Region: us-central1" {
    rectangle "VPC: 10.2.0.0/16" {
        rectangle "Public Subnet: 10.2.1.0/24" {
            component "Cloud Load Balancer" as lb_gcp
            component "Cloud NAT" as nat_gcp
        }

        rectangle "Private Subnet: 10.2.2.0/24" {
            component "GKE Cluster" as gke
            component "App Pods" as app_gcp
        }

        rectangle "Data Subnet: 10.2.3.0/24" {
            database "Cloud SQL" as cloudsql
            database "Memorystore Redis" as redis_gcp
        }
    }
}

cloud "Traffic Manager" as gtm {
    component "Global Load Balancer\n(GeoDNS)" as gslb
}

internet --> cdn
cdn --> ddos
ddos --> gslb

gslb --> alb_aws : "US East traffic"
gslb --> appgw : "EU traffic"
gslb --> lb_gcp : "APAC traffic"

alb_aws --> eks
appgw --> aks
lb_gcp --> gke

eks --> app_aws
aks --> app_azure
gke --> app_gcp

app_aws --> rds
app_aws --> redis_aws
app_azure --> pg_azure
app_azure --> redis_azure
app_gcp --> cloudsql
app_gcp --> redis_gcp

app_aws --> nat_aws : "Outbound traffic"
app_azure --> nat_azure : "Outbound traffic"
app_gcp --> nat_gcp : "Outbound traffic"

@enduml
```

---

## 🔒 Security Architecture

### Defense-in-Depth Layers

```plantuml
@startuml Security_Architecture
!theme plain

skinparam rectangleBackgroundColor<<layer1>> lightblue
skinparam rectangleBackgroundColor<<layer2>> lightgreen
skinparam rectangleBackgroundColor<<layer3>> lightyellow
skinparam rectangleBackgroundColor<<layer4>> lightpink
skinparam rectangleBackgroundColor<<layer5>> lightgray

rectangle "Layer 1: Perimeter Security" <<layer1>> {
    component "WAF (ModSecurity)" as waf
    component "DDoS Protection" as ddos
    component "Rate Limiting" as rate_limit
    component "IP Allowlist/Blocklist" as ip_filter
}

rectangle "Layer 2: Network Security" <<layer2>> {
    component "Network Policies" as netpol
    component "Firewall Rules" as firewall
    component "VPN Gateway" as vpn
    component "Private Link" as privatelink
}

rectangle "Layer 3: Application Security" <<layer3>> {
    component "API Gateway (OAuth2)" as oauth
    component "JWT Validation" as jwt
    component "RBAC" as rbac
    component "mTLS" as mtls
}

rectangle "Layer 4: Data Security" <<layer4>> {
    component "Encryption at Rest" as encrypt_rest
    component "Encryption in Transit" as encrypt_transit
    component "Key Management (Vault)" as vault
    component "Data Masking" as masking
}

rectangle "Layer 5: Monitoring & Response" <<layer5>> {
    component "SIEM (Splunk)" as siem
    component "IDS/IPS (Snort)" as ids
    component "Audit Logging" as audit
    component "Threat Detection" as threat
}

waf --> netpol
ddos --> netpol
rate_limit --> oauth
netpol --> rbac
oauth --> encrypt_transit
rbac --> encrypt_rest
encrypt_rest --> siem
vault --> audit

@enduml
```

### Authentication & Authorization Flow

```plantuml
@startuml Auth_Flow
!theme plain

actor User
participant "Web App" as Web
participant "API Gateway" as Gateway
participant "Auth Service\n(OAuth2)" as Auth
participant "User DB" as UserDB
participant "Vault\n(Secrets)" as Vault
participant "Backend Service" as Backend

User -> Web: Login (email, password)
Web -> Gateway: POST /auth/login
Gateway -> Auth: authenticate(credentials)

Auth -> UserDB: SELECT * FROM users WHERE email=?
UserDB --> Auth: User record
Auth -> Auth: bcrypt.verify(password, passwordHash)
Auth -> Auth: Generate JWT token
note right
  JWT contains:
  - sub: userId
  - tenant: tenantId
  - roles: [admin, user]
  - exp: 1h
end note

Auth --> Gateway: {accessToken, refreshToken}
Gateway --> Web: Tokens
Web -> Web: Store in httpOnly cookie

== Authenticated Request ==

User -> Web: GET /api/workflows
Web -> Gateway: GET /api/workflows\nAuthorization: Bearer <token>
Gateway -> Gateway: Validate JWT signature
Gateway -> Gateway: Check token expiration
Gateway -> Gateway: Extract claims (userId, tenantId, roles)

Gateway -> Backend: Forward request + claims
Backend -> Backend: Check RBAC permissions
note right
  Rules:
  - admin: all operations
  - user: read own workflows
  - viewer: read only
end note

Backend -> Backend: Apply tenant isolation
Backend -> UserDB: Query tenant's data only
UserDB --> Backend: Workflows
Backend --> Gateway: Response
Gateway --> Web: Workflows
Web --> User: Display workflows

@enduml
```

---

## 💾 Data Flow Diagrams

### Workflow Execution Data Flow

```plantuml
@startuml Workflow_Execution_DataFlow
!theme plain

actor User
participant "Web App" as Web
participant "Workflow Engine" as Engine
database "PostgreSQL\n(Workflow State)" as PG
database "Redis\n(Cache)" as Redis
database "InfluxDB\n(Metrics)" as Influx
database "Elasticsearch\n(Logs)" as ES
participant "Event Bus\n(Kafka)" as Kafka
participant "Analytics Service" as Analytics
participant "Notification Service" as Notify

User -> Web: Launch workflow
Web -> Engine: POST /api/cases\n{specId, caseData}

Engine -> Redis: CHECK cache:spec:{specId}
Redis --> Engine: null (cache miss)

Engine -> PG: SELECT * FROM specifications WHERE id=?
PG --> Engine: YSpecification
Engine -> Redis: SET cache:spec:{specId} (TTL 1h)

Engine -> PG: BEGIN TRANSACTION
Engine -> PG: INSERT INTO cases (id, specId, data, status)
Engine -> PG: INSERT INTO work_items (id, caseId, taskId, status)
Engine -> PG: COMMIT TRANSACTION
PG --> Engine: caseId

Engine -> Kafka: PUBLISH workflow.started\n{caseId, specId, tenantId}
Engine -> Influx: WRITE workflow_started\n{caseId, timestamp, tenantId}
Engine -> ES: INDEX log\n{level: INFO, message: "Workflow started"}

Engine --> Web: {caseId, status: "running"}
Web --> User: Workflow running

== Background Processing ==

Kafka -> Analytics: CONSUME workflow.started
Analytics -> PG: Query historical data
Analytics -> Influx: WRITE prediction\n{caseId, estimatedDuration}

Kafka -> Notify: CONSUME workflow.started
Notify -> User: 📧 Email: "Workflow started"

== Work Item Completion ==

User -> Web: Complete task
Web -> Engine: POST /api/work-items/{id}/complete\n{outputData}

Engine -> PG: BEGIN TRANSACTION
Engine -> PG: UPDATE work_items SET status='completed', data=?
Engine -> PG: INSERT INTO work_items (next task)
Engine -> PG: COMMIT TRANSACTION

Engine -> Redis: DEL cache:workitem:{id}
Engine -> Kafka: PUBLISH workitem.completed
Engine -> Influx: WRITE workitem_duration\n{taskId, duration}
Engine -> ES: INDEX log

Engine --> Web: {status: "completed"}

@enduml
```

### AI Workflow Generation Data Flow

```plantuml
@startuml AI_Generation_DataFlow
!theme plain

actor User
participant "Web App" as Web
participant "AI Orchestrator" as AI
database "Neo4j\n(Knowledge Graph)" as Graph
database "Redis\n(Cache)" as Redis
participant "Z.AI API" as ZAI
participant "Workflow Engine" as Engine
database "PostgreSQL" as PG
participant "Event Bus" as Kafka

User -> Web: "Create invoice approval workflow"
Web -> AI: POST /api/ai/generate\n{intent: "invoice approval workflow"}

AI -> Redis: CHECK cache:generation:{hash(intent)}
Redis --> AI: null (cache miss)

AI -> Graph: MATCH (w:Workflow)\nWHERE w.description CONTAINS 'invoice'
note right
  Cypher query finds similar
  workflows from knowledge graph
end note
Graph --> AI: Similar workflows [w1, w2, w3]

AI -> Graph: MATCH (w1)-[:USES]->(p:Pattern)
Graph --> AI: Common patterns [approval, notification, escalation]

AI -> AI: Build prompt:\n- User intent\n- Similar workflows\n- Best practices\n- YAWL schema
AI -> ZAI: POST /v4/chat/completions\n{prompt, model: "glm-4.7-flash"}
note right
  Prompt engineering:
  - System: "You are a YAWL expert"
  - Context: Similar workflows
  - Task: Generate YAWL XML
  - Constraints: Valid schema
end note

ZAI --> AI: YAWL XML
AI -> AI: Validate against YAWL Schema 4.0
AI -> AI: Parse XML → YSpecification object

AI -> Engine: POST /api/specifications/validate
Engine -> Engine: YSpecification.validate()
Engine --> AI: Validation result: OK

AI -> PG: INSERT INTO specifications (id, xml, generatedBy='AI')
PG --> AI: specId

AI -> Graph: CREATE (w:Workflow {id, description, generatedBy='AI'})
AI -> Graph: CREATE (w)-[:SIMILAR_TO]->(w1)
AI -> Graph: CREATE (w)-[:USES]->(p:Pattern {type: 'approval'})
Graph --> AI: Nodes created

AI -> Redis: SET cache:generation:{hash(intent)} = specId (TTL 24h)
AI -> Kafka: PUBLISH ai.workflow.generated\n{specId, intent, userId}

AI --> Web: {specId, preview, estimatedAccuracy: 0.95}
Web --> User: "Workflow created! Review & deploy"

== User Reviews & Deploys ==

User -> Web: "Deploy workflow"
Web -> Engine: POST /api/cases/launch\n{specId, caseData}
Engine -> PG: INSERT INTO cases
Engine --> Web: caseId

Web -> AI: POST /api/ai/feedback\n{specId, action: 'deployed'}
note right
  Feedback loop:
  Deployment = positive signal
  Edit/reject = negative signal
end note

AI -> Graph: MATCH (w:Workflow {id: specId})\nSET w.deployedCount += 1
AI -> Graph: MATCH (w)-[:USES]->(p:Pattern)\nSET p.successRate += 1
Graph --> AI: Updated

@enduml
```

### Marketplace Transaction Data Flow

```plantuml
@startuml Marketplace_DataFlow
!theme plain

actor Buyer
actor Creator
participant "Web App" as Web
participant "Marketplace Service" as Market
database "MongoDB\n(Templates)" as Mongo
participant "Stripe" as Stripe
database "PostgreSQL\n(Purchases)" as PG
participant "Notification Service" as Notify
database "Redis\n(Cache)" as Redis

Buyer -> Web: Browse templates (category: "finance")
Web -> Market: GET /api/templates?category=finance

Market -> Redis: CHECK cache:templates:finance
Redis --> Market: [template list] (cache hit)
Market --> Web: Templates

Web --> Buyer: Show 20 finance templates

Buyer -> Web: Click template details
Web -> Market: GET /api/templates/{id}

Market -> Redis: CHECK cache:template:{id}
Redis --> Market: null (cache miss)

Market -> Mongo: db.templates.findOne({_id: id})
Mongo --> Market: Template document
Market -> Redis: SET cache:template:{id} (TTL 1h)
Market --> Web: Template details
Web --> Buyer: Show preview, reviews, stats

Buyer -> Web: Click "Purchase" ($99.99)
Web -> Market: POST /api/purchases\n{templateId, buyerId}

Market -> PG: SELECT * FROM purchases WHERE buyerId=? AND templateId=?
PG --> Market: null (not purchased yet)

Market -> Mongo: db.templates.findOne({_id: templateId})
Mongo --> Market: {price: 9999} (cents)

Market -> Stripe: stripe.checkout.sessions.create()\n{amount: 9999, metadata: {templateId, buyerId}}
Stripe --> Market: {id: "cs_xxx", url: "https://checkout.stripe.com/..."}

Market --> Web: {checkoutUrl}
Web --> Buyer: Redirect to Stripe

Buyer -> Stripe: Enter payment info (card number, etc.)
Stripe -> Stripe: Process payment
Stripe -> Stripe: Charge $99.99

Stripe -> Market: Webhook: checkout.session.completed\n{paymentIntentId, metadata}
Market -> Market: Verify webhook signature

Market -> PG: BEGIN TRANSACTION
Market -> PG: INSERT INTO purchases (id, templateId, buyerId, price, stripeId)
Market -> Mongo: db.templates.updateOne(\n{_id: templateId},\n{$inc: {downloads: 1}})
Market -> PG: COMMIT TRANSACTION

Market -> Stripe: Calculate payout:\n$99.99 × 0.70 = $69.99 (creator)\n$99.99 × 0.30 = $30.00 (platform)

Market -> Mongo: db.creators.findOne({templateId: templateId})
Mongo --> Market: {stripeAccountId: "acct_creator"}

Market -> Stripe: stripe.transfers.create()\n{amount: 6999, destination: "acct_creator"}
Stripe -> Creator: Transfer $69.99 to bank account
Stripe --> Market: Transfer successful

Market -> Redis: DEL cache:template:{templateId}
note right: Invalidate cache (download count changed)

Market -> Notify: sendEmail(buyer, "purchase_confirmation")
Market -> Notify: sendEmail(creator, "sale_notification")

Notify -> Buyer: 📧 "Thank you for your purchase!"
Notify -> Creator: 📧 "You earned $69.99!"

Market --> Web: Purchase complete
Web --> Buyer: "Download your template"

Buyer -> Web: Click "Download"
Web -> Market: GET /api/templates/{id}/download

Market -> PG: SELECT * FROM purchases WHERE buyerId=? AND templateId=?
PG --> Market: Purchase record ✓

Market -> Mongo: db.templates.findOne({_id: templateId})
Mongo --> Market: {yawlSpec: "<xml>...</xml>"}

Market --> Web: {yawlSpec}
Web --> Buyer: Download YAWL file

@enduml
```

---

## 📊 Observability Architecture

### Monitoring Stack

```plantuml
@startuml Observability_Architecture
!theme plain

rectangle "Application Layer" {
    component "Web App" as web
    component "Workflow Engine" as engine
    component "AI Orchestrator" as ai
    component "Marketplace" as market
}

rectangle "Metrics Collection" {
    component "Prometheus" as prom
    component "Prometheus Exporters" as exporters
    database "Time-Series DB" as tsdb
}

rectangle "Logging Pipeline" {
    component "Fluent Bit" as fluentbit
    component "Loki" as loki
    database "Log Storage" as logs
}

rectangle "Tracing" {
    component "OpenTelemetry Collector" as otel
    component "Jaeger" as jaeger
    database "Trace Storage" as traces
}

rectangle "Visualization & Alerting" {
    component "Grafana" as grafana
    component "Alertmanager" as alertmgr
    component "PagerDuty" as pagerduty
}

web --> exporters : "/metrics"
engine --> exporters : "/metrics"
ai --> exporters : "/metrics"
market --> exporters : "/metrics"

exporters --> prom : "scrape every 15s"
prom --> tsdb : "store"

web --> fluentbit : "stdout/stderr"
engine --> fluentbit : "stdout/stderr"
ai --> fluentbit : "stdout/stderr"
fluentbit --> loki : "ship logs"
loki --> logs : "store"

web --> otel : "OTLP"
engine --> otel : "OTLP"
ai --> otel : "OTLP"
otel --> jaeger : "forward traces"
jaeger --> traces : "store"

prom --> grafana : "data source"
loki --> grafana : "data source"
jaeger --> grafana : "data source"

prom --> alertmgr : "fire alerts"
alertmgr --> pagerduty : "notify on-call"

@enduml
```

### Distributed Tracing Example

```plantuml
@startuml Distributed_Trace
!theme plain

participant "User" as user
participant "Web App\n[span: http-request]" as web
participant "API Gateway\n[span: api-route]" as gateway
participant "AI Orchestrator\n[span: generate-workflow]" as ai
participant "Z.AI API\n[span: ai-completion]" as zai
participant "Neo4j\n[span: graph-query]" as neo4j
participant "Workflow Engine\n[span: validate-spec]" as engine

note over user, engine
  Trace ID: 123e4567-e89b-12d3-a456-426614174000
  Root Span: http-request
end note

user -> web: POST /workflows/generate
activate web #lightblue
web -> web: span.start("http-request")
web -> web: span.setAttribute("user.id", userId)

web -> gateway: POST /api/ai/generate
activate gateway #lightgreen
gateway -> gateway: span.start("api-route", parent: "http-request")
gateway -> gateway: span.setAttribute("tenant.id", tenantId)

gateway -> ai: generateWorkflow(intent)
activate ai #lightyellow
ai -> ai: span.start("generate-workflow", parent: "api-route")
ai -> ai: span.setAttribute("intent", intent)

ai -> neo4j: MATCH (w:Workflow) WHERE ...
activate neo4j #lightpink
neo4j -> neo4j: span.start("graph-query", parent: "generate-workflow")
neo4j -> neo4j: Execute Cypher query (45ms)
neo4j -> neo4j: span.end(duration: 45ms)
deactivate neo4j

ai -> zai: POST /v4/chat/completions
activate zai #lightgray
zai -> zai: span.start("ai-completion", parent: "generate-workflow")
zai -> zai: AI inference (2,340ms)
zai -> zai: span.end(duration: 2340ms)
deactivate zai

ai -> engine: validateSpecification(spec)
activate engine #lightcyan
engine -> engine: span.start("validate-spec", parent: "generate-workflow")
engine -> engine: Schema validation (120ms)
engine -> engine: span.end(duration: 120ms)
deactivate engine

ai -> ai: span.end(duration: 2600ms)
deactivate ai

gateway -> gateway: span.end(duration: 2650ms)
deactivate gateway

web -> web: span.end(duration: 2700ms)
deactivate web

note over user, engine
  Total Duration: 2.7 seconds
  - Graph query: 45ms (1.6%)
  - AI completion: 2,340ms (86.7%)
  - Validation: 120ms (4.4%)
  - Overhead: 195ms (7.2%)

  Bottleneck: AI completion
  Optimization: Cache similar requests
end note

@enduml
```

---

## 🔄 CI/CD Pipeline Architecture

```plantuml
@startuml CICD_Pipeline
!theme plain

rectangle "Developer Workflow" {
    actor Developer
    component "Git Push" as git
    component "GitHub" as github
}

rectangle "CI Pipeline (GitHub Actions)" {
    component "Checkout Code" as checkout
    component "Build (Ant)" as build
    component "Unit Tests" as tests
    component "SAST Scan (SonarQube)" as sast
    component "Build Docker Images" as docker
    component "Vulnerability Scan (Trivy)" as trivy
    component "Push to Registry" as registry
}

rectangle "CD Pipeline (ArgoCD)" {
    component "ArgoCD" as argocd
    component "Helm Chart Render" as helm
    component "Manifest Validation" as validate
    component "Apply to Kubernetes" as k8s
}

rectangle "Environments" {
    cloud "Dev Cluster" as dev
    cloud "Staging Cluster" as staging
    cloud "Production Cluster" as prod
}

rectangle "Testing & Validation" {
    component "Integration Tests" as integration
    component "E2E Tests (Playwright)" as e2e
    component "Load Tests (k6)" as load
    component "Security Tests (OWASP ZAP)" as security
}

rectangle "Approval Gates" {
    actor "QA Team" as qa
    actor "Security Team" as sec
    actor "Product Owner" as po
}

Developer -> git : "git push"
git -> github : "trigger webhook"
github -> checkout : "pull code"

checkout -> build : "ant buildAll"
build -> tests : "ant unitTest"
tests -> sast : "sonarqube scan"
sast -> docker : "docker build"
docker -> trivy : "scan images"
trivy -> registry : "push to ECR/ACR/GCR"

registry -> argocd : "new image available"
argocd -> helm : "render manifests"
helm -> validate : "kubeval, kube-score"

validate -> dev : "auto-deploy"
dev -> integration : "run tests"
integration -> e2e : "playwright tests"

e2e -> qa : "approve staging?"
qa -> staging : "approved"
staging -> load : "k6 load test"
staging -> security : "OWASP ZAP scan"

load -> sec : "approve production?"
sec -> po : "business approval?"
po -> prod : "deploy to prod"

@enduml
```

---

## 🗺️ Service Mesh Architecture

```plantuml
@startuml Service_Mesh
!theme plain

rectangle "Kubernetes Cluster" {
    rectangle "Istio Control Plane" {
        component "Pilot" as pilot
        component "Citadel" as citadel
        component "Galley" as galley
    }

    rectangle "Namespace: yawl-prod" {
        component "AI Orchestrator Pod" {
            component "AI App" as ai_app
            component "Envoy Sidecar" as ai_envoy
        }

        component "Workflow Engine Pod" {
            component "Engine App" as engine_app
            component "Envoy Sidecar" as engine_envoy
        }

        component "Marketplace Pod" {
            component "Market App" as market_app
            component "Envoy Sidecar" as market_envoy
        }
    }

    component "Ingress Gateway" as ingress
}

pilot --> ai_envoy : "configure routes"
pilot --> engine_envoy : "configure routes"
pilot --> market_envoy : "configure routes"

citadel --> ai_envoy : "mTLS certificates"
citadel --> engine_envoy : "mTLS certificates"
citadel --> market_envoy : "mTLS certificates"

ingress --> ai_envoy : "mTLS"
ai_envoy --> ai_app : "http"

ai_envoy --> engine_envoy : "mTLS"
engine_envoy --> engine_app : "http"

ai_envoy --> market_envoy : "mTLS"
market_envoy --> market_app : "http"

note right of ai_envoy
  Envoy features:
  - Load balancing
  - Circuit breaking
  - Retry policies
  - Rate limiting
  - mTLS encryption
  - Distributed tracing
  - Metrics collection
end note

@enduml
```

---

## 💾 Backup & Disaster Recovery Architecture

```plantuml
@startuml Backup_DR_Architecture
!theme plain

rectangle "Primary Region (us-east-1)" {
    database "PostgreSQL Primary" as pg_primary
    database "MongoDB Primary" as mongo_primary
    database "Redis Primary" as redis_primary

    storage "EBS Snapshots" as ebs_snap
    storage "S3 Bucket (Backups)" as s3_backup
}

rectangle "Secondary Region (us-west-2)" {
    database "PostgreSQL Standby" as pg_standby
    database "MongoDB Secondary" as mongo_secondary
    database "Redis Replica" as redis_standby

    storage "S3 Bucket (Replica)" as s3_replica
}

rectangle "Backup Processes" {
    component "Continuous WAL Archiving" as wal
    component "Daily Full Backup" as daily
    component "Hourly Incremental" as hourly
    component "Point-in-Time Recovery" as pitr
}

pg_primary --> wal : "streaming replication"
wal --> pg_standby : "async replication"
wal --> s3_backup : "archive WAL logs"

pg_primary --> daily : "pg_dump (00:00 UTC)"
daily --> s3_backup : "upload compressed dump"

pg_primary --> hourly : "pg_basebackup"
hourly --> ebs_snap : "EBS snapshot"

s3_backup --> s3_replica : "cross-region replication"

mongo_primary --> mongo_secondary : "replica set"
mongo_primary --> s3_backup : "mongodump (daily)"

redis_primary --> redis_standby : "replication"
redis_primary --> s3_backup : "RDB snapshot (hourly)"

note right of pitr
  Recovery Objectives:
  - RPO: 5 minutes (WAL archiving)
  - RTO: 15 minutes (automated failover)

  Retention:
  - Daily: 30 days
  - Weekly: 12 weeks
  - Monthly: 12 months
  - Yearly: 7 years
end note

@enduml
```

---

## 📈 Auto-Scaling Architecture

```plantuml
@startuml Auto_Scaling
!theme plain

rectangle "Metrics Source" {
    component "Prometheus" as prom
    component "Metrics Server" as metrics
}

rectangle "Scaling Decision" {
    component "HPA Controller" as hpa
    component "Custom Metrics API" as custom_api
    component "KEDA" as keda
}

rectangle "Workloads" {
    component "Workflow Engine\n(min: 5, max: 50)" as engine
    component "AI Orchestrator\n(min: 3, max: 20)" as ai
    component "Marketplace\n(min: 2, max: 10)" as market
}

rectangle "Scaling Triggers" {
    component "CPU > 70%" as cpu
    component "Memory > 80%" as memory
    component "Queue Depth > 100" as queue
    component "Request Rate > 1000/s" as rate
}

prom --> custom_api : "query metrics"
metrics --> hpa : "resource metrics"
custom_api --> hpa : "custom metrics"

cpu --> hpa : "scale trigger"
memory --> hpa : "scale trigger"
queue --> keda : "scale trigger"
rate --> hpa : "scale trigger"

hpa --> engine : "scale replicas"
hpa --> ai : "scale replicas"
hpa --> market : "scale replicas"

keda --> engine : "scale to zero / scale up"

note right of engine
  Scaling Policies:
  - Scale up: 2 replicas at a time
  - Scale down: 1 replica every 5 min
  - Cooldown: 3 minutes
  - Metrics window: 30 seconds
end note

@enduml
```

---

## 🌍 Global Distribution Architecture

```plantuml
@startuml Global_Distribution
!theme plain

cloud "Global Users" as users

cloud "CloudFlare CDN" {
    component "Edge Locations (200+)" as edge
    component "DDoS Protection" as ddos
    component "WAF" as waf
}

cloud "GeoDNS (Route 53)" as geodns

cloud "North America Region" {
    component "US-East Cluster" as us_east
    component "US-West Cluster" as us_west
    database "RDS us-east-1" as db_us_east
    database "RDS us-west-2" as db_us_west
}

cloud "Europe Region" {
    component "EU-West Cluster" as eu_west
    component "EU-Central Cluster" as eu_central
    database "RDS eu-west-1" as db_eu_west
    database "RDS eu-central-1" as db_eu_central
}

cloud "Asia-Pacific Region" {
    component "AP-Southeast Cluster" as ap_se
    component "AP-Northeast Cluster" as ap_ne
    database "RDS ap-southeast-1" as db_ap_se
    database "RDS ap-northeast-1" as db_ap_ne
}

users --> edge : "HTTPS"
edge --> ddos : "filter attacks"
ddos --> waf : "filter malicious traffic"
waf --> geodns : "route to nearest region"

geodns --> us_east : "North America traffic"
geodns --> eu_west : "Europe traffic"
geodns --> ap_se : "Asia-Pacific traffic"

us_east --> db_us_east : "primary"
us_east --> db_us_west : "read replica"

eu_west --> db_eu_west : "primary"
eu_west --> db_eu_central : "read replica"

ap_se --> db_ap_se : "primary"
ap_se --> db_ap_ne : "read replica"

db_us_east --> db_eu_west : "cross-region replication"
db_us_east --> db_ap_se : "cross-region replication"

note right of geodns
  Routing Policy:
  - Latency-based routing
  - Health checks every 30s
  - Automatic failover
  - 99.99% uptime SLA

  Data Sovereignty:
  - EU data stays in EU
  - GDPR compliance
  - Regional data residency
end note

@enduml
```

---

## 📋 Summary

### Architecture Diagrams Created

1. **Network Architecture** - Multi-cloud network topology
2. **Security Architecture** - Defense-in-depth layers
3. **Authentication Flow** - OAuth2/JWT authentication
4. **Data Flow Diagrams** - Workflow execution, AI generation, marketplace
5. **Observability Stack** - Prometheus, Loki, Jaeger integration
6. **CI/CD Pipeline** - GitHub Actions → ArgoCD → Kubernetes
7. **Service Mesh** - Istio with mTLS
8. **Backup & DR** - Multi-region backup strategy
9. **Auto-Scaling** - HPA and KEDA configuration
10. **Global Distribution** - CloudFlare CDN + GeoDNS

### Key Architectural Principles

✅ **Security First** - Defense-in-depth, zero trust
✅ **High Availability** - Multi-region, auto-failover
✅ **Scalability** - Horizontal scaling, global distribution
✅ **Observability** - Metrics, logs, traces
✅ **Resilience** - Circuit breakers, retries, backups
✅ **Performance** - CDN, caching, read replicas
✅ **Compliance** - Data residency, encryption, audit logs

---

**All diagrams use PlantUML format for easy rendering and version control.**
