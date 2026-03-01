# Performance tests for QLever FFI
# Benchmarks and performance verification

# Only build if benchmark library is available
if(benchmark_FOUND)
    add_executable(qlever_ffi_benchmarks
        benchmark_engine.cpp
        benchmark_search.cpp
        benchmark_types.cpp
        benchmark_stress.cpp
    )

    target_link_libraries(qlever_ffi_benchmarks
        PRIVATE
            qlever_ffi
            ffi_test_support
            benchmark::benchmark
            GTest::gtest_main
    )

    # Add to test suite
    add_test(NAME performance_benchmarks COMMAND qlever_ffi_benchmarks)
endif()

# CPU intensive tests
add_executable(qlever_ffi_performance_tests
    cpu_stress_test.cpp
    memory_test.cpp
    timeout_test.cpp
)

target_link_libraries(qlever_ffi_performance_tests
    PRIVATE
        qlever_ffi
        ffi_test_support
        GTest::gtest
        GTest::gtest_main
)

# Test discovery
gtest_discover_tests(qlever_ffi_performance_tests
    TEST_PREFIX "PerformanceTest."
    TEST_SUFFIX "Test"
)

# Add test to test suite
add_test(NAME performance_tests COMMAND qlever_ffi_performance_tests)