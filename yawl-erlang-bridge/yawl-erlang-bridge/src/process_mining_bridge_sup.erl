-module(process_mining_bridge_sup).
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
        #{
            id => process_mining_bridge,
            start => {process_mining_bridge, start_link, []},
            restart => permanent,
            shutdown => 5000,
            type => worker,
            modules => [process_mining_bridge]
        }
    ],

    {ok, {SupFlags, ChildSpecs}}.