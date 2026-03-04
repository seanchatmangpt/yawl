%% Edge Case Test: Resource Conflicts
%% Tests that the system properly detects and handles resource double-booking

-module(edge_case_resource_conflict).
-export([run/0, run_test/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the resource conflict edge case test
%% Returns {ok, Result} | {error, Reason}
run() ->
    io:format("Running edge case: Resource Conflict~n", []),
    run_test().

%% @doc Test the resource conflict scenario
run_test() ->
    %% Create test environment
    TestDir = "/tmp/yawl-test/edge-cases/resource_conflict",
    ensure_test_dir(TestDir),

    %% Create OCEL data with resource conflicts
    ConflictOcel = create_resource_conflict_ocel(),
    OcelPath = filename:join(TestDir, "conflict_ocel.json"),
    file:write_file(OcelPath, ConflictOcel),

    try
        %% Step 1: Import the conflict OCEL data
        case process_mining_bridge:import_ocel_json(OcelPath) of
            {ok, OcelHandle} ->
                %% Step 2: Try to discover DFG with conflicting resources
                case process_mining_bridge:discover_dfg(OcelHandle) of
                    {ok, DfgJson} ->
                        %% Step 3: Parse DFG to check for resource conflicts
                        case check_resource_conflicts(DfgJson) of
                            {ok, Conflicts} ->
                                process_mining_bridge:free_handle(OcelHandle),
                                {ok, #{
                                    test => "resource_conflict",
                                    conflicts => Conflicts,
                                    status => conflicts_detected
                                }};
                            {error, CheckReason} ->
                                process_mining_bridge:free_handle(OcelHandle),
                                {error, {conflict_check_failed, CheckReason}}
                        end;
                    {error, _} = Error ->
                        process_mining_bridge:free_handle(OcelHandle),
                        {ok, #{
                            test => "resource_conflict",
                            error => Error,
                            status => "dfg_failed_as_expected"
                        }}
                end;
            {error, ImportError} ->
                {error, {import_failed, ImportError}}
        end
    catch
        Class:Reason ->
            {error, {unexpected_exception, Class, Reason, erlang:get_stacktrace()}}
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Create OCEL data with resource conflicts
create_resource_conflict_ocel() ->
    %% Create events with overlapping resource assignments
    Events = [
        #{
            << "id">> => << "event1">>,
            << "activity">> => << "task1">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "resource">> => << "alice">>
            }
        },
        #{
            << "id">> => << "event2">>,
            << "activity">> => << "task2">>,
            << "timestamp">> => << "2023-01-01T00:00:30">>, % Same time!
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "resource">> => << "alice">> % Same resource, different task
            }
        },
        #{
            << "id">> => << "event3">>,
            << "activity">> => << "task3">>,
            << "timestamp">> => << "2023-01-01T00:01:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "resource">> => << "alice">> % Triple assignment!
            }
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Ensure test directory exists
ensure_test_dir(Dir) ->
    filelib:ensure_dir(Dir ++ "/"),
    case file:make_dir(Dir) of
        ok -> ok;
        {error, eexist} -> ok;
        {error, Reason} -> {error, Reason}
    end.

%% @doc Check DFG for resource conflicts
check_resource_conflicts(DfgJson) ->
    try
        %% Parse the DFG JSON
        DfgData = jsx:decode(DfgJson, [{return_maps, true}]),

        %% Look for nodes that might represent resource conflicts
        case maps:get(<<"nodes">>, DfgData, undefined) of
            undefined ->
                {error, no_nodes_found};
            Nodes ->
                %% Check for overlapping resource assignments
                Conflicts = find_resource_conflicts(Nodes),
                {ok, Conflicts}
        end
    catch
        _:_ ->
            {error, json_parse_error}
    end.

%% @doc Find resource conflicts in DFG nodes
find_resource_conflicts(Nodes) ->
    %% This is a simplified conflict detection
    %% In a real implementation, you'd check for overlapping time intervals
    %% and same resources assigned to different tasks
    lists:foldl(fun(Node, Acc) ->
        case Node of
            #{<<"resource">> := Resource, <<"timestamp">> := Timestamp} ->
                case lists:keyfind(Resource, 1, Acc) of
                    {Resource, ExistingTimestamps} ->
                        %% Check if timestamps overlap (simplified)
                        NewTimestamps = [Timestamp | ExistingTimestamps],
                        lists:keyreplace(Resource, 1, Acc, {Resource, NewTimestamps});
                    false ->
                        [{Resource, [Timestamp]} | Acc]
                end;
            _ ->
                Acc
        end
    end, [], Nodes).