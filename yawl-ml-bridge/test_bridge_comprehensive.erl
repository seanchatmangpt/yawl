%% Comprehensive test for YAWL ML Bridge
-module(test_bridge_comprehensive).
-export([run/0]).

run() ->
    io:format("=== YAWL ML Bridge Comprehensive Test ===\n"),
    
    %% Test 1: NIF loading
    io:format("\n1. Testing NIF loading...\n"),
    case yawl_ml_bridge:ping() of
        pong -> io:format("✓ NIF loaded successfully\n");
        Error -> io:format("✗ NIF loading failed: ~p\n", [Error])
    end,
    
    %% Test 2: DSPy initialization
    io:format("\n2. Testing DSPy initialization...\n"),
    case test_dspy_init() of
        ok -> io:format("✓ DSPy initialized successfully\n");
        Error2 -> io:format("✗ DSPy initialization failed: ~p\n", [Error2])
    end,
    
    %% Test 3: Simple prediction
    io:format("\n3. Testing simple prediction...\n"),
    case test_prediction() of
        ok -> io:format("✓ Simple prediction works\n");
        Error3 -> io:format("✗ Simple prediction failed: ~p\n", [Error3])
    end,
    
    %% Test 4: OCEL data processing
    ocel_test(),
    
    %% Test 5: Performance metrics
    io:format("\n5. Testing performance metrics...\n"),
    case test_performance() of
        ok -> io:format("✓ Performance metrics collected\n");
        Error5 -> io:format("✗ Performance metrics failed: ~p\n", [Error5])
    end,
    
    io:format("\n=== Test Complete ===\n").

test_dspy_init() ->
    Config = #{model_type => linear_regression, 
              max_iterations => 100},
    case yawl_ml_bridge:dspy_init(Config) of
        {ok, _} -> ok;
        Error -> Error
    end.

test_prediction() ->
    Input = #{features => [1.0, 2.0, 3.0], case_id => "test_001"},
    case yawl_ml_bridge:dspy_predict(Input) of
        {ok, _} -> ok;
        Error -> Error
    end.

ocel_test() ->
    io:format("\n4. Testing OCEL data processing...\n"),
    OcelData = #{
        events => [
            #{id => "e1", activity => "A", time => 1633024800},
            #{id => "e2", activity => "B", time => 1633025400}
        ],
        objects => [
            #{id => "o1", type => "Order", attributes => #{amount => 100}},
            #{id => "o2", type => "Customer", attributes => #{name => "John"}}
        ],
        relations => [
            #{source => "e1", target => "o1", type => "relates_to"},
            #{source => "e2", target => "o2", type => "relates_to"}
        ]
    },
    
    case yawl_ml_bridge:process_ocel(OcelData) of
        {ok, _} -> io:format("✓ OCEL data processed successfully\n");
        Error -> io:format("✗ OCEL processing failed: ~p\n", [Error])
    end.

test_performance() ->
    %% Simulate some work and collect metrics
    Start = erlang:monotonic_time(millisecond),
    
    %% Do some predictions
    [yawl_ml_bridge:dspy_predict(#{features => [1.0, 2.0], case_id => integer_to_list(I)}) 
     || I <- lists:seq(1, 10)],
    
    End = erlang:monotonic_time(millisecond),
    Duration = End - Start,
    
    case yawl_ml_bridge:get_metrics() of
        {ok, Metrics} ->
            io:format("  Predictions per second: ~p\n", [10000/Duration]),
            io:format("  Memory usage: ~p MB\n", [maps:get(memory_usage_mb, Metrics, 0)]),
            ok;
        Error -> Error
    end.
