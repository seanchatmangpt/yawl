%% Test full DSPy workflow
-module(test_dspy_clean).
-export([run/0]).

run() ->
    io:format("=== DSPy Full Workflow Test ===\n"),
    
    %% 1. Initialize DSPy
    io:format("\n1. Initializing DSPy...\n"),
    Config = <<"{\"provider\": \"groq\", \"model\": \"llama-3.1-70b-chat\"}">>,
    case yawl_ml_bridge:dspy_init(Config) of
        ok -> io:format("✓ DSPy initialized\n");
        {error, _} -> io:format("✗ Init failed\n"), halt(1)
    end,
    
    %% 2. Define signature
    io:format("\n2. Creating signature...\n"),
    Signature = #{
        description => "Answer questions based on context",
        inputs => ["question", "context"],
        outputs => ["answer"]
    },
    SigJson = encode_json(Signature),
    
    %% 3. Prepare inputs
    io:format("\n3. Preparing inputs...\n"),
    Inputs = #{
        question => "What is the capital of France?",
        context => "France is a country in Europe."
    },
    InJson = encode_json(Inputs),
    
    %% 4. Make prediction
    io:format("\n4. Making prediction...\n"),
    case yawl_ml_bridge:dspy_predict(SigJson, InJson, none) of
        {ok, ResultJson} ->
            Result = decode_json(ResultJson),
            io:format("✓ Prediction successful: ~p\n", [Result]);
        {error, _} ->
            io:format("✗ Prediction failed\n")
    end,
    
    io:format("\n=== DSPy Test Complete ===\n").


encode_json(Term) ->
    iolist_to_binary(json:encode(Term)).

decode_json(Binary) ->
    json:decode(Binary).
