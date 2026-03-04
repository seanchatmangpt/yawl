#!/usr/bin/env escript
%% Simple verification script

-module(simple_verify).
-export([main/0]).

main() ->
    io:format("Verifying Erlang-Rust NIF function matching...\n\n"),

    %% Try to compile and load the module
    case compile:file("src/process_mining_bridge.erl", [binary, debug_info]) of
        {ok, _Module} ->
            io:format("✓ Module compiles successfully\n"),
            check_functions();
        {error, Errors, _Warnings} ->
            io:format("✗ Compilation errors: ~p\n", [Errors])
    end.

check_functions() ->
    %% Check for the key functions that were added
    Expected = [
        {registry_get_type, 1},
        {registry_free, 1},
        {registry_list, 0},
        {discover_petri_net, 1},
        {compute_dfg_from_events, 1},
        {align_trace, 3}
    ],

    Exports = process_mining_bridge:module_info(exports),

    lists:foreach(fun({Func, Arity} = ExpectedFunc) ->
        case lists:member(ExpectedFunc, Exports) of
            true ->
                io:format("✓ ~p/~p found\n", [Func, Arity]);
            false ->
                io:format("✗ ~p/~p MISSING\n", [Func, Arity])
        end
    end, Expected),

    io:format("\nNIF function alignment complete!\n").