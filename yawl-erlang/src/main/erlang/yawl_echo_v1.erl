%% @doc Echo module v1 for hot-reload testing.
%% Returns {ok, Term} to distinguish from v2 behavior.
-module(yawl_echo_v1).
-export([echo/1]).

%% @doc Returns {ok, Term} (v1 behavior for hot-reload test).
echo(Term) -> {ok, Term}.
