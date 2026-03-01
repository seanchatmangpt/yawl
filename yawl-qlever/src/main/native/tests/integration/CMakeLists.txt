# Integration tests for QLever FFI
# Tests the entire FFI workflow end-to-end

# Integration tests
add_executable(qlever_ffi_integration_tests
    workflow_test.cpp
    performance_test.cpp
    error_handling_test.cpp
    multi_thread_test.cpp
)

target_link_libraries(qlever_ffi_integration_tests
    PRIVATE
        qlever_ffi
        ffi_test_support
        GTest::gtest
        GTest::gtest_main
)

# Test discovery
gtest_discover_tests(qlever_ffi_integration_tests
    TEST_PREFIX "IntegrationTest."
    TEST_SUFFIX "Test"
)

# Add test to test suite
add_test(NAME integration_tests COMMAND qlever_ffi_integration_tests)