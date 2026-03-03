-module(yawl_bridge_sup).
-behaviour(supervisor).

-export([start_link/0]).
-export([init/1]).

start_link() ->
    supervisor:start_link({local, ?MODULE}, ?MODULE, []).

init([]) ->
    SupFlags = #{
        strategy => one_for_one,
        intensity => 5,
        period => 60
    },
    ChildSpecs = [
        process_mining_bridge_spec(),
        data_modelling_bridge_spec(),
        mnesia_registry_spec()
    ],
    {ok, {SupFlags, ChildSpecs}}.

process_mining_bridge_spec() ->
    #{
        id => process_mining_bridge,
        start => {process_mining_bridge, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [process_mining_bridge]
    }.

data_modelling_bridge_spec() ->
    #{
        id => data_modelling_bridge,
        start => {data_modelling_bridge, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [data_modelling_bridge]
    }.

mnesia_registry_spec() ->
    #{
        id => mnesia_registry,
        start => {mnesia_registry, start_link, []},
        restart => permanent,
        shutdown => infinity,
        type => worker,
        modules => [mnesia_registry]
    }.