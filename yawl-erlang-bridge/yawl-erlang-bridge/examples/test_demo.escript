#!/usr/bin/env escript
%% Simple demonstration script for YAWL Process Mining Examples
%% This script shows how the examples would work when properly set up

main(_) ->
    io:format("~n=== YAWL Process Mining Examples Demo ===~n~n"),

    %% Check if we're in the right directory
    case file:read_file_info("pm_example.erl") of
        {ok, _} ->
            io:format("✓ Found pm_example.erl~n");
        {error, _} ->
            io:format("✗ pm_example.erl not found~n"),
            halt(1)
    end,

    %% Show example usage patterns
    io:format("~n=== Example Usage Patterns ===~n"),
    io:format("1. pm_example:quick_start()~n"),
    io:format("   - Quick demonstration of all features~n"),
    io:format("   - Shows XES and OCEL operations~n~n"),

    io:format("2. pm_example:run_complete()~n"),
    io:format("   - Complete XES workflow~n"),
    io:format("   - Import → Stats → DFG → Alpha → Conformance~n~n"),

    io:format("3. pm_example:run_complete('/path/to/custom.xes')~n"),
    io:format("   - Process custom XES files~n~n"),

    io:format("4. pm_example:discover_activities(LogHandle)~n"),
    io:format("   - Extract unique activities from event log~n~n"),

    io:format("5. pm_example:simulate_process(LogHandle, N)~n"),
    io:format("   - Generate simulated trace of length N~n~n"),

    io:format("=== Setup Instructions ===~n"),
    io:format("1. Ensure Rust and Erlang are installed~n"),
    io:format("2. Run: ./build_nif.sh~n"),
    io:format("3. Run: make build~n"),
    io:format("4. Run: make test~n~n"),

    io:format("=== Expected Workflow ===~n"),
    io:format("1. Import XES file → LogHandle~n"),
    io:format("2. Get statistics (traces, events, activities)~n"),
    io:format("3. Discover Directly-Follows Graph (DFG)~n"),
    io:format("4. Discover Alpha+++ Petri Net model~n"),
    io:format("5. Run conformance checking with token replay~n"),
    io:format("6. (Optional) Process OCEL object-centric logs~n~n"),

    io:format("=== Sample Output Format ===~n"),
    io:format("{ok, #{~n"),
    io:format("  dfg => DFG_JSON,~n"),
    io:format("  pnml => PNML_XML,~n"),
    io:format("  conformance => #{fitness => 0.95, precision => 0.87},~n"),
    io:format("  stats => #{traces => 100, events => 1250}~n"),
    io:format("}}~n~n"),

    io:format("Demo completed. Run './build_nif.sh' to build the actual implementation.~n"),
    halt(0).