%%%-------------------------------------------------------------------
%%% @doc YAWL workflow supervisor.
%%% One-for-one strategy: individual worker restarts don't affect siblings.
%%% Manages application lifecycle with graceful shutdown.
%%% @end
%%%-------------------------------------------------------------------
-module(yawl_sup).
-behaviour(supervisor).

-export([start_link/0]).
-export([init/1]).

start_link() ->
    supervisor:start_link({local, ?MODULE}, ?MODULE, []).

init([]) ->
    SupFlags = #{strategy => one_for_one, intensity => 5, period => 10},
    Children = [
        #{id => yawl_workflow,
          start => {yawl_workflow, start_link, []},
          restart => permanent,
          shutdown => 5000,
          type => worker,
          modules => [yawl_workflow]},
        #{id => yawl_process_mining,
          start => {yawl_process_mining, start_link, []},
          restart => permanent,
          shutdown => 5000,
          type => worker,
          modules => [yawl_process_mining]},
        #{id => yawl_event_relay,
          start => {gen_event, start_link, [{local, yawl_event_relay}]},
          restart => permanent,
          shutdown => 5000,
          type => worker,
          modules => [dynamic]}
    ],
    {ok, {SupFlags, Children}}.
