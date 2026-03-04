%% @copyright 2026 YAWL Foundation
%% @author YAWL Erlang Team
%% @doc Process mining bridge supervisor

-module(process_mining_sup).
-behaviour(supervisor).

%% API
-export([start_link/0]).

%% Supervisor callbacks
-export([init/1]).

-define(CHILD_ID, process_mining_bridge).

%%====================================================================
%% API functions
%%====================================================================

%% @doc Start the process mining supervisor
-spec start_link() -> {ok, pid()} | {error, any()}.
start_link() ->
    supervisor:start_link({local, ?MODULE}, ?MODULE, []).

%%====================================================================
%% Supervisor callbacks
%%====================================================================

init([]) ->
    %% Define the process mining bridge child spec
    BridgeSpec = #{
        id => ?CHILD_ID,
        start => {process_mining_bridge, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [process_mining_bridge]
    },

    %% Strategy: one_for_one - restart failed children one by one
    Strategy = #{
        strategy => one_for_one,
        intensity => 1,
        period => 5
    },

    %% Child specifications
    Children = [BridgeSpec],

    {ok, {Strategy, Children}}.