# QLever FFI CMake Build Configuration
#
# Builds libqlever_ffi shared library for Java Panama FFM integration.
# Requires QLever to be built and available (as subdirectory or installed).
# Supports jextract for Java bindings generation.

cmake_minimum_required(VERSION 3.20)
project(qlever_ffi VERSION 1.0.0 LANGUAGES C CXX)

# C++20 required for QLever compatibility
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Position-independent code for shared libraries
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Build type defaults to Release for production performance
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Platform-specific compiler flags
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    # Linux/Unix compiler flags - use separate add_compile_options calls for each flag
    add_compile_options(
        $<$<CONFIG:Debug>:-g3>
        $<$<CONFIG:Debug>:-O0>
        $<$<CONFIG:Debug>:-fsanitize=address>
        $<$<CONFIG:Debug>:-fno-omit-frame-pointer>
        $<$<CONFIG:Release>:-O3>
        $<$<CONFIG:Release>:-DNDEBUG>
        $<$<CONFIG:RelWithDebInfo>:-O2>
        $<$<CONFIG:RelWithDebInfo>:-g3>
        $<$<CONFIG:MinSizeRel>:-Os>
        -Wall -Wextra -Wpedantic
        -fPIC -fvisibility=hidden
    )
    # Position-independent flags are now covered by fPIC
elseif(CMAKE_CXX_COMPILER_ID MATCHES "MSVC")
    # Windows MSVC compiler flags
    add_compile_options(
        $<$<CONFIG:Debug>:/Od>
        $<$<CONFIG:Debug>:/Zi>
        $<$<CONFIG:Debug>:/RTC1>
        $<$<CONFIG:Debug>:/fsanitize:address>
        $<$<CONFIG:Release>:/O2>
        $<$<CONFIG:Release>:/DNDEBUG>
        $<$<CONFIG:RelWithDebInfo>:/O2>
        $<$<CONFIG:RelWithDebInfo>:/Zi>
        $<$<CONFIG:MinSizeRel>:/Os>
        /W3 /WX
    )
    # Set as dynamic library for MSVC
    set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)
endif()

# Set C standard for C sources (jextract generates C code)
set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)

# ============================================================================
# jextract Integration
# ============================================================================

find_package(jextract QUIET)
if(NOT jextract_FOUND)
    # Try to find JDK_HOME or JAVA_HOME
    find_package(JDK QUIET)
    if(JDK_FOUND)
        # Add jextract manual detection
        if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Windows")
            set(JEXTRACT_PATH "${JDK_HOME}/lib/jextract")
            set(JEXTRACT_EXT ".exe")
        elseif(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
            set(JEXTRACT_PATH "${JDK_HOME}/lib/jextract")
            set(JEXTRACT_EXT "")
        else()
            set(JEXTRACT_PATH "${JDK_HOME}/lib/jextract")
            set(JEXTRACT_EXT "")
        endif()

        if(EXISTS "${JEXTRACT_PATH}${JEXTRACT_EXT}")
            add_custom_target(jextract_bindings ALL
                COMMAND ${JEXTRACT_PATH}${JEXTRACT_EXT}
                    -t org.yawlfoundation.yawl.qlever.ffi
                    --header "${CMAKE_CURRENT_SOURCE_DIR}/qlever_ffi.h"
                    -d "${CMAKE_CURRENT_SOURCE_DIR}/../generated-sources"
                DEPENDS qlever_ffi
                WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
                COMMENT "Generating Java bindings with jextract"
            )
            message(STATUS "jextract: Found at ${JEXTRACT_PATH}")
        else()
            message(STATUS "jextract: Not found in ${JDK_HOME}")
        endif()
    else()
        message(STATUS "jextract: JDK not found, skipping Java bindings generation")
    endif()
else()
    message(STATUS "jextract: Using system-provided jextract")
endif()

# ============================================================================
# QLever Dependency
# ============================================================================

# Helper function to check QLever engine and index targets
function(check_qlever_targets OUT_VAR)
    set(targets_found FALSE)

    # Check if we have access to QLever build system
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../../../qlever/CMakeLists.txt")
        add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../qlever qlever)

        # Verify the required targets exist
        if(TARGET qlever_engine AND TARGET qlever_index)
            set(targets_found TRUE)
            set(${OUT_VAR} TRUE PARENT_SCOPE)
        else()
            message(WARNING "QLever subdirectory found but targets missing")
            set(${OUT_VAR} FALSE PARENT_SCOPE)
        endif()
    else()
        set(${OUT_VAR} FALSE PARENT_SCOPE)
    endif()
endfunction()

# Option 1: QLever as subdirectory (recommended for embedded builds)
check_qlever_targets(QLEVER_SUBDIR_FOUND)
if(QLEVER_SUBDIR_FOUND)
    set(QLEVER_FOUND TRUE)
    set(QLEVER_LIBRARIES qlever_engine qlever_index)
    set(QLEVER_INCLUDE_DIRS "${CMAKE_CURRENT_SOURCE_DIR}/../../../qlever/src/engine")
    set(QLEVER_INCLUDE_DIRS "${QLEVER_INCLUDE_DIRS};${CMAKE_CURRENT_SOURCE_DIR}/../../../qlever/src/index")
    set(QLEVER_INCLUDE_DIRS "${QLEVER_INCLUDE_DIRS};${CMAKE_CURRENT_SOURCE_DIR}/../../../qlever/src/util")
    message(STATUS "QLever: Found as subdirectory with targets")
endif()

# Option 2: Find installed QLever package
if(NOT QLEVER_FOUND)
    find_package(QLever QUIET)
    if(QLever_FOUND)
        message(STATUS "QLever: Found via find_package")
    endif()
endif()

# Option 3: Use pkg-config for system-installed QLever
if(NOT QLEVER_FOUND)
    find_package(PkgConfig QUIET)
    if(PkgConfig_FOUND)
        pkg_check_modules(QLEVER QUIET qlever)
        if(QLEVER_FOUND)
            message(STATUS "QLever: Found via pkg-config")
        endif()
    endif()
endif()

# Option 4: Manual search with QLEVER_DIR
if(NOT QLEVER_FOUND)
    if(DEFINED ENV{QLEVER_DIR})
        set(QLEVER_FOUND TRUE)
        set(QLEVER_INCLUDE_DIRS "$ENV{QLEVER_DIR}/include")
        set(QLEVER_LIBRARIES qlever_engine qlever_index)
        message(STATUS "QLever: Found via QLEVER_DIR environment variable")
    endif()
endif()

if(NOT QLEVER_FOUND)
    message(WARNING "QLever not found. Building stub library for development.")
    message(STATUS "To build with QLever:")
    message(STATUS "  1. Clone QLever as a sibling directory: git clone https://github.com/Qlever/qlever.git")
    message(STATUS "  2. Install QLever system-wide: sudo apt-get install qlever (Linux)")
    message(STATUS "  3. Set QLEVER_DIR environment variable: export QLEVER_DIR=/path/to/qlever")
endif()

# ============================================================================
# Header Files
# ============================================================================

# Generate C header for jextract if it doesn't exist
if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/qlever_ffi.h")
    file(GENERATE
        OUTPUT "${CMAKE_CURRENT_SOURCE_DIR}/qlever_ffi.h"
        CONTENT "#ifndef QLEVER_FFI_H\n#define QLEVER_FFI_H\n\n// Generated FFI header\n#include <cstdint>\n\n#ifdef __cplusplus\nextern \"C\" {\n#endif\n\n// Core FFI functions\nvoid* qlever_ffi_create_engine();\nvoid qlever_ffi_destroy_engine(void* engine);\nint qlever_ffi_add_data(void* engine, const char* data, size_t size);\nvoid* qlever_ffi_search(void* engine, const char* query);\nvoid qlever_ffi_free_result(void* result);\n\n// Error handling\nconst char* qlever_ffi_get_last_error();\n\n#ifdef __cplusplus\n}\n#endif\n\n#endif // QLEVER_FFI_H\n"
    )
endif()

# ============================================================================
# Shared Library Build
# ============================================================================

# Only include main source files, not tests or benchmarks.
# NOTE: qlever_ffi_types.c has been consolidated into qlever_ffi.cpp
# to eliminate cross-compilation-unit linkage complexity between C and C++.
# The media type string mapping functions are now compiled as part of the
# C++ translation unit with proper extern "C" linkage.
set(FFI_SOURCES
    qlever_ffi.cpp
)

add_library(qlever_ffi SHARED
    ${FFI_SOURCES}
    qlever_ffi.h
    qlever_ffi_types.h
)

# Set include directories with public/private separation
target_include_directories(qlever_ffi
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
        $<INSTALL_INTERFACE:include>
    PRIVATE
        ${QLEVER_INCLUDE_DIRS}
)

# Compile definitions
target_compile_definitions(qlever_ffi PRIVATE
    $<$<CONFIG:Debug>:_DEBUG>
    QLEVER_FFI_EXPORTS
)

# Add jextract as a dependency if we have it
if(TARGET jextract_bindings)
    add_dependencies(jextract_bindings qlever_ffi)
endif()

# Link against QLever libraries if found
if(QLEVER_FOUND)
    target_link_libraries(qlever_ffi PRIVATE
        ${QLEVER_LIBRARIES}
    )
    message(STATUS "QLever FFI: Building with QLever support (${QLEVER_LIBRARIES})")
else()
    # Build stub without QLever for development/testing
    target_compile_definitions(qlever_ffi PRIVATE QLEVER_STUB)
    message(STATUS "QLever FFI: Building stub library (no QLever)")
endif()

# Platform-specific output names and properties
set_target_properties(qlever_ffi PROPERTIES
    OUTPUT_NAME "qlever_ffi"
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    # Additional properties for better cross-platform compatibility
    PUBLIC_HEADER "qlever_ffi.h"
    ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
)

# Platform-specific output names
set_target_properties(qlever_ffi PROPERTIES
    OUTPUT_NAME "qlever_ffi"
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
)

# ============================================================================
# Installation
# ============================================================================

# Create Maven resources directory structure
# Structure: native/<os>/<arch>/<library>
if(APPLE)
    set(OS_NAME "macos")
    # Handle different macOS architectures
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(ARCH_NAME "aarch64")
    else()
        set(ARCH_NAME "x86_64")
    endif()
elseif(WIN32)
    set(OS_NAME "windows")
    # Windows uses different architecture names
    if(CMAKE_SIZEOF_VOID_P EQUAL 8)
        set(ARCH_NAME "x86_64")
    else()
        set(ARCH_NAME "x86")
    endif()
else()
    set(OS_NAME "linux")
    # Linux architecture mapping
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(ARCH_NAME "aarch64")
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|amd64")
        set(ARCH_NAME "x86_64")
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86|i386|i686")
        set(ARCH_NAME "x86")
    else()
        # Fallback to whatever the system reports
        set(ARCH_NAME ${CMAKE_SYSTEM_PROCESSOR})
    endif()
endif()

set(NATIVE_DIR "${OS_NAME}/${ARCH_NAME}")
set(MAVEN_NATIVE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../resources/native/${NATIVE_DIR}")

# Create the installation directory
file(MAKE_DIRECTORY ${MAVEN_NATIVE_DIR})

# Install the shared library to Maven resources
install(TARGETS qlever_ffi
    LIBRARY DESTINATION ${MAVEN_NATIVE_DIR}
    RUNTIME DESTINATION ${MAVEN_NATIVE_DIR}
    ARCHIVE DESTINATION ${MAVEN_NATIVE_DIR}
    PUBLIC_HEADER DESTINATION ${MAVEN_NATIVE_DIR}/include
)

# Install configuration file for Maven
install(FILES "qlever_ffi.properties"
    DESTINATION ${MAVEN_NATIVE_DIR}
    RENAME "libqlever_ffi.properties"
)

# Install CMake config for development
install(EXPORT qlever_ffiTargets
    FILE qlever_ffiTargets.cmake
    NAMESPACE QLever::
    DESTINATION cmake
)

install(TARGETS qlever_ffi
    EXPORT qlever_ffiTargets
    INCLUDES DESTINATION include
)

# ============================================================================
# FFI Test Suite
# ============================================================================

option(BUILD_TESTS "Build test programs" OFF)
option(ENABLE_FFI_TESTS "Enable FFI layer tests" ON)

if(BUILD_TESTS OR ENABLE_FFI_TESTS)
    # Enable testing framework
    enable_testing()
    include(GoogleTest)

    # Find gtest if available
    find_package(GTest QUIET)
    if(GTest_FOUND)
        # GoogleTest-based unit tests
        add_subdirectory(tests)
    else()
        # Fallback to basic test executables
        message(STATUS "GTest not found, using basic test framework")
    endif()

    # FFI Test executable
    add_executable(qlever_ffi_test
        test/ffi_test.cpp
        test/engine_test.cpp
        test/search_test.cpp
    )

    target_include_directories(qlever_ffi_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${QLEVER_INCLUDE_DIRS}
    )

    target_link_libraries(qlever_ffi_test PRIVATE
        qlever_ffi
        ${QLEVER_LIBRARIES}
    )

    # Add test if gtest is available
    if(GTest_FOUND)
        gtest_discover_tests(qlever_ffi_test)
    else()
        # Simple test command
        add_test(NAME ffi_basic_test COMMAND qlever_ffi_test)
    endif()

    # jextract binding tests
    if(TARGET jextract_bindings)
        add_test(NAME jextract_bindings_test
            COMMAND ${CMAKE_JAVA_COMMAND} -cp
            "${CMAKE_CURRENT_SOURCE_DIR}/../generated-sources;${CMAKE_CURRENT_SOURCE_DIR}/../generated-sources/annotations"
            org.junit.runner.JUnitCore org.yawlfoundation.yawl.qlever.ffi.FFITest
        )
    endif()

    # Performance benchmarks
    option(BUILD_BENCHMARKS "Build performance benchmarks" OFF)
    if(BUILD_BENCHMARKS)
        find_package(benchmark QUIET)
        if(benchmark_FOUND)
            add_executable(qlever_ffi_benchmark
                benchmark/search_benchmark.cpp
                benchmark/engine_benchmark.cpp
            )
            target_include_directories(qlever_ffi_benchmark PRIVATE
                ${CMAKE_CURRENT_SOURCE_DIR}
                ${QLEVER_INCLUDE_DIRS}
            )
            target_link_libraries(qlever_ffi_benchmark PRIVATE
                qlever_ffi
                benchmark::benchmark
                ${QLEVER_LIBRARIES}
            )
            add_test(NAME search_benchmark COMMAND qlever_ffi_benchmark)
        endif()
    endif()
endif()

# ============================================================================
# Package Configuration
# ============================================================================

# Create config file for package management
configure_file(
    cmake/qlever_ffi-config.cmake.in
    ${CMAKE_BINARY_DIR}/qlever_ffi-config.cmake
    @ONLY
)

# Install config files
install(FILES
    ${CMAKE_BINARY_DIR}/qlever_ffi-config.cmake
    cmake/qlever_ffi-config-version.cmake
    DESTINATION cmake
)

# ============================================================================
# Build Configuration Summary
# ============================================================================

message(STATUS "")
message(STATUS "QLever FFI Configuration Summary:")
message(STATUS "  Version:        ${PROJECT_VERSION}")
message(STATUS "  Build Type:     ${CMAKE_BUILD_TYPE}")
message(STATUS "  C++ Standard:   ${CMAKE_CXX_STANDARD}")
message(STATUS "  C Standard:     ${CMAKE_C_STANDARD}")
message(STATUS "  QLever:         ${QLEVER_FOUND}")
message(STATUS "  Platform:       ${CMAKE_SYSTEM_NAME}")
message(STATUS "  Architecture:   ${ARCH_NAME}")
message(STATUS "  Output Name:    qlever_ffi")
message(STATUS "  Install Dir:    ${MAVEN_NATIVE_DIR}")
message(STATUS "  Tests:          ${BUILD_TESTS}")
message(STATUS "  Benchmarks:     ${BUILD_BENCHMARKS}")
if(QLEVER_FOUND)
    message(STATUS "  QLever Libs:    ${QLEVER_LIBRARIES}")
    message(STATUS "  QLever Include: ${QLEVER_INCLUDE_DIRS}")
endif()
if(jextract_FOUND OR TARGET jextract_bindings)
    message(STATUS "  jextract:       Enabled")
    message(STATUS "  Java Bindings:  ${CMAKE_CURRENT_SOURCE_DIR}/../generated-sources")
endif()
message(STATUS "")

# Add installation instructions
message(STATUS "Installation:")
message(STATUS "  Library:      ${MAVEN_NATIVE_DIR}/libqlever_ffi${CMAKE_SHARED_LIBRARY_SUFFIX}")
if(QLEVER_FOUND)
    message(STATUS "  Headers:      ${MAVEN_NATIVE_DIR}/include/qlever_ffi.h")
endif()
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/qlever_ffi.properties")
    message(STATUS "  Config:       ${MAVEN_NATIVE_DIR}/libqlever_ffi.properties")
endif()
message(STATUS "")

# Development targets
message(STATUS "Available targets:")
message(STATUS "  qlever_ffi     - Build shared library")
message(STATUS "  install        - Install to Maven resources")
if(TARGET jextract_bindings)
    message(STATUS "  jextract_bindings - Generate Java bindings")
endif()
if(BUILD_TESTS)
    message(STATUS "  qlever_ffi_test  - Run FFI tests")
    message(STATUS "  test           - Run all tests")
endif()
if(BUILD_BENCHMARKS AND benchmark_FOUND)
    message(STATUS "  benchmark      - Run performance benchmarks")
endif()
message(STATUS "")