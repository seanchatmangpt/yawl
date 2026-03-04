#!/usr/bin/env escript

%% escript entry point for JTBD tests
main(_) ->
    io:format("Starting JTBD test runner...~n"),

    %% Add ebin directories to code path
    code:add_patha("ebin"),
    code:add_patha("jtbd"),

    %% Load modules
    c(jtbd_utils),
    c(jtbd_runner),

    %% Create test directories if they don't exist
    filelib:ensure_dir("/tmp/jtbd/input/"),
    filelib:ensure_dir("/tmp/jtbd/output/"),

    %% Run tests
    Results = jtbd_runner:run_all(),

    %% Print results
    io:format("~n=== JTBD Test Results ===~n"),
    io:format("Passed: ~p~n", [maps:get(passed, Results)]),
    io:format("Failed: ~p~n", [maps:get(failed, Results)]),

    %% Print detailed results
    ResultsList = maps:get(results, Results),
    io:format("~nDetailed Results:~n"),
    [begin
        TestName = atom_to_list(T),
        case R of
            {ok, ResultData} ->
                io:format("  ✓ ~s: ~p~n", [TestName, ResultData]);
            {error, Error} ->
                io:format("  ✗ ~s: ~p~n", [TestName, Error])
        end
    end || {T, R} <- ResultsList],

    %% Exit with code based on results
    case maps:get(failed, Results) of
        0 ->
            io:format("~nAll tests passed!~n"),
            halt(0);
        _ ->
            io:format("~nSome tests failed.~n"),
            halt(1)
    end.