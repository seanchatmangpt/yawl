%% Comprehensive test for ALL TPOT2 NIF functions
%% Tests: tpot2_init, tpot2_optimize, tpot2_get_best_pipeline, tpot2_get_fitness, error handling
-module(test_tpot2_comprehensive).
-export([run/0]).

run() ->
    io:format("=== TPOT2 Comprehensive Test Suite ===\n"),

    %% Initialize NIF
    code:ensure_loaded(yawl_ml_bridge),
    io:format("✓ NIF module loaded\n"),

    %% Test 1: TPOT2 Initialization
    io:format("\n=== Test 1: TPOT2 Initialization ===\n"),
    test_tpot2_init(),

    %% Test 2: TPOT2 Optimization with small dataset
    io:format("\n=== Test 2: TPOT2 Optimization ===\n"),
    test_tpot2_optimize(),

    %% Test 3: TPOT2 Get Best Pipeline
    io:format("\n=== Test 3: TPOT2 Get Best Pipeline ===\n"),
    test_tpot2_get_best_pipeline(),

    %% Test 4: TPOT2 Get Fitness
    io:format("\n=== Test 4: TPOT2 Get Fitness ===\n"),
    test_tpot2_get_fitness(),

    %% Test 5: Error Handling
    io:format("\n=== Test 5: Error Handling ===\n"),
    test_error_handling(),

    io:format("\n=== All TPOT2 Tests Completed ===\n").

%% Test TPOT2 Initialization
test_tpot2_init() ->
    %% Test with empty config
    case yawl_ml_bridge:tpot2_init(<<"{}">>) of
        ok ->
            io:format("✓ TPOT2 initialized with empty config\n");
        {error, Reason} ->
            io:format("✗ TPOT2 init failed with empty config: ~p\n", [Reason]);
        Other ->
            io:format("✗ TPOT2 init unexpected result: ~p\n", [Other])
    end,

    %% Test with minimal config
    MinimalConfig = <<"{\"max_generations\": 1, \"population_size\": 2}">>,
    case yawl_ml_bridge:tpot2_init(MinimalConfig) of
        ok ->
            io:format("✓ TPOT2 initialized with minimal config\n");
        {error, Reason2} ->
            io:format("✗ TPOT2 init failed with minimal config: ~p\n", [Reason2]);
        Other2 ->
            io:format("✗ TPOT2 init unexpected result: ~p\n", [Other2])
    end.

%% Test TPOT2 Optimization
test_tpot2_optimize() ->
    %% Create small synthetic dataset
    X = [[1.0, 2.0], [2.0, 3.0], [3.0, 4.0], [4.0, 5.0]],
    Y = [2.0, 3.0, 4.0, 5.0],

    %% Config for fast optimization
    Config = #{
        max_generations => 2,
        population_size => 3,
        max_evaluations_per_individual => 5,
        crossover_rate => 0.9,
        mutation_rate => 0.1,
        verbose => false
    },

    XJson = encode_json(X),
    YJson = encode_json(Y),
    ConfigJson = encode_json(Config),

    case yawl_ml_bridge:tpot2_optimize(XJson, YJson, ConfigJson) of
        {ok, Result} ->
            io:format("✓ TPOT2 optimization succeeded: ~p\n", [Result]);
        {error, Reason} ->
            io:format("✗ TPOT2 optimization failed: ~p\n", [Reason]);
        Other ->
            io:format("✗ TPOT2 optimization unexpected result: ~p\n", [Other])
    end.

%% Test TPOT2 Get Best Pipeline
test_tpot2_get_best_pipeline() ->
    %% First run optimization to get an ID
    X = [[1.0, 2.0], [2.0, 3.0]],
    Y = [2.0, 3.0],

    Config = #{
        max_generations => 1,
        population_size => 2
    },

    XJson = encode_json(X),
    YJson = encode_json(Y),
    ConfigJson = encode_json(Config),

    case yawl_ml_bridge:tpot2_optimize(XJson, YJson, ConfigJson) of
        {ok, Result} ->
            case Result of
                #{pipeline_id := Id} ->
                    case yawl_ml_bridge:tpot2_get_best_pipeline(Id) of
                        {ok, Pipeline} ->
                            io:format("✓ TPOT2 best pipeline retrieved: ~p\n", [Pipeline]);
                        {error, Reason} ->
                            io:format("✗ TPOT2 get best pipeline failed: ~p\n", [Reason]);
                        Other ->
                            io:format("✗ TPOT2 get best pipeline unexpected: ~p\n", [Other])
                    end;
                _ ->
                    io:format("✗ TPOT2 optimization result missing pipeline_id\n")
            end;
        {error, Reason} ->
            io:format("✗ TPOT2 optimization failed (can't test best pipeline): ~p\n", [Reason])
    end.

%% Test TPOT2 Get Fitness
test_tpot2_get_fitness() ->
    %% First run optimization to get an ID
    X = [[1.0], [2.0], [3.0]],
    Y = [1.0, 2.0, 3.0],

    Config = #{
        max_generations => 1,
        population_size => 2
    },

    XJson = encode_json(X),
    YJson = encode_json(Y),
    ConfigJson = encode_json(Config),

    case yawl_ml_bridge:tpot2_optimize(XJson, YJson, ConfigJson) of
        {ok, Result} ->
            case Result of
                #{pipeline_id := Id} ->
                    case yawl_ml_bridge:tpot2_get_fitness(Id) of
                        {ok, Fitness} ->
                            io:format("✓ TPOT2 fitness retrieved: ~p\n", [Fitness]);
                        {error, Reason} ->
                            io:format("✗ TPOT2 get fitness failed: ~p\n", [Reason]);
                        Other ->
                            io:format("✗ TPOT2 get fitness unexpected: ~p\n", [Other])
                    end;
                _ ->
                    io:format("✗ TPOT2 optimization result missing pipeline_id\n")
            end;
        {error, Reason} ->
            io:format("✗ TPOT2 optimization failed (can't test fitness): ~p\n", [Reason])
    end.

%% Test Error Handling
test_error_handling() ->
    %% Test 1: Invalid JSON config
    case yawl_ml_bridge:tpot2_init(<<"invalid json">>) of
        {error, Reason} ->
            io:format("✓ Error handling for invalid JSON: ~p\n", [Reason]);
        ok ->
            io:format("✗ Should have failed with invalid JSON but succeeded\n");
        Other ->
            io:format("✗ Unexpected result for invalid JSON: ~p\n", [Other])
    end,

    %% Test 2: Non-existent pipeline ID
    case yawl_ml_bridge:tpot2_get_best_pipeline(<<"non_existent_id">>) of
        {error, Reason2} ->
            io:format("✓ Error handling for non-existent pipeline: ~p\n", [Reason2]);
        {ok, _} ->
            io:format("✗ Should have failed for non-existent pipeline but succeeded\n");
        Other2 ->
            io:format("✗ Unexpected result for non-existent pipeline: ~p\n", [Other2])
    end,

    %% Test 3: Get fitness without optimization
    case yawl_ml_bridge:tpot2_get_fitness(<<"no_such_id">>) of
        {error, Reason3} ->
            io:format("✓ Error handling for fitness without optimization: ~p\n", [Reason3]);
        {ok, _} ->
            io:format("✗ Should have failed for fitness without optimization\n");
        Other3 ->
            io:format("✗ Unexpected result for fitness without optimization: ~p\n", [Other3])
    end,

    %% Test 4: Empty X and Y arrays
    EmptyX = [],
    EmptyY = [],
    Config = #{max_generations => 1},

    case yawl_ml_bridge:tpot2_optimize(encode_json(EmptyX), encode_json(EmptyY), encode_json(Config)) of
        {error, Reason4} ->
            io:format("✓ Error handling for empty data: ~p\n", [Reason4]);
        {ok, _} ->
            io:format("⚠ Empty data optimization succeeded (might be valid)\n");
        Other4 ->
            io:format("✗ Unexpected result for empty data: ~p\n", [Other4])
    end.

%% Helper function to encode JSON
encode_json(Term) ->
    try
        case json:encode(Term) of
            JsonString when is_list(JsonString) ->
                iolist_to_binary(JsonString);
            JsonBinary when is_binary(JsonBinary) ->
                JsonBinary
        end
    catch
        _:_ ->
            iolist_to_binary(io_lib:format("~p", [Term]))
    end.