-module(minimal_test).
-export([run/0]).

run() ->
    io:format("=== Minimal JTBD Test ===\n"),
    io:format("Testing basic functionality...\n"),

    %% Create test directories
    filelib:ensure_dir("/tmp/jtbd/input/"),
    filelib:ensure_dir("/tmp/jtbd/output/"),

    %% Create test data
    TestData = #{
        <<"test_id">> => <<"minimal_test">>,
        <<"timestamp">> => erlang:system_time(millisecond),
        <<"events">> => [
            #{
                <<"id">> => <<"event1">>,
                <<"type">> => <<"start">>,
                <<"timestamp">> => 1640995200000
            }
        ]
    },

    TestFile = "/tmp/jtbd/input/minimal_test.json",
    file:write_file(TestFile, jsx:encode(TestData)),

    io:format("Test data written to: ~s\n", [TestFile]),

    %% Read it back
    case file:read_file(TestFile) of
        {ok, Content} ->
            Decoded = jsx:decode(Content),
            io:format("Successfully read test data: ~p\n", [maps:get(<<"test_id">>, Decoded)]),

            %% Write result
            Result = #{
                id => <<"minimal_test">>,
                status => passed,
                timestamp => erlang:system_time(millisecond),
                message => "Minimal test completed successfully"
            },

            ResultFile = "/tmp/jtbd/output/minimal_test_result.json",
            file:write_file(ResultFile, jsx:encode(Result)),
            io:format("Result written to: ~s\n", [ResultFile]),

            {ok, Result};
        {error, Reason} ->
            {error, Reason}
    end.