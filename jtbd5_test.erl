-module(jtbd5_test).
-export([run/0]).

run() ->
    %% Step 1: Record PID before error
    Pid1 = erlang:whereis(process_mining_bridge),
    io:format("PID_BEFORE: ~p~n", [Pid1]),
    
    %% Step 2: Send malformed input
    MalformedPath = "/tmp/jtbd/input/malformed.json",
    Result = process_mining_bridge_fixed:import_ocel_json_path(MalformedPath),
    io:format("ERROR_RESULT: ~p~n", [Result]),
    
    %% Step 3: Record PID after error
    Pid2 = erlang:whereis(process_mining_bridge),
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
    
    %% Final report
    io:format("~n=== JTBD 5 FAULT ISOLATION TEST REPORT ===~n"),
    io:format("PID_BEFORE: ~p~n", [Pid1]),
    io:format("ERROR_RESULT: ~p~n", [Result]),
    io:format("PID_AFTER: ~p~n", [Pid2]),
    io:format("RECOVERY_OCEL_ID: ~p~n", [element(2, process_mining_bridge_fixed:import_ocel_json_path("/tmp/jtbd/input/pi-sprint-ocel.json"))]),
    io:format("ISOLATION_GUARANTEE HOLDS: ~p~n", [IsolationGuarantee]).
