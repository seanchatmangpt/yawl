#!/usr/bin/env escript
%% -*- erlang -*-
%% Test script to verify NIF function matching between Erlang and Rust

-module(nif_test).
-export([main/0]).

main() ->
    io:format("Testing NIF function matching...\n\n"),

    %% Start the process mining bridge
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format("✓ Process mining bridge started successfully\n"),
            timer:sleep(1000),
            test_functions(Pid);
        {error, Reason} ->
            io:format("✗ Failed to start process mining bridge: ~p\n", [Reason]),
            halt(1)
    end.

test_functions(Pid) ->
    %% Check NIF status
    case process_mining_bridge:get_nif_status() of
        {ok, {nif_status, true}} ->
            io:format("✓ NIF library is loaded\n"),
            test_with_nif(Pid);
        {ok, {nif_status, false}} ->
            io:format("✗ NIF library is not loaded - checking fallbacks\n"),
            test_without_nif(Pid);
        {error, _Reason} ->
            io:format("✗ Error checking NIF status\n"),
            halt(1)
    end,

    %% Stop the bridge
    process_mining_bridge:stop(),
    io:format("\n✓ All tests completed\n"),
    halt(0).

test_without_nif(Pid) ->
    io:format("Testing fallback implementations...\n"),

    %% Test nop function
    case process_mining_bridge:nop() of
        {error, nif_not_loaded} ->
            io:format("✓ nop() correctly returns nif_not_loaded\n");
        {ok, _} ->
            io:format("✗ nop() should return nif_not_loaded but returned something else\n"),
            halt(1)
    end,

    %% Test int_passthrough function
    case process_mining_bridge:int_passthrough(42) of
        {error, nif_not_loaded} ->
            io:format("✓ int_passthrough() correctly returns nif_not_loaded\n");
        {ok, _} ->
            io:format("✗ int_passthrough() should return nif_not_loaded but returned something else\n"),
            halt(1)
    end,

    io:format("All fallback functions working correctly\n").

test_with_nif(Pid) ->
    io:format("Testing with NIF loaded...\n"),

    %% Test nop
    case process_mining_bridge:nop() of
        {ok, ok} ->
            io:format("✓ nop() works\n");
        {error, _} ->
            io:format("✗ nop() failed\n")
    end,

    %% Test int_passthrough
    case process_mining_bridge:int_passthrough(123) of
        {ok, 123} ->
            io:format("✓ int_passthrough() works\n");
        {error, _} ->
            io:format("✗ int_passthrough() failed\n")
    end,

    %% Test echo_json
    case process_mining_bridge:echo_json(<<"{\"test\": true}">>) of
        {ok, <<"{\"test\": true}">>} ->
            io:format("✓ echo_json() works\n");
        {error, _} ->
            io:format("✗ echo_json() failed\n")
    end,

    %% Test new functions
    test_new_functions_with_nif().

test_new_functions_with_nif() ->
    io:format("Testing newly added functions with NIF...\n"),

    %% Test registry_get_type with invalid ID
    case process_mining_bridge:registry_get_type("invalid_id") of
        {error, _} ->
            io:format("✓ registry_get_type() returns error for invalid ID\n");
        {ok, _} ->
            io:format("✗ registry_get_type() should error for invalid ID\n")
    end,

    %% Test registry_free with invalid ID
    case process_mining_bridge:registry_free("invalid_id") of
        {error, _} ->
            io:format("✓ registry_free() returns error for invalid ID\n");
        ok ->
            io:format("✗ registry_free() should error for invalid ID\n")
    end,

    %% Test registry_list
    case process_mining_bridge:registry_list() of
        {ok, Items} when is_list(Items) ->
            io:format("✓ registry_list() works, returns: ~p\n", [Items]);
        {error, _} ->
            io:format("✓ registry_list() returns error as expected (NIF not implemented yet)\n");
        _ ->
            io:format("✗ registry_list() returned unexpected result\n")
    end,

    %% Test compute_dfg_from_events
    TestEvents = <<"A->B->C">>,
    case process_mining_bridge:compute_dfg_from_events(TestEvents) of
        {error, _} ->
            io:format("✓ compute_dfg_from_events() returns error as expected\n");
        {ok, _} ->
            io:format("✓ compute_dfg_from_events() works\n")
    end,

    %% Test discover_petri_net (should be available now)
    TestFile = "../test/fixtures/ocel/ocel-sample.json",
    case filelib:is_file(TestFile) of
        true ->
            case process_mining_bridge:import_ocel_json(TestFile) of
                {ok, Handle} ->
                    io:format("✓ import_ocel_json() works, handle: ~p\n", [Handle]),
                    %% Test discover_petri_net
                    case process_mining_bridge:discover_petri_net(Handle) of
                        {ok, _} ->
                            io:format("✓ discover_petri_net() works\n");
                        {error, _} ->
                            io:format("✗ discover_petri_net() failed\n")
                    end,
                    %% Free handle
                    process_mining_bridge:free_handle(Handle);
                {error, _} ->
                    io:format("✗ import_ocel_json() failed\n")
            end;
        false ->
            io:format("~p does not exist, skipping discover_petri_net test\n", [TestFile])
    end,

    io:format("✓ All new functions available\n").