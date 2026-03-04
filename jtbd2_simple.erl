#!/usr/bin/env escript
%% -*- erlang -*-

main(_) ->
    %% Add ebin directories to code path
    code:add_patha("ebin"),
    code:add_patha("/Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/ebin"),
    
    %% Create test directories if they don't exist
    filelib:ensure_dir("/tmp/jtbd/input/"),
    filelib:ensure_dir("/tmp/jtbd/output/"),
    
    %% Run JTBD 2 test
    io:format("~n=== Running JTBD 2: Conformance Scoring ===~n"),
    case run_jtbd_2() of
        {ok, Result} ->
            io:format("JTBD 2 Success: ~p~n", [Result]);
        {error, Error} ->
            io:format("JTBD 2 Failed: ~p~n", [Error])
    end,
    
    %% Exit with code 0
    halt(0).

%% Run JTBD 2 test
run_jtbd_2() ->
    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    
    %% Step 1: Import OCEL
    io:format("Step 1: Importing OCEL from ~p~n", [InputPath]),
    case catch process_mining_bridge:import_ocel_json(InputPath) of
        {ok, OcelHandle} ->
            io:format("✓ OCEL imported with ID: ~p~n", [OcelHandle]),
            
            %% Step 2: Discover Petri net
            io:format("Step 2: Discovering Petri net~n"),
            case catch process_mining_bridge:discover_petri_net(OcelHandle) of
                {ok, PetriNetJson} ->
                    PetriNetId = maps:get(handle, PetriNetJson),
                    io:format("✓ Petri net discovered with ID: ~p~n", [PetriNetId]),
                    
                    %% Step 3: Token replay conformance checking
                    io:format("Step 3: Running token replay conformance checking~n"),
                    case catch process_mining_bridge:token_replay(OcelHandle, PetriNetId) of
                        {ok, Result} ->
                            io:format("✓ Token replay completed~n"),
                            io:format("RAW_RESULT: ~p~n", [Result]),
                            
                            %% Step 4: Verify score is a float in (0.0, 1.0) exclusive
                            verify_conformance_score(Result, OcelHandle, PetriNetId),
                            
                            % Clean up
                            process_mining_bridge:free_handle(OcelHandle),
                            process_mining_bridge:free_handle(PetriNetId),
                            {ok, Result};
                        {error, Error} ->
                            io:format("❌ Token replay failed: ~p~n", [Error]),
                            process_mining_bridge:free_handle(OcelHandle),
                            {error, {token_replay_failed, Error}}
                    end;
                {error, Error} ->
                    io:format("❌ Petri net discovery failed: ~p~n", [Error]),
                    process_mining_bridge:free_handle(OcelHandle),
                    {error, {petri_net_failed, Error}}
            end;
        {error, Error} ->
            io:format("❌ OCEL import failed: ~p~n", [Error]),
            {error, {ocel_import_failed, Error}}
    end.

verify_conformance_score(Result, OcelId, PetriNetId) ->
    case Result of
        {ConformanceScore, _OtherData} when is_float(ConformanceScore) ->
            io:format("OCEL_ID: ~p~n", [OcelId]),
            io:format("PETRI_NET_ID: ~p~n", [PetriNetId]),
            io:format("CONFORMANCE_SCORE: ~f~n", [ConformanceScore]),
            
            if
                ConformanceScore > 0.0 andalso ConformanceScore < 1.0 ->
                    io:format("✅ VALID: Score is in (0.0, 1.0) exclusive range~n"),
                    io:format("THE_NUMBER: ~f~n", [ConformanceScore]);
                ConformanceScore == 0.0 ->
                    io:format("❌ INVALID: Score is exactly 0.0 - indicates hardcoding or failed computation!~n"),
                    io:format("⚠️  CRITICAL: This suggests the Rust computation failed or was hardcoded~n");
                ConformanceScore == 1.0 ->
                    io:format("❌ INVALID: Score is exactly 1.0 - indicates hardcoding or failed computation!~n"),
                    io:format("⚠️  CRITICAL: This suggests the Rust computation failed or was hardcoded~n");
                true ->
                    io:format("❌ INVALID: Score is ~f, not in (0.0, 1.0) range~n", [ConformanceScore])
            end;
        _ ->
            io:format("❌ INVALID: Result is not in expected format {float(), _}~n"),
            io:format("Received: ~p~n", [Result])
    end.
