%% Edge Case Test: Empty Event Logs
%% Tests that the system properly handles OCEL files with empty or no events

-module(edge_case_empty_events).
-export([run/0, run_test/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the empty events edge case test
%% Returns {ok, Result} | {error, Reason}
run() ->
    io:format("Running edge case: Empty Event Logs~n", []),
    run_test().

%% @doc Test empty events scenarios
run_test() ->
    TestDir = "/tmp/yawl-test/edge-cases/empty_events",
    ensure_test_dir(TestDir),

    %% Test various empty scenarios
    TestCases = [
        {completely_empty, create_completely_empty_ocel()},
        {empty_events_array, create_empty_events_array()},
        {no_activities, create_no_activities_ocel()},
        {only_metadata, create_only_metadata_ocel()}
    ],

    Results = lists:map(fun({Type, OcelContent}) ->
        TestPath = filename:join(TestDir, Type ++ ".json"),
        file:write_file(TestPath, OcelContent),

        case process_mining_bridge:import_ocel_json(TestPath) of
            {ok, OcelHandle} ->
                %% Try DFG discovery on empty data
                case process_mining_bridge:discover_dfg(OcelHandle) of
                    {ok, _} = DfgResult ->
                        process_mining_bridge:free_handle(OcelHandle),
                        {Type, {dfg_discovery_succeeded, DfgResult}};
                    {error, DfgReason} ->
                        process_mining_bridge:free_handle(OcelHandle),
                        {Type, {dfg_discovery_failed, DfgReason}}
                end;
            {error, ImportError} ->
                {Type, {import_failed, ImportError}}
        end
    end, TestCases),

    %% Analyze results
    SuccessfullyImported = [R || {_, {_, _}} <- Results],
    FailedImports = [R || {_, {import_failed, _}} <- Results],

    {ok, #{
        test => "empty_events",
        test_cases => length(TestCases),
        successful_imports => length(SuccessfullyImported),
        failed_imports => length(FailedImports),
        results => Results,
        status => case FailedImports of
                    0 -> "all_imports_successful";
                    _ -> "some_imports_failed"
                end
    }}.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Create completely empty OCEL
create_completely_empty_ocel() ->
    jsx:encode(#{}).

%% @doc Create OCEL with empty events array
create_empty_events_array() ->
    jsx:encode(#{
        << "events">> => [],
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with events but no activities
create_no_activities_ocel() ->
    jsx:encode(#{
        << "events">> => [
            #{
                << "id">> => << "event1">>,
                %% Missing "activity" key
                << "timestamp">> => << "2023-01-01T00:00:00">>,
                << "attributes">> => #{}
            }
        ],
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with only metadata, no actual events
create_only_metadata_ocel() ->
    jsx:encode(#{
        << "event_types">> => [
            #{
                << "id">> => << "start">>,
                << "attributes">> => [
                    #{
                        << "id">> => << "status">>,
                        << "type">> => << "string">>
                    }
                ]
            }
        ]
        %% Missing "events" array
    }).

%% @doc Ensure test directory exists
ensure_test_dir(Dir) ->
    filelib:ensure_dir(Dir ++ "/"),
    case file:make_dir(Dir) of
        ok -> ok;
        {error, eexist} -> ok;
        {error, Reason} -> {error, Reason}
    end.