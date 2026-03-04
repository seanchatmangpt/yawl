%% @doc Benchmark concurrent performance of the NIF bridge
%% Measures throughput and latency under concurrent load
-module(benchmark_concurrency).

-export([start/0, start/1]).
-export([
    concurrent_calls/3,
    concurrent_import_xes/3,
    concurrent_discover_dfg/3
]).

%%====================================================================
%% API Functions
%%====================================================================

%% @doc Start benchmark with default parameters
start() ->
    start(50, 1000, "test_log_medium.xes").

%% @doc Start benchmark with specified parameters
-spec start({integer(), integer()} | integer(), integer(), string()) -> ok.
start(Concurrency, Iterations, LogFile) when is_integer(Concurrency) ->
    start({Concurrency, Iterations}, LogFile);
start({Concurrency, Iterations}, LogFile) ->
    io:format("=== Concurrency Benchmark ===~n"),
    io:format("Workers: ~p, Iterations per worker: ~p~n", [Concurrency, Iterations]),
    io:format("Test file: ~s~n~n", [LogFile]),
    
    % Generate test log if it doesn't exist
    case file:read_file_info(LogFile) of
        {ok, _} ->
            ok;
        {error, enoent} ->
            case generate_test_log(LogFile, 100, 20) of
                ok -> ok;
                {error, Reason} ->
                    io:format("Error generating test log: ~p~n", [Reason])
            end
    end,
    
    % Test 1: Simple concurrent NIF calls
    io:format("1. Concurrent NIF calls (no computation)...~n"),
    {ConcTime, ConcOps} = benchmark_concurrent_calls(Concurrency, Iterations),
    io:format("   Throughput: ~.2f ops/sec (avg latency: ~.3f μs/op)~n", [
        ConcOps / ConcTime,
        (ConcTime * 1000000) / ConcOps
    ]),
    
    % Test 2: Concurrent XES imports
    io:format("2. Concurrent XES imports...~n"),
    {ImportTime, ImportOps} = benchmark_concurrent_imports(Concurrency, Iterations, LogFile),
    io:format("   Throughput: ~.2f imports/sec (avg latency: ~.3f ms/op)~n", [
        ImportOps / (ImportTime / 1000.0),
        ImportTime / ImportOps
    ]),
    
    % Test 3: Concurrent DFG discovery with shared resource
    io:format("3. Concurrent DFG discovery (with resource sharing)...~n"),
    case process_mining_bridge:import_xes(LogFile) of
        {ok, LogHandle} ->
            {DfgTime, DfgOps} = benchmark_concurrent_dfg(Concurrency, Iterations, LogHandle),
            io:format("   Throughput: ~.2f operations/sec (avg latency: ~.3f ms/op)~n", [
                DfgOps / (DfgTime / 1000.0),
                DfgTime / DfgOps
            ]),
            
            % Cleanup
            process_mining_bridge:free_handle(LogHandle);
        {error, Reason} ->
            io:format("Failed to load test log: ~p~n", [Reason])
    end,
    
    % Test 4: Load testing (varying concurrency levels)
    io:format("4. Load testing (varying concurrency levels)...~n"),
    test_load_levels(LogFile, [10, 25, 50, 100, 250]),
    
    % Memory monitoring
    io:format("5. Memory usage...~n"),
    monitor_memory_usage(Concurrency),
    
    io:format("=== Benchmark Complete ===~n"),
    ok.

%%====================================================================
%% Benchmark Functions
%%====================================================================

benchmark_concurrent_calls(Workers, Iterations) ->
    Parent = self(),
    WorkersList = lists:seq(1, Workers),
    
    Start = erlang:monotonic_time(microsecond),
    
    % Spawn worker processes
    Pids = lists:map(fun(WorkerId) ->
        spawn_link(fun() ->
            concurrent_calls_worker(WorkerId, Iterations, Parent)
        end)
    end, WorkersList),
    
    % Wait for all workers to complete
    Results = wait_for_workers(WorkersList, []),
    
    End = erlang:monotonic_time(microsecond),
    
    % Collect all operation times for analysis
    AllTimes = lists:flatten([Times || {_, Times} <- Results]),
    OpCount = length(AllTimes),
    
    % Calculate summary statistics
    MinTime = lists:min(AllTimes),
    MaxTime = lists:max(AllTimes),
    AvgTime = lists:sum(AllTimes) / OpCount,
    
    % Calculate p50, p90, p99 percentiles
    SortedTimes = lists:sort(AllTimes),
    P50 = get_percentile(SortedTimes, 50),
    P90 = get_percentile(SortedTimes, 90),
    P99 = get_percentile(SortedTimes, 99),
    
    io:format("   Min: ~.3f μs, Max: ~.3f μs, Avg: ~.3f μs~n", [MinTime, MaxTime, AvgTime]),
    io:format("   P50: ~.3f μs, P90: ~.3f μs, P99: ~.3f μs~n", [P50, P90, P99]),
    
    % Terminate workers
    lists:foreach(fun(Pid) -> exit(Pid, kill) end, Pids),
    
    {(End - Start) / 1000000.0, OpCount}.

concurrent_calls_worker(WorkerId, Iterations, Parent) ->
    Parent ! {worker_started, self()},
    
    OpTimes = lists:foldl(fun(_, Acc) ->
        Start = erlang:monotonic_time(microsecond),
        
        % Perform empty NIF call
        process_mining_bridge:nop(),
        
        End = erlang:monotonic_time(microsecond),
        [(End - Start) | Acc]
    end, [], lists:seq(1, Iterations)),
    
    Parent ! {worker_done, self(), OpTimes}.

benchmark_concurrent_imports(Workers, Iterations, LogFile) ->
    Parent = self(),
    WorkersList = lists:seq(1, Workers),
    
    Start = erlang:monotonic_time(microsecond),
    
    % Spawn worker processes
    Pids = lists:map(fun(WorkerId) ->
        spawn_link(fun() ->
            concurrent_import_worker(WorkerId, Iterations, LogFile, Parent)
        end)
    end, WorkersList),
    
    % Wait for all workers to complete
    Results = wait_for_workers(WorkersList, []),
    
    End = erlang:monotonic_time(microsecond),
    OpCount = Workers * Iterations,
    
    {(End - Start) / 1000.0, OpCount}.

concurrent_import_worker(WorkerId, Iterations, LogFile, Parent) ->
    Parent ! {worker_started, self()},
    
    lists:foreach(fun(_) ->
        Start = erlang:monotonic_time(microsecond),
        
        % Import XES log
        case process_mining_bridge:import_xes(LogFile) of
            {ok, _} ->
                ok;
            {error, _} ->
                ok
        end,
        
        End = erlang:monotonic_time(microsecond),
        
        Parent ! {import_done, self(), (End - Start) / 1000.0}
    end, lists:seq(1, Iterations)),
    
    Parent ! {worker_done, self(), []}.

benchmark_concurrent_dfg(Workers, Iterations, LogHandle) ->
    Parent = self(),
    WorkersList = lists:seq(1, Workers),
    
    Start = erlang:monotonic_time(microsecond),
    
    % Spawn worker processes
    Pids = lists:map(fun(WorkerId) ->
        spawn_link(fun() ->
            concurrent_dfg_worker(WorkerId, Iterations, LogHandle, Parent)
        end)
    end, WorkersList),
    
    % Wait for all workers to complete
    wait_for_workers(WorkersList, []),
    
    End = erlang:monotonic_time(microsecond),
    OpCount = Workers * Iterations,
    
    {(End - Start) / 1000.0, OpCount}.

concurrent_dfg_worker(WorkerId, Iterations, LogHandle, Parent) ->
    Parent ! {worker_started, self()},
    
    lists:foreach(fun(_) ->
        Start = erlang:monotonic_time(microsecond),
        
        % Discover DFG
        case process_mining_bridge:discover_dfg(LogHandle) of
            {ok, _} ->
                ok;
            {error, _} ->
                ok
        end,
        
        End = erlang:monotonic_time(microsecond),
        
        Parent ! {dfg_done, self(), (End - Start) / 1000.0}
    end, lists:seq(1, Iterations)),
    
    Parent ! {worker_done, self(), []}.

%%====================================================================
%% Helper Functions
%%====================================================================

wait_for_workers([], Acc) ->
    Acc;
wait_for_workers(Expected, Acc) ->
    receive
        {worker_done, Pid, Results} ->
            wait_for_workers(lists:delete(Pid, Expected), [{Pid, Results} | Acc])
    after 30000 ->
        io:format("Warning: Worker timeout~n"),
        Acc
    end.

get_percentile(List, Percentile) when is_list(List) ->
    Length = length(List),
    Index = round((Percentile / 100.0) * (Length - 1)) + 1,
    lists:nth(min(Index, Length), List).

test_load_levels(LogFile, ConcurrencyLevels) ->
    io:format("   Concurrency Level | Throughput (ops/sec) | Avg Latency (ms)~n"),
    io:format("   ---------------- | ------------------- | ---------------~n"),
    
    lists:foreach(fun(Level) ->
        {Time, Ops} = test_concurrency_level(Level, 500, LogFile),
        Throughput = Ops / (Time / 1000.0),
        AvgLatency = Time / Ops,
        
        io:format("   ~15p | ~20.2f | ~15.3f~n", [
            Level,
            Throughput,
            AvgLatency
        ])
    end, ConcurrencyLevels).

test_concurrency_level(Concurrency, Iterations, LogFile) ->
    Parent = self(),
    WorkersList = lists:seq(1, Concurrency),
    
    Start = erlang:monotonic_time(microsecond),
    
    % Spawn workers
    Pids = lists:map(fun(WorkerId) ->
        spawn_link(fun() ->
            concurrent_import_worker(WorkerId, Iterations, LogFile, Parent)
        end)
    end, WorkersList),
    
    % Wait for completion
    wait_for_workers(WorkersList, []),
    
    End = erlang:monotonic_time(microsecond),
    
    % Terminate workers
    lists:foreach(fun(Pid) -> exit(Pid, kill) end, Pids),
    
    {(End - Start) / 1000.0, Concurrency * Iterations}.

monitor_memory_usage(Concurrency) ->
    % Monitor memory usage during operations
    Before = memory_info(),
    
    % Perform some concurrent operations
    Pids = lists:map(fun(_) ->
        spawn_link(fun() ->
            lists:foreach(fun(_) ->
                process_mining_bridge:nop(),
                timer:sleep(10)
            end, lists:seq(1, 100))
        end)
    end, lists:seq(1, Concurrency)),
    
    % Wait a bit for operations to complete
    timer:sleep(1000),
    
    After = memory_info(),
    
    io:format("   Memory before: ~p~n", [Before]),
    io:format("   Memory after: ~p~n", [After]),
    io:format("   Memory delta: ~p~n", [After - Before]),
    
    % Clean up
    lists:foreach(fun(Pid) -> exit(Pid, kill) end, Pids).

memory_info() ->
    erlang:memory(processes) + erlang:memory(processes_used) + erlang:memory(system).

generate_test_log(Path, NumCases, EventsPerCase) ->
    % Simple implementation - in practice, use real XES generation
    LogContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" ++
                 "<log>\n" ++
                 "  <trace>\n" ++
                 "    <string key=\"concept:name\" value=\"test_trace\"/>\n" ++
                 "  </trace>\n" ++
                 "</log>",
    
    case file:write_file(Path, LogContent) of
        ok -> ok;
        {error, Reason} -> {error, Reason}
    end.
