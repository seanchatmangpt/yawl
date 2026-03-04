#!/usr/bin/env escript
%% -*- erlang -*-

%% Comprehensive DSPy NIF Test Suite
%% Tests all DSPy functions with various providers and scenarios

main(_) ->
    io:format("=== COMPREHENSIVE DSPy NIF TEST SUITE ===~n~n"),

    % Test counters
    TestsPassed = 0,
    TestsTotal = 0,

    % Run all test sections
    {Results, FinalPassed, FinalTotal} = run_all_tests(),

    % Final summary
    io:format("=== FINAL TEST SUMMARY ===~n"),
    io:format("Tests Passed: ~p/~p~n", [FinalPassed, FinalTotal]),
    io:format("Success Rate: ~.1f%%%~n", [FinalPassed/FinalTotal*100]),

    case FinalPassed of
        FinalTotal ->
            io:format("✅ ALL TESTS PASSED~n");
        _ ->
            io:format("❌ SOME TESTS FAILED~n"),
            io:format("Failed tests:~n"),
            [io:format("  - ~p: FAIL~n", [Name]) || {Name, fail} <- Results]
    end.

%% Run all test sections
run_all_tests() ->
    Initial = {[], 0, 0},

    Results =
        [test_nif_loading(),
         test_dspy_init_providers(),
         test_dspy_predict_various(),
         test_dspy_load_examples(),
         test_error_handling(),
         test_configuration_persistence(),
         test_concurrent_predictions()],

    Passed = length([R || {_, pass} <- Results]),
    Total = length(Results),

    {Results, Passed, Total}.

%% Test 1: NIF Loading
test_nif_loading() ->
    io:format("=== Test 1: NIF Loading ===~n"),
    try
        code:ensure_loaded(yawl_ml_bridge),
        case yawl_ml_bridge:ping() of
            {ok, <<"pong">>} ->
                io:format("PASS: NIF loaded successfully~n"),
                {nif_loading, pass};
            _ ->
                io:format("FAIL: NIF ping failed~n"),
                {nif_loading, fail}
        end
    catch
        E:R ->
            io:format("FAIL: NIF loading error: ~p:~p~n", [E, R]),
            {nif_loading, fail}
    end.

%% Test 2: DSPy Init with All Providers
test_dspy_init_providers() ->
    io:format("=== Test 2: DSPy Init with All Providers ===~n"),

    Providers = [
        {groq, #{provider => <<"groq">>, model => <<"llama-3.3-70b-versatile">>}},
        {openai, #{provider => <<"openai">>, model => <<"gpt-4">>}},
        {anthropic, #{provider => <<"anthropic">>, model => <<"claude-3-opus-20240229">>}}
    ],

    Results = lists:map(fun({Name, Config}) ->
        io:format("Testing ~p provider...~n", [Name]),
        try
            ConfigJson = iolist_to_binary(json:encode(Config)),
            case yawl_ml_bridge:dspy_init(ConfigJson) of
                ok ->
                    io:format("  PASS: DSPy init with ~p succeeded~n", [Name]),
                    {Name, pass};
                {error, Reason} ->
                    io:format("  FAIL: DSPy init with ~p failed: ~p~n", [Name, Reason]),
                    {Name, fail}
            catch
                E:R ->
                    io:format("  FAIL: DSPy init with ~p error: ~p:~p~n", [Name, E, R]),
                    {Name, fail}
            end
        end
    end, Providers),

    Passed = length([R || {_, pass} <- Results]),
    Total = length(Results),

    io:format("Provider init results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> {dspy_init_providers, pass};
        _ -> {dspy_init_providers, fail}
    end.

%% Test 3: DSPy Predict with Various Input/Output Combinations
test_dspy_predict_various() ->
    io:format("=== Test 3: DSPy Predict with Various I/O Combinations ===~n"),

    TestCases = [
        {basic_qa,
            #{inputs => [#{name => question, type => string}], outputs => [#{name => answer, type => string}]},
            #{question => <<"What is the capital of France?">>}},
        {math_question,
            #{inputs => [#{name => question, type => string}], outputs => [#{name => answer, type => string}]},
            #{question => <<"What is 2+2?">>}},
        {multiple_inputs,
            #{inputs => [#{name => question, type => string}, #{name => context, type => string}],
              outputs => [#{name => answer, type => string}]},
            #{question => <<"What is AI?">>, context => <<"Artificial Intelligence">>}},
        {json_output,
            #{inputs => [#{name => question, type => string}], outputs => [#{name => response, type => json}]},
            #{question => <<"List 3 colors">>}}
    ],

    Results = lists:map(fun({Name, Signature, Inputs}) ->
        io:format("Testing ~q prediction...~n", [Name]),
        try
            SigJson = iolist_to_binary(json:encode(Signature)),
            InJson = iolist_to_binary(json:encode(Inputs)),
            case yawl_ml_bridge:dspy_predict(SigJson, InJson, <<"none">>) of
                {ok, Result} when is_map(Result) ->
                    io:format("  PASS: ~q prediction succeeded~n", [Name]),
                    {Name, pass};
                {ok, _} ->
                    io:format("  FAIL: ~q prediction returned unexpected result type~n", [Name]),
                    {Name, fail};
                {error, Reason} ->
                    io:format("  FAIL: ~q prediction failed: ~p~n", [Name, Reason]),
                    {Name, fail}
            catch
                E:R ->
                    io:format("  FAIL: ~q prediction error: ~p:~p~n", [Name, E, R]),
                    {Name, fail}
            end
        end
    end, TestCases),

    Passed = length([R || {_, pass} <- Results]),
    Total = length(Results),

    io:format("Prediction test results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> {dspy_predict_various, pass};
        _ -> {dspy_predict_various, fail}
    end.

%% Test 4: DSPy Load Examples
test_dspy_load_examples() ->
    io:format("=== Test 4: DSPy Load Examples ===~n"),

    TestCases = [
        {basic_examples, [
            #{inputs => #{question => <<"What is 2+2?">>}, outputs => #{answer => <<"4">>}},
            #{inputs => #{question => <<"What is 3+3?">>}, outputs => #{answer => <<"6">>}}
        ]},
        {empty_examples, []},
        {single_example, [
            #{inputs => #{question => <<"What is the capital of France?">>}, outputs => #{answer => <<"Paris">>}}
        ]},
        {complex_examples, [
            #{inputs => #{question => <<"Name a programming language">>, context => <<"Web development">>},
              outputs => #{answer => <<"JavaScript">>}},
            #{inputs => #{question => <<"Name a programming language">>, context => <<"Systems programming">>},
              outputs => #{answer => <<"C++">>}}
        ]}
    ],

    Results = lists:map(fun({Name, Examples}) ->
        io:format("Testing ~q examples...~n", [Name]),
        try
            ExJson = iolist_to_binary(json:encode(Examples)),
            case yawl_ml_bridge:dspy_load_examples(ExJson) of
                {ok, Count} when is_integer(Count), Count >= 0 ->
                    io:format("  PASS: Loaded ~p examples for ~q~n", [Count, Name]),
                    {Name, pass};
                {ok, _} ->
                    io:format("  FAIL: ~q returned non-integer count~n", [Name]),
                    {Name, fail};
                {error, Reason} ->
                    io:format("  FAIL: ~q examples failed: ~p~n", [Name, Reason]),
                    {Name, fail}
            catch
                E:R ->
                    io:format("  FAIL: ~q examples error: ~p:~p~n", [Name, E, R]),
                    {Name, fail}
            end
        end
    end, TestCases),

    Passed = length([R || {_, pass} <- Results]),
    Total = length(Results),

    io:format("Load examples results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> {dspy_load_examples, pass};
        _ -> {dspy_load_examples, fail}
    end.

%% Test 5: Error Handling
test_error_handling() ->
    io:format("=== Test 5: Error Handling ===~n"),

    TestCases = [
        {init_invalid_json, yawl_ml_bridge:dspy_init(<<"invalid json">>)},
        {init_empty_config, yawl_ml_bridge:dspy_init(<<"">>)},
        {predict_invalid_signature,
            yawl_ml_bridge:dspy_predict(<<"invalid">>, <<"{\"test\": \"value\"}">>, <<"none">>)},
        {predict_invalid_inputs,
            yawl_ml_bridge:dspy_predict(<<"{\"inputs\":[],\"outputs\":[]}">>, <<"invalid">>, <<"none">>)},
        {predict_invalid_examples,
            yawl_ml_bridge:dspy_predict(<<"{\"inputs\":[],\"outputs\":[]}">>,
                                        <<"{\"test\": \"value\"}">>,
                                        <<"invalid">>)},
        {load_invalid_examples,
            yawl_ml_bridge:dspy_load_examples(<<"invalid json">>)},
        {load_empty_examples,
            yawl_ml_bridge:dspy_load_examples(<<"">>)}
    ],

    Results = lists:map(fun({Name, Call}) ->
        io:format("Testing error handling for ~q...~n", [Name]),
        try
            case Call of
                {error, _} ->
                    io:format("  PASS: ~q correctly returned error~n", [Name]),
                    {Name, pass};
                _ ->
                    io:format("  FAIL: ~q should have failed but succeeded~n", [Name]),
                    {Name, fail}
            catch
                E:R ->
                    io:format("  FAIL: ~q should have returned error but crashed: ~p:~p~n", [Name, E, R]),
                    {Name, fail}
            end
        end
    end, TestCases),

    Passed = length([R || {_, pass} <- Results]),
    Total = length(Results),

    io:format("Error handling results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> {error_handling, pass};
        _ -> {error_handling, fail}
    end.

%% Test 6: Configuration Persistence
test_configuration_persistence() ->
    io:format("=== Test 6: Configuration Persistence ===~n"),

    % Test different configurations persist across calls
    Configs = [
        #{provider => <<"groq">>, model => <<"llama-3.3-70b-versatile">>, max_tokens => 100},
        #{provider => <<"openai">>, model => <<"gpt-4">>, temperature => 0.5},
        #{provider => <<"anthropic">>, model => <<"claude-3-opus-20240229">>, max_tokens => 500}
    ],

    Results = lists:map(fun(Config) ->
        ConfigJson = iolist_to_binary(json:encode(Config)),
        io:format("Testing config persistence: ~p~n", [Config]),
        try
            % Init with config
            case yawl_ml_bridge:dspy_init(ConfigJson) of
                ok ->
                    % Try prediction with this config
                    Signature = #{inputs => [#{name => question, type => string}],
                                 outputs => [#{name => answer, type => string}]},
                    Inputs = #{question => <<"Test question">>},
                    SigJson = iolist_to_binary(json:encode(Signature)),
                    InJson = iolist_to_binary(json:encode(Inputs)),

                    case yawl_ml_bridge:dspy_predict(SigJson, InJson, <<"none">>) of
                        {ok, _} ->
                            io:format("  PASS: Configuration persistent~n"),
                            {persist, pass};
                        {error, Reason} ->
                            io:format("  FAIL: Prediction with persistent config failed: ~p~n", [Reason]),
                            {persist, fail}
                    end;
                {error, Reason} ->
                    io:format("  FAIL: Config init failed: ~p~n", [Reason]),
                    {persist, fail}
            end
        catch
            E:R ->
                io:format("  FAIL: Config persistence error: ~p:~p~n", [E, R]),
                {persist, fail}
        end
    end, Configs),

    Passed = length([R || {_, pass} <- Results]),
    Total = length(Results),

    io:format("Configuration persistence results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> {config_persistence, pass};
        _ -> {config_persistence, fail}
    end.

%% Test 7: Concurrent Predictions
test_concurrent_predictions() ->
    io:format("=== Test 7: Concurrent Predictions ===~n"),

    % Create test data for concurrent predictions
    TestData = [
        {1, #{question => <<"What is 1+1?">>}},
        {2, #{question => <<"What is 2+2?">>}},
        {3, #{question => <<"What is 3+3?">>}},
        {4, #{question => <<"What is 4+4?">>}},
        {5, #{question => <<"What is 5+5?">>}}
    ],

    Signature = #{inputs => [#{name => question, type => string}],
                 outputs => [#{name => answer, type => string}]},
    SigJson = iolist_to_binary(json:encode(Signature)),

    % Spawn concurrent processes for predictions
    Processes = lists:map(fun({Id, Inputs}) ->
        spawn(fun() ->
            InJson = iolist_to_binary(json:encode(Inputs)),
            case yawl_ml_bridge:dspy_predict(SigJson, InJson, <<"none">>) of
                {ok, Result} ->
                    Id ! {self(), {success, Result}};
                {error, Reason} ->
                    Id ! {self(), {error, Reason}}
            end
        end)
    end, TestData),

    % Collect results
    Results = lists:map(fun(Pid) ->
        receive
            {Pid, {success, Result}} ->
                io:format("  PASS: Concurrent prediction succeeded~n"),
                {concurrent, pass};
            {Pid, {error, Reason}} ->
                io:format("  FAIL: Concurrent prediction failed: ~p~n", [Reason]),
                {concurrent, fail}
        after 30000 ->
            io:format("  FAIL: Concurrent prediction timed out~n"),
            {concurrent, fail}
        end
    end, Processes),

    Passed = length([R || {_, pass} <- Results]),
    Total = length(Results),

    io:format("Concurrent prediction results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> {concurrent_predictions, pass};
        _ -> {concurrent_predictions, fail}
    end.