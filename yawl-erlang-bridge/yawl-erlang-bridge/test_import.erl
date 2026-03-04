#!/usr/bin/env escript
%%! -pa ebin
main(_) ->
    % Start the process mining bridge
    io:format("Starting process mining bridge...~n"),
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("Bridge started successfully~n"),

            % Test the import function
            OcelPath = "/Users/sac/yawl/test/jtbd/pi-sprint-ocel.json",
            io:format("Importing OCEL from: ~s~n", [OcelPath]),

            case process_mining_bridge:import_ocel_json_path(OcelPath) of
                {ok, UUID} ->
                    io:format("SUCCESS: Imported OCEL with UUID: ~s~n", [UUID]);
                {error, Reason} ->
                    io:format("ERROR: ~p~n", [Reason])
            end,

            % Stop the bridge
            process_mining_bridge:stop(),
            io:format("Bridge stopped~n");
        {error, Reason} ->
            io:format("Failed to start bridge: ~p~n", [Reason])
    end.