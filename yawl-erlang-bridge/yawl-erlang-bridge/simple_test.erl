#!/usr/bin/env escript
%%! -pa ebin

main(_) ->
    %% Add ebin to path
    code:add_patha("/Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/ebin"),

    io:format("Starting test...~n"),

    %% Try to start the process mining bridge
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format("✓ Process mining bridge started: ~p~n", [Pid]),

            %% Check NIF status
            case process_mining_bridge:get_nif_status() of
                {ok, {nif_status, true}} ->
                    io:format("✓ NIF library file exists~n");
                {ok, {nif_status, false}} ->
                    io:format("✗ NIF library file not found~n");
                {error, Err1} ->
                    io:format("✗ Error getting NIF status: ~p~n", [Err1])
            end,

            %% Try to import OCEL JSON
            InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
            case process_mining_bridge:import_ocel_json(InputPath) of
                {ok, Handle} ->
                    io:format("✓ OCEL imported successfully: ~p~n", [Handle]),

                    %% Try to discover DFG
                    case process_mining_bridge:discover_dfg(Handle) of
                        {ok, DFGJson} ->
                            io:format("✓ DFG discovered: ~p bytes~n", [byte_size(DFGJson)]),

                            %% Clean up
                            process_mining_bridge:free_handle(Handle),
                            io:format("✓ Test completed successfully~n");
                        {error, Err2} ->
                            io:format("✗ DFG discovery failed: ~p~n", [Err2]),
                            process_mining_bridge:free_handle(Handle)
                    end;
                {error, Err3} ->
                    io:format("✗ OCEL import failed: ~p~n", [Err3])
            end,

            %% Stop the bridge
            process_mining_bridge:stop(),
            io:format("✓ Bridge stopped~n");

        {error, Err4} ->
            io:format("✗ Failed to start bridge: ~p~n", [Err4])
    end.
