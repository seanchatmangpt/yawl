#!/usr/bin/env escript
%% -*- erlang -*-
%% Comprehensive verification of Erlang-Rust NIF function matching

-module(verify_functions).
-export([main/0]).

main() ->
    io:format("=== Erlang-Rust NIF Function Matching Verification ===\n\n"),

    %% List all expected functions from Erlang
    erlang_functions(),

    %% List all exported functions from Rust
    rust_functions(),

    %% Check for matches
    verify_matches(),

    io:format("\n=== Verification Complete ===\n"),
    halt(0).

erlang_functions() ->
    io:format("Erlang exported functions:\n"),
    Exports = process_mining_bridge:module_info(exports),

    %% Filter for NIF-related functions
    NifFunctions = lists:filter(fun({Func, Arity}) ->
        case atom_to_list(Func) of
            "discover_" ++ _ -> true;
            "registry_" ++ _ -> true;
            "compute_" ++ _ -> true;
            "align_" ++ _ -> true;
            "token_replay" -> true;
            "event_log_stats" -> true;
            "import_xes" -> true;
            "export_xes" -> true;
            "import_ocel_" ++ _ -> true;
            "export_ocel_" ++ _ -> true;
            "import_pnml" -> true;
            "export_pnml" -> true;
            "nop" -> true;
            "int_passthrough" -> true;
            "echo_json" -> true;
            "calculate_performance_metrics" -> true;
            "get_activity_frequency" -> true;
            "find_longest_traces" -> true;
            "free_handle" -> true;
            true
        end
    end, Exports),

    io:format("  ~p\n", [length(NifFunctions)]),
    [io:format("  - ~p/~p\n", [Func, Arity]) || {Func, Arity} <- NifFunctions],
    io:format("\n").

rust_functions() ->
    io:format("Rust exported functions:\n"),

    %% The Rust NIF functions are hardcoded in the init! macro
    RustFunctions = [
        {nop, 0},
        {int_passthrough, 1},
        {echo_json, 1},
        {echo_term, 1},
        {echo_binary, 1},
        {echo_ocel_event, 1},
        {large_list_transfer, 1},
        {import_ocel_json_path, 1},
        {import_xes_path, 1},
        {num_events, 1},
        {num_objects, 1},
        {index_link_ocel, 1},
        {slim_link_ocel, 1},
        {ocel_type_stats, 1},
        {compute_dfg, 1},
        {discover_dfg, 1},
        {compute_dfg_from_events, 1},
        {align_trace, 2},  % Note: this takes 2 args in Rust but 3 in Erlang
        {discover_dfg_ocel, 1},
        {discover_alpha, 1},
        {discover_petri_net, 1},
        {token_replay, 2},
        {registry_get_type, 1},
        {registry_free, 1},
        {registry_list, 0}
    ],

    io:format("  ~p\n", [length(RustFunctions)]),
    [io:format("  - ~p/~p\n", [Func, Arity]) || {Func, Arity} <- RustFunctions],
    io:format("\n").

verify_matches() ->
    io:format("Function matching analysis:\n"),

    ErlangExports = process_mining_bridge:module_info(exports),
    RustFunctions = [
        {nop, 0},
        {int_passthrough, 1},
        {echo_json, 1},
        {echo_term, 1},
        {echo_binary, 1},
        {echo_ocel_event, 1},
        {large_list_transfer, 1},
        {import_ocel_json_path, 1},
        {import_xes_path, 1},
        {num_events, 1},
        {num_objects, 1},
        {index_link_ocel, 1},
        {slim_link_ocel, 1},
        {ocel_type_stats, 1},
        {compute_dfg, 1},
        {discover_dfg, 1},
        {compute_dfg_from_events, 1},
        {align_trace, 2},  % Signature mismatch
        {discover_dfg_ocel, 1},
        {discover_alpha, 1},
        {discover_petri_net, 1},
        {token_replay, 2},
        {registry_get_type, 1},
        {registry_free, 1},
        {registry_list, 0}
    ],

    %% Check for exact matches
    ExactMatches = lists:filter(fun(RustFunc) ->
        lists:member(RustFunc, ErlangExports)
    end, RustFunctions),

    io:format("  Exact matches: ~p/~p\n", [length(ExactMatches), length(RustFunctions)]),

    %% Check for missing functions
    Missing = lists:filter(fun(RustFunc) ->
        not lists:member(RustFunc, ErlangExports)
    end, RustFunctions),

    case Missing of
        [] ->
            io:format("  ✓ No missing functions\n");
        _ ->
            io:format("  ✗ Missing functions: ~p\n", [length(Missing)]),
            [io:format("    - ~p/~p\n", [Func, Arity]) || {Func, Arity} <- Missing]
    end,

    %% Check for extra Erlang functions
    Extra = lists:filter(fun(ErlangFunc) ->
        not lists:member(ErlangFunc, RustFunctions)
    end, ErlangExports),

    case Extra of
        [] ->
            io:format("  ✓ No extra functions\n");
        _ ->
            io:format("  ⚠ Extra Erlang functions: ~p\n", [length(Extra)]),
            [io:format("    - ~p/~p\n", [Func, Arity]) || {Func, Arity} <- Extra]
    end,

    %% Note signature differences
    io:format("\nNote: Some functions have different signatures:\n"),
    io:format("  - align_trace: Rust(2) vs Erlang(3) - Erlang adds timeout parameter\n"),
    io:format("  - Other mappings are direct translations\n").