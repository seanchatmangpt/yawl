#!/bin/bash

# Run from root directory
cd /Users/sac/yawl

# Start the process mining bridge
erl -pa ebin -noshell -s process_mining_bridge_fixed start_link -s init stop

# Run the test
erl -pa ebin -noshell -e '
Pid1 = erlang:whereis(process_mining_bridge_fixed),
io:format("PID_BEFORE: ~p~n", [Pid1]),

MalformedPath = "/tmp/jtbd/input/malformed.json",
Result = process_mining_bridge_fixed:import_ocel_json_path(MalformedPath),
io:format("ERROR_RESULT: ~p~n", [Result]),

Pid2 = erlang:whereis(process_mining_bridge_fixed),
io:format("PID_AFTER: ~p~n", [Pid2]),

ValidPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
case process_mining_bridge_fixed:import_ocel_json_path(ValidPath) of
    {ok, OcelId} ->
        io:format("RECOVERY_OCEL_ID: ~p~n", [OcelId]);
    {error, Reason} ->
        io:format("RECOVERY_FAILED: ~p~n", [Reason])
end,

IsolationGuarantee = (Pid1 =:= Pid2),
io:format("ISOLATION_GUARANTEE HOLDS: ~p~n", [IsolationGuarantee]),

io:format("~n=== JTBD 5 FAULT ISOLATION TEST REPORT ===~n"),
io:format("PID_BEFORE: ~p~n", [Pid1]),
io:format("ERROR_RESULT: ~p~n", [Result]),
io:format("PID_AFTER: ~p~n", [Pid2]),
io:format("ISOLATION_GUARANTEE HOLDS: ~p~n", [IsolationGuarantee]),
case IsolationGuarantee of
    true -> io:format("✓ PASS: PID isolation guaranteed - gen_server did not crash~n");
    false -> io:format("✗ FAIL: PID changed - gen_server was restarted by supervisor~n")
end,

process_mining_bridge_fixed:stop()
' -s init stop

