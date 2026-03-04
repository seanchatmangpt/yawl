#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin

main(_) ->
    io:format('=== JTBD 1: DFG DISCOVERY ===~n~n'),

    %% Start the server first
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format('Server started successfully~n'),

            InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",

            %% Step 1: Import OCEL
            io:format('Step 1: Importing OCEL from ~s~n', [InputPath]),
            ImportResult = process_mining_bridge:import_ocel_json(InputPath),
            io:format('IMPORT_RESULT: ~p~n', [ImportResult]),

            case ImportResult of
                {ok, OcelId} ->
                    io:format('OCEL_ID: ~s~n', [OcelId]),

                    %% Step 2: Discover DFG
                    io:format('~nStep 2: Discovering DFG...~n'),
                    DfgResult = process_mining_bridge:discover_dfg(OcelId),
                    io:format('DFG_RESULT: ~p~n', [DfgResult]),

                    case DfgResult of
                        {ok, DfgJson} when is_binary(DfgJson) ->
                            io:format('DFG_BYTES: ~p~n', [byte_size(DfgJson)]),
                            %% Write to file
                            ok = file:write_file('/tmp/jtbd/output/pi-sprint-dfg.json', DfgJson),
                            io:format('WRITTEN: /tmp/jtbd/output/pi-sprint-dfg.json~n');
                        {error, Reason} ->
                            io:format('DFG_ERROR: ~p~n', [Reason])
                    end;
                {error, Reason} ->
                    io:format('IMPORT_ERROR: ~p~n', [Reason])
            end,

            %% Stop the server
            process_mining_bridge:stop(),
            io:format('~nServer stopped~n');
        {error, Reason} ->
            io:format('Failed to start server: ~p~n', [Reason])
    end.