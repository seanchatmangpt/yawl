-module(simple_test).
-export([run/0]).

run() ->
    io:format("Starting simple test...~n"),

    %% Check if test data exists
    TestFile = "/tmp/jtbd/input/pi-sprint-ocel.json",
    case file:read_file(TestFile) of
        {ok, Content} ->
            io:format("Test file read: ~p bytes~n", [byte_size(Content)]),

            %% Try to parse JSON
            try
                Decoded = jsx:decode(Content, [{return_maps, true}]),
                io:format("JSON parsed successfully~n"),
                io:format("Events: ~p, Objects: ~p~n", [
                    maps:get(<<"events">>, Decoded, []),
                    maps:get(<<"objects">>, Decoded, [])
                ]),
                {ok, success}
            catch
                Error:Reason ->
                    io:format("JSON parse failed: ~p:~p~n", [Error, Reason]),
                    {error, {json_parse, {Error, Reason}}}
            end;
        {error, Reason} ->
            io:format("Failed to read test file: ~p~n", [Reason]),
            {error, {file_read, Reason}}
    end.