# QLever FFI version configuration
# This file contains version information for the QLever FFI package

set(PACKAGE_VERSION "@PROJECT_VERSION@")

# Check whether the requested version is compatible
function(check_qlever_ffi_version VERSION_MAJOR VERSION_MINOR VERSION_PATCH)
    if(PACKAGE_VERSION VERSION_LESS "${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}")
        return(FATAL_ERROR "QLever FFI version ${PACKAGE_VERSION} is too old")
    endif()
endfunction()