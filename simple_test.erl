%% Simple test without NIF dependencies
-module(simple_test).

-export([run/0]).

run() ->
    io:format("Running simple test...~n"),

    %% Read test file
    case file:read_file("/tmp/jtbd/input/pi-sprint-ocel.json") of
        {ok, Content} ->
            io:format("Input file read: ~p bytes~n", [byte_size(Content)]),

            %% Count events manually
            EventLines = binary:split(Content, <<"\"id\": \"e">>, [global]),
            io:format("Found ~p events (e1-eN)~n", [length(EventLines) - 1]),

            %% Count distinct objects
            ObjectLines = binary:split(Content, <<"\"omap\": [">>, [global]),
            io:format("Found ~p object references~n", [length(ObjectLines) - 1]),

            io:format("Simple test completed successfully~n");
        {error, Reason} ->
            io:format("Error reading file: ~p~n", [Reason])
    end.
