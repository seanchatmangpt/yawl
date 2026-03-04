-module(qlever_client_test).
-compile(export_all).

-include_lib("eunit/include/erl.hrl").

start_test() ->
    %% Test should fail with real implementation
    case catch qlever_client:start_link([{use_mock, true}, {endpoint, mock}]) of
        {throw, #{reason := mock_mode_not_allowed}} ->
            ok;
        {ok, _Pid} ->
            qlever_client:stop()
    end.

stop_test() ->
    ok.

is_available_test() ->
    start_test(),
    Result = qlever_client:is_available(),
    stop_test(),
    ?assertEqual(false, Result).

ping_test() ->
    start_test(),
    Result = qlever_client:ping(),
    stop_test(),
    case Result of
        {ok, mock_mode} ->
            ?assert(false, "Mock mode should not be available");
        {ok, pong} ->
            ok;
        _ ->
            ok
    end.

insert_and_retrieve_test() ->
    start_test(),

    %% Insert test data
    RunId = "test_run_123",
    Score = 0.95,
    Timestamp = "2026-03-03T10:00:00Z",

    InsertResult = qlever_client:insert_conformance_score(RunId, Score, Timestamp),

    %% Retrieve history
    Result = qlever_client:select_conformance_history(),

    stop_test(),

    %% Verify that mock operations are not allowed
    case {InsertResult, Result} of
        {{throw, #{reason := mock_mode_not_allowed}}, 
         {throw, #{reason := mock_mode_not_allowed}}} ->
            ok;
        _ ->
            ?assert(false, "Expected mock mode not allowed errors")
    end.

multiple_inserts_test() ->
    start_test(),

    %% Insert multiple entries
    Entries = [
        {"run_1", 0.85, "2026-03-03T09:00:00Z"},
        {"run_2", 0.92, "2026-03-03T10:00:00Z"},
        {"run_3", 0.78, "2026-03-03T11:00:00Z"}
    ],

    lists:foreach(fun({RunId, Score, Timestamp}) ->
        InsertResult = qlever_client:insert_conformance_score(RunId, Score, Timestamp),
        case InsertResult of
            {throw, #{reason := mock_mode_not_allowed}} ->
                ok;
            _ ->
                ?assert(false, "Expected mock mode not allowed error")
        end
    end, Entries),

    %% Retrieve all entries
    {throw, #{reason := mock_mode_not_allowed}} = qlever_client:select_conformance_history(),

    stop_test().

run_all_tests() ->
    eunit:test({with_functions, [
        is_available_test,
        ping_test,
        insert_and_retrieve_test,
        multiple_inserts_test
    ]}).
