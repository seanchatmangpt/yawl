#!/usr/bin/env escript

%% Script to run JTBD 1 test
-module(run_jtbd_test).

main(_) ->
    %% Start the application
    io:format("Starting application...~n"),
    case application:ensure_all_started(process_mining_bridge) of
        {ok, Apps} ->
            io:format("Applications started: ~p~n", [Apps]),
            timer:sleep(500),

            %% Compile and run the test
            io:format("Compiling test...~n"),
            case compile:file("test/jtbd/jtbd_1_dfg_discovery.erl") of
                {ok, jtbd_1_dfg_discovery} ->
                    io:format("Compilation successful~n"),

                    %% Run the test
                    io:format("Running JTBD 1 test...~n"),
                    case jtbd_1_dfg_discovery:run() of
                        {ok, Results} ->
                            io:format("✓ JTBD 1 test passed!~n"),
                            io:format("Results: ~p~n", [Results]),
                            io:format("~nOutput written to: ~s~n", [maps:get(output, Results)]);
                        {error, Reason} ->
                            io:format("✗ JTBD 1 test failed: ~p~n", [Reason])
                    end;
                {error, Reason} ->
                    io:format("✗ Compilation failed: ~p~n", [Reason])
            end,

            %% Cleanup
            application:stop(process_mining_bridge);
        {error, Reason} ->
            io:format("✗ Failed to start applications: ~p~n", [Reason])
    end.