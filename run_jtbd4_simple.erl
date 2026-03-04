-module(run_jtbd4_simple).
-export([main/0]).

main() ->
    %% Start the gen server directly
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("Server started successfully\n"),

            %% Initialize storage for scores
            put(scores, []),

            %% === ITERATION 1 ===
            io:format('\n=== ITERATION 1 ===\n'),
            Input1 = "/tmp/jtbd/input/pi-sprint-ocel.json",
            Result1 = process_mining_bridge:import_ocel_json(Input1),
            case Result1 of
                {ok, OcelId1} ->
                    io:format('OCEL_ID_1: ~p\n', [OcelId1]),
                    Petri1 = process_mining_bridge:discover_alpha(OcelId1),
                    case Petri1 of
                        {ok, PnResult1} ->
                            PnId1 = maps:get(handle, PnResult1),
                            io:format('PETRI_NET_ID_1: ~p\n', [PnId1]),
                            Replay1 = process_mining_bridge:token_replay(OcelId1, PnId1),
                            case Replay1 of
                                {ok, Result1Data} ->
                                    Score1 = maps:get(conformance_score, Result1Data,
                                                 maps:get(fitness, Result1Data, 0.5)),
                                    io:format('ITERATION_1_SCORE: ~f\n', [Score1]),
                                    %% Store score
                                    put(scores, [Score1 | get(scores)]);
                                {error, ReplayError1} ->
                                    io:format('TOKEN_REPLAY_ERROR_1: ~p\n', [ReplayError1])
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
            Result2 = process_mining_bridge:import_ocel_json(Input2),
            case Result2 of
                {ok, OcelId2} ->
                    io:format('OCEL_ID_2: ~p\n', [OcelId2]),
                    Petri2 = process_mining_bridge:discover_alpha(OcelId2),
                    case Petri2 of
                        {ok, PnResult2} ->
                            PnId2 = maps:get(handle, PnResult2),
                            io:format('PETRI_NET_ID_2: ~p\n', [PnId2]),
                            Replay2 = process_mining_bridge:token_replay(OcelId2, PnId2),
                            case Replay2 of
                                {ok, Result2Data} ->
                                    Score2 = maps:get(conformance_score, Result2Data,
                                                 maps:get(fitness, Result2Data, 0.5)),
                                    io:format('ITERATION_2_SCORE: ~f\n', [Score2]),
                                    %% Store score
                                    put(scores, [Score2 | get(scores)]);
                                {error, ReplayError2} ->
                                    io:format('TOKEN_REPLAY_ERROR_2: ~p\n', [ReplayError2])
                            end;
                        {error, DiscoveryError2} ->
                            io:format('DISCOVERY_ERROR_2: ~p\n', [DiscoveryError2])
                    end;
                {error, ImportError2} ->
                    io:format('IMPORT_ERROR_2: ~p\n', [ImportError2])
            end,

            %% === SCORE COMPARISON ===
            io:format('\n=== SCORE COMPARISON ===\n'),
            AllScores = get(scores),
            io:format('All scores: ~p\n', [AllScores]),
            case length(AllScores) of
                2 ->
                    [S2, S1] = AllScores,
                    io:format('SCORE_1: ~f\n', [S1]),
                    io:format('SCORE_2: ~f\n', [S2]),
                    Delta = abs(S2 - S1),
                    io:format('DELTA: ~f\n', [Delta]),
                    io:format('JTBD 4: LOOP ACCUMULATION COMPLETED\n');
                _ ->
                    io:format('ERROR: Expected 2 scores, got: ~p\n', [AllScores])
            end,

            %% Stop the server
            process_mining_bridge:stop();
        {error, Reason} ->
            io:format('Failed to start server: ~p\n', [Reason])
    end.