#!/usr/bin/env escript
%% Simple test to check if the module compiles with all functions

-module(simple_nif_test).
-export([main/0]).

main() ->
    %% Try to compile the module to check for function mismatches
    case compile:file("src/process_mining_bridge.erl", [binary, debug_info]) of
        {ok, _Module} ->
            io:format("✓ process_mining_bridge.erl compiles successfully\n"),
            check_functions();
        {error, Errors, _Warnings} ->
            io:format("✗ Compilation errors:\n"),
            [io:format("~p\n", [Error]) || Error <- Errors],
            halt(1)
    end.

check_functions() ->
    %% List all exported functions
    Exports = process_mining_bridge:module_info(exports),
    io:format("\nExported functions (~p total):\n", [length(Exports)]),

    %% Check for specific functions we added
    ExpectedFunctions = [
        {registry_get_type, 1},
        {registry_free, 1},
        {registry_list, 0},
        {discover_petri_net, 1},
        {compute_dfg_from_events, 1},
        {align_trace, 3}
    ],

    lists:foreach(fun({Func, Arity}) ->
        case lists:member({Func, Arity}, Exports) of
            true ->
                io:format("✓ ~p/~p found\n", [Func, Arity]);
            false ->
                io:format("✗ ~p/~p MISSING\n", [Func, Arity])
        end
    end, ExpectedFunctions),

    io:format("\nAll function checks completed.\n").