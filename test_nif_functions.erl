#!/usr/bin/env escript
%% -*- erlang -*-
%% Test script for compute_dfg and align_trace functions

main(_) ->
    %% Start the process mining bridge
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format("Started process mining bridge~n"),

            %% Test 1: Compute DFG
            io:format("~n=== Test 1: Compute DFG ===~n"),
            Traces = [
                "A->B->C->D",
                "A->B->E->D",
                "A->C->D"
            ],
            case process_mining_bridge:compute_dfg(Traces) of
                {ok, DfgJson} ->
                    io:format("DFG Result: ~s~n", [DfgJson]);
                {error, Reason} ->
                    io:format("DFG Error: ~p~n", [Reason])
            end,

            %% Test 2: Align Trace
            io:format("~n=== Test 2: Align Trace ===~n"),
            SimpleTrace = ["A", "B", "C"],
            SimplePetriNet = jsx:encode([
                {<<"places">>, [
                    {<<"id">>, <<"p_start">>, {<<"is_start">>, true}, {<<"initial_marking">>, 1}},
                    {<<"id">>, <<"p_end">>, {<<"is_end">>, true}, {<<"initial_marking">>, 0}}
                ]},
                {<<"transitions">>, [
                    {<<"id">>, <<"t_A">>, {<<"name">>, <<"A">>}},
                    {<<"id">>, <<"t_B">>, {<<"name">>, <<"B">>}},
                    {<<"id">>, <<"t_C">>, {<<"name">>, <<"C">>}}
                ]},
                {<<"arcs">>, [
                    {<<"source">>, <<"p_start">>, <<"target">>, <<"t_A">>},
                    {<<"source">>, <<"t_A">>, <<"target">>, <<"t_B">>},
                    {<<"source">>, <<"t_B">>, <<"target">>, <<"t_C">>},
                    {<<"source">>, <<"t_C">>, <<"target">>, <<"p_end">>}
                ]}
            ]),

            case process_mining_bridge:align_trace(SimpleTrace, SimplePetriNet) of
                {ok, AlignmentJson} ->
                    io:format("Alignment Result: ~s~n", [AlignmentJson]);
                {error, Reason} ->
                    io:format("Alignment Error: ~p~n", [Reason])
            end,

            %% Test 3: Benchmark functions
            io:format("~n=== Test 3: Benchmark Functions ===~n"),
            case process_mining_bridge:nop() of
                {ok, ok} ->
                    io:format("nop() test passed~n");
                {error, Reason} ->
                    io:format("nop() test failed: ~p~n", [Reason])
            end,

            case process_mining_bridge:int_passthrough(42) of
                {ok, 42} ->
                    io:format("int_passthrough(42) test passed~n");
                {error, Reason} ->
                    io:format("int_passthrough(42) test failed: ~p~n", [Reason])
            end,

            %% Stop the bridge
            process_mining_bridge:stop(),
            io:format("~nTest completed~n");
        {error, Reason} ->
            io:format("Failed to start process mining bridge: ~p~n", [Reason])
    end.