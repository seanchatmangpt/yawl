-module(jtbd_test).
-export([run_test/0]).

run_test() ->
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("Server started successfully\n"),
            case run_iterations() of
                ok -> ok;
                Error -> 
                    process_mining_bridge:stop(),
                    Error
            end;
        {error, Reason} ->
            io:format("Failed to start server: ~p\n", [Reason]),
            {error, Reason}
    end.

run_iterations() ->
    % Iteration 1
    case process_mining_bridge:import_ocel_json("/tmp/jtbd/input/pi-sprint-ocel.json") of
        {ok, OcelId1} ->
            io:format("ITERATION 1 - OCEL imported: ~p\n", [OcelId1]),
            
            case process_mining_bridge:discover_alpha(OcelId1) of
                {ok, PnResult1} ->
                    PnId1 = maps:get(handle, PnResult1),
                    io:format("ITERATION 1 - Petri Net discovered: ~p\n", [PnId1]),
                    
                    case process_mining_bridge:token_replay(OcelId1, PnId1) of
                        {ok, Result1} ->
                            Score1 = maps:get(conformance_score, Result1),
                            io:format("ITERATION_1_SCORE: ~f\n", [Score1]);
                        {error, TokenReplayError} ->
                            io:format("ITERATION 1 - Token replay error: ~p\n", [TokenReplayError]),
                            {error, TokenReplayError}
                    end;
                {error, DiscoveryError} ->
                    io:format("ITERATION 1 - Discovery error: ~p\n", [DiscoveryError]),
                    {error, DiscoveryError}
            end;
        {error, ImportError} ->
            io:format("ITERATION 1 - Import error: ~p\n", [ImportError]),
            {error, ImportError}
    end.
