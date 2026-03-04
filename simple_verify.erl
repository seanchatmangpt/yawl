#!/usr/bin/env escript

main(_) ->
    io:format("=== NIF Function Verification ===~n"),

    % Start the server
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("✓ Gen server started~n"),
            % Check NIF status
            case process_mining_bridge:get_nif_status() of
                {ok, {nif_status, true}} ->
                    io:format("✓ NIF library is available~n");
                {ok, {nif_status, false}} ->
                    io:format("⚠ NIF library not available (using fallbacks)~n");
                Error ->
                    io:format("✗ Error checking NIF status: ~p~n", [Error])
            end,
            % Test basic functions
            case process_mining_bridge:nop() of
                {ok, ok} -> io:format("✓ nop/0 works~n");
                {error, nif_not_loaded} -> io:format("⚠ nop/0 not loaded (expected)~n");
                _ -> io:format("✗ nop/0 failed~n")
            end,
            % Stop the server
            process_mining_bridge:stop();
        {error, Reason} ->
            io:format("✗ Failed to start gen server: ~p~n", [Reason])
    end,

    io:format("=== Verification Complete ===~n").