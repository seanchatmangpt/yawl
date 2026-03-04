#!/usr/bin/env escript
%%! -pa ebin

main(_) ->
    % Start the process
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format('=== JTBD 1: DFG DISCOVERY ===~n'),

            % Step 1: Import OCEL
            io:format('Step 1: Importing OCEL...~n'),
            InputPath = <<"/tmp/jtbd/input/pi-sprint-ocel.json">>,
            Result1 = process_mining_bridge:import_ocel_json(InputPath),
            io:format('IMPORT_RESULT: ~p~n', [Result1]),

            case Result1 of
                {ok, OcelId} ->
                    io:format('OCEL_ID: ~p~n', [OcelId]),

                    % Step 2: Discover DFG
                    io:format('~nStep 2: Discovering DFG...~n'),
                    Result2 = process_mining_bridge:discover_dfg(OcelId),
                    io:format('DFG_RESULT: ~p~n', [Result2]),

                    case Result2 of
                        {ok, DfgJson} ->
                            io:format('DFG_BYTES: ~p~n', [byte_size(DfgJson)]),
                            % Write to file
                            file:write_file('/tmp/jtbd/output/pi-sprint-dfg.json', DfgJson),
                            io:format('WRITTEN: /tmp/jtbd/output/pi-sprint-dfg.json~n');
                        {error, Reason2} ->
                            io:format('DFG_ERROR: ~p~n', [Reason2])
                    end;
                {error, Reason1} ->
                    io:format('IMPORT_ERROR: ~p~n', [Reason1])
            end,

            % Stop the process
            process_mining_bridge:stop(Pid),
            halt(0);
        {error, Reason} ->
            io:format('Failed to start process: ~p~n', [Reason]),
            halt(1)
    end.