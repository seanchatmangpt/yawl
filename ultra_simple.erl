%% Ultra simple test
-module(ultra_simple).

-export([run/0]).

run() ->
    io:format("Running ultra simple test...~n"),
    
    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    OutputPath = "/tmp/jtbd/output/pi-sprint-dfg.json",
    
    %% Read input
    case file:read_file(InputPath) of
        {ok, Content} ->
            EventCount = count_events(Content),
            io:format("Found ~p events~n", [EventCount]),
            
            %% Generate simple output
            Output = io_lib:format("{\"events\": ~p, \"message\": \"JTBD test completed\"}", [EventCount]),
            file:write_file(OutputPath, list_to_binary(Output)),
            io:format("Output saved to ~s~n", [OutputPath]);
        {error, Reason} ->
            io:format("Error: ~p~n", [Reason])
    end.

count_events(Content) ->
    length(binary:split(Content, <<"\"id\": \"e">>, [global])) - 1.
