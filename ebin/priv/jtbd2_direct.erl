-module(jtbd2_direct).
-export([run/0]).

run() ->
    % Add ebin directories to code path
    code:add_patha("ebin"),
    code:add_patha("yawl-erlang-bridge/yawl-erlang-bridge/ebin"),
    
    % Import OCEL
    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    io:format("Step 1: Importing OCEL from ~p~n", [InputPath]),
    case process_mining_bridge:import_ocel_json(InputPath) of
        {ok, OcelHandle} ->
            io:format("✓ OCEL imported with ID: ~p~n", [OcelHandle]),
            
            % Step 2: Discover Petri net
            io:format("Step 2: Discovering Petri net~n"),
            case process_mining_bridge:discover_alpha(OcelHandle) of
                {ok, PetriNetId} ->
                    io:format("✓ Petri net discovered with ID: ~p~n", [PetriNetId]),
                    
                    % Step 3: Token replay conformance checking
                    io:format("Step 3: Running token replay conformance checking~n"),
                    case process_mining_bridge:token_replay(OcelHandle, PetriNetId) of
                        {ok, Result} ->
                            io:format("✓ Token replay completed~n"),
                            io:format("RAW_RESULT: ~p~n", [Result]),
                            
                            % Step 4: Verify score is a float in (0.0, 1.0) exclusive
                            verify_conformance_score(Result, OcelHandle, PetriNetId),
                            
                            % Clean up
                            process_mining_bridge:free_handle(OcelHandle),
                            process_mining_bridge:free_handle(PetriNetId),
                            ok;
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
