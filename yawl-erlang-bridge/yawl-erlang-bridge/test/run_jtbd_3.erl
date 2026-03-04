%% Test script for JTBD 3: OC-DECLARE Constraints Discovery
%% Simple script to compile and run the test directly

-module(run_jtbd_3).
-export([main/0]).

main() ->
    %% Initialize paths
    io:format("=== JTBD 3 Test: OC-DECLARE Constraints Discovery ===~n"),

    %% Ensure output directory exists
    ok = filelib:ensure_dir("/tmp/jtbd/output/"),

    %% Run the test
    case jtbd_3_constraints:run() of
        {ok, Result} ->
            io:format("✅ Test completed successfully~n"),
            io:format("Result: ~p~n", [Result]),
            0;  % Exit code 0 for success
        {error, Reason} ->
            io:format("❌ Test failed: ~p~n", [Reason]),
            1  % Exit code 1 for failure
    end.