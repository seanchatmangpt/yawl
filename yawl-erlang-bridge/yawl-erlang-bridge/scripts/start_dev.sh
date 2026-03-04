#!/bin/bash

# Development start script for YAWL Erlang Bridge

echo "Starting YAWL Process Mining Bridge (Development)"

# Set environment variables
export ERL_LIBS="/usr/local/lib/erlang/lib"
export PATH="$PATH:/usr/local/lib/erlang/bin"

# Start with proper logging
erl -name yawl_bridge@localhost \
    -pa /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/src \
    -s mnesia \
    -s lager \
    -s yawl_bridge_app \
    -eval "io:format('Bridge started~n')" \
    -noshell \
    -detached