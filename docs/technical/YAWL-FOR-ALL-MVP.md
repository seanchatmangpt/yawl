# YAWL for All - MVP Implementation Plan

**Goal**: Launch a working Agent Coordination Network in **30 days** that demonstrates the core value proposition.

**Success Criteria**:
- ✅ 10 agents registered on the network
- ✅ 1 reference workflow (Travel Planning) end-to-end
- ✅ $10,000 in transaction volume processed
- ✅ Public demo site live
- ✅ Developer SDK published

---

## Week 1: Infrastructure Setup (Days 1-7)

### Day 1-2: Development Environment

**Tasks**:
```bash
# Clone YAWL 5.2 repository
git clone https://github.com/yawlfoundation/yawl.git yawl-for-all
cd yawl-for-all

# Create MVP branch
git checkout -b feature/mvp-agent-network

# Set up Docker Compose for local development
cat > docker-compose.mvp.yml <<'EOF'
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: yawl_platform
      POSTGRES_USER: yawl_admin
      POSTGRES_PASSWORD: dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
    ports:
      - "9092:9092"

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

volumes:
  postgres_data:
EOF

# Start infrastructure
docker-compose -f docker-compose.mvp.yml up -d
```

**Deliverables**:
- [ ] Local development environment running
- [ ] PostgreSQL, Redis, Kafka accessible
- [ ] Database schema initialized

### Day 3-4: Agent Registry Service

**Tasks**:

1. **Create Spring Boot project**:
```bash
mkdir -p mvp-services/agent-registry
cd mvp-services/agent-registry

# Use Spring Initializr
curl https://start.spring.io/starter.zip \
  -d dependencies=web,data-jpa,postgresql,redis,security,actuator \
  -d name=agent-registry \
  -d packageName=com.yawlforall.registry \
  -d javaVersion=21 \
  -o agent-registry.zip

unzip agent-registry.zip
```

2. **Implement core entity classes**:
```java
// src/main/java/com/yawlforall/registry/model/AgentProfile.java
@Entity
@Table(name = "agents")
public class AgentProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID agentId;

    private String name;
    private String ownerOrg;
    private String a2aEndpoint;

    @Column(name = "api_key_hash")
    private String apiKeyHash;

    @Column(name = "trust_score")
    private BigDecimal trustScore = BigDecimal.ZERO;

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL)
    private List<AgentCapability> capabilities = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Getters, setters, etc.
}

// src/main/java/com/yawlforall/registry/model/AgentCapability.java
@Entity
@Table(name = "agent_capabilities")
public class AgentCapability {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID capabilityId;

    @ManyToOne
    @JoinColumn(name = "agent_id")
    private AgentProfile agent;

    private String category; // e.g., "travel", "finance"
    private String action;   // e.g., "search_flights", "book_hotel"

    @Column(columnDefinition = "jsonb")
    private String inputSchema;

    @Column(columnDefinition = "jsonb")
    private String outputSchema;

    // Getters, setters, etc.
}
```

3. **Create REST API**:
```java
// src/main/java/com/yawlforall/registry/controller/AgentController.java
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    @Autowired
    private AgentService agentService;

    @PostMapping
    public ResponseEntity<AgentRegistrationResponse> registerAgent(
        @RequestBody AgentRegistrationRequest request
    ) {
        AgentProfile agent = agentService.registerAgent(request);
        String apiKey = agentService.generateApiKey(agent);

        return ResponseEntity.ok(new AgentRegistrationResponse(
            agent.getAgentId(),
            apiKey
        ));
    }

    @GetMapping
    public ResponseEntity<List<AgentProfile>> searchAgents(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String action
    ) {
        List<AgentProfile> agents = agentService.searchAgents(category, action);
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentProfile> getAgent(@PathVariable UUID agentId) {
        return agentService.getAgent(agentId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
```

**Deliverables**:
- [ ] Agent Registry Service running on port 8080
- [ ] REST API for agent registration
- [ ] Database tables created
- [ ] Basic API key authentication

### Day 5-6: Workflow Executor Service

**Tasks**:

1. **Modify YAWL Engine for MVP**:
```java
// src/main/java/com/yawlforall/executor/CloudYEngine.java
public class CloudYEngine extends YEngine {

    @Autowired
    private AgentRegistryClient agentRegistry;

    @Autowired
    private KafkaTemplate<String, AgentMessage> kafka;

    /**
     * Override to dispatch tasks to agents
     */
    @Override
    public void executeWorkItem(YWorkItem workItem) throws YPersistenceException {
        // Extract task type from work item
        String taskType = workItem.getTaskID();

        // Find agent capable of handling this task
        AgentProfile agent = agentRegistry.findAgentForTask(taskType);

        if (agent == null) {
            throw new NoAgentAvailableException("No agent for task: " + taskType);
        }

        // Create agent message
        AgentMessage message = AgentMessage.builder()
            .workItemId(workItem.getID())
            .agentId(agent.getAgentId())
            .taskType(taskType)
            .inputData(workItem.getDataString())
            .timestamp(Instant.now())
            .build();

        // Send to Kafka topic
        kafka.send("agent-tasks", agent.getAgentId().toString(), message);

        log.info("Dispatched task {} to agent {}", taskType, agent.getName());
    }

    /**
     * Handle agent response from Kafka
     */
    @KafkaListener(topics = "agent-responses", groupId = "workflow-executor")
    public void handleAgentResponse(AgentResponse response) {
        try {
            YWorkItem workItem = getWorkItem(response.getWorkItemId());

            if (response.isSuccess()) {
                completeWorkItem(workItem, response.getOutputData());
                log.info("Completed work item: {}", workItem.getID());
            } else {
                handleWorkItemFailure(workItem, response.getErrorMessage());
                log.error("Work item failed: {}", response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Error processing agent response", e);
        }
    }
}
```

2. **Create Kafka message schemas**:
```java
// AgentMessage.java
@Data
@Builder
public class AgentMessage {
    private String workItemId;
    private UUID agentId;
    private String taskType;
    private String inputData; // JSON
    private Instant timestamp;
    private Instant deadline;
}

// AgentResponse.java
@Data
@Builder
public class AgentResponse {
    private String workItemId;
    private UUID agentId;
    private boolean success;
    private String outputData; // JSON
    private String errorMessage;
    private Duration executionTime;
    private Instant timestamp;
}
```

**Deliverables**:
- [ ] Workflow Executor Service running
- [ ] Kafka integration working
- [ ] Can dispatch tasks to agents
- [ ] Can receive agent responses

### Day 7: Payment Integration (Stripe Test Mode)

**Tasks**:

1. **Set up Stripe Connect**:
```bash
# Create Stripe account (test mode)
# Get API keys from dashboard.stripe.com

# Add to application.properties
echo "stripe.api.key=sk_test_..." >> src/main/resources/application.properties
echo "stripe.webhook.secret=whsec_..." >> src/main/resources/application.properties
```

2. **Create Transaction Service**:
```java
@Service
public class TransactionService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Autowired
    private TransactionRepository transactionRepository;

    public Transaction createEscrow(
        String caseId,
        BigDecimal amount,
        UUID userId
    ) throws StripeException {

        Stripe.apiKey = stripeApiKey;

        // Create payment intent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amount.multiply(new BigDecimal("100")).longValue()) // Convert to cents
            .setCurrency("usd")
            .setDescription("Workflow execution: " + caseId)
            .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL) // Escrow
            .build();

        PaymentIntent intent = PaymentIntent.create(params);

        // Store transaction
        Transaction transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID());
        transaction.setCaseId(caseId);
        transaction.setUserId(userId);
        transaction.setTotalAmount(amount);
        transaction.setStripePaymentIntentId(intent.getId());
        transaction.setStatus(TransactionStatus.ESCROWED);
        transaction.setCreatedAt(Instant.now());

        return transactionRepository.save(transaction);
    }

    public void releaseEscrow(String caseId) throws StripeException {
        Transaction transaction = transactionRepository.findByCaseId(caseId)
            .orElseThrow(() -> new TransactionNotFoundException(caseId));

        Stripe.apiKey = stripeApiKey;

        // Capture payment
        PaymentIntent intent = PaymentIntent.retrieve(
            transaction.getStripePaymentIntentId()
        );
        intent.capture();

        // Update transaction
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(Instant.now());
        transactionRepository.save(transaction);
    }
}
```

**Deliverables**:
- [ ] Stripe Connect account created (test mode)
- [ ] Transaction Service implemented
- [ ] Can create escrow payments
- [ ] Can release payments on workflow completion

---

## Week 2: Reference Implementation (Days 8-14)

### Day 8-9: Build Travel Planning Workflow

**Tasks**:

1. **Create YAWL specification**:
```xml
<!-- specs/TravelPlanning.yawl -->
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="TravelPlanning">
    <version>0.1</version>
    <metaData>
      <title>Travel Planning Workflow</title>
      <description>End-to-end travel booking with multiple agents</description>
    </metaData>

    <decomposition id="TravelPlanningNet" isRootNet="true" xsi:type="NetFactsType">

      <!-- Input: Destination, dates, budget, preferences -->
      <inputParam>
        <name>destination</name>
        <type>string</type>
      </inputParam>
      <inputParam>
        <name>startDate</name>
        <type>date</type>
      </inputParam>
      <inputParam>
        <name>endDate</name>
        <type>date</type>
      </inputParam>
      <inputParam>
        <name>budget</name>
        <type>decimal</type>
      </inputParam>

      <!-- Task 1: Search Flights -->
      <task id="SearchFlights">
        <name>Search for flights</name>
        <decomposesTo id="SearchFlightsDecomp"/>
        <flowsInto>
          <nextElementRef id="SearchHotels"/>
        </flowsInto>
      </task>

      <!-- Task 2: Search Hotels -->
      <task id="SearchHotels">
        <name>Search for hotels</name>
        <decomposesTo id="SearchHotelsDecomp"/>
        <flowsInto>
          <nextElementRef id="UserApproval"/>
        </flowsInto>
      </task>

      <!-- Task 3: User Approval -->
      <task id="UserApproval">
        <name>User reviews and approves</name>
        <decomposesTo id="UserApprovalDecomp"/>
        <flowsInto>
          <nextElementRef id="BookFlights"/>
        </flowsInto>
      </task>

      <!-- Task 4: Book Flights -->
      <task id="BookFlights">
        <name>Book approved flights</name>
        <decomposesTo id="BookFlightsDecomp"/>
        <flowsInto>
          <nextElementRef id="BookHotels"/>
        </flowsInto>
      </task>

      <!-- Task 5: Book Hotels -->
      <task id="BookHotels">
        <name>Book approved hotels</name>
        <decomposesTo id="BookHotelsDecomp"/>
        <flowsInto>
          <nextElementRef id="OutputCondition"/>
        </flowsInto>
      </task>

      <outputCondition id="OutputCondition"/>

    </decomposition>
  </specification>
</specificationSet>
```

2. **Upload specification to YAWL Engine**:
```bash
# Use YAWL Engine's Interface A to upload spec
curl -X POST http://localhost:8080/yawl/ia \
  -F action=upload \
  -F specXML=@specs/TravelPlanning.yawl \
  -F username=admin \
  -F password=YAWL
```

**Deliverables**:
- [ ] TravelPlanning.yawl specification created
- [ ] Uploaded to YAWL Engine
- [ ] Verified via YAWL Control Panel

### Day 10-11: Build Mock Agents

**Tasks**:

1. **Flight Search Agent** (Python):
```python
# agents/flight_search_agent.py
import json
import requests
from kafka import KafkaConsumer, KafkaProducer
from datetime import datetime, timedelta
import uuid

class FlightSearchAgent:
    def __init__(self, agent_id, api_key):
        self.agent_id = agent_id
        self.api_key = api_key

        # Register with Agent Registry
        self.register()

        # Connect to Kafka
        self.consumer = KafkaConsumer(
            'agent-tasks',
            bootstrap_servers=['localhost:9092'],
            value_deserializer=lambda m: json.loads(m.decode('utf-8'))
        )

        self.producer = KafkaProducer(
            bootstrap_servers=['localhost:9092'],
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )

    def register(self):
        """Register agent with Agent Registry"""
        response = requests.post('http://localhost:8080/api/v1/agents', json={
            'name': 'Flight Search Agent',
            'ownerOrg': 'YAWL for All MVP',
            'a2aEndpoint': 'http://localhost:5001',
            'capabilities': [
                {
                    'category': 'travel',
                    'action': 'search_flights',
                    'inputSchema': {
                        'type': 'object',
                        'properties': {
                            'origin': {'type': 'string'},
                            'destination': {'type': 'string'},
                            'departDate': {'type': 'string'},
                            'returnDate': {'type': 'string'},
                            'passengers': {'type': 'integer'}
                        }
                    },
                    'outputSchema': {
                        'type': 'object',
                        'properties': {
                            'flights': {'type': 'array'}
                        }
                    }
                }
            ]
        }, headers={'X-Agent-API-Key': self.api_key})

        if response.status_code == 200:
            print(f"Agent registered: {response.json()['agentId']}")
        else:
            print(f"Registration failed: {response.text}")

    def listen(self):
        """Listen for tasks from workflow executor"""
        print("Flight Search Agent listening for tasks...")

        for message in self.consumer:
            task = message.value

            if task['agentId'] == self.agent_id and task['taskType'] == 'SearchFlights':
                print(f"Received task: {task['workItemId']}")
                self.handle_task(task)

    def handle_task(self, task):
        """Handle flight search task"""
        try:
            input_data = json.loads(task['inputData'])

            # Simulate flight search (in real version, call Amadeus API)
            flights = [
                {
                    'airline': 'United Airlines',
                    'flightNumber': 'UA123',
                    'price': 450.00,
                    'departTime': '08:00 AM',
                    'arriveTime': '11:30 AM'
                },
                {
                    'airline': 'Delta',
                    'flightNumber': 'DL456',
                    'price': 420.00,
                    'departTime': '10:15 AM',
                    'arriveTime': '01:45 PM'
                }
            ]

            # Send response
            response = {
                'workItemId': task['workItemId'],
                'agentId': self.agent_id,
                'success': True,
                'outputData': json.dumps({'flights': flights}),
                'executionTime': 1.2,  # seconds
                'timestamp': datetime.now().isoformat()
            }

            self.producer.send('agent-responses', value=response)
            print(f"Task completed: {task['workItemId']}")

        except Exception as e:
            # Send error response
            response = {
                'workItemId': task['workItemId'],
                'agentId': self.agent_id,
                'success': False,
                'errorMessage': str(e),
                'timestamp': datetime.now().isoformat()
            }
            self.producer.send('agent-responses', value=response)

if __name__ == '__main__':
    agent = FlightSearchAgent(
        agent_id=str(uuid.uuid4()),
        api_key='mvp_test_key_1'
    )
    agent.listen()
```

2. **Hotel Search Agent** (similar structure)
3. **Booking Agents** (Flight & Hotel)

**Deliverables**:
- [ ] 4 mock agents implemented
- [ ] Agents register with Agent Registry
- [ ] Agents listen to Kafka for tasks
- [ ] Agents respond with mock data

### Day 12-13: End-to-End Test

**Tasks**:

1. **Create test script**:
```bash
#!/bin/bash
# tests/test_travel_workflow.sh

set -e

echo "=== YAWL for All MVP Test ==="

# Step 1: Launch workflow
echo "Launching travel planning workflow..."
CASE_ID=$(curl -s -X POST http://localhost:8080/yawl/ib \
  -d action=launchCase \
  -d specID=TravelPlanning \
  -d specVersion=0.1 \
  -d specURI=TravelPlanning \
  -d caseParams='<data><destination>Tokyo</destination><startDate>2026-06-01</startDate><endDate>2026-06-10</endDate><budget>5000</budget></data>' \
  -d sessionHandle=$SESSION \
  | grep -oP '(?<=<caseID>)[^<]+')

echo "Case ID: $CASE_ID"

# Step 2: Create escrow
echo "Creating escrow payment..."
curl -s -X POST http://localhost:8081/api/v1/transactions \
  -H 'Content-Type: application/json' \
  -d "{\"caseId\":\"$CASE_ID\",\"amount\":5000,\"userId\":\"$USER_ID\"}"

# Step 3: Monitor workflow progress
echo "Monitoring workflow..."
while true; do
  STATUS=$(curl -s http://localhost:8080/yawl/ib \
    -d action=getCaseData \
    -d caseID=$CASE_ID \
    -d sessionHandle=$SESSION \
    | grep -oP '(?<=<status>)[^<]+')

  echo "Status: $STATUS"

  if [ "$STATUS" == "Complete" ]; then
    echo "Workflow completed successfully!"
    break
  elif [ "$STATUS" == "Failed" ]; then
    echo "Workflow failed!"
    exit 1
  fi

  sleep 5
done

# Step 4: Release escrow
echo "Releasing escrow payment..."
curl -s -X POST http://localhost:8081/api/v1/transactions/$CASE_ID/release

echo "=== Test Complete! ==="
```

2. **Run test**:
```bash
chmod +x tests/test_travel_workflow.sh
./tests/test_travel_workflow.sh
```

**Deliverables**:
- [ ] End-to-end workflow executes successfully
- [ ] All 4 agents respond correctly
- [ ] Escrow created and released
- [ ] Transaction recorded in database

### Day 14: Demo UI

**Tasks**:

1. **Create simple React app**:
```bash
npx create-react-app yawl-demo-ui
cd yawl-demo-ui

# Install dependencies
npm install axios react-router-dom
```

2. **Build workflow launcher**:
```jsx
// src/components/TravelPlanner.jsx
import React, { useState } from 'react';
import axios from 'axios';

function TravelPlanner() {
  const [destination, setDestination] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [budget, setBudget] = useState('');
  const [caseId, setCaseId] = useState(null);
  const [status, setStatus] = useState('');

  const launchWorkflow = async () => {
    try {
      const response = await axios.post('http://localhost:8080/api/v1/workflows/launch', {
        specId: 'TravelPlanning',
        params: { destination, startDate, endDate, budget }
      });

      setCaseId(response.data.caseId);
      setStatus('Workflow launched! Agents are working...');

      // Poll for status
      const interval = setInterval(async () => {
        const statusRes = await axios.get(`http://localhost:8080/api/v1/workflows/${response.data.caseId}/status`);

        if (statusRes.data.status === 'Complete') {
          setStatus('Trip booked! Check your email for confirmation.');
          clearInterval(interval);
        } else if (statusRes.data.status === 'Failed') {
          setStatus('Booking failed. Please try again.');
          clearInterval(interval);
        } else {
          setStatus(`Status: ${statusRes.data.currentTask}`);
        }
      }, 3000);

    } catch (error) {
      setStatus('Error: ' + error.message);
    }
  };

  return (
    <div style={{ padding: '50px', maxWidth: '600px', margin: '0 auto' }}>
      <h1>YAWL for All - Travel Planner Demo</h1>

      <div style={{ marginBottom: '20px' }}>
        <label>Destination:</label>
        <input
          type="text"
          value={destination}
          onChange={(e) => setDestination(e.target.value)}
          style={{ width: '100%', padding: '10px', marginTop: '5px' }}
        />
      </div>

      <div style={{ marginBottom: '20px' }}>
        <label>Start Date:</label>
        <input
          type="date"
          value={startDate}
          onChange={(e) => setStartDate(e.target.value)}
          style={{ width: '100%', padding: '10px', marginTop: '5px' }}
        />
      </div>

      <div style={{ marginBottom: '20px' }}>
        <label>End Date:</label>
        <input
          type="date"
          value={endDate}
          onChange={(e) => setEndDate(e.target.value)}
          style={{ width: '100%', padding: '10px', marginTop: '5px' }}
        />
      </div>

      <div style={{ marginBottom: '20px' }}>
        <label>Budget ($):</label>
        <input
          type="number"
          value={budget}
          onChange={(e) => setBudget(e.target.value)}
          style={{ width: '100%', padding: '10px', marginTop: '5px' }}
        />
      </div>

      <button
        onClick={launchWorkflow}
        style={{
          padding: '15px 30px',
          backgroundColor: '#4CAF50',
          color: 'white',
          border: 'none',
          cursor: 'pointer',
          fontSize: '16px'
        }}
      >
        Plan My Trip
      </button>

      {status && (
        <div style={{
          marginTop: '30px',
          padding: '20px',
          backgroundColor: '#f0f0f0',
          borderRadius: '5px'
        }}>
          <h3>Status:</h3>
          <p>{status}</p>
        </div>
      )}
    </div>
  );
}

export default TravelPlanner;
```

**Deliverables**:
- [ ] Demo UI deployed to Netlify/Vercel
- [ ] Can launch workflows from browser
- [ ] Shows real-time status updates
- [ ] Public URL for demos

---

## Week 3: Developer Experience (Days 15-21)

### Day 15-16: Agent SDK (Python)

**Tasks**:

1. **Create Python SDK package**:
```bash
mkdir yawl-agent-sdk-python
cd yawl-agent-sdk-python

# Create package structure
mkdir -p yawl_agent_sdk
touch yawl_agent_sdk/__init__.py
touch yawl_agent_sdk/agent.py
touch yawl_agent_sdk/registry.py
touch yawl_agent_sdk/kafka_handler.py
touch setup.py
touch README.md
```

2. **Implement SDK**:
```python
# yawl_agent_sdk/agent.py
from typing import Dict, Callable, List
from kafka import KafkaConsumer, KafkaProducer
import json
import requests
from datetime import datetime

class YawlAgent:
    """
    YAWL Agent SDK - Build AI agents for the YAWL for All network

    Example usage:

        agent = YawlAgent(
            name="My Flight Agent",
            capabilities=[
                Capability(category="travel", action="search_flights")
            ]
        )

        @agent.task("search_flights")
        def search_flights(input_data):
            # Your logic here
            return {"flights": [...]}

        agent.run()
    """

    def __init__(
        self,
        name: str,
        owner_org: str,
        capabilities: List['Capability'],
        registry_url: str = "http://localhost:8080/api/v1/agents",
        kafka_brokers: List[str] = ["localhost:9092"]
    ):
        self.name = name
        self.owner_org = owner_org
        self.capabilities = capabilities
        self.registry_url = registry_url
        self.kafka_brokers = kafka_brokers

        self.task_handlers = {}
        self.agent_id = None
        self.api_key = None

    def task(self, task_type: str):
        """Decorator to register task handler"""
        def decorator(func: Callable):
            self.task_handlers[task_type] = func
            return func
        return decorator

    def register(self):
        """Register agent with YAWL platform"""
        response = requests.post(self.registry_url, json={
            'name': self.name,
            'ownerOrg': self.owner_org,
            'capabilities': [cap.to_dict() for cap in self.capabilities]
        })

        if response.status_code == 200:
            data = response.json()
            self.agent_id = data['agentId']
            self.api_key = data['apiKey']
            print(f"✅ Agent registered: {self.agent_id}")
        else:
            raise Exception(f"Registration failed: {response.text}")

    def run(self):
        """Start listening for tasks"""
        if not self.agent_id:
            self.register()

        consumer = KafkaConsumer(
            'agent-tasks',
            bootstrap_servers=self.kafka_brokers,
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            group_id=self.agent_id
        )

        producer = KafkaProducer(
            bootstrap_servers=self.kafka_brokers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )

        print(f"🚀 Agent {self.name} listening for tasks...")

        for message in consumer:
            task = message.value

            if task['agentId'] == self.agent_id:
                self._handle_task(task, producer)

    def _handle_task(self, task: Dict, producer: KafkaProducer):
        """Handle incoming task"""
        task_type = task['taskType']

        if task_type not in self.task_handlers:
            self._send_error(task, producer, f"No handler for task type: {task_type}")
            return

        try:
            input_data = json.loads(task['inputData'])

            # Call user-defined handler
            output = self.task_handlers[task_type](input_data)

            # Send success response
            response = {
                'workItemId': task['workItemId'],
                'agentId': self.agent_id,
                'success': True,
                'outputData': json.dumps(output),
                'timestamp': datetime.now().isoformat()
            }

            producer.send('agent-responses', value=response)
            print(f"✅ Task completed: {task['workItemId']}")

        except Exception as e:
            self._send_error(task, producer, str(e))

    def _send_error(self, task: Dict, producer: KafkaProducer, error: str):
        """Send error response"""
        response = {
            'workItemId': task['workItemId'],
            'agentId': self.agent_id,
            'success': False,
            'errorMessage': error,
            'timestamp': datetime.now().isoformat()
        }

        producer.send('agent-responses', value=response)
        print(f"❌ Task failed: {error}")

class Capability:
    def __init__(self, category: str, action: str, input_schema: Dict = None, output_schema: Dict = None):
        self.category = category
        self.action = action
        self.input_schema = input_schema or {}
        self.output_schema = output_schema or {}

    def to_dict(self):
        return {
            'category': self.category,
            'action': self.action,
            'inputSchema': self.input_schema,
            'outputSchema': self.output_schema
        }
```

3. **Create example agent**:
```python
# examples/simple_agent.py
from yawl_agent_sdk import YawlAgent, Capability

# Create agent
agent = YawlAgent(
    name="Simple Math Agent",
    owner_org="YAWL Demo",
    capabilities=[
        Capability(category="math", action="add"),
        Capability(category="math", action="multiply")
    ]
)

@agent.task("add")
def add_numbers(input_data):
    """Add two numbers"""
    a = input_data['a']
    b = input_data['b']
    return {"result": a + b}

@agent.task("multiply")
def multiply_numbers(input_data):
    """Multiply two numbers"""
    a = input_data['a']
    b = input_data['b']
    return {"result": a * b}

if __name__ == '__main__':
    agent.run()
```

4. **Publish to PyPI (test)**:
```bash
python setup.py sdist bdist_wheel
twine upload --repository testpypi dist/*
```

**Deliverables**:
- [ ] Python SDK published to PyPI (test)
- [ ] Documentation with examples
- [ ] Can build agent in < 15 minutes

### Day 17-18: Documentation

**Tasks**:

1. **Create docs site (Docusaurus)**:
```bash
npx create-docusaurus@latest yawl-docs classic
cd yawl-docs

# Add docs
mkdir -p docs/getting-started
mkdir -p docs/guides
mkdir -p docs/api-reference
```

2. **Write key docs**:
- Getting Started: "Build Your First Agent in 15 Minutes"
- API Reference: REST API documentation
- Workflow Guide: How to create YAWL workflows
- Example Gallery: 10 example agents + workflows

3. **Deploy to docs.yawlforall.com**:
```bash
npm run build
# Deploy to Vercel/Netlify
```

**Deliverables**:
- [ ] Documentation site live
- [ ] Getting started guide
- [ ] API reference
- [ ] Example gallery

### Day 19-20: Marketing Website

**Tasks**:

1. **Create landing page** (Next.js):
```bash
npx create-next-app@latest yawl-website
cd yawl-website
```

2. **Key pages**:
- Home: Value proposition, demo video
- How It Works: Architecture diagram
- For Developers: Agent SDK, docs link
- Pricing: Tiered pricing (free tier, pay-as-you-grow)
- About: Team, vision, contact

3. **Deploy to yawlforall.com**:
```bash
npm run build
vercel --prod
```

**Deliverables**:
- [ ] Marketing site live at yawlforall.com
- [ ] Demo video embedded
- [ ] CTA for developer signup

### Day 21: Public Beta Launch

**Tasks**:

1. **Prepare launch materials**:
- Blog post: "Introducing YAWL for All"
- Twitter thread (10 tweets)
- Hacker News post
- Product Hunt submission

2. **Set up monitoring**:
```bash
# Add Sentry for error tracking
npm install @sentry/node

# Add Mixpanel for analytics
npm install mixpanel

# Set up Grafana dashboards
```

3. **Launch checklist**:
- [ ] All services running in production
- [ ] Monitoring dashboards live
- [ ] Documentation complete
- [ ] Demo video recorded
- [ ] Launch materials ready

**Deliverables**:
- [ ] Public beta launched
- [ ] Press materials distributed
- [ ] First external developers signing up

---

## Week 4: Scale & Refine (Days 22-30)

### Day 22-24: Performance Optimization

**Tasks**:
- Database query optimization
- Redis caching implementation
- API rate limiting
- Load testing (100 concurrent workflows)

### Day 25-27: Security Hardening

**Tasks**:
- API key rotation mechanism
- Rate limiting per agent
- Input validation & sanitization
- Penetration testing

### Day 28-29: Community Building

**Tasks**:
- Launch Discord server
- Weekly office hours schedule
- First community call
- Agent showcase gallery

### Day 30: Metrics & Retrospective

**Tasks**:

1. **Measure success metrics**:
```
Target vs. Actual:
├─ Agents registered: 10 (target) vs. __ (actual)
├─ Workflows executed: 100 (target) vs. __ (actual)
├─ Transaction volume: $10K (target) vs. $__ (actual)
├─ Developer signups: 50 (target) vs. __ (actual)
└─ Demo site visitors: 1,000 (target) vs. __ (actual)
```

2. **Retrospective**:
- What went well?
- What didn't?
- Key learnings?
- Next 30-day priorities?

**Deliverables**:
- [ ] Metrics dashboard
- [ ] Retrospective doc
- [ ] Next sprint planned

---

## Success Criteria

At the end of 30 days, you should have:

✅ **Working Platform**:
- Agent Registry with 10+ registered agents
- Workflow Executor processing real workflows
- Transaction Service handling payments
- Demo UI showing end-to-end flow

✅ **Developer Tools**:
- Python Agent SDK published
- Documentation site live
- Example agents & workflows

✅ **Public Presence**:
- Marketing website (yawlforall.com)
- Social media presence
- Hacker News launch

✅ **Traction**:
- $10K in transaction volume
- 50+ developer signups
- 1,000+ demo site visitors

---

## Post-MVP: Next 90 Days

After MVP success, focus on:

1. **Months 2-3: Scale to 100 agents**
   - Add 10 new workflow templates
   - JavaScript/TypeScript SDK
   - Enterprise partnerships

2. **Month 4: Fundraising**
   - Prepare Series A deck
   - Metrics: 500 agents, $50K MRR
   - Target: $10-20M raise

3. **Month 5-6: Product Market Fit**
   - Hit 1,000 agents
   - $100K MRR
   - Network effects visible

---

**Let's build the Internet for AI agents!**

Next: See `docs/technical/API-REFERENCE.md` for complete API documentation.
