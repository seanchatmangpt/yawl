#!/usr/bin/env escript

%%====================================================================
%% Process Mining Bridge Test Script
%%====================================================================

main(_) ->
    %% Start the bridge
    io:format("Starting process mining bridge...~n"),
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format("Bridge started with PID: ~p~n", [Pid]),

            %% Test NIF status
            io:format("Testing NIF status...~n"),
            case process_mining_bridge:get_nif_status() of
                {ok, {nif_status, true}} ->
                    io:format("✓ NIF library file exists~n");
                {ok, {nif_status, false}} ->
                    io:format("✗ NIF library file not found~n")
            end,

            %% Test ping
            io:format("Testing ping...~n"),
            case process_mining_bridge:ping() of
                {ok, {pong, true}} ->
                    io:format("✓ NIF loaded~n");
                {ok, {pong, false}} ->
                    io:format("✗ NIF not loaded~n")
            end,

            %% Test unsupported operation (should throw)
            io:format("Testing unsupported operation...~n"),
            try
                process_mining_bridge:import_ocel_xml("test.xml"),
                io:format("✗ Expected unsupported operation exception~n")
            catch
                throw:{'UnsupportedOperationException', Msg} ->
                    io:format("✓ Correctly threw: ~s~n", [Msg]);
                _:_ ->
                    io:format("✗ Unexpected exception~n")
            end,

            %% Stop the bridge
            io:format("Stopping bridge...~n"),
            process_mining_bridge:stop(),
            io:format("✓ Bridge stopped~n");

        {error, Reason} ->
            io:format("Failed to start bridge: ~p~n", [Reason])
    end.