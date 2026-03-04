%% Step by step TPOT2 test
-module(test_tpot2_step_by_step).
-export([run/0]).

run() ->
    io:format("=== TPOT2 Step-by-Step Test ===\n"),

    % Load NIF
    code:ensure_loaded(yawl_ml_bridge),
    io:format("NIF module loaded\n"),

    % Test 1: tpot2_init
    io:format("\n1. Testing tpot2_init...\n"),
    case yawl_ml_bridge:tpot2_init(<<"{}">>) of
        ok -> io:format("   ✓ tpot2_init() with empty config: SUCCESS\n");
        {error, Reason} -> io:format("   ✗ tpot2_init() with empty config: ~p\n", [Reason]);
        _ -> io:format("   ✗ tpot2_init() with empty config: Unexpected result\n")
    end,

    % Test 2: tpot2_init with config
    io:format("\n2. Testing tpot2_init with config...\n"),
    Config = <<"{\"generations\": 1, \"population_size\": 2, \"timeout_minutes\": 1}">>,
    case yawl_ml_bridge:tpot2_init(Config) of
        ok -> io:format("   ✓ tpot2_init() with config: SUCCESS\n");
        {error, Reason2} -> io:format("   ✗ tpot2_init() with config: ~p\n", [Reason2]);
        _ -> io:format("   ✗ tpot2_init() with config: Unexpected result\n")
    end,

    % Test 3: tpot2_optimize
    io:format("\n3. Testing tpot2_optimize...\n"),
    X = [[1.0], [2.0]],
    Y = [1.0, 2.0],
    OptConfig = #{
        generations => 1,
        population_size => 2,
        timeout_minutes => 1
    },
    case yawl_ml_bridge:tpot2_optimize(
        iolist_to_binary(io_lib:format("~p", [X])),
        iolist_to_binary(io_lib:format("~p", [Y])),
        iolist_to_binary(io_lib:format("~p", [OptConfig]))
    ) of
        {ok, Result} -> io:format("   ✓ tpot2_optimize(): SUCCESS - ~p\n", [Result]);
        {error, Reason3} -> io:format("   ✗ tpot2_optimize(): ~p\n", [Reason3]);
        _ -> io:format("   ✗ tpot2_optimize(): Unexpected result\n")
    end,

    % Test 4: tpot2_get_best_pipeline
    io:format("\n4. Testing tpot2_get_best_pipeline...\n"),
    case yawl_ml_bridge:tpot2_get_best_pipeline(<<"test_id">>) of
        {ok, Pipeline} -> io:format("   ✓ tpot2_get_best_pipeline(): SUCCESS - ~p\n", [Pipeline]);
        {error, Reason4} -> io:format("   ✗ tpot2_get_best_pipeline(): ~p\n", [Reason4]);
        _ -> io:format("   ✗ tpot2_get_best_pipeline(): Unexpected result\n")
    end,

    % Test 5: tpot2_get_fitness
    io:format("\n5. Testing tpot2_get_fitness...\n"),
    case yawl_ml_bridge:tpot2_get_fitness(<<"test_id">>) of
        {ok, Fitness} -> io:format("   ✓ tpot2_get_fitness(): SUCCESS - ~p\n", [Fitness]);
        {error, Reason5} -> io:format("   ✗ tpot2_get_fitness(): ~p\n", [Reason5]);
        _ -> io:format("   ✗ tpot2_get_fitness(): Unexpected result\n")
    end,

    % Test 6: Error handling - invalid JSON
    io:format("\n6. Testing error handling - invalid JSON...\n"),
    case yawl_ml_bridge:tpot2_init(<<"not json">>) of
        {error, Reason6} -> io:format("   ✓ tpot2_init() with invalid JSON: SUCCESS - ~p\n", [Reason6]);
        _ -> io:format("   ✗ tpot2_init() with invalid JSON: Should have failed\n")
    end,

    % Test 7: Error handling - invalid pipeline ID
    io:format("\n7. Testing error handling - invalid pipeline ID...\n"),
    case yawl_ml_bridge:tpot2_get_best_pipeline(<<"invalid_id">>) of
        {error, Reason7} -> io:format("   ✓ tpot2_get_best_pipeline() with invalid ID: SUCCESS - ~p\n", [Reason7]);
        _ -> io:format("   ✗ tpot2_get_best_pipeline() with invalid ID: Should have failed\n")
    end,

    io:format("\n=== TPOT2 Step-by-Step Test Complete ===\n").