%% Simple test for TPOT2 functions
-module(simple_tpot2_test).
-export([run/0]).

run() ->
    io:format("=== Simple TPOT2 Test ===\n"),

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

    io:format("=== Test Complete ===\n").