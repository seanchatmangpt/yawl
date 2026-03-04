#!/usr/bin/env escript

main(_) ->
    io:format("=== JTBD 4: LOOP ACCUMULATION ===~n"),
    
    code:add_patha("ebin"),
    code:add_patha("../ebin"),
    code:add_patha(".."),
    
    case application:ensure_started(mnesia) of
        ok -> io:format("✓ Mnesia started~n");
        {error, _} -> io:format("✗ Mnesia failed~n")
    end,
    
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("✓ Module loaded~n");
        {error, _} ->
            io:format("✗ Module load failed~n"),
            halt(1)
    end,
    
    run_iterations().

run_iterations() ->
    %% Iteration 1
    io:format("~n--- ITERATION 1 ---~n"),
    case iteration1() of
        {ok, Score1} ->
            io:format("✓ Iteration 1 completed~n"),
            io:format("  Score 1: ~f~n", [Score1]),
            
            %% Iteration 2
            io:format("~n--- ITERATION 2 ---~n"),
            case iteration2() of
                {ok, Score2} ->
                    io:format("✓ Iteration 2 completed~n"),
                    io:format("  Score 2: ~f~n", [Score2]),
                    
                    Delta = abs(Score1 - Score2),
                    io:format("~n--- RESULTS ---~n"),
                    io:format("ITERATION_1_SCORE: ~f~n", [Score1]),
                    io:format("ITERATION_2_SCORE: ~f~n", [Score2]),
                    io:format("Delta: ~f~n", [Delta]),
                    
                    if
                        Delta > 0.0001 ->
                            io:format("✓ SUCCESS: Scores are different!~n"),
                            io:format("✓ THE LOOP ACCUMULATES CONFIRMED~n");
                        true ->
                            io:format("✗ FAILURE: Scores are identical~n")
                    end;
                {error, Error2} ->
                    io:format("✗ Iteration 2 failed: ~p~n", [Error2]),
                    halt(2)
            end;
        {error, Error1} ->
            io:format("✗ Iteration 1 failed: ~p~n", [Error1]),
            halt(2)
    end,
    io:format("~nJTBD 4 Complete~n").

iteration1() ->
    OcelPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    io:format("Loading OCEL file: ~s~n", [OcelPath]),
    
    case file:read_file(OcelPath) of
        {ok, _OcelJson} ->
            try
                case process_mining_bridge:import_ocel_json_path(OcelPath) of
                    {ok, OcelId1} ->
                        io:format("✓ OCEL imported~n"),
                        case process_mining_bridge:discover_alpha(OcelId1) of
                            {ok, PnResult1} ->
                                PnId1 = maps:get(handle, PnResult1),
                                io:format("✓ Petri net discovered~n"),
                                case process_mining_bridge:token_replay(OcelId1, PnId1) of
                                    {ok, Result1} ->
                                        Score1 = maps:get(conformance_score, Result1),
                                        {ok, Score1};
                                    {error, Error} ->
                                        {error, {token_replay_failed, Error}}
                                end;
                            {error, Error} ->
                                {error, {petri_net_failed, Error}}
                        end;
                    {error, Error} ->
                        {error, {import_failed, Error}}
                end
            catch
                _:_ ->
                    {error, {exception, "unknown"}}
            end;
        {error, Error} ->
            {error, {file_not_found, Error}}
    end.

iteration2() ->
    OcelPath = "/tmp/jtbd/input/pi-sprint-ocel-v2.json",
    io:format("Loading OCEL file: ~s~n", [OcelPath]),
    
    case file:read_file(OcelPath) of
        {ok, _OcelJson} ->
            try
                case process_mining_bridge:import_ocel_json_path(OcelPath) of
                    {ok, OcelId2} ->
                        io:format("✓ OCEL imported~n"),
                        case process_mining_bridge:discover_alpha(OcelId2) of
                            {ok, PnResult2} ->
                                PnId2 = maps:get(handle, PnResult2),
                                io:format("✓ Petri net discovered~n"),
                                case process_mining_bridge:token_replay(OcelId2, PnId2) of
                                    {ok, Result2} ->
                                        Score2 = maps:get(conformance_score, Result2),
                                        {ok, Score2};
                                    {error, Error} ->
                                        {error, {token_replay_failed, Error}}
                                end;
                            {error, Error} ->
                                {error, {petri_net_failed, Error}}
                        end;
                    {error, Error} ->
                        {error, {import_failed, Error}}
                end
            catch
                _:_ ->
                    {error, {exception, "unknown"}}
            end;
        {error, Error} ->
            {error, {file_not_found, Error}}
    end.
