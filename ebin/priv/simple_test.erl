#!/usr/bin/env escript

%% Simple test to verify JTBD 4 functionality
main(_) ->
    io:format("=== JTBD 4: LOOP ACCUMULATION ===~n"),
    
    %% Add ebin to path
    code:add_patha("ebin"),
    code:add_patha("../ebin"),
    
    %% Start mnesia
    case application:ensure_started(mnesia) of
        ok -> io:format("✓ Mnesia started~n");
        {error, _Reason} -> io:format("✗ Mnesia failed~n")
    end,
    
    %% Try to load the module directly
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("✓ Module loaded~n");
        {error, Reason} ->
            io:format("✗ Module load failed: ~p~n", [Reason]),
            halt(1)
    end,
    
    %% Check if functions exist
    case erlang:function_exported(process_mining_bridge, import_ocel_json, 1) of
        true ->
            io:format("✓ import_ocel_json function exists~n");
        false ->
            io:format("✗ import_ocel_json function missing~n")
    end,
    
    case erlang:function_exported(process_mining_bridge, discover_petri_net, 1) of
        true ->
            io:format("✓ discover_petri_net function exists~n");
        false ->
            io:format("✗ discover_petri_net function missing~n")
    end,
    
    case erlang:function_exported(process_mining_bridge, token_replay, 2) of
        true ->
            io:format("✓ token_replay function exists~n");
        false ->
            io:format("✗ token_replay function missing~n")
    end,
    
    io:format("=== Test completed ===~n").
