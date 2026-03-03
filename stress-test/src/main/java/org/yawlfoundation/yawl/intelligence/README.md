# YAWL Blake3 Receipt Chain

This implementation provides a Blake3-based receipt chain for tracking YAWL self-play workflow intelligence with cryptographic integrity verification.

## Components

### ReceiptEntry
Immutable record containing:
- `timestamp`: Instant when the entry was created
- `hash`: Blake3 hash of the entry
- `delta`: List of strings representing the change (work item events, errors, metrics)
- `previousHash`: Hash of the previous entry in the chain (null for genesis)

### ReceiptChain
Main class providing:
- Genesis entry creation
- Addition of new entries with delta tracking
- Cryptographic validation of chain integrity
- Persistence to `receipts/intelligence.jsonl`
- Loading from persisted state

## Usage

### Basic Usage
```java
ReceiptChain chain = new ReceiptChain();
chain.createGenesis();  // Start the chain

// Add a work item event
List<String> delta = List.of(
    "workitem_created",
    "case_id:CASE-123",
    "task_id:Task-1",
    "status:created"
);
chain.addEntry(delta);

// Validate integrity
boolean isValid = chain.validateChain();
```

### Integration with YAWL Workflows
```java
public class WorkflowIntelligence {
    private final ReceiptChain receiptChain;

    public void onWorkItemEvent(String caseId, String workItemId,
                               String taskId, String status) {
        List<String> delta = List.of(
            "workflow_event",
            "case_id:" + caseId,
            "workitem_id:" + workItemId,
            "task_id:" + taskId,
            "status:" + status,
            "timestamp:" + Instant.now()
        );

        receiptChain.addEntry(delta);
    }
}
```

### Error Tracking
```java
public void onError(String caseId, String errorType, String details) {
    List<String> delta = List.of(
        "error_event",
        "case_id:" + caseId,
        "error_type:" + errorType,
        "details:" + details,
        "timestamp:" + Instant.now()
    );

    receiptChain.addEntry(delta);
}
```

## Security Features

1. **Blake3 Hashing**: Cryptographic hash function for integrity
2. **Chain Validation**: Detects tampering or corruption
3. **Timestamp Inclusion**: Prevents replay attacks
4. **Immutable Entries**: Once created, entries cannot be modified

## Persistence

Data is stored in JSON Lines format at `receipts/intelligence.jsonl`. Each line contains:
```json
{"timestamp":"2024-03-02T15:30:45Z","hash":"abc123...","delta":["event","data"],"previousHash":"def456..."}
```

## Validation

The `validateChain()` method checks:
- Genesis entry integrity
- Hash chain continuity (each entry links to previous)
- Hash calculation correctness
- No missing or modified entries

## Performance

- **Hashing**: Uses Bouncy Castle's Blake3 implementation
- **Memory**: Efficient storage with incremental updates
- **Concurrency**: Thread-safe operations with synchronization
- **Persistence**: Atomic writes to maintain consistency

## Testing

Run the test suite:
```bash
mvn test -Dtest=ReceiptChainTest
```

Run the demo:
```bash
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.intelligence.ReceiptChainDemo"
```

## Dependencies

- Bouncy Castle (bcprov-jdk18on) for Blake3 hashing
- Java 25+ with preview features enabled
- SLF4J for logging (optional)

## Error Handling

- Throws `NullPointerException` for null deltas
- Throws `IllegalStateException` for duplicate genesis creation
- Throws `RuntimeException` for persistence failures
- Returns `false` from `validateChain()` if tampering detected

## Best Practices

1. Always create a genesis entry before adding data
2. Validate chain integrity after loading from disk
3. Handle persistence failures appropriately
4. Use meaningful delta descriptions for debugging
5. Store chain file in a secure location (contains hashed data)