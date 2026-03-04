%% @doc Benchmark process mining operations using the NIF bridge
%% Measures end-to-end performance of typical PM operations
-module(benchmark_pm_operations).

-export([start/0, start/1]).
-export([
    import_xes_benchmark/1,
    import_ocel_json_benchmark/1,
    discover_dfg_benchmark/1,
    discover_alpha_benchmark/1,
    event_log_stats_benchmark/1
]).

-include_lib("kernel/include/file.hrl").

%%====================================================================
%% API Functions
%%====================================================================

%% @doc Start benchmark with default datasets
start() ->
    start(default).

%% @doc Start benchmark with specified dataset size
-spec start(atom() | {integer(), integer()}) -> ok.
start(default) ->
    % Use medium-sized dataset
    LogFile = "/tmp/test_log_medium.xes",
    case generate_test_log(LogFile, 100, 50) of
        ok ->
            start_from_file(LogFile);
        {error, Reason} ->
            io:format("Error generating test log: ~p~n", [Reason])
    end;
start({NumCases, EventsPerCase}) ->
    LogFile = "/tmp/test_log_custom.xes",
    case generate_test_log(LogFile, NumCases, EventsPerCase) of
        ok ->
            start_from_file(LogFile);
        {error, Reason} ->
            io:format("Error generating test log: ~p~n", [Reason])
    end.

start_from_file(LogFile) ->
    io:format("=== Process Mining Operations Benchmark ===~n"),
    io:format("Test log: ~s~n", [LogFile]),
    
    % Get log stats first
    case event_log_stats_benchmark(LogFile) of
        {ok, Stats} ->
            io:format("Log stats: ~p traces, ~p events~n", [
                maps:get(traces, Stats),
                maps:get(events, Stats)
            ]),
            
            % Benchmark operations
            io:format("1. XES Import benchmark...~n"),
            {ImportTime1, ImportOps} = import_xes_benchmark(LogFile),
            io:format("   XES Import: ~.3f ms, ~.2f events/sec~n", [
                ImportTime1,
                (maps:get(events, Stats) / ImportTime1) * 1000
            ]),
            
            % Create handle for other operations
            case process_mining_bridge:import_xes(LogFile) of
                {ok, LogHandle} ->
                    io:format("2. DFG Discovery benchmark...~n"),
                    {DfgTime, DfgOps} = discover_dfg_benchmark(LogHandle),
                    io:format("   DFG Discovery: ~.3f ms, ~.2f events/sec~n", [
                        DfgTime,
                        (maps:get(events, Stats) / DfgTime) * 1000
                    ]),
                    
                    io:format("3. Alpha Miner benchmark...~n"),
                    {AlphaTime, AlphaOps} = discover_alpha_benchmark(LogHandle),
                    io:format("   Alpha Miner: ~.3f ms, ~.2f events/sec~n", [
                        AlphaTime,
                        (maps:get(events, Stats) / AlphaTime) * 1000
                    ]),
                    
                    io:format("4. Event Log Stats benchmark...~n"),
                    {StatsTime, StatsOps} = event_log_stats_benchmark(LogHandle),
                    io:format("   Event Log Stats: ~.3f ms~n", [StatsTime]),
                    
                    % Cleanup
                    process_mining_bridge:free_handle(LogHandle),
                    io:format("5. XES Export benchmark...~n"),
                    ExportFile = "/tmp/exported_log.xes",
                    {ExportTime, ExportOps} = export_xes_benchmark(LogHandle, ExportFile),
                    io:format("   XES Export: ~.3f ms, ~.2f events/sec~n", [
                        ExportTime,
                        (maps:get(events, Stats) / ExportTime) * 1000
                    ]),
                    
                    % Clean up exported file
                    file:delete(ExportFile),
                    
                    io:format("=== Benchmark Complete ===~n");
                {error, Reason} ->
                    io:format("Failed to import XES: ~p~n", [Reason])
            end;
        {error, Reason} ->
            io:format("Failed to get log stats: ~p~n", [Reason])
    end.

%%====================================================================
%% NIF Functions (implemented in Rust)
%%====================================================================

import_xes_benchmark(Path) ->
    timer:tc(fun() -> process_mining_bridge:import_xes(Path) end).

import_ocel_json_benchmark(Path) ->
    timer:tc(fun() -> process_mining_bridge:import_ocel_json(Path) end).

discover_dfg_benchmark(LogHandle) ->
    timer:tc(fun() -> process_mining_bridge:discover_dfg(LogHandle) end).

discover_alpha_benchmark(LogHandle) ->
    timer:tc(fun() -> process_mining_bridge:discover_alpha(LogHandle) end).

event_log_stats_benchmark(LogHandle) ->
    timer:tc(fun() -> process_mining_bridge:event_log_stats(LogHandle) end).

export_xes_benchmark(LogHandle, Path) ->
    timer:tc(fun() -> process_mining_bridge:export_xes(LogHandle, Path) end).

%%====================================================================
%% Test Data Generation
%%====================================================================

generate_test_log(Path, NumCases, EventsPerCase) ->
    % Create a simple XES log
    LogContent = generate_xes_content(NumCases, EventsPerCase),
    
    case file:write_file(Path, LogContent) of
        ok ->
            ok;
        {error, Reason} ->
            {error, Reason}
    end.

generate_xes_content(NumCases, EventsPerCase) ->
    % Generate XES header
    Header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" ++
             "<log>\n" ++
             "  <global trace_attributes=\"concept:name\">\n" ++
             "    <string key=\"concept:name\" value=\"DefaultTraceClassifier\"/>\n" ++
             "  </global>\n" ++
             "  <global event_attributes=\"concept:name\">\n" ++
             "    <string key=\"concept:name\" value=\"DefaultEventClassifier\"/>\n" ++
             "  </global>\n",
    
    % Generate traces
    TraceContent = generate_traces_content(NumCases, EventsPerCase),
    
    % XES footer
    Footer = "</log>",
    
    Header ++ TraceContent ++ Footer.

generate_traces_content(0, _EventsPerCase) ->
    "";
generate_traces_content(NumCases, EventsPerCase) ->
    TraceId = integer_to_list(NumCases),
    TraceContent = generate_single_trace(TraceId, EventsPerCase),
    TraceContent ++ generate_traces_content(NumCases - 1, EventsPerCase).

generate_single_trace(TraceId, 0) ->
    io_lib:format("  <trace>\n    <string key=\"concept:name\" value=\"trace_~s\"/>\n  </trace>\n", [TraceId]);
generate_single_trace(TraceId, EventNum) ->
    EventContent = generate_single_event(EventNum),
    io_lib:format("  <trace>\n    <string key=\"concept:name\" value=\"trace_~s\"/>\n~s</trace>\n", [
        TraceId,
        EventContent,
        generate_single_trace(TraceId, EventNum - 1)
    ]).

generate_single_event(EventNum) ->
    io_lib:format("    <event>\n      <string key=\"concept:name\" value=\"activity_~B\"/>\n      <date key=\"time:timestamp\" value=\"2023-01-01T~2.0.0B:~2.0.0B:~2.0.0B\"/>\n    </event>\n", [
        EventNum rem 5 + 1,
        EventNum,
        EventNum,
        EventNum
    ]).
