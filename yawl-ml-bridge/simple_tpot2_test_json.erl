%% Simple test for TPOT2 functions with proper JSON strings
-module(simple_tpot2_test_json).
-export([run/0]).

run() ->
    io:format("=== Simple TPOT2 Test (JSON Strings) ===\n"),

    % Test basic ping
    case yawl_ml_bridge:ping() of
        pong -> io:format("✓ NIF ping works\n");
        _ -> io:format("✗ NIF ping failed\n")
    end,

    % Test TPOT2 init
    case yawl_ml_bridge:tpot2_init(<<"{}">>) of
        ok -> io:format("✓ TPOT2 init works\n");
        {error, Reason} -> io:format("✗ TPOT2 init failed: ~p\n", [Reason]);
        _ -> io:format("✗ TPOT2 init unexpected\n")
    end,

    % Test TPOT2 optimize with proper JSON strings
    XJson = "[[1.0], [2.0]]",
    YJson = "[1.0, 2.0]",
    ConfigJson = "{\"generations\": 1, \"population_size\": 1, \"timeout_minutes\": 1}",

    case yawl_ml_bridge:tpot2_optimize(
        XJson,
        YJson,
        ConfigJson
    ) of
        {ok, Result} ->
            io:format("✓ TPOT2 optimize works: ~p\n", [Result]);
        {error, Reason2} ->
            io:format("✗ TPOT2 optimize failed: ~p\n", [Reason2]);
        _ ->
            io:format("✗ TPOT2 optimize unexpected\n")
    end,

    % Test TPOT2 get fitness (need a pipeline ID first)
    case yawl_ml_bridge:tpot2_get_fitness(<<"test_id">>) of
        {error, Reason3} ->
            io:format("✓ Error handling for fitness test: ~p\n", [Reason3]);
        {ok, Fitness} ->
            io:format("✓ TPOT2 fitness test: ~p\n", [Fitness]);
        _ ->
            io:format("✗ TPOT2 fitness test unexpected\n")
    end,

    % Test TPOT2 get best pipeline
    case yawl_ml_bridge:tpot2_get_best_pipeline(<<"test_id">>) of
        {error, Reason4} ->
            io:format("✓ Error handling for pipeline test: ~p\n", [Reason4]);
        {ok, Pipeline} ->
            io:format("✓ TPOT2 pipeline test: ~p\n", [Pipeline]);
        _ ->
            io:format("✗ TPOT2 pipeline test unexpected\n")
    end,

    io:format("=== Test Complete ===\n").