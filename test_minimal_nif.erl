-module(test_minimal_nif).
-export([start/0]).

start() ->
    case process_mining_bridge:start_link() of
        {ok, _} ->
            io:format("Started bridge~n"),
            % Test just the basic NIF loading
            case process_mining_bridge:echo_json(<<"test">>) of
                {ok, Result} ->
                    io:format("echo_json works: ~p~n", [Result]);
                {error, Error} ->
                    io:format("echo_json failed: ~p~n", [Error])
            end,
            process_mining_bridge:stop();
        {error, Reason} ->
            io:format("Failed to start: ~p~n", [Reason])
    end.