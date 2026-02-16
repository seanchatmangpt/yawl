# Event-Based Deferred Choice Pattern Specification

## Pattern Overview

**Pattern ID**: `event-based-deferred-choice`
**Category**: Workflow Control Pattern (Event-Driven)
**Workflow Patterns Reference**: WCP-16 (Deferred Choice)

### Description

The Event-Based Deferred Choice pattern allows the workflow to wait for one of several possible events to occur. The choice of which path to take is deferred until one of the events actually happens. Unlike an XOR split where the choice is made based on data, here the choice is made by the external world through events. This is essential for event-driven architectures and reactive workflows.

### State Machine Definition

```
Deferred Choice States:
  - IDLE: Not waiting for events
  - WAITING: Waiting for one of multiple events
  - TRIGGERED: An event has occurred
  - PROCESSING: Processing the triggered event
  - COMPLETED: Event processed, path chosen

Event Listener States:
  - REGISTERED: Listener registered, waiting
  - TRIGGERED: Event received
  - CONSUMED: Event consumed by workflow
  - CANCELLED: Listener cancelled (another event won)

Transitions:
  IDLE -> WAITING (on registerListeners)
  WAITING -> TRIGGERED (on eventReceived)
  TRIGGERED -> PROCESSING (on consumeEvent)
  PROCESSING -> COMPLETED (on pathChosen)
  WAITING -> CANCELLED (on non-winning events when one triggers)
```

### Class Hierarchy

```
YTask
  └── YDeferredChoiceTask (NEW)
        ├── YEventListener (NEW) - abstract event listener
        │     ├── YTimerEventListener (NEW)
        │     ├── YMessageEventListener (NEW)
        │     ├── YSignalEventListener (NEW)
        │     └── YConditionEventListener (NEW)
        ├── YEventRegistry (NEW) - manages active listeners
        └── YDeferredChoiceState (NEW) - choice state tracker
```

### Integration Points

| Existing Class | Integration Point | Purpose |
|----------------|-------------------|---------|
| `YTask` | New task type | Deferred choice task |
| `YTask.t_enabled()` | Override | Always enabled (waits for events) |
| `YTimerVariable` | Event source | Timer-based events |
| `YNetRunner` | Event dispatch | Route events to listeners |
| `YWorkItem` | Suspended state | Waiting for event |
| `E2WFOJNet` | Structural analysis | Deferred choice in reset net |

### Required Methods

#### YDeferredChoiceTask (extends YTask)

```java
public class YDeferredChoiceTask extends YTask {

    // Deferred choice constants
    public static final int _DEFERRED_CHOICE_JOIN = 80;
    public static final int _DEFERRED_CHOICE_SPLIT = 81;

    // State and registry
    private YDeferredChoiceState _choiceState;
    private YEventRegistry _eventRegistry;
    private List<YEventListener> _listeners;
    private YFlow _triggeredFlow;

    // Core deferred choice methods
    public void registerListeners(YPersistenceManager pmgr)
        throws YPersistenceException;
    public void unregisterListeners(YPersistenceManager pmgr)
        throws YPersistenceException;
    public void onEventReceived(String listenerId, Object eventData)
        throws YPersistenceException;
    public void consumeEvent(YPersistenceManager pmgr)
        throws YPersistenceException, YStateException;

    // Listener management
    public void addListener(YEventListener listener);
    public void removeListener(String listenerId);
    public List<YEventListener> getListeners();
    public YEventListener getTriggeredListener();
    public YFlow getTriggeredFlow();

    // State queries
    public YDeferredChoiceState getChoiceState();
    public boolean isWaiting();
    public boolean isTriggered();
    public String getWinningListenerId();
    public Object getEventData();

    // Override from YTask
    @Override
    public synchronized boolean t_enabled(YIdentifier id);

    @Override
    public synchronized List<YIdentifier> t_fire(YPersistenceManager pmgr)
        throws YStateException, YDataStateException, YQueryException,
               YPersistenceException;

    @Override
    protected void startOne(YPersistenceManager pmgr, YIdentifier id)
        throws YPersistenceException;

    // Cancel handling
    @Override
    public synchronized void cancel(YPersistenceManager pmgr)
        throws YPersistenceException;
}
```

#### YEventListener (Abstract)

```java
public abstract class YEventListener {
    protected String _listenerId;
    protected String _correlationKey;  // For event matching
    protected YFlow _targetFlow;       // Flow to trigger
    protected ListenerState _state;

    public enum ListenerState {
        REGISTERED, TRIGGERED, CONSUMED, CANCELLED
    }

    // Abstract methods
    public abstract void register(YPersistenceManager pmgr)
        throws YPersistenceException;
    public abstract void unregister(YPersistenceManager pmgr)
        throws YPersistenceException;
    public abstract boolean matches(YEvent event);

    // Common methods
    public String getListenerId();
    public String getCorrelationKey();
    public YFlow getTargetFlow();
    public ListenerState getState();
    public void setState(ListenerState state);

    // Event handling
    public void onEvent(Object eventData);
    public Object getEventData();
}
```

#### YTimerEventListener

```java
public class YTimerEventListener extends YEventListener {
    private long _delayMs;
    private Date _expiresAt;
    private YTimerVariable _timer;

    public YTimerEventListener(String listenerId, long delayMs, YFlow targetFlow) {
        _listenerId = listenerId;
        _delayMs = delayMs;
        _targetFlow = targetFlow;
    }

    @Override
    public void register(YPersistenceManager pmgr) throws YPersistenceException {
        _expiresAt = new Date(System.currentTimeMillis() + _delayMs);
        _timer = new YTimerVariable(_delayMs);
        _timer.start();
        _state = ListenerState.REGISTERED;
    }

    @Override
    public void unregister(YPersistenceManager pmgr) throws YPersistenceException {
        if (_timer != null) {
            _timer.cancel();
        }
        _state = ListenerState.CANCELLED;
    }

    @Override
    public boolean matches(YEvent event) {
        return event.getType() == YEventType.TIMER
            && event.getListenerId().equals(_listenerId);
    }

    public Date getExpiresAt();
    public long getDelayMs();
}
```

#### YMessageEventListener

```java
public class YMessageEventListener extends YEventListener {
    private String _messageType;  // Type of message to listen for
    private String _xpathFilter;  // XPath to filter messages
    private String _queueName;    // JMS queue or internal queue

    public YMessageEventListener(String listenerId, String messageType,
                                  String xpathFilter, YFlow targetFlow) {
        _listenerId = listenerId;
        _messageType = messageType;
        _xpathFilter = xpathFilter;
        _targetFlow = targetFlow;
    }

    @Override
    public void register(YPersistenceManager pmgr) throws YPersistenceException {
        // Subscribe to message queue
        MessageQueueManager.subscribe(_queueName, _messageType, this);
        _state = ListenerState.REGISTERED;
    }

    @Override
    public void unregister(YPersistenceManager pmgr) throws YPersistenceException {
        MessageQueueManager.unsubscribe(_queueName, _messageType, this);
        _state = ListenerState.CANCELLED;
    }

    @Override
    public boolean matches(YEvent event) {
        if (event.getType() != YEventType.MESSAGE) return false;
        if (!event.getMessageType().equals(_messageType)) return false;
        if (_xpathFilter != null) {
            return evaluateXPathFilter(event.getData(), _xpathFilter);
        }
        return true;
    }

    public String getMessageType();
    public String getXpathFilter();
    public String getQueueName();
}
```

#### YSignalEventListener

```java
public class YSignalEventListener extends YEventListener {
    private String _signalName;  // Signal to listen for
    private String _sourceNetId; // Optional: specific source net

    public YSignalEventListener(String listenerId, String signalName,
                                 YFlow targetFlow) {
        _listenerId = listenerId;
        _signalName = signalName;
        _targetFlow = targetFlow;
    }

    @Override
    public void register(YPersistenceManager pmgr) throws YPersistenceException {
        SignalManager.registerListener(_signalName, this);
        _state = ListenerState.REGISTERED;
    }

    @Override
    public void unregister(YPersistenceManager pmgr) throws YPersistenceException {
        SignalManager.unregisterListener(_signalName, this);
        _state = ListenerState.CANCELLED;
    }

    @Override
    public boolean matches(YEvent event) {
        return event.getType() == YEventType.SIGNAL
            && event.getSignalName().equals(_signalName);
    }

    public String getSignalName();
    public String getSourceNetId();
}
```

#### YEventRegistry

```java
public class YEventRegistry {
    private Map<String, YEventListener> _activeListeners;
    private Map<String, List<YEventListener>> _listenersByCorrelationKey;

    // Registry operations
    public void registerListener(YEventListener listener);
    public void unregisterListener(String listenerId);
    public YEventListener getListener(String listenerId);

    // Event dispatch
    public void dispatchEvent(YEvent event)
        throws YPersistenceException;
    public List<YEventListener> findMatchingListeners(YEvent event);

    // Bulk operations
    public void cancelAllListeners(YPersistenceManager pmgr)
        throws YPersistenceException;
    public List<YEventListener> getActiveListeners();
    public int getActiveListenerCount();
}
```

#### YDeferredChoiceState

```java
public class YDeferredChoiceState {
    private String _choiceId;
    private ChoicePhase _phase;
    private String _triggeredListenerId;
    private Object _eventData;
    private Date _waitingSince;
    private Date _triggeredAt;

    public enum ChoicePhase {
        IDLE, WAITING, TRIGGERED, PROCESSING, COMPLETED
    }

    // State transitions
    public void startWaiting();
    public void trigger(String listenerId, Object eventData);
    public void processing();
    public void complete();

    // Queries
    public ChoicePhase getPhase();
    public boolean isWaiting();
    public boolean isTriggered();
    public String getTriggeredListenerId();
    public Object getEventData();
    public long getWaitingDuration();
}
```

### Example Usage

```xml
<!-- YAWL Specification Extension -->
<net id="OrderProcessing">
  <!-- Deferred choice task -->
  <deferredChoice id="WaitForResponse">
    <listeners>
      <!-- Timer event: timeout after 24 hours -->
      <timerListener id="timeout" delay="86400000">
        <flow target="HandleTimeout"/>
      </timerListener>

      <!-- Message event: customer response -->
      <messageListener id="customerResponse"
                       messageType="CustomerDecision"
                       queue="orderResponses">
        <flow target="ProcessCustomerDecision"/>
      </messageListener>

      <!-- Signal event: cancellation from admin -->
      <signalListener id="adminCancel" signalName="OrderCancelled">
        <flow target="HandleCancellation"/>
      </signalListener>
    </listeners>
  </deferredChoice>

  <!-- Target tasks -->
  <task id="HandleTimeout">...</task>
  <task id="ProcessCustomerDecision">...</task>
  <task id="HandleCancellation">...</task>
</net>
```

### Runtime Behavior

```
Scenario: Order processing waiting for customer response

1. DeferredChoice task becomes enabled
   - State: IDLE -> WAITING
   - All listeners registered
   - Timer started (24 hours)
   - Message queue subscribed
   - Signal listener registered

2. Customer responds within 24 hours
   - MessageListener receives "CustomerDecision" message
   - MessageListener state: REGISTERED -> TRIGGERED
   - DeferredChoice state: WAITING -> TRIGGERED
   - triggeredListenerId: "customerResponse"
   - eventData: {decision: "APPROVED", orderId: "123"}

3. Other listeners cancelled
   - TimerListener: REGISTERED -> CANCELLED (timer stopped)
   - SignalListener: REGISTERED -> CANCELLED

4. Event consumed
   - DeferredChoice state: TRIGGERED -> PROCESSING
   - Flow to "ProcessCustomerDecision" activated
   - Token moves to that branch

5. Processing complete
   - DeferredChoice state: PROCESSING -> COMPLETED
   - Path chosen, workflow continues

Alternative scenarios:
- Timeout occurs first -> HandleTimeout branch taken
- Admin cancels first -> HandleCancellation branch taken
```

### Event Types

| Event Type | Listener Class | Use Case |
|------------|---------------|----------|
| Timer | YTimerEventListener | Timeout handling |
| Message | YMessageEventListener | JMS/MQ integration |
| Signal | YSignalEventListener | Cross-process communication |
| Condition | YConditionEventListener | Data-based triggers |
| External | YExternalEventListener | Custom integrations |

### Correlation

```
Events can be correlated using keys:
- Case ID correlation (events for specific case)
- Business key correlation (order ID, customer ID)
- Composite correlation (multiple fields)

Example:
<messageListener correlationKey="${caseId}">
  <!-- Only matches events with this case ID -->
</messageListener>
```

### Event Payload Handling

```xml
<!-- Event data mapped to task input -->
<deferredChoice id="WaitForPayment">
  <listeners>
    <messageListener id="payment" messageType="PaymentReceived">
      <flow target="ProcessPayment">
        <dataMapping>
          <expression>${event.paymentId}</expression>
          <mapsTo>paymentId</mapsTo>
        </dataMapping>
        <dataMapping>
          <expression>${event.amount}</expression>
          <mapsTo>amount</mapsTo>
        </dataMapping>
      </flow>
    </messageListener>
  </listeners>
</deferredChoice>
```

### Cancellation and Cleanup

```
On cancellation:
1. Unregister all active listeners
2. Stop timers
3. Unsubscribe from queues
4. Clean up resources
5. Mark choice as CANCELLED

On completion:
1. Unregister remaining listeners
2. Clean up resources
3. Mark choice as COMPLETED
```

### Interaction with Other Patterns

| Pattern | Interaction | Notes |
|---------|-------------|-------|
| Parallel Split | Precedes | Multiple deferred choices can run in parallel |
| Cancel Region | Full | Cancel releases all listeners |
| Timer | Built-in | Timer is an event type |
| Multi-Instance | Limited | MI with deferred choice needs care |

### Thread Safety

- Event dispatch must be atomic
- State transitions synchronized
- Listener registration thread-safe
- Event queue operations concurrent-safe

### Verification Rules

1. Deferred choice must have at least 2 listeners
2. All flows must reference valid downstream tasks
3. Message types must be defined
4. Signal names must be unique within net
5. Timer delays must be positive
