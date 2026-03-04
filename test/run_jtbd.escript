#!/usr/bin/env escript
%%! -pa ebin -pa jtbd

main(_) ->
    io:format("Starting JTBD test runner...~n"),

    %% Compile modules if needed
    case code:ensure_loaded(jtbd_runner) of
        {module, jtbd_runner} ->
            io:format("jtbd_runner already loaded~n");
        _ ->
            io:format("Compiling jtbd_runner...~n"),
            case c:jtbd_runner() of
                {ok, _} -> ok;
                {error, _Error} -> io:format("Failed to compile jtbd_runner~n")
            end
    end,

    case code:ensure_loaded(jtbd_utils) of
        {module, jtbd_utils} ->
            io:format("jtbd_utils already loaded~n");
        _ ->
            io:format("Compiling jtbd_utils...~n"),
            case c:jtbd_utils() of
                {ok, _} -> ok;
                {error, _Error} -> io:format("Failed to compile jtbd_utils~n")
            end
    end,

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
            {error, _Error} ->
                io:format("  ✗ ~s: Error occurred~n", [TestName])
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
