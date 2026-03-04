%% Test runner for JTBD 5 Fault Isolation Test
%% Can be called from Makefile or bash script

-module(run_test).
-export([main/0]).

main() ->
    io:format("Running JTBD 5: Fault Isolation / Crash Recovery Test~n"),

    %% Start the application if not already started
    case application:ensure_all_started(process_mining_bridge) of
        {ok, _Apps} ->
            timer:sleep(500),
            io:format("Application started successfully~n"),

            %% Run the test
            case jtbd_5_fault_isolation:run() of
                {ok, ProofMap} ->
                    %% Write results to JSON file
                    JsonResult = jsx:encode(ProofMap),
                    case file:write_file("/tmp/jtbd/output/crash-recovery-proof.json", JsonResult) of
                        ok ->
                            io:format("Test completed successfully!~n"),
                            io:format("Results written to /tmp/jtbd/output/crash-recovery-proof.json~n"),
                            print_results(ProofMap),
                            halt(0);
                        {error, Reason} ->
                            io:format("Failed to write results: ~p~n", [Reason]),
                            halt(1)
                    end;
                {error, Reason} ->
                    io:format("Test failed: ~p~n", [Reason]),
                    halt(1)
            end;
        {error, Reason} ->
            io:format("Failed to start application: ~p~n", [Reason]),
            halt(1)
    end.

print_results(ProofMap) ->
    io:format("~n=== TEST RESULTS ===~n"),
    io:format("Before PID: ~p~n", [maps:get(before_pid, ProofMap)]),
    io:format("After PID: ~p~n", [maps:get(after_pid, ProofMap)]),
    io:format("PID unchanged: ~p~n", [maps:get(pid_unchanged, ProofMap)]),
    io:format("Error result: ~p~n", [maps:get(error_result, ProofMap)]),
    io:format("Recovery result: ~p~n", [maps:get(recovery_result, ProofMap)]),
    io:format("Isolation proven: ~p~n", [maps:get(isolation_proven, ProofMap)]),

    case maps:get(isolation_proven, ProofMap) of
        true ->
            io:format("~n✅ PASSED: Fault isolation and recovery working correctly!~n");
        false ->
            io:format("~n❌ FAILED: Fault isolation or recovery not working!~n")
    end.