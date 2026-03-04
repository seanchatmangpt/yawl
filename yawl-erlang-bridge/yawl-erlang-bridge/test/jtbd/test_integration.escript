#!/usr/bin/env escript

% Script to test qlever_client integration

main(_) ->
    io:format("=== QLever Client Integration Test ===~n~n"),

    % Add the ebin directory to code path
    code:add_patha("../ebin"),

    % Test 1: Start in mock mode
    io:format("Test 1: Starting in mock mode~n"),
    case qlever_client:start_link([{use_mock, true}]) of
        {ok, _Pid} -> io:format("✓ Mock mode started successfully~n");
        {error, Reason} ->
            io:format("✗ Failed to start: ~p~n", [Reason]),
            halt(1)
    end,

    % Test 2: Check availability
    io:format("~nTest 2: Checking availability~n"),
    Available = qlever_client:is_available(),
    io:format("Available: ~p~n", [Available]),
    if
        Available =:= false ->
            io:format("✓ Correctly reports unavailable in mock mode~n");
        true ->
            io:format("✗ Should be unavailable in mock mode~n"),
            halt(1)
    end,

    % Test 3: Ping
    io:format("~nTest 3: Ping test~n"),
    PingResult = qlever_client:ping(),
    io:format("Ping result: ~p~n", [PingResult]),
    case PingResult of
        {ok, mock_mode} -> io:format("✓ Ping successful in mock mode~n");
        _ ->
            io:format("✗ Expected mock_mode response~n"),
            halt(1)
    end,

    % Test 4: Insert conformance score
    io:format("~nTest 4: Insert conformance score~n"),
    InsertResult = qlever_client:insert_conformance_score(
        "integration_test_run",
        0.87,
        "2026-03-03T15:45:00Z"
    ),
    io:format("Insert result: ~p~n", [InsertResult]),
    case InsertResult of
        ok -> io:format("✓ Insert successful~n");
        _ ->
            io:format("✗ Insert failed~n"),
            halt(1)
    end,

    % Test 5: Select history
    io:format("~nTest 5: Select conformance history~n"),
    HistoryResult = qlever_client:select_conformance_history(),
    io:format("History result: ~p~n", [HistoryResult]),
    case HistoryResult of
        {ok, History} ->
            % Check if our inserted data is there
            case lists:keyfind("integration_test_run", 2, History) of
                false ->
                    io:format("✗ Inserted data not found in history~n"),
                    halt(1);
                Entry ->
                    Score = element(3, Entry),
                    Timestamp = element(4, Entry),
                    io:format("✓ Found inserted data: Score=~.2f, Time=~s~n", [Score, Timestamp])
            end;
        _ ->
            io:format("✗ Expected {ok, History} response~n"),
            halt(1)
    end,

    % Test 6: Multiple inserts
    io:format("~nTest 6: Multiple inserts~n"),
    TestRuns = [
        {"run_a", 0.95, "2026-03-03T16:00:00Z"},
        {"run_b", 0.82, "2026-03-03T16:30:00Z"},
        {"run_c", 0.91, "2026-03-03T17:00:00Z"}
    ],
    lists:foreach(fun({RunId, Score, Time}) ->
        ok = qlever_client:insert_conformance_score(RunId, Score, Time),
        io:format("✓ Inserted ~s~n", [RunId])
    end, TestRuns),

    % Verify all data is there
    {ok, AllHistory} = qlever_client:select_conformance_history(),
    lists:foreach(fun({RunId, _Score, _Time}) ->
        case lists:any(fun(E) -> maps:get(run, E) =:= RunId end, AllHistory) of
            true -> ok;
            false ->
                io:format("✗ Missing run: ~s~n", [RunId]),
                halt(1)
        end
    end, TestRuns),
    io:format("✓ All multiple inserts verified~n"),

    % Test 7: Cleanup
    io:format("~nTest 7: Stopping client~n"),
    case qlever_client:stop() of
        ok -> io:format("✓ Client stopped successfully~n");
        _ ->
            io:format("✗ Failed to stop client~n"),
            halt(1)
    end,

    io:format("~n=== All Tests Passed! ===~n").