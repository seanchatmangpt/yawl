#!/usr/bin/env escript

%%====================================================================
%% Simple JTBD Test Script
%%====================================================================

main(_) ->
    %% Add paths
    code:add_patha("ebin"),
    code:add_patha("jtbd"),
    code:add_patha("_build/default/lib/*/ebin"),

    io:format("Starting JTBD tests...~n"),

    %% Start the application
    io:format("Starting applications...~n"),
    case mnesia:start() of
        ok ->
            io:format("✓ Mnesia started~n"),
            case application:start(process_mining_bridge) of
                ok ->
                    io:format("✓ Process mining bridge started~n"),
                    run_jtbd_tests();
                {error, Reason} ->
                    io:format("✗ Failed to start process mining bridge: ~p~n", [Reason])
            end;
        {error, Reason} ->
            io:format("✗ Failed to start mnesia: ~p~n", [Reason])
    end.

run_jtbd_tests() ->
    %% Define the JTBD tests to run
    Tests = [
        {jtbd_1_dfg_discovery, "JTBD 1: DFG Discovery"},
        {jtbd_2_conformance, "JTBD 2: Conformance"},
        {jtbd_3_constraints, "JTBD 3: Constraints"},
        {jtbd_4_qlever_accumulation, "JTBD 4: QLever Accumulation"},
        {jtbd_5_fault_isolation, "JTBD 5: Fault Isolation"}
    ],

    %% Run each test
    Results = lists:map(fun({Module, Desc}) ->
        io:format("~n~s~n", [Desc]),
        case catch Module:run() of
            {ok, Result} ->
                io:format("  ✓ PASSED: ~p~n", [Result]),
                {passed, Module, Result};
            {error, Error} ->
                io:format("  ✗ FAILED: ~p~n", [Error]),
                {failed, Module, Error};
            Exception ->
                io:format("  ✗ EXCEPTION: ~p~n", [Exception]),
                {exception, Module, Exception}
        end
    end, Tests),

    %% Print summary
    Passed = length([R || R <- Results, element(1, R) =:= passed]),
    Failed = length([R || R <- Results, element(1, R) =:= failed]),
    Exception = length([R || R <- Results, element(1, R) =:= exception]),
    Total = length(Tests),

    io:format("~n=== SUMMARY ===~n"),
    io:format("Total: ~p, Passed: ~p, Failed: ~p, Exceptions: ~p~n",
              [Total, Passed, Failed, Exception]),

    %% Return exit code based on results
    case Failed of
        0 when Exception =:= 0 ->
            io:format("✓ All tests passed!~n"),
            halt(0);
        _ ->
            io:format("✗ Some tests failed!~n"),
            halt(1)
    end.