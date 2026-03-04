io:format("Testing NIF loading...~n"),
case process_mining_bridge:nop() of
    {ok, Result} -> io:format("nop() = ~p~n", [Result]);
    {error, Reason} -> io:format("nop() ERROR: ~p~n", [Reason])
end.
