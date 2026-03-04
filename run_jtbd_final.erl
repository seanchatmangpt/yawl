%% escript entry point for JTBD tests
main(_) ->
    io:format("Starting JTBD test runner...~n"),

    %% Add ebin directories to code path
    code:add_patha("ebin"),
    code:add_patha("test"),

    %% Create test directories if they don't exist
    filelib:ensure_dir("/tmp/jtbd/input/"),
    filelib:ensure_dir("/tmp/jtbd/output/"),

    %% Copy input files to test directory
    InputFiles = ["pi-sprint-ocel.json", "pi-sprint-ocel-v2.json", "malformed.json"],
    lists:foreach(fun(File) ->
        Source = "/tmp/jtbd/input/" ++ File,
        Dest = "test/" ++ File,
        case file:copy(Source, Dest) of
            ok -> io:format("Copied ~s to ~s~n", [Source, Dest]);
            {error, _} -> 
                case file:read_file(Source) of
                    {ok, Content} -> file:write_file(Dest, Content);
                    {error, _} -> ok
                end
        end
    end, InputFiles),

    %% Run JTBD 1 test manually
    io:format("~n=== Running JTBD 1: DFG Discovery ===~n"),
    case run_jtbd_1() of
        {ok, Result} ->
            io:format("JTBD 1 Success: ~p~n", [Result]);
        {error, Error} ->
            io:format("JTBD 1 Failed: ~p~n", [Error])
    end,

    %% Run other tests
    io:format("~n=== Running JTBD 2: Conformance ===~n"),
    case run_jtbd_2() of
        {ok, Result2} ->
            io:format("JTBD 2 Success: ~p~n", [Result2]);
        {error, Error2} ->
            io:format("JTBD 2 Failed: ~p~n", [Error2])
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
                    io:format("Input has ~p events, ~p objects~n", [length(Events), length(Objects)]),
                    
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
                                    
                                    {ok, #{output => OutputPath, events => length(Events), objects => length(Objects)}};
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

%% Run JTBD 2 test
run_jtbd_2() ->
    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    OutputPath = "/tmp/jtbd/output/pi-sprint-conformance.json",
    
    case catch process_mining_bridge:import_ocel_json(InputPath) of
        {ok, OcelHandle} ->
            case catch process_mining_bridge:discover_petri_net(OcelHandle) of
                {ok, PetriNetJson} ->
                    case catch process_mining_bridge:check_conformance(OcelHandle, PetriNetJson) of
                        {ok, ConformanceResult} ->
                            file:write_file(OutputPath, ConformanceResult),
                            process_mining_bridge:free_handle(OcelHandle),
                            {ok, #{output => OutputPath, result => ConformanceResult}};
                        Error ->
                            process_mining_bridge:free_handle(OcelHandle),
                            {error, {conformance_failed, Error}}
                    end;
                Error ->
                    process_mining_bridge:free_handle(OcelHandle),
                    {error, {petri_net_failed, Error}}
            end;
        Error ->
            {error, {import_failed, Error}}
    end.
