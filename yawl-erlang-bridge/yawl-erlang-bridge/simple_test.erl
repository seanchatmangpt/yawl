#!/usr/bin/env escript
%%! -pa ebin
main(_) ->
    % Try to load the module
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format('Module loaded successfully~n'),
            % Test the import function directly
            OcelPath = "/Users/sac/yawl/test/jtbd/pi-sprint-ocel.json",
            io:format('Testing import_ocel_json_path with: ~s~n', [OcelPath]),

            case process_mining_bridge:import_ocel_json_path(OcelPath) of
                {ok, UUID} ->
                    io:format('SUCCESS: ~p~n', [{ok, UUID}]);
                {error, Reason} ->
                    io:format('ERROR: ~p~n', [Reason])
            end;
        {error, Reason} ->
            io:format('Failed to load module: ~p~n', [Reason])
    end.