#!/usr/bin/env escript
%%! -pa ebin
main(_) ->
    % Start the gen_server to trigger NIF loading
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format('Gen server started with PID: ~p~n', [Pid]),

            % Now test our function
            OcelPath = "/Users/sac/yawl/test/jtbd/pi-sprint-ocel.json",
            io:format('Testing import_ocel_json_path with: ~s~n', [OcelPath]),

            case process_mining_bridge:import_ocel_json_path(OcelPath) of
                {ok, UUID} ->
                    io:format('SUCCESS: {ok, "~s"}~n', [UUID]);
                {error, Reason} ->
                    io:format('ERROR: ~p~n', [Reason])
            end,

            % Stop the gen_server
            process_mining_bridge:stop(),
            io:format('Gen server stopped~n');
        {error, Reason} ->
            io:format('Failed to start gen_server: ~p~n', [Reason])
    end.