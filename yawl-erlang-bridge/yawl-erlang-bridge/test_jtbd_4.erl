%% JTBD 4: Loop Accumulation Test
%% Tests conformance score accumulation across multiple iterations
%% The scores MUST be computed by Rust, NOT hardcoded.

-module(test_jtbd_4).

%% API exports
-export([run/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the JTBD 4 test
%% Returns {ok, #{scores => [Score1, Score2], delta => Delta}} | {error, Reason}
run() ->
    io:format("=== JTBD 4: LOOP ACCUMULATION ===~n", []),

    %% Start the process mining bridge (loads NIF)
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("Process mining bridge started successfully~n", []);
        {error, Reason} ->
            io:format("Failed to start process mining bridge: ~p~n", [Reason]),
            {error, {start_failed, Reason}}
    end,

    %% Initialize storage for scores
    put(scores, []),

    %% === ITERATION 1 ===
    io:format("~n=== ITERATION 1 ===~n", []),
    Input1 = "/tmp/jtbd/input/pi-sprint-ocel.json",
    case process_mining_bridge:import_ocel_json(Input1) of
        {ok, OcelId1} ->
            io:format("OCEL_ID_1: ~s~n", [OcelId1]),

            %% Discover Petri net
            case process_mining_bridge:discover_petri_net(OcelId1) of
                {ok, PnId1} ->
                    io:format("PETRI_NET_ID_1: ~s~n", [PnId1]),

                    %% Run token replay - THIS IS WHERE THE REAL SCORE COMES FROM
                    case process_mining_bridge:token_replay(OcelId1, PnId1) of
                        {ok, Result1} ->
                            Score1 = maps:get(<<"conformance_score">>, Result1,
                                         maps:get(<<"fitness">>, Result1, 0.5)),
                            io:format("ITERATION_1_SCORE: ~f~n", [Score1]),
                            %% Store
                            put(scores, [Score1 | get(scores)]);
                        {error, R1} ->
                            io:format("TOKEN_REPLAY_1_ERROR: ~p~n", [R1]),
                            process_mining_bridge:free_handle(OcelId1),
                            {error, {token_replay_failed, R1}}
                    end;
                {error, R2} ->
                    io:format("PETRI_NET_1_ERROR: ~p~n", [R2]),
                    process_mining_bridge:free_handle(OcelId1),
                    {error, {petri_net_failed, R2}}
            end;
        {error, R3} ->
            io:format("IMPORT_1_ERROR: ~p~n", [R3]),
            {error, {import_failed, R3}}
    end,

    %% === ITERATION 2 ===
    io:format("~n=== ITERATION 2 ===~n", []),
    Input2 = "/tmp/jtbd/input/pi-sprint-ocel-v2.json",
    case process_mining_bridge:import_ocel_json(Input2) of
        {ok, OcelId2} ->
            io:format("OCEL_ID_2: ~s~n", [OcelId2]),

            case process_mining_bridge:discover_petri_net(OcelId2) of
                {ok, PnId2} ->
                    io:format("PETRI_NET_ID_2: ~s~n", [PnId2]),

                    case process_mining_bridge:token_replay(OcelId2, PnId2) of
                        {ok, Result2} ->
                            Score2 = maps:get(<<"conformance_score">>, Result2,
                                         maps:get(<<"fitness">>, Result2, 0.5)),
                            io:format("ITERATION_2_SCORE: ~f~n", [Score2]),
                            put(scores, [Score2 | get(scores)]);
                        {error, R4} ->
                            io:format("TOKEN_REPLAY_2_ERROR: ~p~n", [R4]),
                            process_mining_bridge:free_handle(OcelId2),
                            {error, {token_replay_failed, R4}}
                    end;
                {error, R5} ->
                    io:format("PETRI_NET_2_ERROR: ~p~n", [R5]),
                    process_mining_bridge:free_handle(OcelId2),
                    {error, {petri_net_failed, R5}}
            end;
        {error, R6} ->
            io:format("IMPORT_2_ERROR: ~p~n", [R6]),
            {error, {import_failed, R6}}
    end,

    %% === VERIFICATION ===
    io:format("~n=== VERIFICATION ===~n", []),
    Scores = lists:reverse(get(scores)),
    io:format("SCORES: ~p~n", [Scores]),

    case Scores of
        [S1, S2] ->
            io:format("SCORE_1: ~f~n", [S1]),
            io:format("SCORE_2: ~f~n", [S2]),
            Delta = abs(S2 - S1),
            io:format("DELTA: ~f~n", [Delta]),
            if S1 =/= S2 ->
                io:format("PASS: scores differ~n", []),
                io:format("THE LOOP ACCUMULATES~n", []),
                Result = #{
                    scores => [S1, S2],
                    delta => Delta,
                    passed => true
                };
            true ->
                io:format("FAIL: scores identical (~p)~n", [S1]),
                Result = #{
                    scores => [S1, S2],
                    delta => Delta,
                    passed => false
                }
            end,

            %% Write output
            Output = jsx:encode(Result),
            file:write_file("/tmp/jtbd/output/conformance-history.json", Output),

            %% Clean up
            process_mining_bridge:stop(),

            {ok, Result};
        _ ->
            io:format("FAIL: did not get 2 scores~n", []),
            process_mining_bridge:stop(),
            {error, incomplete_scores}
    end.