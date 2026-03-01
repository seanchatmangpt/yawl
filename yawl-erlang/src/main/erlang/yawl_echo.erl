%% @doc Echo module for integration testing. Returns any term unchanged.
%% Used to verify end-to-end ETF encoding/decoding through the bridge.
-module(yawl_echo).
-export([echo/1]).

%% @doc Returns the term unchanged (identity function).
echo(Term) -> Term.
