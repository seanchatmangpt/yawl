-module(simple_jtbd5).
-export([run/0]).

run() ->
    %% Try to start the process mining bridge
    case process_mining_bridge_fixed:start_link() of
        {ok, Pid} ->
            io:format("Process started: ~p~n", [Pid]),
            
            %% Check NIF status
            case process_mining_bridge_fixed:get_nif_status() of
                {ok, {nif_status, true}} ->
                    io:format("NIF is loaded successfully~n");
                {ok, {nif_status, false}} ->
                    io:format("NIF is NOT loaded~n")
            end,
            
            %% Get PID before test
            Pid1 = erlang:whereis(process_mining_bridge_fixed),
            io:format("PID_BEFORE: ~p~n", [Pid1]),
            
            %% Try malformed input
            MalformedPath = "/tmp/jtbd/input/malformed.json",
            Result = process_mining_bridge_fixed:import_ocel_json_path(MalformedPath),
            io:format("ERROR_RESULT: ~p~n", [Result]),
            
            %% Get PID after test
            Pid2 = erlang:whereis(process_mining_bridge_fixed),
            io:format("PID_AFTER: ~p~n", [Pid2]),
            
            %% Check if PIDs are the same (fault isolation)
            IsolationGuarantee = (Pid1 =:= Pid2),
            io:format("ISOLATION_GUARANTEE HOLDS: ~p~n", [IsolationGuarantee]),
            
            %% Try to stop
            process_mining_bridge_fixed:stop();
        {error, Reason} ->
            io:format("Failed to start: ~p~n", [Reason])
    end.
