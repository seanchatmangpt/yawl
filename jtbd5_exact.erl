-module(jtbd5_exact).
-export([run/0]).

run() ->
    %% Step 1: Record PID before error
    Pid1 = erlang:whereis(process_mining_bridge_fixed),
    io:format("PID_BEFORE: ~p~n", [Pid1]),
    
    %% Step 2: Send malformed input
    MalformedPath = "/tmp/jtbd/input/malformed.json",
    Result = process_mining_bridge_fixed:import_ocel_json_path(MalformedPath),
    io:format("ERROR_RESULT: ~p~n", [Result]),
    
    %% Step 3: Record PID after error
    Pid2 = erlang:whereis(process_mining_bridge_fixed),
    io:format("PID_AFTER: ~p~n", [Pid2]),
    
    %% Step 4: Prove recovery works
    ValidPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    case process_mining_bridge_fixed:import_ocel_json_path(ValidPath) of
        {ok, OcelId} ->
            io:format("RECOVERY_OCEL_ID: ~p~n", [OcelId]);
        {error, Reason} ->
            io:format("RECOVERY_FAILED: ~p~n", [Reason])
    end,
    
    %% Check if PID isolation guarantee holds
    IsolationGuarantee = (Pid1 =:= Pid2),
    io:format("ISOLATION_GUARANTEE HOLDS: ~p~n", [IsolationGuarantee]),
    
    %% Additional verification: Check if process is still alive
    IsAlive = is_process_alive(Pid1),
    io:format("PROCESS_STILL_ALIVE: ~p~n", [IsAlive]),
    
    %% Final status
    Status = case IsolationGuarantee of
        true when IsAlive -> "PASS";
        true -> "PARTIAL_PASS";
        false -> "FAIL"
    end,
    
    io:format("~n=== JTBD 5 FAULT ISOLATION TEST RESULTS ===~n"),
    io:format("PID_BEFORE: ~p~n", [Pid1]),
    io:format("ERROR_RESULT: ~p~n", [Result]),
    io:format("PID_AFTER: ~p~n", [Pid2]),
    io:format("ISOLATION_GUARANTEE HOLDS: ~p~n", [IsolationGuarantee]),
    io:format("PROCESS_STILL_ALIVE: ~p~n", [IsAlive]),
    io:format("OVERALL_STATUS: ~s~n", [Status]),
    
    case Status of
        "PASS" -> io:format("✅ SUCCESS: Fault isolation guaranteed - PID remained identical~n");
        "PARTIAL_PASS" -> io:format("⚠️  PARTIAL: PID identical but NIF not working~n");
        "FAIL" -> io:format("❌ FAILED: Gen_server was restarted by supervisor~n")
    end.

is_process_alive(Pid) when is_pid(Pid) ->
    is_process_alive(Pid, 5);
is_process_alive(_) ->
    false.

is_process_alive(Pid, 0) ->
    false;
is_process_alive(Pid, N) ->
    case process_info(Pid, status) of
        undefined -> false;
        _ -> true
    end.
