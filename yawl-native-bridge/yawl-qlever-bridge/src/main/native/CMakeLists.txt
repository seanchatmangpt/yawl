# CMake build configuration for QLever Native FFI
# This CMakeLists.txt builds the qlever_ffi native library

cmake_minimum_required(VERSION 3.10)
project(qlever_native VERSION 1.0.0 LANGUAGES C CXX)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Set C standard
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Configuration options
option(BUILD_SHARED_LIBS "Build shared libraries" ON)
option(BUILD_STATIC_LIBS "Build static libraries" OFF)
option(BUILD_TESTS "Build test executables" ON)
option(WITH_DEBUG "Enable debug symbols" ON)
option(WITH_OPTIMIZATION "Enable optimization" OFF)

# Platform-specific settings
if(WIN32)
    add_definitions(-D_WIN32_WINNT=0x0601)  # Support Windows 7+
    set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)
else()
    # macOS and Linux
    if(APPLE)
        set(CMAKE_MACOSX_RPATH ON)
    endif()
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
endif()

# Compiler flags
if(WITH_DEBUG)
    if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" OR CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
        add_compile_options(-g -fno-omit-frame-pointer)
    elseif(CMAKE_CXX_COMPILER_ID STREQUAL "MSVC")
        add_compile_options(/Zi /Od)
    endif()
endif()

if(WITH_OPTIMIZATION)
    if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" OR CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
        add_compile_options(-O3 -march=native -flto)
    elseif(CMAKE_CXX_COMPILER_ID STREQUAL "MSVC")
        add_compile_options(/O2)
    endif()
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../..
)

# Find required packages
find_package(Threads REQUIRED)

# Enable C++11 features
if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
    add_compile_options(-Wall -Wextra -Wpedantic)
    if(WITH_DEBUG)
        add_compile_options(-fsanitize=address -fsanitize=undefined)
    endif()
elseif(CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    add_compile_options(-Wall -Wextra -Wpedantic)
    if(WITH_DEBUG)
        add_compile_options(-fsanitize=address -fsanitize=undefined)
    endif()
elseif(CMAKE_CXX_COMPILER_ID STREQUAL "MSVC")
    add_compile_options(/W4 /WX)
endif()

# Source files
set(NATIVE_SOURCES
    qlever_ffi.h
    qlever_native.cpp
)

# Set include directories for the library
set_target_properties(qlever_native PROPERTIES
    PUBLIC_HEADER "qlever_ffi.h"
    VERSION ${PROJECT_VERSION}
    SOVERSION ${PROJECT_VERSION_MAJOR}
)

# Create the shared library
if(BUILD_SHARED_LIBS)
    add_library(qlever_native SHARED ${NATIVE_SOURCES})
    target_compile_definitions(qlever_native PRIVATE QLEVER_EXPORTS)
    set_target_properties(qlever_native PROPERTIES
        OUTPUT_NAME "qlever_native"
        PUBLIC_HEADER "qlever_ffi.h"
    )
endif()

# Create static library if requested
if(BUILD_STATIC_LIBS)
    add_library(qlever_static STATIC ${NATIVE_SOURCES})
    set_target_properties(qlever_static PROPERTIES
        OUTPUT_NAME "qlever_native"
        POSITION_INDEPENDENT_CODE ON
    )
endif()

# Link libraries
if(BUILD_SHARED_LIBS)
    target_link_libraries(qlever_native
        Threads::Threads
    )
endif()

if(BUILD_STATIC_LIBS)
    target_link_libraries(qlever_static
        Threads::Threads
    )
endif()

# Installation
install(TARGETS qlever_native
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    PUBLIC_HEADER DESTINATION include
)

if(BUILD_STATIC_LIBS)
    install(TARGETS qlever_static
        ARCHIVE DESTINATION lib
    )
endif()

# Install headers
install(FILES qlever_ffi.h
    DESTINATION include
)

# Create pkg-config file
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/qlever-native.pc.in"
    "${CMAKE_CURRENT_BINARY_DIR}/qlever-native.pc"
    @ONLY
)

install(FILES "${CMAKE_CURRENT_BINARY_DIR}/qlever-native.pc"
    DESTINATION lib/pkgconfig
)

# Testing
if(BUILD_TESTS)
    enable_testing()

    # Test sources
    set(TEST_SOURCES
        qlever_ffi.h
        test_main.cpp
        test_engine.cpp
        test_queries.cpp
    )

    # Add test executable
    add_executable(qlever_tests ${TEST_SOURCES})
    target_link_libraries(qlever_tests
        qlever_native
        Threads::Threads
        ${CMAKE_THREAD_LIBS_INIT}
    )

    # Add tests
    add_test(NAME BasicTest COMMAND qlever_tests --gtest_filter="*Basic*")
    add_test(NAME EngineTest COMMAND qlever_tests --gtest_filter="*Engine*")
    add_test(NAME QueryTest COMMAND qlever_tests --gtest_filter="*Query*")

    # Memory testing with valgrind if available
    find_program(VALGRIND valgrind)
    if(VALGRIND)
        add_test(NAME MemoryTest
            COMMAND ${VALGRIND} --leak-check=full --error-exitcode=1 qlever_tests
        )
    endif()

    # Installation
    install(TARGETS qlever_tests DESTINATION bin)
endif()

# Create configuration header
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/qlever_config.h.in"
    "${CMAKE_CURRENT_BINARY_DIR}/qlever_config.h"
    @ONLY
)

# Add export macro for Windows
if(WIN32)
    configure_file(
        "${CMAKE_CURRENT_SOURCE_DIR}/qlever_export.h.in"
        "${CMAKE_CURRENT_BINARY_DIR}/qlever_export.h"
        @ONLY
    )
endif()

# Generate documentation
find_package(Doxygen)
if(DOXYGEN_FOUND)
    set(DOXYGEN_IN ${CMAKE_CURRENT_SOURCE_DIR}/Doxyfile.in)
    set(DOXYGEN_OUT ${CMAKE_CURRENT_BINARY_DIR}/Doxyfile)
    configure_file(${DOXYGEN_IN} ${DOXYGEN_OUT} @ONLY)

    add_custom_target(doc
        COMMAND ${DOXYGEN_EXECUTABLE} ${DOXYGEN_OUT}
        WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
        COMMENT "Generating API documentation with Doxygen"
        VERBATIM
    )
endif()

# Package generation
include(CMakePackageConfigHelpers)
write_basic_package_version_file(
    "${CMAKE_CURRENT_BINARY_DIR}/qleverNativeConfigVersion.cmake"
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY AnyNewerVersion
)

configure_package_config_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/qleverNativeConfig.cmake.in"
    "${CMAKE_CURRENT_BINARY_DIR}/qleverNativeConfig.cmake"
    INSTALL_DESTINATION lib/cmake/qleverNative
)

install(EXPORT qleverNativeTargets
    FILE qleverNativeTargets.cmake
    NAMESPACE qleverNative::
    DESTINATION lib/cmake/qleverNative
)

install(FILES
    "${CMAKE_CURRENT_BINARY_DIR}/qleverNativeConfig.cmake"
    "${CMAKE_CURRENT_BINARY_DIR}/qleverNativeConfigVersion.cmake"
    DESTINATION lib/cmake/qleverNative
)

# Build targets summary
message(STATUS "Build Configuration:")
message(STATUS "  Version: ${PROJECT_VERSION}")
message(STATUS "  Shared Library: ${BUILD_SHARED_LIBS}")
message(STATUS "  Static Library: ${BUILD_STATIC_LIBS}")
message(STATUS "  Tests: ${BUILD_TESTS}")
message(STATUS "  Debug Symbols: ${WITH_DEBUG}")
message(STATUS "  Optimization: ${WITH_OPTIMIZATION}")

# Installation instructions
message(STATUS "\nTo install:")
message(STATUS "  cmake --build . --target install")
message(STATUS "\nTo run tests:")
message(STATUS "  cmake --build . --target test")
if(BUILD_TESTS)
    message(STATUS "  ctest")
endif()