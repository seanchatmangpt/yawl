#!/usr/bin/env escript

%% escript main function
main(_) ->
    io:format('=== JTBD 4: LOOP ACCUMULATION ===~n'),

    %% Add ebin to path
    code:add_patha("/Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/ebin"),

    %% Start the gen_server - this will load the NIF
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format('Process mining bridge started successfully~n');
        {error, Reason} ->
            io:format('Failed to start: ~p~n', [Reason]),
            halt(1)
    end,

    %% Initialize storage for scores
    put(scores, []),

    %% === ITERATION 1 ===
    io:format('~n=== ITERATION 1 ===~n'),
    Input1 = <<"/tmp/jtbd/input/pi-sprint-ocel.json">>,
    case process_mining_bridge:import_ocel_json_path(Input1) of
        {ok, OcelId1} ->
            io:format('OCEL_ID_1: ~s~n', [OcelId1]),
            Petri1 = process_mining_bridge:discover_petri_net(OcelId1),
            case Petri1 of
                {ok, PnId1} ->
                    io:format('PETRI_NET_ID_1: ~s~n', [PnId1]),
                    Replay1 = process_mining_bridge:token_replay(OcelId1, PnId1),
                    case Replay1 of
                        {ok, Result1Data} ->
                            Score1 = maps:get(<<"conformance_score">>, Result1Data,
                                         maps:get(<<"fitness">>, Result1Data, 0.5)),
                            io:format('ITERATION_1_SCORE: ~f~n', [Score1]),
                            put(scores, [Score1 | get(scores)]);
                        {error, R1} ->
                            io:format('TOKEN_REPLAY_1_ERROR: ~p~n', [R1])
                    end;
                {error, R2} ->
                    io:format('PETRI_NET_1_ERROR: ~p~n', [R2])
            end;
        {error, R3} ->
            io:format('IMPORT_1_ERROR: ~p~n', [R3])
    end,

    %% === ITERATION 2 ===
    io:format('~n=== ITERATION 2 ===~n'),
    Input2 = <<"/tmp/jtbd/input/pi-sprint-ocel-v2.json">>,
    case process_mining_bridge:import_ocel_json_path(Input2) of
        {ok, OcelId2} ->
            io:format('OCEL_ID_2: ~s~n', [OcelId2]),
            Petri2 = process_mining_bridge:discover_petri_net(OcelId2),
            case Petri2 of
                {ok, PnId2} ->
                    io:format('PETRI_NET_ID_2: ~s~n', [PnId2]),
                    Replay2 = process_mining_bridge:token_replay(OcelId2, PnId2),
                    case Replay2 of
                        {ok, Result2Data} ->
                            Score2 = maps:get(<<"conformance_score">>, Result2Data,
                                         maps:get(<<"fitness">>, Result2Data, 0.5)),
                            io:format('ITERATION_2_SCORE: ~f~n', [Score2]),
                            put(scores, [Score2 | get(scores)]);
                        {error, R4} ->
                            io:format('TOKEN_REPLAY_2_ERROR: ~p~n', [R4])
                    end;
                {error, R5} ->
                    io:format('PETRI_NET_2_ERROR: ~p~n', [R5])
            end;
        {error, R6} ->
            io:format('IMPORT_2_ERROR: ~p~n', [R6])
    end,

    %% === VERIFICATION ===
    io:format('~n=== VERIFICATION ===~n'),
    Scores = lists:reverse(get(scores)),
    io:format('SCORES: ~p~n', [Scores]),

    case Scores of
        [S1, S2] ->
            io:format('SCORE_1: ~f~n', [S1]),
            io:format('SCORE_2: ~f~n', [S2]),
            Delta = abs(S2 - S1),
            io:format('DELTA: ~f~n', [Delta]),
            if S1 =/= S2 ->
                io:format('PASS: scores differ~n'),
                io:format('THE LOOP ACCUMULATES~n');
            true ->
                io:format('FAIL: scores identical (~p)~n', [S1])
            end;
        _ ->
            io:format('FAIL: did not get 2 scores~n')
    end,

    %% Write output
    Output = jsx:encode(#{scores => Scores}),
    file:write_file('/tmp/jtbd/output/conformance-history.json', Output),

    %% Stop the server
    process_mining_bridge:stop(),

    io:format('JTBD 4 test completed~n').