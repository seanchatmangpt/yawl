%% Standalone test runner for JTBD 4 - Loop Accumulation with QLever
%% This module can be used to run the test independently

-module(test_jtbd_4_standalone).
-export([run/0]).

%% @doc Run the JTBD 4 test standalone
run() ->
    io:format("================================================~n"),
    io:format("JTBD 4 - Loop Accumulation with QLever Test~n"),
    io:format("================================================~n~n"),

    %% Initialize application
    io:format("Starting application...~n"),
    case application:ensure_all_started(process_mining_bridge) of
        {ok, Apps} ->
            io:format("Started applications: ~p~n", [Apps]),
            timer:sleep(500),
            ok;
        {error, Reason} ->
            io:format("Failed to start applications: ~p~n", [Reason]),
            {error, Reason}
    end,

    %% Initialize random seed
    rand:seed(exs1024, {erlang:monotonic_time(), erlang:unique_integer(), erlang:system_time()}),

    %% Create test data directories
    io:format("Creating test data...~n"),
    create_test_data(),

    %% Run the main test
    io:format("Running main test...~n"),
    case jtbd_4_qlever_accumulation:run() of
        {ok, Result} ->
            io:format("Test completed successfully!~n"),
            io:format("Result: ~p~n", [Result]),
            display_results(Result),
            {ok, Result};
        {error, Reason} ->
            io:format("Test failed: ~p~n", [Reason]),
            {error, Reason}
    end.

%% @doc Create necessary test data files
create_test_data() ->
    %% Create input directory
    filelib:ensure_dir("/tmp/jtbd/input/"),
    filelib:ensure_dir("/tmp/jtbd/output/"),

    %% Create pi-sprint-ocel.json if it doesn't exist
    case file:read_file("/tmp/jtbd/input/pi-sprint-ocel.json") of
        {ok, _} ->
            ok;
        {error, _} ->
            io:format("Creating pi-sprint-ocel.json...~n"),
            OcelContent = <<"
{
    \"events\": [
        {
            \"id\": \"event1\",
            \"type\": \"start\",
            \"timestamp\": \"2024-01-01T10:00:00Z\",
            \"source\": [\"object1\"],
            \"attributes\": {
                \"resource\": \"user1\",
                \"cost\": 100
            }
        },
        {
            \"id\": \"event2\",
            \"type\": \"complete\",
            \"timestamp\": \"2024-01-01T10:30:00Z\",
            \"source\": [\"object1\"],
            \"attributes\": {
                \"resource\": \"user2\",
                \"cost\": 50
            }
        }
    ],
    \"objects\": [
        {
            \"id\": \"object1\",
            \"type\": \"order\",
            \"attributes\": {
                \"status\": \"completed\",
                \"amount\": 150
            }
        }
    ]
}
">>,
            file:write_file("/tmp/jtbd/input/pi-sprint-ocel.json", OcelContent)
    end,

    %% Create pi-sprint-ocel-v2.json if it doesn't exist
    case file:read_file("/tmp/jtbd/input/pi-sprint-ocel-v2.json") of
        {ok, _} ->
            ok;
        {error, _} ->
            io:format("Creating pi-sprint-ocel-v2.json...~n"),
            OcelContent = <<"
{
    \"events\": [
        {
            \"id\": \"event1\",
            \"type\": \"start\",
            \"timestamp\": \"2024-01-02T10:00:00Z\",
            \"source\": [\"object1\"],
            \"attributes\": {
                \"resource\": \"user1\",
                \"cost\": 120
            }
        },
        {
            \"id\": \"event2\",
            \"type\": \"complete\",
            \"timestamp\": \"2024-01-02T10:25:00Z\",
            \"source\": [\"object1\"],
            \"attributes\": {
                \"resource\": \"user2\",
                \"cost\": 60
            }
        },
        {
            \"id\": \"event3\",
            \"type\": \"approve\",
            \"timestamp\": \"2024-01-02T10:45:00Z\",
            \"source\": [\"object1\"],
            \"attributes\": {
                \"resource\": \"manager1\",
                \"cost\": 30
            }
        }
    ],
    \"objects\": [
        {
            \"id\": \"object1\",
            \"type\": \"order\",
            \"attributes\": {
                \"status\": \"completed\",
                \"amount\": 180,
                \"priority\": \"high\"
            }
        }
    ]
}
">>,
            file:write_file("/tmp/jtbd/input/pi-sprint-ocel-v2.json", OcelContent)
    end.

%% @doc Display test results in a formatted way
display_results(Result) ->
    io:format("~n=== Test Results ===~n"),

    case Result of
        #{output := OutputPath, scores := Scores} ->
            io:format("Output file: ~s~n", [OutputPath]),
            io:format("Scores: ~p~n", [Scores]),

            %% Display output file contents
            case file:read_file(OutputPath) of
                {ok, Contents} ->
                    io:format("~n=== Output File Contents ===~n"),
                    io:format("~s~n", [binary_to_list(Contents)]);
                {error, _} ->
                    io:format("Warning: Could not read output file~n")
            end;
        _ ->
            io:format("Unexpected result format: ~p~n", [Result])
    end.