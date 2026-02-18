# How to Set Up the Organisational Model

## Prerequisites

- Resource service deployed and connected to the engine (see
  `docs/how-to/configure-resource-service.md`)
- Admin credentials for the resource service (default: `admin / YAWL`)
- An active session handle obtained from the resource service gateway

## Steps

### 1. Obtain an Admin Session Handle

All resource gateway calls require a valid session handle. Authenticate first:

```bash
SESSION=$(curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=connect" \
  -d "userid=admin" \
  -d "password=YAWL" | grep -oP '(?<=<response>)[^<]+')
echo "Session: $SESSION"
```

Store `$SESSION` for use in subsequent calls. Sessions expire after 60 minutes of
inactivity; re-authenticate if you receive an `invalid session` response.

### 2. Create Roles

Roles define job functions. A task's `<offer>` block references one or more roles to
select which participants receive each work item:

```bash
# Create a role named "CreditAnalyst"
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addRole" \
  -d "roleName=CreditAnalyst" \
  -d "description=Reviews and approves credit applications" \
  -d "sessionHandle=$SESSION"
# Returns: <response>RoleID_<uuid></response>
CREDIT_ANALYST_ROLE_ID="<uuid returned above>"

# Create a subordinate role that "belongs to" another (hierarchy)
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addRole" \
  -d "roleName=SeniorCreditAnalyst" \
  -d "description=Senior analyst, can override decisions" \
  -d "belongsToID=$CREDIT_ANALYST_ROLE_ID" \
  -d "sessionHandle=$SESSION"
```

A role hierarchy lets routing constraints use `belongsTo` filters — for example,
"offer to any participant whose role belongs to the CreditAnalyst family."

### 3. Create Capabilities

Capabilities describe specific skills or certifications. They can be attached to
participants and used as constraints in the `<filters>` block of a task's resourcing spec:

```bash
# Create a capability
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addCapability" \
  -d "capabilityName=RegulatoryReview" \
  -d "description=Authorised to perform regulatory compliance checks" \
  -d "sessionHandle=$SESSION"
REGULATORY_CAP_ID="<uuid returned above>"
```

### 4. Create Positions

Positions represent organisational units (job titles, departments). They are used
alongside roles to scope routing to a particular organisational context:

```bash
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addPosition" \
  -d "positionName=LoanOfficer" \
  -d "positionID=LO-001" \
  -d "description=Responsible for originating loan applications" \
  -d "sessionHandle=$SESSION"
LOAN_OFFICER_POS_ID="<uuid returned above>"
```

### 5. Create Org Groups

Org groups are organisational containers (teams, divisions, cost centres). They act as
a tenancy-like boundary — participants in different org groups can be isolated from
each other's worklists:

```bash
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addOrgGroup" \
  -d "groupName=RetailLending" \
  -d "groupType=Team" \
  -d "description=Retail lending division" \
  -d "sessionHandle=$SESSION"
RETAIL_GROUP_ID="<uuid returned above>"
```

Valid `groupType` values: `Division`, `Group`, `Team`, `Unit`.

### 6. Create Participants and Assign Org Model Entities

Create each participant, then assign their role, capability, position, and org group in
one update call:

```bash
# Create participant
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addParticipant" \
  -d "userid=asmith" \
  -d "firstname=Alice" \
  -d "lastname=Smith" \
  -d "password=changeme" \
  -d "isAdmin=false" \
  -d "sessionHandle=$SESSION"
PARTICIPANT_ID="<uuid returned above>"

# Assign role
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addParticipantToRole" \
  -d "participantID=$PARTICIPANT_ID" \
  -d "roleID=$CREDIT_ANALYST_ROLE_ID" \
  -d "sessionHandle=$SESSION"

# Assign capability
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addParticipantToCapability" \
  -d "participantID=$PARTICIPANT_ID" \
  -d "capabilityID=$REGULATORY_CAP_ID" \
  -d "sessionHandle=$SESSION"

# Assign position
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=addParticipantToPosition" \
  -d "participantID=$PARTICIPANT_ID" \
  -d "positionID=$LOAN_OFFICER_POS_ID" \
  -d "sessionHandle=$SESSION"
```

### 7. Reference Roles and Capabilities in Specification Resourcing

With the org model in place, reference roles and capabilities directly in the
specification XML. The resource service resolves names to IDs at runtime:

```xml
<resourcing>
  <offer initiator="system">
    <distributionSet>
      <initialSet>
        <!-- Offer to everyone in the CreditAnalyst role -->
        <role>CreditAnalyst</role>
      </initialSet>
      <filters>
        <!-- Further restrict to participants with RegulatoryReview capability -->
        <filter>
          <name>CapabilityFilter</name>
          <params>
            <param>
              <key>Capability</key>
              <value>RegulatoryReview</value>
            </param>
          </params>
        </filter>
      </filters>
    </distributionSet>
  </offer>
  <allocate initiator="system">
    <allocator>
      <name>ShortestQueue</name>
      <params/>
    </allocator>
  </allocate>
  <start initiator="user"/>
</resourcing>
```

### 8. Bulk-Load the Org Model from XML

For large deployments, define the entire org model in XML and import it via the gateway
rather than making individual API calls:

```xml
<!-- orgmodel.xml -->
<orgModel>
  <roles>
    <role id="r1" name="CreditAnalyst"
          description="Reviews credit applications"/>
  </roles>
  <capabilities>
    <capability id="c1" name="RegulatoryReview"
                description="Compliance authorisation"/>
  </capabilities>
  <positions>
    <position id="p1" name="LoanOfficer" positionID="LO-001"/>
  </positions>
  <orgGroups>
    <orgGroup id="g1" name="RetailLending" groupType="Team"/>
  </orgGroups>
  <participants>
    <participant id="u1" userid="asmith" firstname="Alice" lastname="Smith"
                 isAdmin="false">
      <roles><roleref id="r1"/></roles>
      <capabilities><capref id="c1"/></capabilities>
      <positions><posref id="p1"/></positions>
    </participant>
  </participants>
</orgModel>
```

```bash
curl -s -X POST "http://localhost:8080/resourceService/gateway" \
  -d "action=importOrgData" \
  -d "xml=$(cat orgmodel.xml)" \
  -d "sessionHandle=$SESSION"
```

## Verify

Query the org model to confirm everything was created:

```bash
# List all roles
curl -s "http://localhost:8080/resourceService/gateway?action=getRoles&sessionHandle=$SESSION"

# List all participants with their roles
curl -s "http://localhost:8080/resourceService/gateway?action=getParticipants&sessionHandle=$SESSION"

# Check a specific participant's roles
curl -s "http://localhost:8080/resourceService/gateway?action=getParticipantRoles\
&participantID=$PARTICIPANT_ID&sessionHandle=$SESSION"
```

Each response is an XML document. Presence of the created entities in the response
confirms the org model is active and persisted.

Launch a case whose specification routes to the `CreditAnalyst` role. Navigate to
`http://localhost:8080/resourceService` and log in as `asmith`. The work item should
appear in Alice's offered queue.

## Troubleshooting

**"Resource with this id has not such role" in scheduling service log**
The participant ID used in the scheduling service's properties no longer matches the
resource service database. Re-fetch participant IDs from `getParticipants` and update
`yawl.properties`.

**Work items not routed to a participant despite correct role assignment**
Confirm the specification's `<offer>` block uses `initiator="system"` and that the role
name in XML matches exactly (case-sensitive) the role name in the org model.

**Import via `importOrgData` silently ignores entries**
The resource service rejects duplicate IDs without error. Export the current model first
with `action=exportOrgData` and merge rather than overwriting.

**Participant appears in role but receives no worklist items**
Check if a `CapabilityFilter` or `PositionFilter` in the spec is excluding the
participant. Remove filters temporarily to confirm the base routing works, then re-add
filters one at a time.
