%% @doc Echo module v2 for hot-reload testing.
%% Returns {v2, Term} to distinguish from v1 behavior.
-module(yawl_echo_v2).
-export([echo/1]).

%% @doc Returns {v2, Term} (v2 behavior for hot-reload test).
echo(Term) -> {v2, Term}.
