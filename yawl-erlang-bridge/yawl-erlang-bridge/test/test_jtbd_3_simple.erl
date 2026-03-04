%% Simple test script for JTBD 3
%% This script handles compilation and execution gracefully

-module(test_jtbd_3_simple).
-export([run/0]).

run() ->
    io:format("=== JTBD 3 Test: OC-DECLARE Constraints Discovery ===~n"),

    %% Initialize output directory
    ok = filelib:ensure_dir("/tmp/jtbd/output/"),

    %% Check if required modules can be compiled
    case catch compile_module(jtbd_3_constraints) of
        {ok, _} ->
            %% Try to run the test
            case catch jtbd_3_constraints:run() of
                {ok, Result} ->
                    io:format("✅ Test completed successfully~n"),
                    io:format("Result: ~p~n", [Result]),
                    0;
                {'EXIT', Reason} ->
                    io:format("❌ Test execution failed: ~p~n", [Reason]),
                    1;
                {error, Reason} ->
                    io:format("❌ Test failed: ~p~n", [Reason]),
                    1
            end;
        {error, Reason} ->
            io:format("❌ Compilation failed: ~p~n", [Reason]),
            1
    end.

compile_module(Module) ->
    %% Try to compile the module
    case compile:file(Module,
                    [return, debug_info,
                     {i, "../include"},
                     {outdir, "../ebin"}]) of
        {ok, Module} ->
            {ok, Module};
        {ok, Module, _Binary} ->
            {ok, Module};
        {error, Errors, _Warnings} ->
            {error, Errors};
        {error, Reason} ->
            {error, Reason}
    end.