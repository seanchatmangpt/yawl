%% Test direct DSPy NIF calls
-module(test_dspy_direct).
-export([run/0]).

run() ->
    io:format("=== Direct DSPy NIF Test ===\n"),
    
    %% 1. Initialize DSPy
    io:format("\n1. Initializing DSPy...\n"),
    Config = "{\"provider\": \"groq\", \"model\": \"llama-3.1-70b-chat\"}",
    case yawl_ml_bridge:dspy_init(Config) of
        ok -> io:format("✓ DSPy initialized\n");
        {error, Reason} -> io:format("✗ Init failed: ~p\n", [Reason]), halt(1)
    end,
    
    %% 2. Make prediction
    io:format("\n2. Making prediction...\n"),
    Signature = #{
        description => "Answer a simple math question",
        inputs => ["question"],
        outputs => ["answer"]
    },
    Inputs = #{question => "What is the sum of 2 and 2?"},
    Examples = [],
    
    SigJson = encode_json(Signature),
    InJson = encode_json(Inputs),
    ExJson = encode_json(Examples),
    
    case yawl_ml_bridge:dspy_predict(SigJson, InJson, ExJson) of
        {ok, ResultJson} ->
            Result = decode_json(ResultJson),
            io:format("✓ Prediction successful: ~p\n", [Result]);
        {error, Reason} ->
            io:format("✗ Prediction failed: ~p\n", [Reason])
    end,
    
    io:format("\n=== Direct DSPy Test Complete ===\n").


encode_json(Term) ->
    iolist_to_binary(json:encode(Term)).

decode_json(Binary) ->
    json:decode(Binary).
