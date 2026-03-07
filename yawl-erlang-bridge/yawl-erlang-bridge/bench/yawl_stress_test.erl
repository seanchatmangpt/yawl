%% @doc Stress Test Suite for YAWL Bridge
%% Comprehensive load testing, concurrent access, and failure injection
-module(yawl_stress_test).
-include_lib("eunit/include/eunit.hrl").

-define(CONCURRENT_CLIENTS, 100).
-define(OPERATIONS_PER_CLIENT, 1000).
-define(MAX_LATENCY_MS, 100).

%%====================================================================
%% Test Generators
%%====================================================================

stress_test_() ->
    {timeout, 300, [
        {"Supervisor restart stress", fun supervisor_restart_stress/0},
        {"Concurrent gen_server calls", fun concurrent_genserver_stress/0},
        {"Message queue overflow", fun message_queue_overflow_stress/0},
        {"Mnesia concurrent writes", fun mnesia_concurrent_stress/0},
        {"NIF call timeout stress", fun nif_timeout_stress/0},
        {"Health check under load", fun health_check_under_load/0},
        {"Memory pressure test", fun memory_pressure_test/0},
        {"Long running stability", fun long_running_stability/0}
    ]}.

%%====================================================================
%% Supervisor Stress Tests
%%====================================================================

supervisor_restart_stress() ->
    io:format("~n=== Supervisor Restart Stress Test ===~n"),

    %% Start telemetry if not running
    ensure_telemetry_running(),

    %% Record initial state
    InitialMetrics = yawl_bridge_telemetry:get_metrics(),

    %% Perform rapid restarts
    RestartCount = 50,
    RestartTimes = lists:map(fun(I) ->
        Start = erlang:system_time(microsecond),

        %% Kill and let supervisor restart
        case whereis(process_mining_bridge) of
            undefined -> skip;
            Pid ->
                exit(Pid, kill),
                %% Wait for restart
                wait_for_process(process_mining_bridge, 5000)
        end,

        End = erlang:system_time(microsecond),
        End - Start
    end, lists:seq(1, RestartCount)),

    %% Verify supervisor didn't crash
    ?assertMatch({ok, _}, yawl_bridge_telemetry:get_metrics()),

    %% Analyze restart times
    AvgRestartTime = lists:sum(RestartTimes) / length(RestartTimes),
    MaxRestartTime = lists:max(RestartTimes),
    MinRestartTime = lists:min(RestartTimes),

    io:format("Restart statistics:~n"),
    io:format("  Count: ~p~n", [RestartCount]),
    io:format("  Average: ~s us~n", [format_us(AvgRestartTime)]),
    io:format("  Min: ~s us~n", [format_us(MinRestartTime)]),
    io:format("  Max: ~s us~n", [format_us(MaxRestartTime)]),

    %% Restart should complete within 1 second
    ?assert(MaxRestartTime < 1000000),

    %% Check health status
    {ok, Health} = yawl_bridge_telemetry:get_health_status(),
    io:format("  Health status: ~p~n", [maps:get(status, Health)]),

    ok.

%%====================================================================
%% gen_server Stress Tests
%%====================================================================

concurrent_genserver_stress() ->
    io:format("~n=== Concurrent gen_server Stress Test ===~n"),

    ensure_mnesia_running(),

    %% Spawn concurrent clients
    Clients = ?CONCURRENT_CLIENTS,
    OpsPerClient = ?OPERATIONS_PER_CLIENT,

    StartTime = erlang:system_time(millisecond),

    %% Spawn client processes
    Pids = lists:map(fun(ClientId) ->
        spawn_monitor(fun() ->
            client_worker(ClientId, OpsPerClient)
        end)
    end, lists:seq(1, Clients)),

    %% Collect results
    Results = collect_results(Pids, []),
    EndTime = erlang:system_time(millisecond),

    %% Analyze results
    TotalOps = Clients * OpsPerClient,
    SuccessfulOps = length([R || R <- Results, maps:get(success, R, false) == true]),
    FailedOps = TotalOps - SuccessfulOps,
    Duration = EndTime - StartTime,
    Throughput = TotalOps / (Duration / 1000),

    io:format("Test results:~n"),
    io:format("  Clients: ~p~n", [Clients]),
    io:format("  Operations per client: ~p~n", [OpsPerClient]),
    io:format("  Total operations: ~p~n", [TotalOps]),
    io:format("  Successful: ~p~n", [SuccessfulOps]),
    io:format("  Failed: ~p~n", [FailedOps]),
    io:format("  Duration: ~p ms~n", [Duration]),
    io:format("  Throughput: ~s ops/sec~n", [format_ops(Throughput)]),

    %% Verify success rate > 95%
    SuccessRate = SuccessfulOps / TotalOps * 100,
    io:format("  Success rate: ~p%~n", [SuccessRate]),
    ?assert(SuccessRate > 95.0),

    ok.

client_worker(ClientId, Ops) ->
    Results = lists:map(fun(OpId) ->
        try
            %% Random operation
            Op = rand:uniform(4),
            Result = case Op of
                1 -> mnesia_registry:get_registry_stats();
                2 -> gen_server:call(mnesia_registry, get_table_info, 1000);
                3 -> yawl_bridge_health_v2:liveness();
                4 -> yawl_bridge_health_v2:readiness()
            end,
            #{success => true, op => Op}
        catch
            _:Reason ->
                #{success => false, error => Reason}
        end
    end, lists:seq(1, Ops)),

    SuccessCount = length([R || R <- Results, maps:get(success, R) == true]),
    exit({ok, #{client_id => ClientId, success_count => SuccessCount}}).

collect_results([], Acc) -> Acc;
collect_results([{Pid, Ref} | Rest], Acc) ->
    receive
        {'DOWN', Ref, process, Pid, {ok, Result}} ->
            collect_results(Rest, [Result | Acc]);
        {'DOWN', Ref, process, Pid, Reason} ->
            collect_results(Rest, [#{error => Reason} | Acc])
    after 60000 ->
        collect_results(Rest, [#{error => timeout} | Acc])
    end.

%%====================================================================
%% Message Queue Stress Tests
%%====================================================================

message_queue_overflow_stress() ->
    io:format("~n=== Message Queue Overflow Stress Test ===~n"),

    %% Start a test queue process
    {ok, QueuePid} = yawl_bridge_queue:start_link(test_queue_stress, [
        {limit, 100},
        {strategy, drop_oldest}
    ]),

    %% Flood the queue
    MessageCount = 10000,
    StartTime = erlang:system_time(microsecond),

    lists:foreach(fun(I) ->
        yawl_bridge_queue:cast(test_queue_stress, {test_message, I})
    end, lists:seq(1, MessageCount)),

    EndTime = erlang:system_time(microsecond),

    %% Get queue stats
    {ok, Stats} = yawl_bridge_queue:get_stats(test_queue_stress),

    io:format("Queue statistics:~n"),
    io:format("  Messages sent: ~p~n", [MessageCount]),
    io:format("  Current length: ~p~n", [maps:get(current_length, Stats)]),
    io:format("  Limit: ~p~n", [maps:get(limit, Stats)]),
    io:format("  Dropped: ~p~n", [maps:get(dropped, Stats)]),
    io:format("  Time: ~s us~n", [format_us(EndTime - StartTime)]),

    %% Verify queue didn't exceed limit
    ?assert(maps:get(current_length, Stats) =< maps:get(limit, Stats)),

    %% Cleanup
    exit(QueuePid, normal),

    ok.

%%====================================================================
%% Mnesia Stress Tests
%%====================================================================

mnesia_concurrent_stress() ->
    io:format("~n=== Mnesia Concurrent Write Stress Test ===~n"),

    ensure_mnesia_running(),

    Writers = 50,
    WritesPerWriter = 200,
    TotalWrites = Writers * WritesPerWriter,

    StartTime = erlang:system_time(millisecond),

    %% Spawn concurrent writers
    Pids = lists:map(fun(WriterId) ->
        spawn_monitor(fun() ->
            mnesia_writer(WriterId, WritesPerWriter)
        end)
    end, lists:seq(1, Writers)),

    %% Wait for completion
    Results = collect_mnesia_results(Pids, []),

    EndTime = erlang:system_time(millisecond),

    %% Analyze
    SuccessCount = lists:sum([S || #{success_count := S} <- Results]),
    Duration = EndTime - StartTime,
    Throughput = TotalWrites / (Duration / 1000),

    io:format("Mnesia write results:~n"),
    io:format("  Writers: ~p~n", [Writers]),
    io:format("  Writes per writer: ~p~n", [WritesPerWriter]),
    io:format("  Total writes: ~p~n", [TotalWrites]),
    io:format("  Successful: ~p~n", [SuccessCount]),
    io:format("  Duration: ~p ms~n", [Duration]),
    io:format("  Throughput: ~s writes/sec~n", [format_ops(Throughput)]),

    %% Expect > 1000 writes/sec
    ?assert(Throughput > 1000),

    ok.

mnesia_writer(WriterId, Count) ->
    SuccessCount = lists:foldl(fun(I, Acc) ->
        OcelId = <<"stress_test_", (integer_to_binary(WriterId))/binary, "_", (integer_to_binary(I))/binary>>,
        RustPtr = <<"dummy_pointer">>,
        try
            mnesia_registry:register_ocel(OcelId, RustPtr),
            Acc + 1
        catch
            _:_ -> Acc
        end
    end, 0, lists:seq(1, Count)),

    exit({ok, #{writer_id => WriterId, success_count => SuccessCount}}).

collect_mnesia_results([], Acc) -> Acc;
collect_mnesia_results([{Pid, Ref} | Rest], Acc) ->
    receive
        {'DOWN', Ref, process, Pid, {ok, Result}} ->
            collect_mnesia_results(Rest, [Result | Acc]);
        {'DOWN', Ref, process, Pid, _Reason} ->
            collect_mnesia_results(Rest, Acc)
    after 60000 ->
        collect_mnesia_results(Rest, Acc)
    end.

%%====================================================================
%% NIF Stress Tests
%%====================================================================

nif_timeout_stress() ->
    io:format("~n=== NIF Timeout Stress Test ===~n"),

    %% Test NIF guard with various timeouts
    Timeouts = [100, 500, 1000, 5000],
    Iterations = 100,

    Results = lists:map(fun(Timeout) ->
        {Time, SuccessCount} = lists:foldl(fun(_, {TotalTime, Succ}) ->
            Start = erlang:system_time(microsecond),
            _ = yawl_bridge_nif_guard:call_nif(
                non_existent_module,
                non_existent_function,
                [],
                Timeout
            ),
            End = erlang:system_time(microsecond),
            {TotalTime + (End - Start), Succ}
        end, {0, 0}, lists:seq(1, Iterations)),

        AvgTime = Time / Iterations,
        io:format("  Timeout ~p ms: avg response ~s us~n", [Timeout, format_us(AvgTime)]),

        #{timeout => Timeout, avg_time_us => AvgTime}
    end, Timeouts),

    %% Verify timeout handling doesn't block
    lists:foreach(fun(#{avg_time_us := AvgTime, timeout := Timeout}) ->
        %% Response should be much faster than timeout (NIF not loaded = fast fail)
        ?assert(AvgTime < Timeout * 1000)
    end, Results),

    ok.

%%====================================================================
%% Health Check Stress Tests
%%====================================================================

health_check_under_load() ->
    io:format("~n=== Health Check Under Load Test ===~n"),

    %% Start background load
    LoadPid = spawn(fun() ->
        lists:foreach(fun(_) ->
            catch mnesia_registry:get_registry_stats(),
            timer:sleep(1)
        end, lists:seq(1, 10000))
    end),

    %% Perform health checks concurrently
    HealthChecks = 1000,
    Checkers = 50,

    StartTime = erlang:system_time(millisecond),

    Pids = lists:map(fun(_) ->
        spawn_monitor(fun() ->
            ChecksPerProcess = HealthChecks div Checkers,
            Results = lists:map(fun(_) ->
                try
                    {ok, _} = yawl_bridge_health_v2:check(),
                    true
                catch
                    _:_ -> false
                end
            end, lists:seq(1, ChecksPerProcess)),
            exit({ok, length([R || R <- Results, R])})
        end)
    end, lists:seq(1, Checkers)),

    SuccessCounts = collect_health_results(Pids, []),
    EndTime = erlang:system_time(millisecond),

    TotalSuccessful = lists:sum(SuccessCounts),
    Duration = EndTime - StartTime,
    Throughput = TotalSuccessful / (Duration / 1000),

    io:format("Health check results:~n"),
    io:format("  Total checks: ~p~n", [HealthChecks]),
    io:format("  Successful: ~p~n", [TotalSuccessful]),
    io:format("  Duration: ~p ms~n", [Duration]),
    io:format("  Throughput: ~s checks/sec~n", [format_ops(Throughput)]),

    %% Expect > 500 health checks/sec
    ?assert(Throughput > 500),

    exit(LoadPid, normal),

    ok.

collect_health_results([], Acc) -> Acc;
collect_health_results([{Pid, Ref} | Rest], Acc) ->
    receive
        {'DOWN', Ref, process, Pid, {ok, Count}} ->
            collect_health_results(Rest, [Count | Acc]);
        {'DOWN', Ref, process, Pid, _} ->
            collect_health_results(Rest, [0 | Acc])
    after 30000 ->
        collect_health_results(Rest, [0 | Acc])
    end.

%%====================================================================
%% Memory Stress Tests
%%====================================================================

memory_pressure_test() ->
    io:format("~n=== Memory Pressure Test ===~n"),

    InitialMemory = erlang:memory(total),
    io:format("  Initial memory: ~s MB~n", [format_bytes_to_mb(InitialMemory)]),

    %% Create memory pressure through Mnesia writes
    LargeData = binary:copy(<<"X">>, 10000),  %% 10KB per record

    lists:foreach(fun(I) ->
        OcelId = <<"memory_test_", (integer_to_binary(I))/binary>>,
        mnesia_registry:register_ocel(OcelId, LargeData)
    end, lists:seq(1, 1000)),

    PeakMemory = erlang:memory(total),
    io:format("  Peak memory: ~s MB~n", [format_bytes_to_mb(PeakMemory)]),
    io:format("  Memory increase: ~s MB~n", [format_bytes_to_mb(PeakMemory - InitialMemory)]),

    %% Force GC and cleanup
    mnesia_registry:clear_stale_entries(),
    erlang:garbage_collect(),

    FinalMemory = erlang:memory(total),
    io:format("  Final memory: ~s MB~n", [format_bytes_to_mb(FinalMemory)]),

    %% Memory should not grow unbounded
    MemoryGrowth = (PeakMemory - InitialMemory) / InitialMemory * 100,
    io:format("  Memory growth: ~p%~n", [MemoryGrowth]),
    ?assert(MemoryGrowth < 200),  %% Less than 200% growth

    ok.

%%====================================================================
%% Stability Tests
%%====================================================================

long_running_stability() ->
    io:format("~n=== Long Running Stability Test ===~n"),

    DurationSec = 10,  %% Run for 10 seconds
    EndTime = erlang:system_time(millisecond) + (DurationSec * 1000),

    {OkCount, ErrorCount, Latencies} = run_stability_loop(EndTime, 0, 0, []),

    AvgLatency = case Latencies of
        [] -> 0;
        _ -> lists:sum(Latencies) / length(Latencies)
    end,

    io:format("Stability test results:~n"),
    io:format("  Duration: ~p seconds~n", [DurationSec]),
    io:format("  Successful ops: ~p~n", [OkCount]),
    io:format("  Failed ops: ~p~n", [ErrorCount]),
    io:format("  Average latency: ~s us~n", [format_us(AvgLatency)]),
    io:format("  Operations/sec: ~s~n", [format_ops((OkCount + ErrorCount) / DurationSec)]),

    %% No more than 1% errors
    ErrorRate = ErrorCount / (OkCount + ErrorCount) * 100,
    io:format("  Error rate: ~p%~n", [ErrorRate]),
    ?assert(ErrorRate < 1.0),

    ok.

run_stability_loop(EndTime, Ok, Err, Latencies) ->
    case erlang:system_time(millisecond) >= EndTime of
        true ->
            {Ok, Err, Latencies};
        false ->
            Start = erlang:system_time(microsecond),
            try
                {ok, _} = yawl_bridge_health_v2:check(),
                End = erlang:system_time(microsecond),
                run_stability_loop(EndTime, Ok + 1, Err, [End - Start | Latencies])
            catch
                _:_ ->
                    run_stability_loop(EndTime, Ok, Err + 1, Latencies)
            end
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

ensure_telemetry_running() ->
    case whereis(yawl_bridge_telemetry) of
        undefined ->
            {ok, _} = yawl_bridge_telemetry:start_link(),
            timer:sleep(100);
        _ ->
            ok
    end.

ensure_mnesia_running() ->
    case mnesia:system_info(is_running) of
        yes -> ok;
        no ->
            mnesia:start(),
            timer:sleep(500)
    end.

wait_for_process(Name, Timeout) ->
    wait_for_process(Name, Timeout, erlang:system_time(millisecond)).

wait_for_process(Name, Timeout, StartTime) ->
    case whereis(Name) of
        undefined ->
            case erlang:system_time(millisecond) - StartTime > Timeout of
                true -> {error, timeout};
                false ->
                    timer:sleep(10),
                    wait_for_process(Name, Timeout, StartTime)
            end;
        Pid -> {ok, Pid}
    end.

%%====================================================================
%% Formatting Helpers
%%====================================================================

%% @doc Format microseconds with 2 decimal places
format_us(Us) when is_number(Us) ->
    io_lib:format("~.2f", [Us]).

%% @doc Format operations per second with 2 decimal places
format_ops(OpsPerSec) when is_number(OpsPerSec) ->
    io_lib:format("~.2f", [OpsPerSec]).

%% @doc Format bytes to megabytes with 2 decimal places
format_bytes_to_mb(Bytes) when is_number(Bytes) ->
    Mb = Bytes / (1024 * 1024),
    io_lib:format("~.2f", [Mb]).
