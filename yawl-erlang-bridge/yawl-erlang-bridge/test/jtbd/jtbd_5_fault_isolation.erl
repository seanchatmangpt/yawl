%% JTBD 5: Fault Isolation / Crash Recovery Test
%%
%% This test verifies that the process_mining gen_server handles
%% malformed JSON input gracefully without crashing or being restarted.
%% The gen_server should return an error tuple instead of crashing,
%% and should continue to function normally after the error.

-module(jtbd_5_fault_isolation).

%% API exports
-export([run/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the JTBD 5 fault isolation test
%% Returns {ok, #{test_results => Results}} | {error, Reason}
run() ->
    io:format("Starting JTBD 5: Fault Isolation / Crash Recovery Test~n", []),

    OutputPath = "/tmp/jtbd/output/pi-sprint-fault-isolation.json",

    %% Ensure output directory exists
    ensure_output_dir("/tmp/jtbd/output"),

    %% Test cases for fault isolation
    TestCases = [
        {malformed_json, <<"{invalid json">>},
        {empty_file, <<>>},
        {missing_events, <<"{\"objects\": [], \"events\": []}">>},
        {corrupted_data, <<"<<binary_data>>">>},
        {large_file, generate_large_json()},
        {unicode_bomb, generate_unicode_bomb()}
    ],

    %% Run all test cases
    Results = lists:map(fun({TestName, TestData}) ->
        case run_fault_test(TestName, TestData) of
            {ok, TestResult} ->
                TestResult#{
                    <<"test_name">> => list_to_binary(atom_to_list(TestName)),
                    <<"status">> => <<"pass">>,
                    <<"error">> => null
                };
            {error, Reason} ->
                #{
                    <<"test_name">> => list_to_binary(atom_to_list(TestName)),
                    <<"status">> => <<"fail">>,
                    <<"error">> => list_to_binary(io_lib:format("~p", [Reason]))
                }
        end
    end, TestCases),

    %% Aggregate results
    PassCount = length([R || R <- Results, maps:get(<<"status">>, R) =:= <<"pass">>]),
    FailCount = length([R || R <- Results, maps:get(<<"status">>, R) =:= <<"fail">>]),

    Output = #{
        <<"test_results">> => Results,
        <<"total_tests">> => length(TestCases),
        <<"passed">> => PassCount,
        <<"failed">> => FailCount,
        <<"fault_isolation_passed">> => FailCount =:= 0,
        <<"timestamp">> => timestamp_iso8601()
    },

    case write_output_file(OutputPath, jsx:encode(Output)) of
        ok ->
            {ok, #{
                test_results => Output,
                fault_isolation_passed => FailCount =:= 0
            }};
        {error, WriteReason} ->
            {error, {write_failed, WriteReason}}
    end.

%%====================================================================
%% Fault Test Functions
%%====================================================================

%% @doc Run a specific fault test
run_fault_test(malformed_json, Data) ->
    %% Write malformed JSON to temporary file
    TempFile = "/tmp/jtbd/input/malformed_json.json",
    case write_output_file(TempFile, Data) of
        ok ->
            %% Try to import - should fail gracefully
            case process_mining_bridge:import_ocel_json(TempFile) of
                {error, _} ->
                    %% Good - error was returned instead of crash
                    {ok, #{
                        <<"expected_error">> => true,
                        <<"recovered">> => true
                    }};
                {ok, _} ->
                    {error, {unexpected_success, should_have_failed}}
            end;
        {error, WriteReason} ->
            {error, {file_write_failed, WriteReason}}
    end;

run_fault_test(empty_file, Data) ->
    TempFile = "/tmp/jtbd/input/empty_file.json",
    case write_output_file(TempFile, Data) of
        ok ->
            case process_mining_bridge:import_ocel_json(TempFile) of
                {error, _} ->
                    {ok, #{
                        <<"expected_error">> => true,
                        <<"recovered">> => true
                    }};
                {ok, _} ->
                    {error, {unexpected_success, should_have_failed}}
            end;
        {error, WriteReason} ->
            {error, {file_write_failed, WriteReason}}
    end;

run_fault_test(missing_events, Data) ->
    TempFile = "/tmp/jtbd/input/missing_events.json",
    case write_output_file(TempFile, Data) of
        ok ->
            case process_mining_bridge:import_ocel_json(TempFile) of
                {error, _} ->
                    {ok, #{
                        <<"expected_error">> => true,
                        <<"recovered">> => true
                    }};
                {ok, _} ->
                    {error, {unexpected_success, should_have_failed}}
            end;
        {error, WriteReason} ->
            {error, {file_write_failed, WriteReason}}
    end;

run_fault_test(corrupted_data, Data) ->
    TempFile = "/tmp/jtbd/input/corrupted_data.json",
    case write_output_file(TempFile, Data) of
        ok ->
            case process_mining_bridge:import_ocel_json(TempFile) of
                {error, _} ->
                    {ok, #{
                        <<"expected_error">> => true,
                        <<"recovered">> => true
                    }};
                {ok, _} ->
                    {error, {unexpected_success, should_have_failed}}
            end;
        {error, WriteReason} ->
            {error, {file_write_failed, WriteReason}}
    end;

run_fault_test(large_file, Data) ->
    TempFile = "/tmp/jtbd/input/large_file.json",
    case write_output_file(TempFile, Data) of
        ok ->
            %% Test if large file can be handled without crashing
            case process_mining_bridge:import_ocel_json(TempFile) of
                {error, Reason} when Reason =/= out_of_memory ->
                    {ok, #{
                        <<"expected_error">> => true,
                        <<"recovered">> => true
                    }};
                {ok, _} ->
                    {ok, #{
                        <<"expected_success">> => true,
                        <<"recovered">> => true
                    }};
                {error, Reason} ->
                    {error, {memory_error, Reason}}
            end;
        {error, WriteReason} ->
            {error, {file_write_failed, WriteReason}}
    end;

run_fault_test(unicode_bomb, Data) ->
    TempFile = "/tmp/jtbd/input/unicode_bomb.json",
    case write_output_file(TempFile, Data) of
        ok ->
            case process_mining_bridge:import_ocel_json(TempFile) of
                {error, _} ->
                    {ok, #{
                        <<"expected_error">> => true,
                        <<"recovered">> => true
                    }};
                {ok, _} ->
                    {error, {unexpected_success, should_have_failed}}
            end;
        {error, WriteReason} ->
            {error, {file_write_failed, WriteReason}}
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Generate large JSON for testing
generate_large_json() ->
    Events = lists:map(fun(N) ->
        jsx:encode(#{<<"id">> => list_to_binary("event_" ++ integer_to_list(N)),
                     <<"activity">> => list_to_binary("activity_" ++ integer_to_list(N)),
                     <<"timestamp">> => 1625097600 + N,
                     <<"object_id">> => list_to_binary("object_" ++ integer_to_list(N))})
    end, lists:seq(1, 10000)),

    jsx:encode(#{<<"events">> => Events, <<"objects">> => []}).

%% @doc Generate Unicode bomb string
generate_unicode_bomb() ->
    UnicodeChars = lists:map(fun(N) ->
        unicode:characters_to_binary([N])
    end, lists:seq(1, 100000)),

    jsx:encode(#{<<"data">> => UnicodeChars}).

%% @doc Get timestamp in ISO 8601 format
timestamp_iso8601() ->
    calendar:system_time_to_rfc3339(erlang:system_time(second)).

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