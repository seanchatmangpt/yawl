# Unit tests for QLever FFI
# Tests individual components in isolation

# GoogleTest tests
add_executable(qlever_ffi_unit_tests
    engine_test.cpp
    search_test.cpp
    types_test.cpp
    utils_test.cpp
)

target_link_libraries(qlever_ffi_unit_tests
    PRIVATE
        qlever_ffi
        ffi_test_support
        GTest::gtest
        GTest::gtest_main
)

# Test discovery
gtest_discover_tests(qlever_ffi_unit_tests
    TEST_PREFIX "UnitTest."
    TEST_SUFFIX "Test"
)

# Add test to test suite
add_test(NAME unit_tests COMMAND qlever_ffi_unit_tests)