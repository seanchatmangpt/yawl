# OTP 80/20 Best Practices — YAWL Erlang Bridge

**Pareto Principle Applied**: The 20% of OTP patterns that deliver 80% of the value.

---

## Executive Summary

This document captures the critical OTP (Open Telecom Platform) patterns essential for building fault-tolerant, scalable Erlang/BEAM applications. Focus on these patterns yields maximum reliability with minimum complexity.

---

## The 80/20 OTP Matrix

| Pattern Category | 20% Patterns | 80% Value Delivered |
|------------------|--------------|---------------------|
| **Supervision** | `one_for_one`, intensity 5/60s | Isolated restarts, cascade prevention |
| **gen_server** | `init/1`, `handle_call/3`, `handle_cast/2`, `terminate/2` | Stateful services, synchronous API |
| **Application** | `app` file, `start/2`, `stop/1` | OTP-compliant lifecycle |
| **Mnesia** | Disc copies, transactions, backups | Durable state, crash recovery |
| **NIF** | Load once, resource management, error handling | Native interop without crashes |
| **Distribution** | `node()`, `spawn/4`, monitors | Multi-node fault tolerance |

---

## 1. SUPERVISION TREES (20% → 80% Reliability)

### 1.1 Strategy Selection Matrix

| Strategy | Use Case | When NOT to Use |
|----------|----------|-----------------|
| `one_for_one` | Independent workers | Workers share state |
| `one_for_all` | Dependent workers | Workers are independent |
| `rest_for_one` | Layered dependencies | Workers are peers |

**YAWL Current**: `one_for_one` — Correct for independent gen_servers.

### 1.2 Intensity Configuration

```erlang
%% 80/20 Sweet Spot
#{
    strategy => one_for_one,
    intensity => 5,    % Max 5 restarts
    period => 60       % Within 60 seconds
}
```

**Why 5/60?**:
- Allows temporary failures (network blips)
- Prevents infinite restart loops
- Gives operator time to respond
- Standard OTP convention

### 1.3 Child Spec Template

```erlang
%% 80/20 Child Spec
#{
    id => my_worker,
    start => {my_gen_server, start_link, [Args]},
    restart => permanent,     % Always restart
    shutdown => 5000,         % 5 second graceful shutdown
    type => worker,           % Not a supervisor
    modules => [my_gen_server]
}
```

### 1.4 Supervisor Best Practices

| Practice | Reason | Anti-Pattern |
|----------|--------|--------------|
| Named supervisors | Easy monitoring | Anonymous PIDs |
| `permanent` restart | Automatic recovery | `transient` for critical services |
| `trap_exit` in workers | Clean shutdown | Ignoring EXIT signals |
| Separate supervisors per domain | Blast radius containment | One giant supervisor |

---

## 2. GEN_SERVER PATTERNS (20% → 80% Service Logic)

### 2.1 Essential Callbacks

```erlang
%% Required: init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2

init(Args) ->
    process_flag(trap_exit, true),  %% CRITICAL: Enable cleanup
    {ok, #state{}}.

handle_call(Request, From, State) ->
    {reply, Reply, State}.          %% Synchronous

handle_cast(Msg, State) ->
    {noreply, NewState}.            %% Asynchronous

handle_info(Info, State) ->
    {noreply, State}.               %% Out-of-band messages

terminate(Reason, State) ->
    %% Cleanup: close sockets, free resources, log
    ok.
```

### 2.2 State Management Patterns

| Pattern | Use When | Example |
|---------|----------|---------|
| Record | Fixed structure | `#state{count = 0}` |
| Map | Dynamic keys | `#{<<"key">> => value}` |
| ETS | High read volume | `ets:lookup(Table, Key)` |
| Mnesia | Persistence required | `mnesia:transaction(F)` |

### 2.3 API Design Patterns

```erlang
%% Public API (hides gen_server details)
-export([do_something/1, get_status/0]).

%% Synchronous (blocks caller)
do_something(Arg) ->
    gen_server:call(?MODULE, {do_something, Arg}).

%% Asynchronous (fire-and-forget)
notify_event(Event) ->
    gen_server:cast(?MODULE, {event, Event}).

%% With timeout
get_status() ->
    gen_server:call(?MODULE, get_status, 5000).  %% 5s timeout
```

### 2.4 Error Handling in gen_server

```erlang
handle_call({risky_op, Arg}, _From, State) ->
    try
        Result = risky_operation(Arg),
        {reply, {ok, Result}, State}
    catch
        throw:Reason ->
            {reply, {error, Reason}, State};
        error:Reason:Stacktrace ->
            lager:error("Error: ~p~n~p", [Reason, Stacktrace]),
            {reply, {error, Reason}, State}
    end.
```

---

## 3. APPLICATION STRUCTURE (20% → 80% Lifecycle Management)

### 3.1 Application Callbacks

```erlang
%% app file: process_mining_bridge.app
{application, process_mining_bridge,
 [{description, "YAWL Process Mining Bridge"},
  {vsn, "1.0.0"},
  {modules, [process_mining_bridge_app, yawl_bridge_sup, ...]},
  {registered, [process_mining_bridge, mnesia_registry]},
  {applications, [kernel, stdlib, mnesia, lager]},
  {mod, {process_mining_bridge_app, []}},
  {env, [{backup_interval, 300000}]}
 ]}.

%% Application module
start(_Type, Args) ->
    %% 1. Initialize dependencies (Mnesia)
    case mnesia:create_schema([node()]) of
        ok -> ok;
        {error, {already_exists, _}} -> ok
    end,
    mnesia:start(),

    %% 2. Start supervisor tree
    yawl_bridge_sup:start_link().

stop(_State) ->
    mnesia:stop(),
    ok.
```

### 3.2 Application Environment

```erlang
%% Get config from app env
BackupInterval = application:get_env(
    process_mining_bridge,
    backup_interval,
    300000  %% Default: 5 minutes
).
```

---

## 4. MNESIA PATTERNS (20% → 80% Persistence)

### 4.1 Table Definition Template

```erlang
%% Create table with 80/20 settings
mnesia:create_table(my_table, [
    {attributes, record_info(fields, my_table)},
    {type, set},                    %% set | ordered_set | bag
    {disc_copies, [node()]},        %% Persist to disk
    {index, [secondary_key]},       %% Secondary indices
    {access_mode, read_write}
]).
```

### 4.2 Transaction Patterns

```erlang
%% Read-Write Transaction
write_record(Record) ->
    mnesia:transaction(fun() ->
        mnesia:write(Record)
    end).

%% Dirty Read (faster, no transaction)
read_record(Key) ->
    mnesia:dirty_read(my_table, Key).

%% Fold/Map over table
fold_all(Fun, Acc) ->
    mnesia:transaction(fun() ->
        mnesia:foldl(Fun, Acc, my_table)
    end).
```

### 4.3 Backup Strategy

```erlang
%% Periodic backup (5 min)
erlang:send_after(300000, self(), backup_tables),

handle_info(backup_tables, State) ->
    BackupPath = "/var/backup/mnesia_" ++
                 integer_to_list(erlang:system_time(second)),
    case mnesia:backup(BackupPath) of
        {ok, _} -> lager:info("Backup complete: ~s", [BackupPath]);
        {error, Reason} -> lager:error("Backup failed: ~p", [Reason])
    end,
    erlang:send_after(300000, self(), backup_tables),
    {noreply, State}.
```

### 4.4 Health Monitoring

```erlang
check_mnesia_health() ->
    case mnesia:system_info(is_running) of
        yes ->
            Tables = mnesia:system_info(tables),
            lists:foreach(fun(Table) ->
                case mnesia:table_info(Table, status) of
                    {error, R} -> throw({table_error, Table, R});
                    _ -> ok
                end
            end, Tables),
            healthy;
        no ->
            unhealthy
    end.
```

---

## 5. NIF PATTERNS (20% → 80% Native Integration)

### 5.1 NIF Loading Pattern

```erlang
%% Load NIF once at module load
-define(NIF_LIB, "librust4pm").

init() ->
    case erlang:load_nif(?NIF_LIB, 0) of
        ok -> ok;
        {error, {reload, _}} -> ok;  %% Already loaded
        {error, Reason} ->
            lager:error("NIF load failed: ~p", [Reason]),
            {error, nif_load_failed}
    end.

%% Stubs for when NIF not loaded
my_nif_function(_Arg) ->
    erlang:nif_error({nif_not_loaded, ?MODULE}).
```

### 5.2 Resource Management

```erlang
%% Create resource object for NIF-managed data
-define(RESOURCE_TYPE, my_nif_resource).

init() ->
    %% Create resource type with destructor
    ResourceType = create_resource_type(
        ?RESOURCE_TYPE,
        fun destructor/1  %% Called on GC
    ),
    {ok, #state{resource_type = ResourceType}}.

destructor(Resource) ->
    %% Called when resource is garbage collected
    nif_destroy_resource(Resource),
    ok.
```

### 5.3 NIF Error Handling

```erlang
%% In NIF (Rust):
%% - Never crash the VM
%% - Return error tuples instead of throwing
%% - Use resource objects for managed memory

%% In Erlang:
call_nif(Arg) ->
    case nif_function(Arg) of
        {ok, Result} -> {ok, Result};
        {error, Reason} ->
            lager:warning("NIF error: ~p", [Reason]),
            {error, Reason}
    end.
```

### 5.4 NIF Performance Guidelines

| Practice | Reason | Anti-Pattern |
|----------|--------|--------------|
| Keep NIFs < 1ms | Don't block scheduler | Long-running NIFs |
| Use dirty NIFs | For CPU-intensive work | Blocking scheduler |
| Pre-allocate binaries | Avoid GC pressure | Creating many small binaries |
| Resource objects | Safe memory management | Raw pointers |

---

## 6. DISTRIBUTION PATTERNS (20% → 80% Multi-Node)

### 6.1 Node Connection

```erlang
%% Connect to remote node
connect_node(RemoteNode) ->
    case net_kernel:connect_node(RemoteNode) of
        true ->
            lager:info("Connected to ~p", [RemoteNode]),
            {ok, RemoteNode};
        false ->
            lager:error("Failed to connect to ~p", [RemoteNode]),
            {error, connection_failed}
    end.
```

### 6.2 Remote Process Monitoring

```erlang
%% Monitor remote process
monitor_remote(Pid, Node) ->
    case net_kernel:monitor_nodes(true) of
        ok ->
            %% Monitor node status
            receive
                {nodedown, Node} ->
                    lager:warning("Node ~p down", [Node]),
                    {error, node_down}
            after 5000 ->
                %% Monitor process
                erlang:monitor(process, Pid),
                {ok, monitored}
            end
    end.
```

### 6.3 Distributed Mnesia

```erlang
%% Add node to Mnesia cluster
add_db_node(Node) ->
    case mnesia:change_config(extra_db_nodes, [Node]) of
        {ok, [Node]} ->
            %% Copy tables to new node
            Tables = mnesia:system_info(tables),
            lists:foreach(fun(T) ->
                mnesia:add_table_copy(T, Node, disc_copies)
            end, Tables -- [schema]),
            {ok, Node};
        {ok, []} ->
            {error, already_connected}
    end.
```

---

## 7. OBSERVABILITY PATTERNS (20% → 80% Debugging)

### 7.1 Logging Standards

```erlang
%% Use lager with levels
lager:debug("Detailed: ~p", [Data]).      %% Development
lager:info("Normal: ~p", [Event]).        %% Production info
lager:warning("Attention: ~p", [Issue]).  %% Needs review
lager:error("Problem: ~p", [Error]).      %% Action needed
lager:critical("Crisis: ~p", [Crash]).    %% Immediate action
```

### 7.2 Metrics Collection

```erlang
%% Track key metrics in gen_server state
-record(state, {
    metrics = #{
        operations => 0,
        errors => 0,
        latency_sum => 0,
        start_time => erlang:system_time(millisecond)
    }
}).

%% Update metrics
update_metrics(Operation, Latency, State) ->
    Metrics = State#state.metrics,
    NewMetrics = Metrics#{
        operations => maps:get(operations, Metrics) + 1,
        latency_sum => maps:get(latency_sum, Metrics) + Latency
    },
    State#state{metrics = NewMetrics}.

%% Get metrics
handle_call(get_metrics, _From, State) ->
    Metrics = State#state.metrics,
    AvgLatency = case maps:get(operations, Metrics) of
        0 -> 0;
        Ops -> maps:get(latency_sum, Metrics) / Ops
    end,
    {reply, {ok, Metrics#{avg_latency => AvgLatency}}, State}.
```

### 7.3 Health Check Endpoint

```erlang
%% For load balancers and monitoring
handle_call(health_check, _From, State) ->
    Health = #{
        status => healthy,
        uptime => erlang:system_time(millisecond) -
                  maps:get(start_time, State#state.metrics),
        operations => maps:get(operations, State#state.metrics),
        errors => maps:get(errors, State#state.metrics)
    },
    {reply, {ok, Health}, State}.
```

---

## 8. TESTING PATTERNS (20% → 80% Confidence)

### 8.1 Unit Test Template

```erlang
-module(my_gen_server_tests).
-include_lib("eunit/include/eunit.hrl").

setup() ->
    %% Start Mnesia in memory
    mnesia:start(),
    {ok, Pid} = my_gen_server:start_link(),
    Pid.

cleanup(_Pid) ->
    my_gen_server:stop(),
    mnesia:stop().

basic_test_() ->
    {foreach,
     fun setup/0,
     fun cleanup/1,
     [
        fun start_stop_test/0,
        fun call_test/0,
        fun cast_test/0
     ]}.

start_stop_test() ->
    ?assertMatch({ok, _}, my_gen_server:start_link()).

call_test() ->
    ?assertEqual({ok, result}, my_gen_server:do_something(arg)).

cast_test() ->
    ok = my_gen_server:notify_event(test_event),
    timer:sleep(10),  %% Allow async processing
    ?assertEqual({ok, handled}, my_gen_server:get_last_event()).
```

### 8.2 Property-Based Testing

```erlang
-module(my_gen_server_prop).
-include_lib("proper/include/proper.hrl").

prop_always_valid_state() ->
    ?FORALL(Operations, list(operation()),
        begin
            %% Setup
            {ok, Pid} = my_gen_server:start_link(),

            %% Apply operations
            Results = [apply_op(Pid, Op) || Op <- Operations],

            %% Invariant: server never crashes
            Alive = is_process_alive(Pid),

            %% Cleanup
            my_gen_server:stop(),

            %% Check invariant
            ?assert(Alive),
            true
        end).

operation() ->
    oneof([
        {call, do_something, binary()},
        {cast, notify_event, term()},
        {call, get_state, []}
    ]).
```

### 8.3 Integration Test Pattern

```erlang
integration_test() ->
    %% Start full application
    application:start(process_mining_bridge),

    %% Wait for startup
    timer:sleep(100),

    %% Test real operations
    {ok, OcelId} = process_mining_bridge:import_ocel("test.json"),
    {ok, Dfg} = process_mining_bridge:discover_dfg(OcelId),

    %% Verify
    ?assertMatch({ok, _}, Dfg),

    %% Cleanup
    process_mining_bridge:free_ocel(OcelId),
    application:stop(process_mining_bridge).
```

---

## 9. PERFORMANCE TUNING (20% → 80% Throughput)

### 9.1 ETS vs Mnesia Decision

| Scenario | Choice | Reason |
|----------|--------|--------|
| Read-heavy, no persistence | ETS | 10x faster reads |
| Write-heavy, persistence needed | Mnesia | Transaction safety |
| Multi-node replication | Mnesia | Built-in distribution |
| Simple key-value cache | ETS | Lower overhead |

### 9.2 Process Count Guidelines

| Metric | Target | Warning |
|--------|--------|---------|
| Processes per node | < 100K | > 1M = review design |
| Message queue length | < 100 | > 1000 = bottleneck |
| Reductions per process | < 1000/sec | > 10000 = CPU bound |

### 9.3 Memory Management

```erlang
%% Binary heap size (large binaries)
erlang:system_flag(binary_heap_size, 1024 * 1024).  %% 1MB

%% Process heap size for workers
spawn_opt(fun() -> worker() end, [
    {min_heap_size, 65536},  %% 64KB minimum
    {max_heap_size, 1048576} %% 1MB maximum
]).
```

---

## 10. HOT CODE RELOADING (20% → 80% Zero-Downtime)

### 10.1 Code Change Callback

```erlang
code_change(OldVsn, State, Extra) ->
    %% Handle version migration
    NewState = case OldVsn of
        {1, 0} -> migrate_from_1_0(State);
        {2, 0} -> State;  %% No migration needed
        _ -> State
    end,
    {ok, NewState}.

migrate_from_1_0(State) ->
    %% Example: rename field
    OldValue = State#state.old_field,
    State#state{
        new_field = OldValue,
        old_field = undefined
    }.
```

### 10.2 Upgrade Procedure

```bash
# 1. Compile new code
erlc my_gen_server.erl

# 2. Load new module
l(my_gen_server).

# 3. Suspend processes using sys module
sys:suspend(my_gen_server).

# 4. Load code
code:purge(my_gen_server).
code:load_file(my_gen_server).

# 5. Resume processes
sys:resume(my_gen_server).

# 6. Verify version
sys:get_status(my_gen_server).
```

---

## 11. ANTI-PATTERNS (Avoid These)

### 11.1 Supervision Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| `intensity = 0` | No restarts ever | Use `permanent` or `transient` |
| Single supervisor for all | Blast radius too large | Domain-specific supervisors |
| `shutdown = brutal_kill` | No cleanup time | Use 5000ms default |
| Circular dependencies | Deadlock | Re-architect dependency graph |

### 11.2 gen_server Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| Blocking in `handle_call` | Deadlock risk | Use `handle_cast` or timeout |
| No `trap_exit` | Unclean shutdown | Add `process_flag(trap_exit, true)` |
| Unbounded message queue | Memory leak | Add queue limit with overflow |
| Global state in module | Race conditions | Use gen_server state |

### 11.3 Mnesia Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| `ram_copies` only | Data loss on crash | Use `disc_copies` |
| No transactions | Inconsistent state | Wrap in `mnesia:transaction/1` |
| Single node | No HA | Add replica nodes |
| No backups | No recovery | Schedule periodic backups |

---

## 12. QUICK REFERENCE CARD

### 12.1 Supervisor One-Liner

```erlang
supervisor:start_link({local, ?MODULE}, ?MODULE, []).
```

### 12.2 gen_server One-Liner

```erlang
gen_server:start_link({local, ?MODULE}, ?MODULE, [], []).
```

### 12.3 Application Start

```erlang
application:start(my_app).
```

### 12.4 Mnesia Transaction

```erlang
mnesia:transaction(fun() -> mnesia:write(Record) end).
```

### 12.5 Process Monitor

```erlang
erlang:monitor(process, Pid).
```

### 12.6 Node Connect

```erlang
net_kernel:connect_node('node@host').
```

---

## APPENDIX A: YAWL Bridge Architecture

```
process_mining_bridge_app (application)
└── yawl_bridge_sup (supervisor, one_for_one, 5/60s)
    ├── process_mining_bridge (gen_server)
    │   └── NIF: librust4pm.dylib
    ├── data_modelling_bridge (gen_server)
    │   └── Schema validation, caching
    └── mnesia_registry (gen_server)
        └── Mnesia tables: ocel, slim_ocel, petri_net, conformance
```

---

## APPENDIX B: Recommended Reading

1. **Learn You Some Erlang** - Chapters on OTP supervisors and gen_server
2. **Erlang/OTP Documentation** - `:gen_server`, `:supervisor`, `:application`
3. **Designing for Scalability** - Release handling and hot code reload
4. **Property-Based Testing** - Proper and PropEr libraries

---

**Document Version**: 1.0.0
**Last Updated**: 2026-03-06
**Applies To**: YAWL v6.0.0+, OTP 28.x

