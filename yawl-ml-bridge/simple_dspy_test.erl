#!/usr/bin/env escript
%% -*- erlang -*-

%% Simple DSPy Test Script

main(_) ->
    io:format("=== SIMPLE DSPy TEST SUITE ===~n~n"),

    % Test 1: Basic functionality
    io:format("Test 1: NIF Loading~n"),
    case test_nif_loading() of
        pass -> io_format("PASS: NIF loading~n");
        fail -> io_format("FAIL: NIF loading~n")
    end,

    io_format("~n"),

    % Test 2: DSPy init
    io_format("Test 2: DSPy Init~n"),
    case test_dspy_init() of
        pass -> io_format("PASS: DSPy init~n");
        fail -> io_format("FAIL: DSPy init~n")
    end,

    io_format("~n"),

    % Test 3: DSPy predict
    io_format("Test 3: DSPy Predict~n"),
    case test_dspy_predict() of
        pass -> io_format("PASS: DSPy predict~n");
        fail -> io_format("FAIL: DSPy predict~n")
    end,

    io_format("~n"),

    % Test 4: DSPy load examples
    io_format("Test 4: DSPy Load Examples~n"),
    case test_dspy_load_examples() of
        pass -> io_format("PASS: DSPy load examples~n");
        fail -> io_format("FAIL: DSPy load examples~n")
    end,

    io_format("~n=== TEST COMPLETE ===~n").

io_format(Str) -> io:format(Str).

test_nif_loading() ->
    try
        code:ensure_loaded(yawl_ml_bridge),
        case yawl_ml_bridge:ping() of
            {ok, <<"pong">>} -> pass;
            _ -> fail
        end
    catch
        _:_ -> fail
    end.

test_dspy_init() ->
    try
        Config = "{\"provider\":\"groq\",\"model\":\"llama-3.3-70b-versatile\"}",
        case yawl_ml_bridge:dspy_init(iolist_to_binary(Config)) of
            ok -> pass;
            _ -> fail
        end
    catch
        _:_ -> fail
    end.

test_dspy_predict() ->
    try
        Signature = "{\"inputs\":[{\"name\":\"question\",\"type\":\"string\"}],\"outputs\":[{\"name\":\"answer\",\"type\":\"string\"}]}",
        Input = "{\"question\":\"What is 2+2?\"}",
        case yawl_ml_bridge:dspy_predict(iolist_to_binary(Signature), iolist_to_binary(Input), <<"none">>) of
            {ok, _} -> pass;
            _ -> fail
        end
    catch
        _:_ -> fail
    end.

test_dspy_load_examples() ->
    try
        Examples = "[{\"inputs\":{\"question\":\"What is the capital of France?\"},\"outputs\":{\"answer\":\"Paris\"}}]",
        case yawl_ml_bridge:dspy_load_examples(iolist_to_binary(Examples)) of
            {ok, _} -> pass;
            _ -> fail
        end
    catch
        _:_ -> fail
    end.