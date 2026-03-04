%% JTBD 1: OCEL → DFG Discovery Test
%% Tests the workflow: Import OCEL → Discover DFG → Write JSON → Validate Output
%% This is a Job To Be Done test that validates the end-to-end discovery pipeline.

-module(jtbd_1_dfg_discovery).

%% API exports
-export([run/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the JTBD 1 test pipeline
%% Returns {ok, #{output => OutputPath, assertions => AssertionResults}} | {error, Reason}
run() ->
    io:format("Starting JTBD 1: OCEL → DFG Discovery test~n", []),

    %% Step 1: Define test paths
    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    OutputPath = "/tmp/jtbd/output/pi-sprint-dfg.json",

    %% Step 2: Ensure output directory exists
    ensure_output_dir("/tmp/jtbd/output"),

    %% Step 3: Read input file
    case read_input_file(InputPath) of
        {ok, InputContent} ->
            io:format("Input file read: ~p bytes~n", [byte_size(InputContent)]),

            %% Step 4: Convert input to map for inspection
            case jsx:decode(InputContent, [{return_maps, true}]) of
                #{<<"events">> := Events, <<"objects">> := Objects} ->
                    io:format("Input OCEL has ~p events, ~p objects~n", [length(Events), length(Objects)]),

                    %% Step 5: Import OCEL
                    case process_mining_bridge:import_ocel_json(InputPath) of
                        {ok, OcelHandle} ->
                            io:format("OCEL imported: ~p~n", [OcelHandle]),

                            %% Step 6: Discover DFG
                            case process_mining_bridge:discover_dfg(OcelHandle) of
                                {ok, DfgJson} ->
                                    io:format("DFG discovered: ~p bytes~n", [byte_size(DfgJson)]),

                                    %% Step 7: Write output
                                    case write_output_file(OutputPath, DfgJson) of
                                        ok ->
                                            io:format("Output written to: ~s~n", [OutputPath]),

                                            %% Step 8: Run assertions
                                            Assertions = run_assertions(OutputPath, DfgJson, Events),

                                            %% Clean up
                                            process_mining_bridge:free_handle(OcelHandle),

                                            %% Return results
                                            {ok, #{
                                                output => OutputPath,
                                                assertions => Assertions,
                                                stats => #{
                                                    input_file => InputPath,
                                                    output_file => OutputPath,
                                                    input_events => length(Events),
                                                    input_objects => length(Objects),
                                                    dfg_size => byte_size(DfgJson)
                                                }
                                            }};
                                        {error, WriteReason} ->
                                            io:format("Failed to write output: ~p~n", [WriteReason]),
                                            process_mining_bridge:free_handle(OcelHandle),
                                            {error, {write_failed, WriteReason}}
                                    end;
                                {error, DfgReason} ->
                                    io:format("DFG discovery failed: ~p~n", [DfgReason]),
                                    process_mining_bridge:free_handle(OcelHandle),
                                    {error, {dfg_discovery_failed, DfgReason}}
                            end;
                        {error, ImportReason} ->
                            io:format("OCEL import failed: ~p~n", [ImportReason]),
                            {error, {ocel_import_failed, ImportReason}}
                    end;
                {error, DecodeReason} ->
                    io:format("Input JSON decode failed: ~p~n", [DecodeReason]),
                    {error, {input_decode_failed, DecodeReason}}
            end;
        {error, ReadReason} ->
            io:format("Failed to read input file: ~p~n", [ReadReason]),
            {error, {input_read_failed, ReadReason}}
    end.

%%====================================================================
%% Assertion Functions
%%====================================================================

%% @doc Run all assertions and return results
run_assertions(OutputPath, DfgJson, InputEvents) ->
    Assertions = [
        {output_file_exists, assert_output_file_exists(OutputPath)},
        {valid_json, assert_valid_json(DfgJson)},
        {contains_nodes_edges, assert_contains_nodes_edges(DfgJson)},
        {node_count_ge_2, assert_node_count_ge_2(DfgJson)},
        {edge_count_ge_1, assert_edge_count_ge_1(DfgJson)},
        {node_names_match_activities, assert_node_names_match_activities(DfgJson, InputEvents)},
        {no_uuid_in_output, assert_no_uuid_in_output(DfgJson)}
    ],

    %% Convert to map with pass/fail results
    Results = lists:map(fun({Name, Result}) ->
        {Name, case Result of
            true -> {pass, passed};
            false -> {fail, assertion_failed};
            {error, Error} -> {error, Error}
        end}
    end, Assertions),

    %% Count passes/fails
    PassCount = length([ok || {_, {pass, _}} <- Results]),
    FailCount = length([ok || {_, {fail, _}} <- Results]),
    ErrorCount = length([ok || {_, {error, _}} <- Results]),

    #{
        total => length(Assertions),
        passed => PassCount,
        failed => FailCount,
        errors => ErrorCount,
        details => Results
    }.

%% @doc Assert output file exists
assert_output_file_exists(Path) ->
    filelib:is_file(Path).

%% @doc Assert output is valid JSON
assert_valid_json(Binary) ->
    try
        jsx:decode(Binary, []),
        true
    catch
        _:_ -> false
    end.

%% @doc Assert output contains "nodes" and "edges" keys
assert_contains_nodes_edges(Binary) ->
    try
        Decoded = jsx:decode(Binary, [{return_maps, true}]),
        maps:is_key(<<"nodes">>, Decoded) andalso maps:is_key(<<"edges">>, Decoded)
    catch
        _:_ -> false
    end.

%% @doc Assert node count >= 2
assert_node_count_ge_2(Binary) ->
    try
        Decoded = jsx:decode(Binary, [{return_maps, true}]),
        Nodes = maps:get(<<"nodes">>, Decoded, []),
        length(Nodes) >= 2
    catch
        _:_ -> false
    end.

%% @doc Assert edge count >= 1
assert_edge_count_ge_1(Binary) ->
    try
        Decoded = jsx:decode(Binary, [{return_maps, true}]),
        Edges = maps:get(<<"edges">>, Decoded, []),
        length(Edges) >= 1
    catch
        _:_ -> false
    end.

%% @doc Assert every node name appears in input OCEL activity names
assert_node_names_match_activities(Binary, InputEvents) ->
    try
        %% Extract node names from DFG
        Decoded = jsx:decode(Binary, [{return_maps, true}]),
        Nodes = maps:get(<<"nodes">>, Decoded, []),
        NodeNames = lists:foldl(fun(Node, Acc) ->
            case jsx:get_value(<<"id">>, Node) of
                undefined -> Acc;
                Name -> [Name | Acc]
            end
        end, [], Nodes),

        %% Extract activity names from OCEL events
        ActivityNames = lists:foldl(fun(Event, Acc) ->
            case jsx:get_value(<<"activity">>, Event) of
                undefined ->
                    case jsx:get_value(<<"type">>, Event) of
                        undefined -> Acc;
                        Type -> [Type | Acc]
                    end;
                Activity -> [Activity | Acc]
            end
        end, [], InputEvents),

        %% Check all node names are in activity names
        lists:all(fun(NodeName) ->
            lists:member(NodeName, ActivityNames)
        end, NodeNames)
    catch
        _:_ -> false
    end.

%% @doc Assert no UUID in output (check for pattern like "xxxxxxxx-xxxx-xxxx")
assert_no_uuid_in_output(Binary) ->
    try
        %% Convert to string for regex matching
        BinaryString = binary_to_list(Binary),

        %% UUID pattern: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        case re:run(BinaryString, "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", [caseless]) of
            nomatch -> true;
            _ -> false
        end
    catch
        _:_ -> false
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Read input file
read_input_file(Path) ->
    case file:read_file(Path) of
        {ok, Content} ->
            {ok, Content};
        {error, Reason} ->
            {error, Reason}
    end.

%% @doc Write output file
write_output_file(Path, Data) ->
    case filelib:ensure_dir(Path) of
        ok ->
            case file:write_file(Path, Data) of
                ok -> ok;
                {error, Reason} -> {error, Reason}
            end;
        {error, Reason} ->
            {error, Reason}
    end.

%% @doc Ensure output directory exists
ensure_output_dir(Dir) ->
    filelib:ensure_dir(Dir ++ "/").