%% Standalone JTBD 5 Fault Isolation Test
%% Can be run directly with erl to test the fault isolation logic

-module(test_jtbd_5_standalone).
-export([main/0]).

main() ->
    %% Start applications needed for the test
    case application:ensure_all_started(process_mining_bridge) of
        {ok, _Apps} ->
            timer:sleep(500), % Wait for initialization
            io:format("Starting JTBD 5 test...~n"),
            case jtbd_5_fault_isolation:run() of
                {ok, ProofMap} ->
                    io:format("Test completed successfully!~n"),
                    io:format("Proof Map: ~p~n", [ProofMap]),
                    %% Write to JSON
                    JsonOutput = io_lib:format("{
    \"before_pid\": \"~p\",
    \"after_pid\": \"~p\",
    \"pid_unchanged\": ~p,
    \"error_result\": ~p,
    \"recovery_result\": ~p,
    \"isolation_proven\": ~p
}", [
                        maps:get(before_pid, ProofMap),
                        maps:get(after_pid, ProofMap),
                        maps:get(pid_unchanged, ProofMap),
                        maps:get(error_result, ProofMap),
                        maps:get(recovery_result, ProofMap),
                        maps:get(isolation_proven, ProofMap)
                    ]),
                    file:write_file("/tmp/jtbd/output/crash-recovery-proof.json', JsonOutput),
                    io:format("Results written to /tmp/jtbd/output/crash-recovery-proof.json~n"),
                    ok;
                {error, Reason} ->
                    io:format("Test failed: ~p~n", [Reason]),
                    {error, Reason}
            end;
        {error, Reason} ->
            io:format("Failed to start application: ~p~n", [Reason]),
            {error, Reason}
    end.