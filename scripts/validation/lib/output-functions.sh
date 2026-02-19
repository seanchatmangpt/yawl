#!/bin/bash
#
# Output functions for validation scripts
# Contains reusable JSON and JUnit output functions
#

# JSON output with file parameter
output_json_file() {
    local output_file=$1
    shift
    output_json "$output_file"
}

# JUnit output with file parameter
output_junit_file() {
    local output_file=$1
    shift
    output_junit "$output_file"
}
