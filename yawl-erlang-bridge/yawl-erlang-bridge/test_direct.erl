main(_) ->
    io:format("Testing direct NIF load...~n"),
    case erlang:load_nif("./priv/process_mining_bridge", 0) of
        ok ->
            io:format("NIF loaded successfully~n");
        {error, {reload, _}} ->
            io:format("NIF already loaded~n");
        {error, Reason} ->
            io:format("Failed to load NIF: ~p~n", [Reason])
    end,
    halt().