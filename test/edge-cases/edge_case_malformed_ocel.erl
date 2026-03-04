%% Edge Case Test: Malformed OCEL Data
%% Tests that the system properly handles various types of malformed OCEL data

-module(edge_case_malformed_ocel).
-export([run/0, run_test/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the malformed OCEL edge case test
%% Returns {ok, Result} | {error, Reason}
run() ->
    io:format("Running edge case: Malformed OCEL Data~n", []),
    run_test().

%% @doc Test malformed OCEL scenarios
run_test() ->
    TestDir = "/tmp/yawl-test/edge-cases/malformed_ocel",
    ensure_test_dir(TestDir),

    %% Test various malformed scenarios
    TestCases = [
        {missing_events, create_missing_events_ocel()},
        {invalid_timestamp, create_invalid_timestamp_ocel()},
        {missing_attributes, create_missing_attributes_ocel()},
        {invalid_json, create_invalid_json()},
        {empty_file, create_empty_file()},
        {null_values, create_null_values_ocel()}
    ],

    Results = lists:map(fun({Type, OcelContent}) ->
        TestPath = filename:join(TestDir, Type ++ ".json"),
        file:write_file(TestPath, OcelContent),

        case process_mining_bridge:import_ocel_json(TestPath) of
            {ok, OcelHandle} ->
                %% Try to process and see if it fails later
                process_mining_bridge:free_handle(OcelHandle),
                {Type, success_unexpected};
            {error, Reason} ->
                {Type, {expected_error, Reason}}
        end
    end, TestCases),

    %% Analyze results
    ExpectedFails = length([R || {_, {expected_error, _}} <- Results]),
    UnexpectedSuccesses = length([R || {_, success_unexpected} <- Results]),

    {ok, #{
        test => "malformed_ocel",
        test_cases => length(TestCases),
        expected_failures => ExpectedFails,
        unexpected_successes => UnexpectedSuccesses,
        results => Results,
        status => case UnexpectedSuccesses of
                    0 -> "all_errors_handled";
                    _ -> "some_errors_missed"
                end
    }}.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Create OCEL with missing events array
create_missing_events_ocel() ->
    jsx:encode(#{
        << "objects">> => [],
        << "object_types">> => []
        %% Missing "events" key
    }).

%% @doc Create OCEL with invalid timestamp format
create_invalid_timestamp_ocel() ->
    jsx:encode(#{
        << "events">> => [
            #{
                << "id">> => << "event1">>,
                << "activity">> => << "task1">>,
                << "timestamp">> => << "invalid-timestamp">>, % Invalid format
                << "attributes">> => #{}
            }
        ],
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with missing required attributes
create_missing_attributes_ocel() ->
    jsx:encode(#{
        << "events">> => [
            #{
                << "id">> => << "event1">>,
                << "activity">> => << "task1">>,
                << "timestamp">> => << "2023-01-01T00:00:00">>
                %% Missing "attributes" key
            }
        ],
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create invalid JSON string
create_invalid_json() ->
    << "{ \"events\": [ ], \"objects\": [ ], \"object_types\": [ ]">> % Missing closing brace

%% @doc Create empty file content
create_empty_file() ->
    << "">>

%% @doc Create JSON with null values that should be strings
create_null_values_ocel() ->
    jsx:encode(#{
        << "events">> => [
            #{
                << "id">> => null, % Should be string
                << "activity">> => << "task1">>,
                << "timestamp">> => << "2023-01-01T00:00:00">>,
                << "attributes">> => #{}
            }
        ],
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