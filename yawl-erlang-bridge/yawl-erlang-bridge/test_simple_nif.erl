#!/usr/bin/env escript

main(_) ->
    io:format("Testing NIF atom_passthrough function...~n"),
    try
        % Try to load the NIF library directly
        case erlang:load_nif("./priv/process_mining_bridge", 0) of
            ok ->
                io:format("NIF library loaded successfully~n"),
                % Test the atom_passthrough function
                Atom = test_atom,
                case atom_passthrough(Atom) of
                    {ok, ReturnedAtom} when ReturnedAtom =:= Atom ->
                        io:format("atom_passthrough test PASSED: ~p~n", [ReturnedAtom]);
                    {ok, WrongAtom} ->
                        io:format("atom_passthrough test FAILED: expected ~p, got ~p~n", [Atom, WrongAtom]);
                    Error ->
                        io:format("atom_passthrough call failed: ~p~n", [Error])
                end;
            {error, {reload, _}} ->
                io:format("NIF library already loaded~n"),
                % Test the atom_passthrough function
                Atom = test_atom,
                case atom_passthrough(Atom) of
                    {ok, ReturnedAtom} when ReturnedAtom =:= Atom ->
                        io:format("atom_passthrough test PASSED: ~p~n", [ReturnedAtom]);
                    {ok, WrongAtom} ->
                        io:format("atom_passthrough test FAILED: expected ~p, got ~p~n", [Atom, WrongAtom]);
                    Error ->
                        io:format("atom_passthrough call failed: ~p~n", [Error])
                end;
            {error, Reason} ->
                io:format("Failed to load NIF library: ~p~n", [Reason])
        end
    catch
        E:R ->
            io:format("Error: ~p:~p~n", [E, R])
    end.