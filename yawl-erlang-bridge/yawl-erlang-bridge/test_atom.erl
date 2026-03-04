-module(test_atom).
-export([test/0]).

test() ->
    try
        io:format("Loading NIF...~n"),
        case erlang:load_nif("./priv/process_mining_bridge", 0) of
            ok ->
                io:format("NIF loaded successfully~n"),
                case atom_passthrough(test_atom) of
                    {ok, test_atom} ->
                        io:format("atom_passthrough test PASSED~n");
                    {ok, Other} ->
                        io:format("atom_passthrough test FAILED: expected test_atom, got ~p~n", [Other]);
                    Error ->
                        io:format("atom_passthrough call failed: ~p~n", [Error])
                end;
            {error, {reload, _}} ->
                io:format("NIF already loaded~n"),
                case atom_passthrough(test_atom) of
                    {ok, test_atom} ->
                        io:format("atom_passthrough test PASSED~n");
                    {ok, Other} ->
                        io:format("atom_passthrough test FAILED: expected test_atom, got ~p~n", [Other]);
                    Error ->
                        io:format("atom_passthrough call failed: ~p~n", [Error])
                end;
            {error, Reason} ->
                io:format("Failed to load NIF: ~p~n", [Reason])
        end
    catch
        E:R ->
            io:format("Error: ~p:~p~n", [E, R])
    end.