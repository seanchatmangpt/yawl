# YAWL Process Mining Bridge - Usage Examples

This document provides practical examples for using the YAWL Process Mining Bridge.

## Table of Contents
1. [Basic Workflow](#basic-workflow)
2. [Error Handling](#error-handling)
3. [File Processing](#file-processing)
4. [Data Analysis](#data-analysis)
5. [Integration Patterns](#integration-patterns)
6. [Performance Examples](#performance-examples)

## Basic Workflow

### Example 1: Simple XES Processing

```erlang
%% Basic import, process, and export workflow
process_xes_file(Path) ->
    case process_mining_bridge:import_xes(Path) of
        {ok, LogHandle} ->
            try
                % Get basic statistics
                {ok, Stats} = process_mining_bridge:event_log_stats(LogHandle),
                io:format("Statistics: ~p~n", [Stats]),
                
                % Discover DFG
                {ok, DfgJson} = process_mining_bridge:discover_dfg(LogHandle),
                {struct, DfgData} = mochijson2:decode(DfgJson),
                
                % Export results
                process_mining_bridge:export_xes(LogHandle, "/tmp/output.xes"),
                
                {ok, Stats}
            catch
                Error:Reason ->
                    {error, {Error, Reason}}
            after
                process_mining_bridge:free_handle(LogHandle)
            end;
        {error, Reason} ->
            {error, Reason}
    end.
```

### Example 2: Alpha Miner Workflow

```erlang
%% Import XES, discover Petri net, and export PNML
process_alpha_mining(Path) ->
    case process_mining_bridge:import_xes(Path) of
        {ok, LogHandle} ->
            try
                % Discover Alpha++ Petri net
                case process_mining_bridge:discover_alpha(LogHandle) of
                    {ok, #{handle := NetHandle, pnml := Pnml}} ->
                        % Export to PNML file
                        file:write_file("/tmp/net.pnml", Pnml),
                        io:format("Petri net exported~n"),
                        
                        % Clean up
                        process_mining_bridge:free_handle(NetHandle),
                        {ok, net_exported};
                    
                    {error, Reason} ->
                        {error, {alpha_failed, Reason}}
                end
            catch
                Error:Reason ->
                    {error, {Error, Reason}}
            after
                process_mining_bridge:free_handle(LogHandle)
            end;
        {error, Reason} ->
            {error, Reason}
    end.
```

## Error Handling

### Example 1: Comprehensive Error Handling

```erlang
%% Robust error handling with retry logic
safe_import_with_retry(Path, Retries) when Retries > 0 ->
    case process_mining_bridge:import_xes(Path) of
        {ok, Handle} ->
            {ok, Handle};
        {error, Reason} ->
            case Reason of
                nif_not_loaded ->
                    % Try to reload NIF
                    case reload_nif() of
                        ok ->
                            safe_import_with_retry(Path, Retries - 1);
                        Error ->
                            {error, Error}
                    end;
                file_not_found ->
                    % Check file exists and retry
                    case filelib:is_file(Path) of
                        true ->
                            safe_import_with_retry(Path, Retries - 1);
                        false ->
                            {error, {file_not_found, Path}}
                    end;
                _ ->
                    % Other errors - retry
                    timer:sleep(1000),
                    safe_import_with_retry(Path, Retries - 1)
            end
    end;
safe_import_with_retry(_Path, 0) ->
    {error, retry_exhausted}.

%% Helper function to reload NIF
reload_nif() ->
    % Implementation depends on your NIF loading mechanism
    ok.
```

### Example 2: Error Logging and Recovery

```erlang
%% Process with detailed error logging
process_with_logging(Path) ->
    case process_mining_bridge:import_xes(Path) of
        {ok, LogHandle} ->
            try
                Result = do_process_operations(LogHandle),
                log_success(Path, Result),
                Result;
            catch
                Error:Reason ->
                    log_error(Path, Error, Reason),
                    {error, {Error, Reason}}
            after
                process_mining_bridge:free_handle(LogHandle)
            end;
        {error, Reason} ->
            log_error(Path, import, Reason),
            {error, Reason}
    end.

do_process_operations(LogHandle) ->
    % Multiple operations with proper error handling
    case process_mining_bridge:event_log_stats(LogHandle) of
        {ok, Stats} ->
            case process_mining_bridge:discover_dfg(LogHandle) of
                {ok, DfgJson} ->
                    {ok, #{stats => Stats, dfg => DfgJson}};
                {error, Reason} ->
                    {error, {dfg_failed, Reason}}
            end;
        {error, Reason} ->
            {error, {stats_failed, Reason}}
    end.

log_success(Path, Result) ->
    Timestamp = erlang:universaltime(),
    io:format("[~p] Success: ~p -> ~p~n", [Timestamp, Path, Result]).

log_error(Path, Error, Reason) ->
    Timestamp = erlang:universaltime(),
    io:format("[~p] Error: ~p (~p) -> ~p~n", [Timestamp, Path, Error, Reason]).
```

## File Processing

### Example 1: Batch Processing Multiple Files

```erlang
%% Process multiple XES files in parallel
process_batch(Files) ->
    Self = self(),
    Pids = lists:map(fun(File) ->
        spawn_link(fun() ->
            Result = process_xes_file(File),
            Self ! {self(), File, Result}
        end)
    end, Files),
    
    collect_results(Pids, []).

collect_results([], Results) -> Results;
collect_results(Pids, Results) ->
    receive
        {Pid, File, Result} ->
            NewPids = Pids -- [Pid],
            case Result of
                {ok, _} ->
                    collect_results(NewPids, [{File, success} | Results]);
                {error, _} ->
                    collect_results(NewPids, [{File, failed} | Results])
            end
    after 30000 ->
        % Timeout
        lists:foreach(fun(Pid) -> exit(Pid, timeout) end, Pids),
        timeout
    end.
```

### Example 2: File Validation

```erlang
%% Validate XES file before processing
validate_and_process(Path) ->
    % Step 1: Check file exists
    case filelib:is_file(Path) of
        false ->
            {error, {file_not_found, Path}};
        true ->
            % Step 2: Check file size
            case filelib:file_size(Path) of
                Size when Size > 100 * 1024 * 1024 ->
                    io:format("Warning: Large file (~p MB)~n", [Size div 1024 div 1024]);
                _ ->
                    ok
            end,
            
            % Step 3: Check XML validity
            case validate_xes_xml(Path) of
                valid ->
                    process_xes_file(Path);
                {invalid, Reason} ->
                    {error, {invalid_xml, Reason}}
            end
    end.

validate_xes_xml(Path) ->
    try
        % Simple XML validation
        {ok, Content} = file:read_file(Path),
        case re:run(Content, "<xes:version.*</xes:>") of
            nomatch ->
                {invalid, missing_xes_namespace};
            _ ->
                valid
        catch
            _:_ ->
                {invalid, parse_error}
        end
    catch
        Error:Reason ->
            {invalid, {Error, Reason}}
    end.
```

## Data Analysis

### Example 1: Activity Analysis

```erlang
%% Analyze activities and their frequencies
analyze_activities(LogHandle) ->
    case process_mining_bridge:event_log_stats(LogHandle) of
        {ok, Stats} ->
            case process_mining_bridge:discover_dfg(LogHandle) of
                {ok, DfgJson} ->
                    {struct, DfgData} = mochijson2:decode(DfgJson),
                    Activities = extract_activities(DfgData),
                    Frequencies = calculate_frequencies(DfgData),
                    {ok, #{
                        stats => Stats,
                        activities => Activities,
                        frequencies => Frequencies
                    }};
                {error, Reason} ->
                    {error, Reason}
            end;
        {error, Reason} ->
            {error, Reason}
    end.

extract_activities(DfgData) ->
    % Extract all unique activities from DFG
    Nodes = propl:get_value("nodes", DfgData, []),
    Edges = propl:get_value("edges", DfgData, []),
    
    AllActivities = lists:foldl(fun(Node, Acc) ->
        [propl:get_value("id", Node) | Acc]
    end, [], Nodes),
    
    % Also get activities from edges
    EdgeActivities = lists:foldl(fun(Edge, Acc) ->
        [propl:get_value("source", Edge), propl:get_value("target", Edge) | Acc]
    end, [], Edges),
    
    % Get unique activities
    lists:usort(AllActivities ++ EdgeActivities).

calculate_frequencies(DfgData) ->
    % Calculate activity frequencies
    Nodes = propl:get_value("nodes", DfgData, []),
    lists:map(fun(Node) ->
        Id = propl:get_value("id", Node),
        Count = propl:get_value("count", Node, 0),
        {Id, Count}
    end, Nodes).
```

### Example 2: Performance Metrics

```erlang
%% Calculate performance metrics from timestamps
analyze_performance(LogHandle) ->
    case get_log_events(LogHandle) of
        {ok, Events} ->
            Processed = process_events(Events),
            {ok, Processed};
        {error, Reason} ->
            {error, Reason}
    end.

get_log_events(LogHandle) ->
    % This would need to be implemented based on available functions
    % For now, using event_log_stats as a placeholder
    case process_mining_bridge:event_log_stats(LogHandle) of
        {ok, Stats} ->
            % Simulate event processing
            Events = simulate_events(Stats),
            {ok, Events};
        {error, Reason} ->
            {error, Reason}
    end.

simulate_events(Stats) ->
    % Generate mock events for demonstration
    NumEvents = maps:get(events, Stats),
    NumTraces = maps:get(traces, Stats),
    
    lists:map(fun(I) ->
        #{
            trace_id => I rem NumTraces,
            activity => "activity_" ++ integer_to_list(I rem 10),
            timestamp = "2024-01-01T" ++ format_time(I)
        }
    end, lists:seq(1, NumEvents)).

process_events(Events) ->
    % Calculate performance metrics
    Duration = calculate_total_duration(Events),
    AvgDuration = Duration / length(Events),
    
    #{
        total_duration => Duration,
        avg_duration => AvgDuration,
        throughput => length(Events) / (Duration / 3600), % events per hour
        bottlenecks => find_bottlenecks(Events)
    }.

calculate_total_duration(Events) ->
    % Calculate total processing time
    MinTime = lists:min([maps:get(timestamp, E) || E <- Events]),
    MaxTime = lists:max([maps:get(timestamp, E) || E <- Events]),
    % Convert timestamp difference to hours
    time_difference(MinTime, MaxTime).
```

## Integration Patterns

### Example 1: YAWL Integration

```erlang
%% Integrate with YAWL workflow engine
start_workflow_with_mining(WorkflowId) ->
    % Start YAWL workflow
    case yaws:start_workflow(WorkflowId) of
        {ok, YawlHandle} ->
            % Monitor workflow execution
            spawn_link(fun() ->
                monitor_workflow(YawlHandle, WorkflowId)
            end),
            {ok, YawlHandle};
        {error, Reason} ->
            {error, Reason}
    end.

monitor_workflow(YawlHandle, WorkflowId) ->
    % Monitor workflow execution and collect events
    Events = collect_workflow_events(YawlHandle),
    
    % When workflow completes
    case yaws:workflow_status(YawlHandle) of
        completed ->
            % Analyze process execution
            case process_mining_bridge:import_xes(write_events_to_xes(Events)) of
                {ok, LogHandle} ->
                    {ok, Dfg} = process_mining_bridge:discover_dfg(LogHandle),
                    % Store analysis results
                    store_analysis_results(WorkflowId, Dfg),
                    process_mining_bridge:free_handle(LogHandle);
                {error, Reason} ->
                    log_error(WorkflowId, mining_failed, Reason)
            end;
        _ ->
            ok
    end.

write_events_to_xes(Events) ->
    % Write collected events to temporary XES file
    TempPath = "/tmp/workflow_" ++ erlang:pid_to_list(self()) ++ ".xes",
    XesContent = convert_to_xes(Events),
    file:write_file(TempPath, XesContent),
    TempPath.
```

### Example 2: REST API Integration

```erlang
%% Create a simple REST API for process mining
start_mining_server(Port) ->
    mochiweb:start([{port, Port}, {loop, fun loop/1}]).

loop(Req) ->
    Path = mochiweb_request:path(Req),
    Method = mochiweb_request:get(method, Req),
    
    case {Method, Path} of
        {'POST', [<<"import">>]} ->
            handle_import(Req);
        {'POST', [<<"discover">>]} ->
            handle_discover(Req);
        {'GET', [<<"stats">>]} ->
            handle_stats(Req);
        _ ->
            mochiweb_server:respond({404, [], "Not Found"}, Req)
    end.

handle_import(Req) ->
    case mochiweb_request:body(Req) of
        {ok, Body} ->
            Path = binary_to_term(Body), % Assuming path is sent as binary
            case process_mining_bridge:import_xes(Path) of
                {ok, Handle} ->
                    Response = mochijson2:encode({struct, [{handle, binary_to_term(Handle)}]}),
                    mochiweb_server:respond({200, [{"Content-Type", "application/json"}], Response}, Req);
                {error, Reason} ->
                    ErrorResponse = mochijson2:encode({struct, [{error, binary_to_list(term_to_binary(Reason))}]}),
                    mochiweb_server:respond({400, [{"Content-Type", "application/json"}], ErrorResponse}, Req)
            end;
        _ ->
            mochiweb_server:respond({400, [], "Invalid request"}, Req)
    end.
```

## Performance Examples

### Example 1: Streaming Large Files

```erlang
%% Process large XES files in chunks
stream_large_file(Path) ->
    case file:open(Path, [read]) of
        {ok, File} ->
            try
                % Read header
                Header = read_xes_header(File),
                
                % Process events in batches
                process_event_batches(File, Header, [], 0)
            after
                file:close(File)
            end;
        {error, Reason} ->
            {error, Reason}
    end.

process_event_batches(File, Header, Batch, Count) when Count >= 1000 ->
    % Process current batch
    case process_batch(Batch) of
        ok ->
            % Start new batch
            process_event_batches(File, Header, [], 0);
        {error, Reason} ->
            {error, Reason}
    end;
process_event_batches(File, Header, Batch, Count) ->
    case read_next_event(File, Header) of
        {ok, Event} ->
            process_event_batches(File, Header, [Event | Batch], Count + 1);
        eof ->
            % Process remaining events
            case Batch of
                [] -> ok;
                _ -> process_batch(Batch)
            end;
        {error, Reason} ->
            {error, Reason}
    end.

process_batch(Batch) ->
    % Temporary implementation - would need actual batch processing
    timer:sleep(10), % Simulate processing
    ok.
```

### Example 2: Caching and Optimization

```erlang
%% Cache frequently accessed results
-define(CACHE_TIMEOUT, 3600000). % 1 hour

get_cached_or_compute(Key, Fun) ->
    case get_cached_result(Key) of
        undefined ->
            case Fun() of
                {ok, Result} ->
                    cache_result(Key, Result),
                    {ok, Result};
                {error, Reason} ->
                    {error, Reason}
            end;
        {ok, CachedResult} ->
            {ok, CachedResult}
    end.

cache_result(Key, Result) ->
    put(Key, {Result, erlang:system_time(millisecond)}).

get_cached_result(Key) ->
    case get(Key) of
        undefined -> undefined;
        {Result, Timestamp} ->
            case erlang:system_time(millisecond) - Timestamp > ?CACHE_TIMEOUT of
                true ->
                    erase(Key),
                    undefined;
                false ->
                    {ok, Result}
            end
    end.

%% Usage example
get_dfg_with_cache(LogHandle) ->
    Key = {dfg, LogHandle},
    get_cached_or_compute(Key, fun() -> 
        process_mining_bridge:discover_dfg(LogHandle)
    end).
```

## Complete Example: Business Process Analysis

```erlang
%% Analyze business process performance
analyze_business_process(XesPath, KPIs) ->
    case process_mining_bridge:import_xes(XesPath) of
        {ok, LogHandle} ->
            try
                % Get basic statistics
                {ok, Stats} = process_mining_bridge:event_log_stats(LogHandle),
                
                % Discover process model
                {ok, DfgJson} = process_mining_bridge:discover_dfg(LogHandle),
                ProcessModel = parse_dfg(DfgJson),
                
                % Calculate KPIs
                KpiResults = calculate_kpis(LogHandle, KPIs),
                
                % Generate report
                Report = generate_report(Stats, ProcessModel, KpiResults),
                
                {ok, Report}
            catch
                Error:Reason ->
                    {error, {Error, Reason}}
            after
                process_mining_bridge:free_handle(LogHandle)
            end;
        {error, Reason} ->
            {error, Reason}
    end.

calculate_kpis(LogHandle, KPIs) ->
    lists:foldl(fun(KPI, Acc) ->
        KPIValue = calculate_kpi(LogHandle, KPI),
        Acc#{KPI => KPIValue}
    end, #{}, KPIs).

calculate_kpi(LogHandle, cycle_time) ->
    % Calculate average cycle time
    {ok, Events} = get_log_events(LogHandle),
    calculate_average_cycle_time(Events);

calculate_kpi(LogHandle, throughput) ->
    % Calculate process throughput
    {ok, Stats} = process_mining_bridge:event_log_stats(LogHandle),
    calculate_throughput(Stats);

calculate_kpi(LogHandle, conformance) ->
    % Calculate conformance rate
    case process_mining_bridge:discover_alpha(LogHandle) of
        {ok, #{handle := NetHandle}} ->
            {ok, Report} = process_mining_bridge:token_replay(LogHandle, NetHandle),
            calculate_conformance_rate(Report);
        {error, _} ->
            undefined
    end.

generate_report(Stats, ProcessModel, KpiResults) ->
    #{
        basic_stats => Stats,
        process_model => ProcessModel,
        kpis => KpiResults,
        analysis_timestamp => erlang:universaltime(),
        recommendations => generate_recommendations(KpiResults)
    }.
```

These examples demonstrate various patterns for using the YAWL Process Mining Bridge in different scenarios. Each example includes proper error handling and resource management.
