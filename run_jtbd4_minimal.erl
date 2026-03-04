-module(run_jtbd4_minimal).
-export([main/0]).

main() ->
    io:format("=== JTBD 4: LOOP ACCUMULATION ===\n"),

    %% Start the gen server directly
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("Server started successfully\n"),

            %% === ITERATION 1 ===
            io:format('\n=== ITERATION 1 ===\n'),
            Input1 = "/tmp/jtbd/input/pi-sprint-ocel.json",
            case process_mining_bridge:import_ocel_json(Input1) of
                {ok, OcelId1} ->
                    io:format('OCEL_ID_1: ~p\n', [OcelId1]),
                    case process_mining_bridge:discover_alpha(OcelId1) of
                        {ok, PnResult1} ->
                            PnId1 = maps:get(handle, PnResult1),
                            io:format('PETRI_NET_ID_1: ~p\n', [PnId1]),
                            case process_mining_bridge:token_replay(OcelId1, PnId1) of
                                {ok, Result1} ->
                                    Score1 = maps:get(conformance_score, Result1, 0.0),
                                    io:format('ITERATION_1_SCORE: ~f\n', [Score1]);
                                {error, TokenReplayError1} ->
                                    io:format('TOKEN_REPLAY_ERROR_1: ~p\n', [TokenReplayError1])
                            end;
                        {error, DiscoveryError1} ->
                            io:format('DISCOVERY_ERROR_1: ~p\n', [DiscoveryError1])
                    end;
                {error, ImportError1} ->
                    io:format('IMPORT_ERROR_1: ~p\n', [ImportError1])
            end,

            %% === ITERATION 2 ===
            io:format('\n=== ITERATION 2 ===\n'),
            Input2 = "/tmp/jtbd/input/pi-sprint-ocel-v2.json",
            case process_mining_bridge:import_ocel_json(Input2) of
                {ok, OcelId2} ->
                    io:format('OCEL_ID_2: ~p\n', [OcelId2]),
                    case process_mining_bridge:discover_alpha(OcelId2) of
                        {ok, PnResult2} ->
                            PnId2 = maps:get(handle, PnResult2),
                            io:format('PETRI_NET_ID_2: ~p\n', [PnId2]),
                            case process_mining_bridge:token_replay(OcelId2, PnId2) of
                                {ok, Result2} ->
                                    Score2 = maps:get(conformance_score, Result2, 0.0),
                                    io:format('ITERATION_2_SCORE: ~f\n', [Score2]);
                                {error, TokenReplayError2} ->
                                    io:format('TOKEN_REPLAY_ERROR_2: ~p\n', [TokenReplayError2])
                            end;
                        {error, DiscoveryError2} ->
                            io:format('DISCOVERY_ERROR_2: ~p\n', [DiscoveryError2])
                    end;
                {error, ImportError2} ->
                    io:format('IMPORT_ERROR_2: ~p\n', [ImportError2])
            end,

            %% Stop the server
            process_mining_bridge:stop();
        {error, Reason} ->
            io:format('Failed to start server: ~p\n', [Reason])
    end.