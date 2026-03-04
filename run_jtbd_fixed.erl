%% escript entry point for JTBD tests
main(_) ->
    io:format("Starting JTBD test runner...~n"),

    %% Add ebin directories to code path
    code:add_patha("ebin"),
    code:add_patha("test"),
    code:add_patha("/Users/sac/yawl/yawl-erlang/_build/default/lib/jsx/ebin"),

    %% Create test directories if they don't exist
    filelib:ensure_dir("/tmp/jtbd/input/"),
    filelib:ensure_dir("/tmp/jtbd/output/"),

    %% Run JTBD 1 test manually
    io:format("~n=== Running JTBD 1: DFG Discovery ===~n"),
    case run_jtbd_1() of
        {ok, Result} ->
            io:format("JTBD 1 Success: ~p~n", [Result]);
        {error, Error} ->
            io:format("JTBD 1 Failed: ~p~n", [Error])
    end,

    %% Exit with code 0
    halt(0).

%% Run JTBD 1 test
run_jtbd_1() ->
    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    OutputPath = "/tmp/jtbd/output/pi-sprint-dfg.json",
    
    case file:read_file(InputPath) of
        {ok, InputContent} ->
            case jsx:decode(InputContent, [{return_maps, true}]) of
                #{<<"events">> := Events, <<"objects">> := Objects} ->
                    % Convert maps to lists for length calculation
                    EventsList = maps:values(Events),
                    ObjectsList = maps:values(Objects),
                    io:format("Input has ~p events, ~p objects~n", [length(EventsList), length(ObjectsList)]),
                    
                    % Try to run the process mining functions
                    case catch process_mining_bridge:import_ocel_json(InputPath) of
                        {ok, OcelHandle} ->
                            io:format("OCEL imported: ~p~n", [OcelHandle]),
                            
                            case catch process_mining_bridge:discover_dfg(OcelHandle) of
                                {ok, DfgJson} ->
                                    io:format("DFG discovered: ~p bytes~n", [byte_size(DfgJson)]),
                                    file:write_file(OutputPath, DfgJson),
                                    
                                    % Clean up
                                    process_mining_bridge:free_handle(OcelHandle),
                                    
                                    {ok, #{output => OutputPath, events => length(EventsList), objects => length(ObjectsList)}};
                                Error ->
                                    process_mining_bridge:free_handle(OcelHandle),
                                    {error, {dfg_failed, Error}}
                            end;
                        Error ->
                            {error, {import_failed, Error}}
                    end;
                Error ->
                    {error, {decode_failed, Error}}
            end;
        Error ->
            {error, {read_failed, Error}}
    end.
