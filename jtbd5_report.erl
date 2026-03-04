-module(jtbd5_report).
-export([run/0]).

run() ->
    %% Start the process mining bridge first
    case process_mining_bridge_fixed:start_link() of
        {ok, Pid} ->
            io:format("Process mining bridge started: ~p~n", [Pid]);
        {error, Reason} ->
            io:format("Failed to start: ~p~n", [Reason])
    end,
    
    timer:sleep(100),
    
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
    
    %% Final report
    io:format("~n=== JTBD 5 FAULT ISOLATION TEST REPORT ===~n"),
    io:format("PID_BEFORE: ~p~n", [Pid1]),
    io:format("ERROR_RESULT: ~p~n", [Result]),
    io:format("PID_AFTER: ~p~n", [Pid2]),
    io:format("ISOLATION_GUARANTEE HOLDS: ~p~n", [IsolationGuarantee]),
    case IsolationGuarantee of
        true -> io:format("✓ PASS: PID isolation guaranteed - gen_server did not crash~n");
        false -> io:format("✗ FAIL: PID changed - gen_server was restarted by supervisor~n")
    end,
    
    process_mining_bridge_fixed:stop().
