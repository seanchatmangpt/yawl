%% Process Mining Example Module
%% Demonstrates the full workflow: Import XES → Discover DFG → Discover Alpha+++ → Conformance Check
%%
%% Usage:
%%   pm_example:run_complete().
%%   pm_example:run_complete("/path/to/custom.xes").

-module(pm_example).
-export([
    run_complete/0,
    run_complete/1,
    run_xes_import/1,
    run_dfg_discovery/1,
    run_alpha_discovery/1,
    run_conformance_check/2,
    run_stats/1,
    run_ocel_example/0,
    check_nif_loaded/0,
    discover_activities/1,
    simulate_process/2,
    quick_start/0,
    display_welcome/0
]).

%% Default sample XES file path
%% Default sample XES file path
-define(DEFAULT_XES, "/Users/sac/yawl/yawl-rust4pm/rust4pm/examples/sample_log.xes").
%% Default OCEL file path
-define(DEFAULT_OCEL, "/Users/sac/yawl/yawl-rust4pm/rust4pm/examples/sample_ocel.json").

%% @doc Display welcome message and usage instructions
display_welcome() ->
    io:format("~n~n"),
    io:format("╔════════════════════════════════════════════════════════════╗~n"),
    io:format("║                    YAWL Process Mining Examples             ║~n"),
    io:format("╠════════════════════════════════════════════════════════════╣~n"),
    io:format("║ Available Functions:                                        ║~n"),
    io:format("║  pm_example:run_complete()      - Run complete XES workflow ║~n"),
    io:format("║  pm_example:run_ocel_example()   - Run OCEL example         ║~n"),
    io:format("║  pm_example:check_nif_loaded()   - Check NIF status         ║~n"),
    io:format("║  pm_example:discover_activities(Log) - Extract activities    ║~n"),
    io:format("║  pm_example:simulate_process(Log, N) - Simulate trace of N   ║~n"),
    io:format("╚════════════════════════════════════════════════════════════╝~n"),
    io:format("~n").

%% @doc Run complete workflow with default XES file
run_complete() ->
    display_welcome(),
    run_complete(?DEFAULT_XES).

%% @doc Quick start demonstration
quick_start() ->
    display_welcome(),
    io:format("=== Quick Start Demonstration ===~n~n"),

    case check_nif_loaded() of
        true ->
            io:format("✓ NIF library loaded~n"),

            %% Demonstrate XES operations
            io:format("~n=== 1. XES Event Log Operations ===~n"),
            case run_xes_import(?DEFAULT_XES) of
                {ok, LogHandle} ->
                    io:format("✓ XES file imported~n"),
                    case run_stats(LogHandle) of
                        {ok, Stats} ->
                            io:format("  Traces: ~p, Events: ~p~n",
                                [maps:get(traces, Stats), maps:get(events, Stats)]),
                            case discover_activities(LogHandle) of
                                {ok, Activities} ->
                                    io:format("  Activities found: ~p~n", [length(Activities)]);
                                _ ->
                                    ok
                            end;
                        _ ->
                            ok
                    end,

                    %% Simulate a process
                    case simulate_process(LogHandle, 3) of
                        {ok, _Trace} ->
                            ok;
                        _ ->
                            ok
                    end,

                    process_mining_bridge:free_handle(LogHandle);
                _ ->
                    io:format("✗ XES import failed~n")
            end,

            %% Demonstrate OCEL operations
            io:format("\n=== 2. OCEL Operations ===~n"),
            run_ocel_example();
        false ->
            io:format("✗ NIF library not loaded. Cannot run demonstration.~n")
    end.

%% @doc Run complete workflow with custom XES file
%% Returns {ok, #{dfg => DfgJson, pnml => PnmlXml, conformance => Metrics}}
run_complete(XesPath) ->
    %% Step 0: Check NIF loading
    io:format("~n=== YAWL Process Mining Example ===~n"),
    io:format("XES File: ~s~n~n", [XesPath]),

    case check_nif_loaded() of
        true ->
            io:format("✓ NIF library loaded successfully~n"),
            run_complete_with_nif(XesPath);
        false ->
            io:format("✗ NIF library not loaded~n"),
            io:format("Please ensure the NIF library is built and available at priv/yawl_process_mining~n"),
            {error, nif_not_loaded}
    end.
        {ok, LogResult} ->
            io:format("Log imported successfully~n"),
            io:format("  ~p~n~n", [LogResult]),

            %% Extract LogHandle from result
            LogHandle = case LogResult of
                #{handle := H} -> H;
                _ -> error({unexpected_log_format, LogResult})
            end,

            %% Step 2: Get Log Statistics
            io:format("=== Step 2: Event Log Statistics ===~n"),
            case run_stats(LogHandle) of
                {ok, Stats} ->
                    io:format("  Traces: ~p~n", [maps:get(traces, Stats)]),
                    io:format("  Events: ~p~n", [maps:get(events, Stats)]),
                    io:format("  Activities: ~p~n", [maps:get(activities, Stats)]),
                    io:format("  Avg Events/Trace: ~.2f~n~n", [maps:get(avg_events_per_trace, Stats)]);

                {error, StatsError} ->
                    io:format("  Stats error: ~p~n~n", [StatsError])
            end,

            %% Step 3: Discover DFG
            io:format("=== Step 3: Discovering DFG ===~n"),
            case run_dfg_discovery(LogHandle) of
                {ok, DfgJson} ->
                    io:format("DFG discovered (~p bytes)~n~n", [byte_size(DfgJson)]),

                    %% Step 4: Discover Alpha+++ Petri Net
                    io:format("=== Step 4: Discovering Alpha+++ Petri Net ===~n"),
                    case run_alpha_discovery(LogHandle) of
                        {ok, #{pnml := Pnml, handle := NetHandle}} ->
                            io:format("Petri Net discovered~n"),
                            io:format("  PNML size: ~p bytes~n~n", [byte_size(Pnml)]),

                            %% Step 5: Conformance Checking
                            io:format("=== Step 5: Conformance Checking (Token Replay) ===~n"),
                            case run_conformance_check(LogHandle, NetHandle) of
                                {ok, Conformance} ->
                                    io:format("Conformance metrics:~n"),
                                    io:format("  ~p~n~n", [Conformance]),

                                    %% Success!
                                    io:format("=== SUCCESS ===~n"),
                                    io:format("All process mining operations completed successfully~n~n"),

                                    {ok, #{
                                        dfg => DfgJson,
                                        pnml => Pnml,
                                        conformance => Conformance,
                                        stats => Stats
                                    }};

                                {error, ConformanceError} ->
                                    io:format("Conformance check failed: ~p~n", [ConformanceError]),
                                    {error, {conformance_failed, ConformanceError}}
                            end;

                        {ok, #{pnml := Pnml}} ->
                            %% Alpha succeeded but no net handle for conformance
                            io:format("Petri Net discovered (no handle for conformance)~n"),
                            io:format("  PNML size: ~p bytes~n~n", [byte_size(Pnml)]),
                            io:format("=== SUCCESS (partial) ===~n"),
                            {ok, #{
                                dfg => DfgJson,
                                pnml => Pnml,
                                stats => Stats
                            }};

                        {error, AlphaError} ->
                            io:format("Alpha discovery failed: ~p~n", [AlphaError]),
                            {error, {alpha_failed, AlphaError}}
                    end;

                {error, DfgError} ->
                    io:format("DFG discovery failed: ~p~n", [DfgError]),
                    {error, {dfg_failed, DfgError}}
            end;

        {error, ImportError} ->
            io:format("XES import failed: ~p~n", [ImportError]),
            {error, {import_failed, ImportError}}
    end.

%% @doc Import XES file
run_xes_import(Path) ->
    process_mining_bridge:import_xes(#{path => Path}).

%% @doc Discover DFG from log handle
run_dfg_discovery(LogHandle) ->
    process_mining_bridge:discover_dfg(#{log_handle => LogHandle}).

%% @doc Discover Alpha+++ model from log handle
run_alpha_discovery(LogHandle) ->
    process_mining_bridge:discover_alpha(#{log_handle => LogHandle}).

%% @doc Run token replay conformance check
run_conformance_check(LogHandle, NetHandle) ->
    process_mining_bridge:token_replay(#{log_handle => LogHandle, net_handle => NetHandle}).

%% @doc Get event log statistics
run_stats(LogHandle) ->
    process_mining_bridge:event_log_stats(#{log_handle => LogHandle}).

%% @doc Check if NIF library is loaded
%% Returns true if loaded, false otherwise
check_nif_loaded() ->
    %% Try to call a NIF function that should be implemented
    case process_mining_bridge:import_xes(#{path => "test"}) of
        {error, nif_not_loaded} ->
            false;
        _ ->
            true
    end.

%% @doc Extract and display unique activities from a log
%% Returns {ok, Activities} where Activities is a list of activity names
discover_activities(LogHandle) ->
    case run_stats(LogHandle) of
        {ok, Stats} ->
            Activities = maps:get(activities, Stats),
            io:format("  Unique activities (~p total):~n", [length(Activities)]),
            lists:foreach(fun(Activity) ->
                io:format("    - ~s~n", [Activity])
            end, Activities),
            {ok, Activities};
        {error, Reason} ->
            {error, Reason}
    end.

%% @doc Simulate process execution by generating a sample trace
%% Returns a simulated trace as a list of activities
simulate_process(LogHandle, SimulationLength) when SimulationLength > 0 ->
    case discover_activities(LogHandle) of
        {ok, Activities} ->
            %% Pick random activities for simulation
            SimulatedTrace = lists:foldl(fun(_I, Acc) ->
                RandomActivity = lists:nth(
                    rand:uniform(length(Activities)),
                    Activities
                ),
                [RandomActivity | Acc]
            end, [], lists:seq(1, SimulationLength)),
            ReversedTrace = lists:reverse(SimulatedTrace),
            io:format("  Simulated process trace (length ~p):~n", [SimulationLength]),
            lists:foreach(fun(Activity, Index) ->
                io:format("    ~p. ~s~n", [Index, Activity])
            end, ReversedTrace, lists:seq(1, length(ReversedTrace))),
            {ok, ReversedTrace};
        {error, Reason} ->
            {error, Reason}
    end;
simulate_process(_LogHandle, 0) ->
    {error, invalid_simulation_length}.

%% @doc Run OCEL (Object-Centric Event Log) example
run_ocel_example() ->
    io:format("~n=== YAWL OCEL Process Mining Example ===~n"),
    OcelPath = ?DEFAULT_OCEL,

    io:format("OCEL File: ~s~n~n", [OcelPath]),
    case check_nif_loaded() of
        true ->
            io:format("✓ NIF library loaded~n"),
            %% For now, show OCEL operations that would be available
            io:format("=== Available OCEL Operations ===~n"),
            io:format("1. import_ocel_json - Import OCEL from JSON file~n"),
            io:format("2. import_ocel_xml - Import OCEL from XML file~n"),
            io:format("3. import_ocel_sqlite - Import OCEL from SQLite database~n"),
            io:format("4. discover_oc_dfg - Discover Object-Centric DFG~n"),
            io:format("5. export_ocel_json - Export OCEL to JSON file~n~n"),

            %% Try to import OCEL if file exists
            case filelib:is_file(OcelPath) of
                true ->
                    io:format("=== Attempting OCEL Import ===~n"),
                    case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
                        {ok, OcelHandle} ->
                            io:format("✓ OCEL imported successfully (handle: ~p)~n", [OcelHandle]),
                            %% Would continue with OCEL operations here
                            process_mining_bridge:free_handle(OcelHandle);
                        {error, nif_not_loaded} ->
                            io:format("✗ NIF not loaded - OCEL operations not available~n");
                        {error, Reason} ->
                            io:format("✗ OCEL import failed: ~p~n", [Reason])
                    end;
                false ->
                    io:format("✗ OCEL file not found: ~s~n", [OcelPath]),
                    io:format("Creating sample OCEL JSON for demonstration...~n"),
                    create_sample_ocel_json()
            end;
        false ->
            io:format("✗ NIF library not loaded~n")
    end.

%% @doc Create a sample OCEL JSON file for testing
create_sample_ocel_json() ->
    % Try to use jsx if available, otherwise create a simple JSON
    OcelPath = ?DEFAULT_OCEL,
    SimpleJson =
"{\n" ++
"  \"$schema\": \"http://www.xes-standard.org/ocel.xsd\",\n" ++
"  \"globalEventLog\": [\n" ++
"    {\n" ++
"      \"name\": \"concept:name\",\n" ++
"      \"type\": \"string\"\n" ++
"    }\n" ++
"  ],\n" ++
"  \"globalObjectTypes\": [],\n" ++
"  \"events\": [\n" ++
"    {\n" ++
"      \"id\": \"event1\",\n" ++
"      \"activity\": \"order\",\n" ++
"      \"timestamp\": \"2024-01-01T10:00:00\",\n" ++
"      \"objects\": [],\n" ++
"      \"attributes\": {\n" ++
"        \"concept:name\": \"Order Process Started\"\n" ++
"      }\n" ++
"    }\n" ++
"  ]\n" ++
"}",
    case file:write_file(OcelPath, SimpleJson) of
        ok ->
            io:format("✓ Sample OCEL JSON created: ~s~n", [OcelPath]);
        {error, Reason} ->
            io:format("✗ Failed to create OCEL JSON: ~p~n", [Reason])
    end.
