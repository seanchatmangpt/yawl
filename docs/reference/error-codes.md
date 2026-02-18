# YAWL Error Codes and Exception Reference

**Audience**: AI coding agents interpreting YAWL engine logs, debugging workflow failures, or handling exceptions in custom services.

This document catalogues every exception class thrown by the YAWL engine, the XML failure responses returned by the REST and servlet APIs, and common error conditions with resolution steps.

---

## Exception Class Hierarchy

```
java.lang.Exception
  └── YAWLException  (org.yawlfoundation.yawl.exceptions)
        ├── YStateException
        ├── YSyntaxException
        │     └── YConnectivityException
        ├── YPersistenceException
        ├── YQueryException
        ├── YEngineStateException
        ├── YAuthenticationException
        ├── YExternalDataException
        ├── YLogException
        ├── YSchemaBuildingException
        └── YDataStateException
              ├── YDataQueryException
              └── YDataValidationException
```

All exceptions in `org.yawlfoundation.yawl.exceptions` extend `YAWLException`, which extends `java.lang.Exception`. They carry a `_message` field and support optional `context` (key-value map) and `troubleshootingGuide` via builder-style methods.

---

## Exception Class Reference

### `YAWLException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YAWLException`
**Extends**: `java.lang.Exception`

The base exception for all YAWL-specific errors. Provides:
- `getMessage()` — returns the stored `_message` field.
- `toXML()` — serialises the exception to XML for transport across service boundaries.
- `withContext(key, value)` — attaches diagnostic context (returns `this` for chaining).
- `withTroubleshootingGuide(guide)` — attaches resolution text.
- `rethrow()` — convenience re-throw as the appropriate typed subclass.
- Static `unmarshal(Document)` — deserialises an exception from its XML form.

**When thrown**: Directly only for `ObserverGatewayController` when an `ObserverGateway` has a null scheme (`"Cannot add: ObserverGateway has a null scheme."`). All other engine code throws a typed subclass.

---

### `YStateException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YStateException`
**Extends**: `YAWLException`

Thrown when an operation is attempted but the engine or net is in an invalid state to perform it.

| Throw site | Message pattern | Cause |
|------------|----------------|-------|
| `YTask.fire()` | `"{task} cannot fire due to not being enabled"` | Task fired but not in enabled state. |
| `YNetRunner.startWorkItem()` | `"Task is not (or no longer) enabled: {taskID}"` | Work item start attempted after task left enabled state. |
| `YNetRunner.addNewInstance()` | `"Cannot add instance to non-busy task: {taskID}"` | MI instance added to a task not in executing state. |
| `YEngine.startCase()` | `"Invalid or malformed caseParams."` | Case launch params XML is not well-formed. |
| `YEngine.startCase()` | `"Invalid caseParams: outermost element name must match {specID}"` | Root element of launch params does not match specification URI. |
| `YEngine.startCase()` | `"CaseID '{caseID}' is already active."` | Attempt to launch a case with an ID already in use. |
| `YEngine.startCase()` | `"Unable to start case."` | Net runner failed to initialise; see engine log for detail. |
| `YEngine.cancelCase()` | `"Invalid case id '{caseID}'."` | No running case matches the given ID. |
| `YEngine.suspendCase()` | `"Could not suspend case (See log for details)"` | Suspension failed; underlying exception logged. |
| `YEngine.resumeCase()` | `"Could not resume case (See log for details)"` | Resume failed; underlying exception logged. |
| `YEngine.unloadSpecification()` | `"Cannot unload specification '{specID}' as there are active cases."` | Attempt to unload a spec while cases are running against it. |
| `YEngine.startWorkItem()` | `"Cannot start null work item."` | Null work item reference passed to start. |
| `YEngine.completeWorkItem()` | `"WorkItem with ID [{id}] not in executing state"` | Complete called on item not in executing state. |
| `YEngine.completeWorkItem()` | `"WorkItem argument is equal to null."` | Null work item passed to complete. |
| `YEngine.skipWorkItem()` | `"Could not skip workitem: {id}"` | Skip operation failed. |
| `YEngine.rollbackWorkItem()` | `"Unable to rollback: work Item[{id}] not in executing state"` | Rollback called on non-executing item. |
| `YEngine.rollbackWorkItem()` | `"Work Item[{id}] not found."` | No work item found for given ID. |
| `YEngine.getWorkItem()` | `"No work item found with id = {id}"` | Work item lookup by ID returned nothing. |
| `YNet` | `"Failed to write completion data to external source: {msg}"` | External data gateway write failed. |
| `YNet` | `"Failed to get starting data from external source: {msg}"` | External data gateway read failed. |
| `YSpecificationTable` | `"No specification found with ID [{specID}]"` | Specification lookup by ID returned nothing. |
| `YTask` | `"Failed to update external data: {msg}"` | External data handler failed during task execution. |
| `YTask` | `"External data pull failure: {msg}"` | External data pull failed at task start. |
| `YTask` | `"External data pull failure: No data"` | External data gateway returned null. |

**Resolution**: Check that the operation sequence is valid. Verify the work item or case ID exists and is in the correct state before calling. Use `YEngine.getWorkItem(id)` or `YEngine.getCases()` to confirm current state.

---

### `YSyntaxException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YSyntaxException`
**Extends**: `YAWLException`

Thrown when a specification fails XML parsing or schema validation, or when a net element has invalid syntax.

| Throw site | Message pattern | Cause |
|------------|----------------|-------|
| `YMarshal.unmarshalSpecifications()` | `"Invalid XML specification."` | XML string is not well-formed (JDOM parse failed). |
| `YMarshal.unmarshalSpecifications()` | `" The specification file failed to verify against YAWL's Schema:\n{xerces errors}"` | XML parsed but does not conform to `YAWL_Schema4.0.xsd`. |
| `YSyntaxException(YExternalNetElement, message)` | `"{element} {message}"` | Syntax error associated with a specific net element. |

**Resolution**:
- For `"Invalid XML specification."`: the input string is not valid XML. Check for unclosed tags, bad encoding, or truncated content.
- For schema verification failure: use `xmllint --schema schema/YAWL_Schema4.0.xsd spec.yawl` to identify the specific schema violation. Common causes include missing required elements (`<join>`, `<split>`, `<metaData>`), incorrect element ordering, or invalid attribute values.

---

### `YConnectivityException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YConnectivityException`
**Extends**: `YSyntaxException`

Thrown when an attempt is made to connect two net elements in a way that violates YAWL's structural rules (e.g., task-to-task direct connection without an intermediate condition, or input condition to output condition).

| Message pattern | Cause |
|----------------|-------|
| `"YAWL Syntax does not permit {source} to be connected with {destination}"` | Invalid arc between net elements. |

**Resolution**: YAWL nets follow Petri net rules. Tasks must connect to conditions and vice versa. Verify the net structure: tasks connect to conditions (or directly to outputCondition), conditions connect to tasks (or to inputCondition). Direct task-to-task arcs are not permitted except where implicitly handled by the engine.

---

### `YPersistenceException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YPersistenceException`
**Extends**: `YAWLException`

Thrown when the Hibernate persistence layer fails. Treat as fatal: the engine cannot guarantee state consistency after this exception.

| Throw site | Message pattern | Cause |
|------------|----------------|-------|
| `YPersistenceManager.initialise()` | `"Failure initialising persistence layer"` | Hibernate SessionFactory failed to build (bad config, DB unreachable). |
| `YPersistenceManager.startTransaction()` | `"Failure to start transactional session"` | Cannot open Hibernate session. |
| `YPersistenceManager.deleteObject()` | `"Failure to remove object of type {class}"` | Hibernate delete failed. |
| `YPersistenceManager.storeObject()` | `"Failure detected whilst persisting instance of {class}"` | Hibernate save/update failed (after attempted rollback). |
| `YPersistenceManager.commitTransaction()` | `"Failure to commit transactional session"` | Hibernate commit failed. |
| `YPersistenceManager.rollbackTransaction()` | `"Failure to rollback transaction"` | Hibernate rollback itself failed (serious state corruption risk). |
| `YPersistenceManager.createQuery()` | `"Failure to create Hibernate query object"` | HQL query syntax error or invalid entity class. |
| `YPersistenceManager.execQuery()` | `"Error executing query"` | HQL query execution failed. |
| `YPersistenceManager.getObjectsForClass()` | `"Error reading data for class: {className}"` | Full table read failed. |
| `YPersistenceManager.getObjectsForClassWhere()` | `"Error querying {class} for {field}={value}"` | Conditional query failed. |
| `YEngine.init()` | `"Failure to restart engine from persistence image"` | Engine could not restore state from DB on startup. |
| `YEngine.loadSpecification()` | `"Failure whilst persisting new specification"` | New spec loaded but could not be saved to DB. |
| `YEngineRestorer.restore()` | `"Failure whilst restoring specification"` | Specification XML in DB is unreadable. |
| `YEngineRestorer` | `"{message}"` | Persisted object references a missing entity during restore. |
| `CaseImporter` | `"Could not start db transaction"` | DB transaction cannot be started during case import. |
| `YEngine.clearCase()` | `"Failure whilst clearing case"` | Case cancellation failed to persist. |

**Resolution**:
1. Check database connectivity (JDBC URL, credentials, DB server running).
2. Review Hibernate configuration in `hibernate.cfg.xml` or application config.
3. On startup failure (`"Failure to restart engine from persistence image"`): the DB schema may be out of sync. Run schema migration or drop and recreate the schema with `hbm2ddl.auto=create`.
4. On commit failure: the engine logs a fatal message and enters a degraded state. Restart the engine after fixing the root DB issue.

---

### `YQueryException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YQueryException`
**Extends**: `YAWLException`

Thrown when an XQuery or XPath expression evaluation fails at runtime.

| Throw site | Message pattern | Cause |
|------------|----------------|-------|
| `YTask.evaluateTimerPredicate()` | `"Evaluated XQuery did not return a singular result..."` | Timer XQuery returned multiple or zero nodes. |
| `YTask.evaluateTimerPredicate()` | `"Invalid XQuery expression ({xquery})."` | XQuery syntax error in timer expression. |
| `YTask.getTimerState()` | `"Unable to determine current timer status for {task}"` | Timer state lookup failed. |
| `YNetRunner.checkTimerPredicate()` | `"Unable to find timer state for task named {taskID}"` | Timer predicate evaluated against unknown task. |
| `YNetRunner.checkTimerPredicate()` | `"Malformed timer predicate: {predicate}"` | Timer predicate string is not well-formed. |

**Resolution**: Review XQuery expressions in `<timer>` elements and `<startingMappings>`/`<completedMappings>`. Use an XQuery tester to validate expressions against sample data. Ensure the data document has the expected structure before the query runs.

---

### `YEngineStateException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YEngineStateException`
**Extends**: `YAWLException`

Thrown when the engine itself is not in the correct operational state to service a request.

| Throw site | Message pattern | Cause |
|------------|----------------|-------|
| `YEngine.checkEngineRunning()` | `"Unable to accept request as engine not in running state: Current state = {state}"` | Engine is starting up, shutting down, or in error state. |
| `CaseImporter` | `"Invalid xml for import of case(s)"` | Case import XML is not parseable. |
| `CaseImporter` | `"No net runners found to import for case"` | Case XML has no net runner data. |
| `CaseImporter` | `"No workitems found to import for case"` | Case XML has no work item data. |
| `CaseImporter` | `"Null specification for case"` | Imported case references a null specification. |
| `CaseImporter` | `"Specification for case is not loaded"` | Imported case references a specification not currently in the engine. |

**Engine states**:
- `Running` — normal operation.
- `Starting` — engine is initialising.
- `Terminating` — engine is shutting down.
- `JournallingFailure` — persistence layer has failed (engine enters read-only degraded mode).

**Resolution**: Retry the request after the engine reaches `Running` state. For case import errors, validate the import XML and ensure the target specification is loaded before importing.

---

### `YAuthenticationException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YAuthenticationException`
**Extends**: `YAWLException`

Thrown when authentication credentials are invalid or a session has expired.

**When thrown**: Service-level code (custom services, resource service) when authentication against the engine fails. The engine's `EngineGatewayImpl` returns a `<failure><reason>Invalid or expired session.</reason></failure>` XML string rather than throwing this exception at the gateway level.

**Resolution**: Obtain a new session handle by calling the engine's connect endpoint (`/ia/connect` or `InterfaceA_EnvironmentBasedClient.connect()`). Session handles expire after inactivity.

---

### `YExternalDataException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YExternalDataException`
**Extends**: `YAWLException`

Thrown when operations against an external data gateway (registered via `<externalDataGateway>`) fail.

**When thrown**: `YNet` and `YTask` when the external data source cannot be written to or read from.

**Resolution**: Verify the external data gateway class is correctly registered with the engine and that the external data source is accessible. Check the message for the underlying cause. The gateway class name is specified in the `<externalDataGateway>` element of the net's decomposition.

---

### `YLogException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YLogException`
**Extends**: `YAWLException`

Thrown when process log operations fail.

**When thrown**: Logging subsystem when it cannot write audit trail entries to the process log database.

**Resolution**: Verify the process log database connection. Check Hibernate configuration for the logging persistence unit. Process log failures do not stop workflow execution but mean the audit trail is incomplete.

---

### `YSchemaBuildingException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YSchemaBuildingException`
**Extends**: `YAWLException`

Thrown when the engine cannot build an XML Schema validator from the schema fragment embedded in a specification.

**When thrown**: During specification loading when the `<xs:schema>` element within a `<specification>` contains invalid XSD.

**Resolution**: Validate the embedded `<xs:schema>` block independently. Ensure custom type definitions are syntactically valid XML Schema and do not reference undefined types. Remove unused schema content.

---

### `YDataStateException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YDataStateException`
**Extends**: `YAWLException`

Base class for data-related exceptions. Carries structured context:
- `_queryString` — the XQuery expression that was executing.
- `_queriedData` — the XML data being queried.
- `_schema` — the XML Schema used for validation.
- `_dataInput` — the actual data that failed validation.
- `_xercesErrors` — Xerces schema validation error text.
- `_source` — the task ID where the failure occurred.

The full `getMessage()` output format:
```
{message}
Task [{source}]
XQuery [{queryString}]
Document [{queriedData XML}]
Schema for Expected [{schema XML}]
But received [{dataInput XML}]
Validation error message [{xercesErrors}]
```

**Resolution**: Examine the `_source` (task ID), `_queryString`, and `_xercesErrors` fields to identify the mapping or schema mismatch. Fix the XQuery expression or update the variable type declaration.

---

### `YDataQueryException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YDataQueryException`
**Extends**: `YDataStateException`

Thrown when an XQuery expression in a data mapping fails to execute against the task's data.

| Throw site | Cause |
|------------|-------|
| `YTask.prepareDataForTaskStarting()` | Starting mapping XQuery failed to evaluate. |
| `YTask.prepareDataForTaskCompletion()` | Completed mapping XQuery failed to evaluate. |
| `YTask.splitMIData()` | Multi-instance data splitting XQuery failed. |

**Message format**:
```
The MI data accessing query ({queryString}) for the task ({taskID}) was applied over some data. It failed to execute as expected[: {cause}]
```

**Resolution**: The most common causes:
1. The XQuery references a variable path that does not exist in the data document.
2. The data document root element name does not match what the XQuery expects (net ID for starting mappings, task ID for completed mappings).
3. XQuery syntax error in the expression.

Check the `<expression query="..."/>` values in `<startingMappings>` and `<completedMappings>`. The data root for starting mappings is `/NetworkID/VariableName/`, for completed mappings it is `/TaskID/ParameterName/`.

---

### `YDataValidationException`

**FQN**: `org.yawlfoundation.yawl.exceptions.YDataValidationException`
**Extends**: `YDataStateException`

Thrown when task output data fails validation against the parameter's declared XML Schema type.

**When thrown**: After a work item completes, when the returned data for an output parameter does not conform to the declared type schema.

**Resolution**:
1. Read `_xercesErrors` for the specific schema violation.
2. Check that the custom service or user form returns data in the correct XML format for each output parameter.
3. Verify that the `<type>` and `<namespace>` declarations in `<outputParam>` elements match what the task implementation produces.

---

## Engine API Response Format

The servlet-based engine API (Interface A and Interface B servlets, `EngineGatewayImpl`) returns XML strings, not exceptions. Successful responses are wrapped in `<success>` and failures in `<failure>`.

**Success format**:
```xml
<success>result content here</success>
```
or for simple acknowledgements:
```xml
<success/>
```

**Failure format**:
```xml
<failure><reason>Human-readable error message here.</reason></failure>
```

Callers must check whether the response starts with `<fail` to detect errors. The `EngineGatewayImpl.isFailureMessage(String)` method performs this check (`msg.startsWith("<fail")`).

The REST API (`YawlExceptionMapper`) maps all exceptions to HTTP 500 responses with a JSON body:
```json
{"error": "ExceptionClassName", "message": "Human-readable message"}
```
The REST API returns HTTP 401 for missing session handles (from `InterfaceARestResource`).

---

## API Error Message Catalogue

These are the string messages returned inside `<failure><reason>...</reason></failure>` by `EngineGatewayImpl`.

| Operation | Error message | Cause | Resolution |
|-----------|--------------|-------|-----------|
| Any request | `"Invalid or expired session."` | Session handle not found in session cache or has timed out. | Re-authenticate: call `connect()` to get a fresh session handle. |
| `getWorkItemOutputData` | `"WorkItem with ID '{id}' not found."` | Work item ID does not exist in the engine's work item repository. | Verify the work item ID. Only items in Executing or Suspended state have retrievable output data. |
| `startWorkItem` | `"WorkItem with ID '{id}' not found."` | Work item ID does not exist. | Verify the work item ID against the engine's enabled work item list. |
| `getSpecificationXML` | `"Specification with ID ({specID}) not found."` | Spec ID not loaded. | Load the specification first via `loadSpecification()`. |
| `getSpecificationXML` | `"Failed to marshal the specification into XML."` | Internal marshalling error. | Check engine logs for the underlying exception. |
| `checkConnection` (session) | `"WorkItem with ID [{id}] not found."` | Work item not found when attempting connection check. | Ensure the work item exists before checking connection status. |
| `startWorkItem` (various) | `"{YStateException message}"` | Task not in enabled state; see `YStateException` table. | Check work item state before starting. |
| `completeWorkItem` | `"WorkItem with ID [{id}] not found."` | Work item does not exist or has already been completed. | Retrieve the current work item list to confirm the ID is still active. |
| `completeWorkItem` | `"{YStateException or YDataStateException message}"` | Completion data invalid or work item in wrong state. | Fix data mappings or check item state. |
| `suspendWorkItem` | `"WorkItem with ID [{id}] not found."` | Work item not found. | Confirm the ID. |
| `suspendWorkItem` | `"{exception message}"` | Suspension failed (state or persistence error). | Check logs. |
| `unsuspendWorkItem` | `"WorkItem with ID [{id}] not found."` | Work item not found. | Confirm the ID. |
| `completeWorkItem` | `"Work item has no decomposition"` | Work item's task has no linked decomposition. | Ensure every task element has a `<decomposesTo>` referencing a valid `WebServiceGatewayFactsType` decomposition. |
| `rollbackWorkItem` | `"No work item with id = {id}"` | Work item not found for rollback. | Confirm the ID. |
| `connect` (admin user) | `"The generic 'admin' user has been disabled."` | Admin account deactivated. | Use a named user account or re-enable the admin account in user configuration. |
| `getTaskInformation` | `"The was no task found with ID {taskID}"` | Task ID not found in the specification. | Verify the task ID against the specification's process elements. |
| `launchCase` | `"{YStateException message}"` | Case could not be started; see `YStateException` table. | Check specification is loaded and case params are valid. |
| `launchCase` (delayed) | `"{exception message}"` | Delayed case launch failed. | Check timer configuration and ensure specification remains loaded. |
| `getCaseData` | `"Specification ID for case ({caseID}) not found."` | Running case's specification was unloaded mid-flight. | Reload the specification. |
| `getCaseData` | `"Running case with id ({caseID}) not found."` | No case running with that ID. | Verify the case ID is active. |
| `getCaseSpecification` | `"Failed to marshal the specification into XML."` | Internal marshalling error for the running case's spec. | Check engine logs. |
| `getCaseSpecification` | `"Specification for case ({caseID}) not found."` | Spec for a running case is missing (shouldn't happen in normal operation). | Restart the engine and reload specifications. |
| `getCaseSpecification` | `"Running case with id ({caseID}) not found."` | No active case with that ID. | Confirm the case ID. |
| Engine init | `"No specification found for id: {specID}"` | Gateway called with unknown spec ID. | Load the specification before referencing it by ID. |

---

## Common Error Conditions with Resolution Steps

### Spec Loading Errors

**Symptom**: `YSyntaxException` with `"Invalid XML specification."` on spec upload.
**Cause**: The XML string is not well-formed.
**Resolution**: Parse the file with `xmllint --noout myspec.yawl`. Fix malformed XML (unclosed tags, bad character encoding, BOM issues).

**Symptom**: `YSyntaxException` with `"The specification file failed to verify against YAWL's Schema"` followed by Xerces errors.
**Cause**: XML is well-formed but violates `YAWL_Schema4.0.xsd`.
**Resolution**: Run `xmllint --schema schema/YAWL_Schema4.0.xsd myspec.yawl`. Common violations:
- Missing `<join>` or `<split>` on a task.
- `<decomposesTo>` references an ID not present in the spec.
- `<metaData>` is absent from `<specification>`.
- `<processControlElements>` is missing `<inputCondition>` or `<outputCondition>`.

**Symptom**: `YSchemaBuildingException` during load.
**Cause**: The `<xs:schema>` block inside the specification contains invalid XSD.
**Resolution**: Validate the embedded schema block independently. Remove or correct custom type declarations.

---

### Case Launch Errors

**Symptom**: `<failure><reason>Invalid or malformed caseParams.</reason></failure>`
**Cause**: The case parameters XML string passed to `launchCase` is not parseable.
**Resolution**: Ensure the case params string is valid XML. The root element must be the specification URI.

**Symptom**: `<failure><reason>Invalid caseParams: outermost element name must match {specID}</reason></failure>`
**Cause**: Root element name in case params does not match the specification's URI.
**Resolution**: The root element must match the `uri` attribute of `<specification>`. Example: if `uri="OrderProcess"`, the params must be `<OrderProcess>...</OrderProcess>`.

**Symptom**: `<failure><reason>No specification found for id: {specID}</reason></failure>`
**Cause**: The specification is not loaded in the engine.
**Resolution**: Upload and load the specification via Interface A before launching cases.

---

### Work Item Errors

**Symptom**: `YStateException` with `"Task is not (or no longer) enabled: {taskID}"`
**Cause**: Another worker started or the task was cancelled between the item appearing in the enabled queue and your `startWorkItem` call.
**Resolution**: Refresh the work item list. The item may have been taken by another resource or the case may have progressed past that task.

**Symptom**: `YDataQueryException` on `completeWorkItem`
**Cause**: The XQuery in `<completedMappings>` cannot be evaluated against the output data.
**Resolution**: Check the `query` attribute. The data root path uses the task ID: `/TaskID/ParameterName/text()`. Verify the XML structure of the data returned by the custom service.

**Symptom**: `YDataValidationException` on `completeWorkItem`
**Cause**: Output data does not conform to the declared type schema.
**Resolution**: Inspect `_xercesErrors` for the schema violation. Ensure the service returns the correct XML structure and data types for each output parameter.

---

### Persistence Errors

**Symptom**: `YPersistenceException` with `"Failure initialising persistence layer"` at startup.
**Cause**: Database unreachable, wrong credentials, or Hibernate configuration error.
**Resolution**:
1. Verify the DB server is running and accessible.
2. Check JDBC URL, username, password in Hibernate config.
3. Verify the DB schema exists; run with `hbm2ddl.auto=create` on first deployment.
4. Review full exception cause in the log (logged at FATAL level).

**Symptom**: `YPersistenceException` with `"Failure to commit transactional session"` during operation.
**Cause**: Transaction commit failed (DB write error, constraint violation, DB timeout).
**Resolution**: The engine logs a FATAL-level message with the Hibernate cause. Check DB disk space, connection pool, and constraint violations. The engine attempts a rollback automatically.

**Symptom**: `YPersistenceException` with `"Failure to restart engine from persistence image"` at startup.
**Cause**: Persisted engine state cannot be restored (schema mismatch, corrupt data, missing tables).
**Resolution**: If using in-memory/H2 DB (development): the DB was wiped; restart with a clean state. If using production DB: run Hibernate schema validation (`hbm2ddl.auto=validate`) to identify mismatches. Schema migration is required if YAWL version changed.

---

### Concurrency Errors

**Symptom**: `YStateException` with `"CaseID '{caseID}' is already active."`
**Cause**: `launchCase` called with an explicit case ID already in use.
**Resolution**: Use a unique case ID or let the engine auto-assign IDs by not specifying one.

**Symptom**: `YStateException` with `"Task is not (or no longer) enabled: {taskID}"` under high concurrency.
**Cause**: Race condition between two resources attempting to start the same enabled work item simultaneously.
**Resolution**: This is expected behaviour. The second caller must treat this as `try again with a different item`. Implement retry with fresh work item list fetch.

**Symptom**: `YStateException` with `"Cannot add instance to non-busy task: {taskID}"`
**Cause**: Dynamic MI instance addition attempted after the task left executing state.
**Resolution**: Check task state before calling `addNewInstance`. Only tasks with `creationMode="dynamic"` and in executing state accept new instances.

---

## Exception Serialisation Format

Exceptions can be serialised to XML for transport between services using `YAWLException.toXML()`. The format is:

```xml
<fully.qualified.ClassName>
  <message>Human readable message</message>
</fully.qualified.ClassName>
```

For `YDataStateException` and subclasses, additional fields are included:
```xml
<org.yawlfoundation.yawl.exceptions.YDataStateException>
  <message>...</message>
  <queryString>XQuery expression</queryString>
  <queriedData>XML element</queriedData>
  <schema>XSD fragment</schema>
  <dataInput>XML element</dataInput>
  <xercesErrors>Schema error text</xercesErrors>
  <source>taskID</source>
</org.yawlfoundation.yawl.exceptions.YDataStateException>
```

Deserialise with `YAWLException.unmarshal(Document)`, which dispatches to the correct subclass based on the root element name.

---

## `Problem` Class

**FQN**: `org.yawlfoundation.yawl.exceptions.Problem`

Not an exception — a serialisable warning carrier used for non-fatal runtime observations.

| Field | Type | Description |
|-------|------|-------------|
| `source` | String | Component or task that generated the problem. |
| `problemTime` | Instant | When the problem was detected. |
| `messageType` | String | Category. Known constant: `Problem.EMPTY_RESOURCE_SET_MESSAGETYPE = "EmptyResourceSetType"` — emitted when a resource offer returns zero eligible participants. |
| `message` | String | Human-readable description. |

`Problem` instances are collected and returned to callers (e.g., resource service) rather than interrupting workflow execution. An `EMPTY_RESOURCE_SET_MESSAGETYPE` problem means the resource service's distribution set configuration found no eligible participants for a task — the task will remain unallocated until resolved manually or by an escalation rule.
