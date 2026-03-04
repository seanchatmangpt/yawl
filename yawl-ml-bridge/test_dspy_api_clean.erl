%% Test DSPy API wrapper
-module(test_dspy_api_clean).
-export([run/0]).

run() ->
    io:format("=== DSPy API Wrapper Test ===\n"),
    
    %% 1. Initialize DSPy via API
    io:format("\n1. Initializing DSPy via API...\n"),
    Config = #{provider => groq, model => "llama-3.1-70b-chat"},
    case yawl_ml_bridge:dspy_init_api(Config) of
        {ok, _} -> io:format("✓ DSPy initialized via API\n");
        {error, _} -> io:format("✗ Init failed\n")
    end,
    
    %% 2. Create a simple prediction
    io:format("\n2. Making simple prediction...\n"),
    Signature = #{
        description => "Simple question answering",
        inputs => ["question"],
        outputs => ["answer"]
    },
    Inputs = #{question => "What is 2+2?"},
    Examples = [],
    
    case yawl_ml_bridge:dspy_predict_api(Signature, Inputs, Examples) of
        {ok, Result} -> 
            io:format("✓ Prediction successful: ~p\n", [Result]);
        {error, _} ->
            io:format("✗ Prediction failed\n")
    end,
    
    io:format("\n=== DSPy API Test Complete ===\n").
