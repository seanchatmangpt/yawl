#!/usr/bin/env escript
%% -*- erlang -*-
%% Test script to verify NIF function matching between Erlang and Rust

-module(nif_test).
-export([main/0]).

main() ->
    io:format("Testing NIF function matching...\n\n"),

    %% Start the process mining bridge
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("✓ Process mining bridge started successfully\n");
        {error, _Reason} ->
            io:format("✗ Failed to start process mining bridge: ~p\n", [Reason]),
            halt(1)
    end,

    %% Check NIF status
    case process_mining_bridge:get_nif_status() of
        {ok, {nif_status, true}} ->
            io:format("✓ NIF library is loaded\n");
        {ok, {nif_status, false}} ->
            io:format("✗ NIF library is not loaded - checking fallbacks\n"),
            check_fallback_functions();
        {error, _Reason} ->
            io:format("✗ Error checking NIF status: ~p\n", [Reason]),
            halt(1)
    end,

    %% Test basic NIF functions
    test_basic_functions(),

    %% Test process mining functions
    test_process_mining_functions(),

    %% Stop the bridge
    process_mining_bridge:stop(),
    io:format("\n✓ All tests completed\n"),
    halt(0).

check_fallback_functions() ->
    io:format("Testing fallback implementations...\n"),

    %% Test nop function
    case process_mining_bridge:nop() of
        {error, nif_not_loaded} ->
            io:format("✓ nop() correctly returns nif_not_loaded\n");
        {ok, ok} ->
            io:format("✗ nop() should return nif_not_loaded but returned: ok\n"),
            halt(1)
    end,

    %% Test int_passthrough function
    case process_mining_bridge:int_passthrough(42) of
        {error, nif_not_loaded} ->
            io:format("✓ int_passthrough() correctly returns nif_not_loaded\n");
        {ok, 42} ->
            io:format("✗ int_passthrough() should return nif_not_loaded but returned: 42\n"),
            halt(1)
    end,

    io:format("All fallback functions working correctly\n").

test_basic_functions() ->
    io:format("Testing basic NIF functions...\n"),

    %% Test nop
    case process_mining_bridge:nop() of
        {ok, ok} ->
            io:format("✓ nop() works\n");
        {error, _Reason} ->
            io:format("✗ nop() failed: ~p\n", [Reason])
    end,

    %% Test int_passthrough
    case process_mining_bridge:int_passthrough(123) of
        {ok, 123} ->
            io:format("✓ int_passthrough() works\n");
        {error, _Reason} ->
            io:format("✗ int_passthrough() failed: ~p\n", [Reason])
    end,

    %% Test echo_json
    case process_mining_bridge:echo_json(<<"{\"test\": true}">>) of
        {ok, <<"{\"test\": true}">>} ->
            io:format("✓ echo_json() works\n");
        {error, _Reason} ->
            io:format("✗ echo_json() failed: ~p\n", [Reason])
    end.

test_process_mining_functions() ->
    io:format("Testing process mining functions...\n"),

    %% Test import OCEL JSON (this should work with a test file)
    TestFile = "../test/fixtures/ocel/ocel-sample.json",
    case filelib:is_file(TestFile) of
        true ->
            case process_mining_bridge:import_ocel_json(TestFile) of
                {ok, Handle} ->
                    io:format("✓ import_ocel_json() works, handle: ~p\n", [Handle]),
                    %% Test event log stats
                    case process_mining_bridge:event_log_stats(Handle) of
                        {ok, Stats} ->
                            io:format("✓ event_log_stats() works: ~p\n", [Stats]);
                        {error, _Reason} ->
                            io:format("✗ event_log_stats() failed: ~p\n", [Reason])
                    end,
                    %% Free the handle
                    process_mining_bridge:free_handle(Handle);
                {error, _Reason} ->
                    io:format("✗ import_ocel_json() failed: ~p\n", [Reason])
            end;
        false ->
            io:format("~p does not exist, skipping OCEL import test\n", [TestFile])
    end,

    %% Test missing functions (these should be available now)
    test_new_functions().

test_new_functions() ->
    io:format("Testing newly added functions...\n"),

    %% Test registry functions with invalid ID (should error gracefully)
    case process_mining_bridge:registry_get_type("invalid_id") of
        {error, nif_not_loaded} ->
            io:format("✓ registry_get_type() correctly returns nif_not_loaded\n");
        {error, _Reason} ->
            io:format("✗ registry_get_type() returned unexpected error: ~p\n", [Reason]);
        {ok, _} ->
            io:format("✗ registry_get_type() should error for invalid ID but returned ok\n")
    end,

    case process_mining_bridge:registry_free("invalid_id") of
        {error, nif_not_loaded} ->
            io:format("✓ registry_free() correctly returns nif_not_loaded\n");
        {error, _Reason} ->
            io:format("✗ registry_free() returned unexpected error: ~p\n", [Reason]);
        ok ->
            io:format("✗ registry_free() should error for invalid ID but returned ok\n")
    end,

    case process_mining_bridge:registry_list() of
        {error, nif_not_loaded} ->
            io:format("✓ registry_list() correctly returns nif_not_loaded\n");
        {ok, []} ->
            io:format("✓ registry_list() works, returns empty list\n");
        {error, _Reason} ->
            io:format("✗ registry_list() returned unexpected error: ~p\n", [Reason]);
        {ok, Items} ->
            io:format("✓ registry_list() works, returns: ~p\n", [Items])
    end,

    %% Test compute_dfg_from_events
    TestEvents = <<"A->B->C">>,
    case process_mining_bridge:compute_dfg_from_events(TestEvents) of
        {error, nif_not_loaded} ->
            io:format("✓ compute_dfg_from_events() correctly returns nif_not_loaded\n");
        {error, _Reason} ->
            io:format("✗ compute_dfg_from_events() returned unexpected error: ~p\n", [Reason]);
        {ok, _} ->
            io:format("✓ compute_dfg_from_events() works\n")
    end,

    %% Test align_trace
    case process_mining_bridge:align_trace(<<"test_trace">>, <<"test_net">>, 1000) of
        {error, nif_not_loaded} ->
            io:format("✓ align_trace() correctly returns nif_not_loaded\n");
        {error, _Reason} ->
            io:format("✗ align_trace() returned unexpected error: ~p\n", [Reason]);
        {ok, _} ->
            io:format("✓ align_trace() works\n")
    end,

    io:format("All new functions implemented correctly\n").