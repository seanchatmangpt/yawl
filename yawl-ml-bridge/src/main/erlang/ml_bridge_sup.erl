%%%-------------------------------------------------------------------
%%% @doc ML Bridge Supervisor - Fault-tolerant supervisor for ML bridges
%%%
%%% Supervises dspy_bridge and tpot2_bridge with automatic restart
%%% on failure. Part of the enterprise-grade architecture.
%%%
%%% == Architecture ==
%%%                    +------------------+
%%%                    |  ml_bridge_sup   |
%%%                    +------------------+
%%%                     /              \
%%%          +------------+          +-------------+
%%%          | dspy_bridge|          | tpot2_bridge|
%%%          +------------+          +-------------+
%%%
%%% @end
%%%-------------------------------------------------------------------
-module(ml_bridge_sup).
-behaviour(supervisor).

%% API
-export([
    start_link/0,
    start_link/1
]).

%% supervisor callbacks
-export([
    init/1
]).

-define(SERVER, ?MODULE).

%%%===================================================================
%%% API
%%%===================================================================

start_link() ->
    start_link([]).

start_link(Options) ->
    supervisor:start_link({local, ?SERVER}, ?MODULE, [Options]).

%%%===================================================================
%%% supervisor callbacks
%%%===================================================================

init([_Options]) ->
    SupFlags = #{
        strategy => one_for_one,
        intensity => 10,  % Max 10 restarts
        period => 60      % Within 60 seconds
    },

    ChildSpecs = [
        % DSPy Bridge
        #{
            id => dspy_bridge,
            start => {dspy_bridge, start_link, []},
            restart => permanent,
            shutdown => 5000,
            type => worker,
            modules => [dspy_bridge]
        },
        % TPOT2 Bridge
        #{
            id => tpot2_bridge,
            start => {tpot2_bridge, start_link, []},
            restart => permanent,
            shutdown => 5000,
            type => worker,
            modules => [tpot2_bridge]
        }
    ],

    {ok, {SupFlags, ChildSpecs}}.
