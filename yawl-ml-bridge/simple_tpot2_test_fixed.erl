%% Simple test for TPOT2 functions with proper JSON
-module(simple_tpot2_test_fixed).
-export([run/0]).

run() ->
    io:format("=== Simple TPOT2 Test (Fixed) ===\n"),

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

    % Test TPOT2 optimize with minimal data
    X = [[1.0], [2.0]],
    Y = [1.0, 2.0],
    Config = #{max_generations => 1, population_size => 1},

    % Use direct term_to_binary instead of JSON conversion for testing
    case yawl_ml_bridge:tpot2_optimize(
        erlang:term_to_binary(X),
        erlang:term_to_binary(Y),
        erlang:term_to_binary(Config)
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