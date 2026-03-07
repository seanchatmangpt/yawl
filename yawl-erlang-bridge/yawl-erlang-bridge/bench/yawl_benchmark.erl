%% @doc Benchmark Suite for YAWL Bridge
%% Performance measurement and regression detection
-module(yawl_benchmark).
-include_lib("eunit/include/eunit.hrl").

-define(WARMUP_ITERATIONS, 100).
-define(BENCHMARK_ITERATIONS, 10000).
-define(PERCENTILES, [50, 90, 95, 99, 99.9]).

%%====================================================================
%% Benchmark Runner
%%====================================================================

run_all() ->
    io:format("~n========================================~n", []),
    io:format("YAWL Bridge Benchmark Suite~n", []),
    io:format("========================================~n~n", []),

    Benchmarks = [
        {"gen_server call latency", fun bench_genserver_call/0},
        {"Mnesia write latency", fun bench_mnesia_write/0},
        {"Mnesia read latency", fun bench_mnesia_read/0},
        {"Health check latency", fun bench_health_check/0},
        {"Telemetry recording", fun bench_telemetry/0},
        {"Queue operations", fun bench_queue_ops/0},
        {"NIF guard overhead", fun bench_nif_guard/0},
        {"Supervisor operations", fun bench_supervisor/0}
    ],

    Results = lists:map(fun({Name, BenchFn}) ->
        io:format("~n--- ~s ---~n", [Name]),
        Result = run_benchmark(Name, BenchFn),
        print_result(Result),
        Result
    end, Benchmarks),

    io:format("~n========================================~n", []),
    io:format("Benchmark Summary~n", []),
    io:format("========================================~n", []),
    print_summary(Results),

    Results.

%%====================================================================
%% Individual Benchmarks
%%====================================================================

bench_genserver_call() ->
    ensure_mnesia_running(),

    fun() ->
        {ok, _} = mnesia_registry:get_registry_stats()
    end.

bench_mnesia_write() ->
    ensure_mnesia_running(),
    Counter = atomics:new(1, []),

    fun() ->
        Id = atomics:add_get(Counter, 1, 1),
        OcelId = <<"bench_", (integer_to_binary(Id))/binary>>,
        mnesia_registry:register_ocel(OcelId, <<"ptr">>)
    end.

bench_mnesia_read() ->
    ensure_mnesia_running(),

    %% Pre-populate some data
    lists:foreach(fun(I) ->
        mnesia_registry:register_ocel(<<"read_bench_", (integer_to_binary(I))/binary>>, <<"ptr">>)
    end, lists:seq(1, 1000)),

    Counter = atomics:new(1, []),

    fun() ->
        Id = (atomics:add_get(Counter, 1, 1) rem 1000) + 1,
        mnesia_registry:lookup_ocel(<<"read_bench_", (integer_to_binary(Id))/binary>>)
    end.

bench_health_check() ->
    ensure_health_running(),

    fun() ->
        {ok, _} = yawl_bridge_health_v2:check()
    end.

bench_telemetry() ->
    ensure_telemetry_running(),

    fun() ->
        yawl_bridge_telemetry:record_restart(yawl_bridge_sup, test_reason)
    end.

bench_queue_ops() ->
    {ok, _} = yawl_bridge_queue:start_link(bench_queue, [{limit, 10000}]),

    fun() ->
        yawl_bridge_queue:cast(bench_queue, {test, message})
    end.

bench_nif_guard() ->
    ensure_nif_guard_running(),

    fun() ->
        yawl_bridge_nif_guard:call_nif(non_existent, func, [], 100)
    end.

bench_supervisor() ->
    fun() ->
        {ok, _} = yawl_bridge_sup:get_health()
    end.

%%====================================================================
%% Benchmark Framework
%%====================================================================

run_benchmark(Name, BenchFn) ->
    %% Warmup
    lists:foreach(fun(_) ->
        BenchFn()
    end, lists:seq(1, ?WARMUP_ITERATIONS)),

    %% Measure
    Latencies = lists:map(fun(_) ->
        Start = erlang:system_time(nanosecond),
        BenchFn(),
        End = erlang:system_time(nanosecond),
        End - Start
    end, lists:seq(1, ?BENCHMARK_ITERATIONS)),

    %% Calculate statistics
    SortedLatencies = lists:sort(Latencies),
    Min = hd(SortedLatencies),
    Max = lists:last(SortedLatencies),
    Avg = lists:sum(SortedLatencies) / length(SortedLatencies),

    Percentiles = lists:map(fun(P) ->
        Index = trunc(length(SortedLatencies) * P / 100) - 1,
        {P, lists:nth(max(1, Index + 1), SortedLatencies)}
    end, ?PERCENTILES),

    #{
        name => Name,
        iterations => ?BENCHMARK_ITERATIONS,
        min_ns => Min,
        max_ns => Max,
        avg_ns => Avg,
        percentiles => Percentiles,
        ops_per_sec => 1000000000 / Avg
    }.

print_result(Result) ->
    MinNs = maps:get(min_ns, Result),
    MaxNs = maps:get(max_ns, Result),
    AvgNs = maps:get(avg_ns, Result),
    OpsPerSec = maps:get(ops_per_sec, Result),
    Iterations = maps:get(iterations, Result),
    io:format("  Iterations: ~p~n", [Iterations]),
    io:format("  Min: ~p ns (~s us)~n", [MinNs, format_ns_to_us(MinNs)]),
    io:format("  Max: ~p ns (~s ms)~n", [MaxNs, format_ns_to_ms(MaxNs)]),
    io:format("  Avg: ~p ns (~s us)~n", [AvgNs, format_ns_to_us(AvgNs)]),
    io:format("  Throughput: ~s ops/sec~n", [format_ops(OpsPerSec)]),
    io:format("  Percentiles:~n", []),
    lists:foreach(fun({P, Ns}) ->
        io:format("    P~p: ~p ns (~s us)~n", [P, Ns, format_ns_to_us(Ns)])
    end, maps:get(percentiles, Result)).

print_summary(Results) ->
    io:format("~n| Benchmark | Avg (us) | P99 (us) | Ops/sec |~n", []),
    io:format("|-----------|----------|----------|---------|~n", []),
    lists:foreach(fun(R) ->
        Name = maps:get(name, R),
        AvgNs = maps:get(avg_ns, R),
        {_, P99Ns} = lists:keyfind(99, 1, maps:get(percentiles, R)),
        OpsPerSec = maps:get(ops_per_sec, R),
        io:format("| ~-20s | ~8s | ~8s | ~9s |~n", [string:slice(Name, 0, 20), format_ns_to_us(AvgNs), format_ns_to_us(P99Ns), format_ops(OpsPerSec)])
    end, Results).

%%====================================================================
%% EUnit Test Integration
%%====================================================================

benchmark_test_() ->
    {timeout, 120, [
        {"Run all benchmarks", fun() ->
            Results = run_all(),
            %% Verify all benchmarks completed
            ?assertEqual(8, length(Results)),
            %% Verify reasonable performance
            lists:foreach(fun(R) ->
                %% No benchmark should average > 10ms
                ?assert(maps:get(avg_ns, R) < 10000000)
            end, Results)
        end}
    ]}.

%%====================================================================
%% Regression Detection
%%====================================================================

check_regression(CurrentResults, BaselineFile) ->
    case file:consult(BaselineFile) of
        {ok, [Baseline]} ->
            Regressions = find_regressions(CurrentResults, Baseline),
            case Regressions of
                [] ->
                    io:format("~nNo performance regressions detected~n", []),
                    ok;
                _ ->
                    io:format("~n!!! PERFORMANCE REGRESSIONS DETECTED !!!~n", []),
                    lists:foreach(fun({Name, Current, Baseline, Pct}) ->
                        io:format("  ~s: ~s us vs baseline ~s us (+~p%)~n", [Name, format_ns_to_us(Current), format_ns_to_us(Baseline), Pct])
                    end, Regressions),
                    {error, regressions_detected}
            end;
        {error, _} ->
            io:format("No baseline file found, creating new baseline~n", []),
            save_baseline(CurrentResults, BaselineFile),
            ok
    end.

find_regressions(Current, Baseline) ->
    Threshold = 20,  %% 20% regression threshold

    lists:filtermap(fun(C) ->
        Name = maps:get(name, C),
        case lists:keyfind(Name, name, Baseline) of
            false -> false;
            B ->
                CurrentAvg = maps:get(avg_ns, C),
                BaselineAvg = maps:get(avg_ns, B),
                PctChange = (CurrentAvg - BaselineAvg) / BaselineAvg * 100,

                case PctChange > Threshold of
                    true -> {true, {Name, CurrentAvg, BaselineAvg, trunc(PctChange)}};
                    false -> false
                end
        end
    end, Current).

save_baseline(Results, File) ->
    file:write_file(File, io_lib:format("~p.~n", [Results])).

%%====================================================================
%% Helpers
%%====================================================================

ensure_mnesia_running() ->
    case mnesia:system_info(is_running) of
        yes -> ok;
        no -> mnesia:start(), timer:sleep(100)
    end.

ensure_health_running() ->
    case whereis(yawl_bridge_health_v2) of
        undefined -> {ok, _} = yawl_bridge_health_v2:start_link(), timer:sleep(50);
        _ -> ok
    end.

ensure_telemetry_running() ->
    case whereis(yawl_bridge_telemetry) of
        undefined -> {ok, _} = yawl_bridge_telemetry:start_link(), timer:sleep(50);
        _ -> ok
    end.

ensure_nif_guard_running() ->
    case whereis(yawl_bridge_nif_guard) of
        undefined -> {ok, _} = yawl_bridge_nif_guard:start_link(), timer:sleep(50);
        _ -> ok
    end.

%%====================================================================
%% Formatting Helpers
%%====================================================================

%% @doc Format nanoseconds to microseconds with 2 decimal places
format_ns_to_us(Ns) when is_number(Ns) ->
    Us = Ns / 1000,
    io_lib:format("~.2f", [Us]).

%% @doc Format nanoseconds to milliseconds with 3 decimal places
format_ns_to_ms(Ns) when is_number(Ns) ->
    Ms = Ns / 1000000,
    io_lib:format("~.3f", [Ms]).

%% @doc Format operations per second with 2 decimal places
format_ops(OpsPerSec) when is_number(OpsPerSec) ->
    io_lib:format("~.2f", [OpsPerSec]).
