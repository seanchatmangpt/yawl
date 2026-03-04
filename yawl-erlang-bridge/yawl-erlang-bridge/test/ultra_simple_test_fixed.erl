-module(ultra_simple_test_fixed).
-export([run/0]).

run() ->
    io:format("=== Ultra Simple JTBD Test ===\n"),
    io:format("Testing basic Erlang functionality...\n"),

    %% Create test directories
    case filelib:ensure_dir("/tmp/jtbd/input/") of
        ok -> io:format("✓ Created input directory\n");
        Error1 -> io:format("✗ Failed to create input directory: ~p\n", [Error1])
    end,

    case filelib:ensure_dir("/tmp/jtbd/output/") of
        ok -> io:format("✓ Created output directory\n");
        Error2 -> io:format("✗ Failed to create output directory: ~p\n", [Error2])
    end,

    %% Test basic file operations
    TestFile = "/tmp/jtbd/input/ultra_simple_test.txt",
    TestContent = "Ultra Simple Test Data\nTimestamp: " ++ integer_to_list(erlang:system_time(millisecond)),

    case file:write_file(TestFile, TestContent) of
        ok ->
            io:format("✓ Wrote test file: ~s\n", [TestFile]),

            %% Read it back
            case file:read_file(TestFile) of
                {ok, Content} ->
                    io:format("✓ Read back ~p bytes\n", [byte_size(Content)]),

                    %% Write result
                    ResultFile = "/tmp/jtbd/output/ultra_simple_test_result.txt",
                    Result = "TEST RESULT: SUCCESS\nTimestamp: " ++ integer_to_list(erlang:system_time(millisecond)),

                    case file:write_file(ResultFile, Result) of
                        ok ->
                            io:format("✓ Wrote result file: ~s\n", [ResultFile]),
                            {ok, #{result => success, files => [TestFile, ResultFile]}};
                        {error, WriteReason} ->
                            {error, #{reason => write_failed, file => ResultFile, error => WriteReason}}
                    end;
                {error, ReadReason} ->
                    {error, #{reason => read_failed, file => TestFile, error => ReadReason}}
            end;
        {error, WriteReason} ->
            {error, #{reason => write_failed, file => TestFile, error => WriteReason}}
    end.