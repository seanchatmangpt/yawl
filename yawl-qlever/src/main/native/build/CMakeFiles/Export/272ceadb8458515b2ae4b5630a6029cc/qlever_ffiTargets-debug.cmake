#----------------------------------------------------------------
# Generated CMake target import file for configuration "Debug".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "QLever::qlever_ffi" for configuration "Debug"
set_property(TARGET QLever::qlever_ffi APPEND PROPERTY IMPORTED_CONFIGURATIONS DEBUG)
set_target_properties(QLever::qlever_ffi PROPERTIES
  IMPORTED_LOCATION_DEBUG "${_IMPORT_PREFIX}/lib/libqlever_ffi.1.0.0.dylib"
  IMPORTED_SONAME_DEBUG "@rpath/libqlever_ffi.1.dylib"
  )

list(APPEND _cmake_import_check_targets QLever::qlever_ffi )
list(APPEND _cmake_import_check_files_for_QLever::qlever_ffi "${_IMPORT_PREFIX}/lib/libqlever_ffi.1.0.0.dylib" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
