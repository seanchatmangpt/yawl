# GoogleTest-based unit tests for QLever FFI
# This is included from the main CMakeLists.txt when BUILD_TESTS is ON

# Enable testing if not already enabled
enable_testing()

# Find GoogleTest components
if(NOT GTest_FOUND)
    message(FATAL_ERROR "GTest not found. Install GoogleTest or set GTest_DIR")
endif()

# Add gtest_main library for convenience
add_library(ffi_test_support STATIC
    support/test_environment.cpp
    support/mock_engine.cpp
    support/utils.cpp
)

target_include_directories(ffi_test_support PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/..
    ${QLEVER_INCLUDE_DIRS}
)

target_link_libraries(ffi_test_support PRIVATE
    qlever_ffi
    ${QLEVER_LIBRARIES}
)

# Individual test modules
add_subdirectory(unit)
add_subdirectory(integration)
add_subdirectory(performance)