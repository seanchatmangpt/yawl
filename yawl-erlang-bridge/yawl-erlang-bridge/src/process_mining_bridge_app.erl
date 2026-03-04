-module(process_mining_bridge_app).
-behaviour(application).
-export([start/2, stop/1]).

start(_Type, _Args) ->
    %% Initialize Mnesia schema and start Mnesia
    case mnesia:create_schema([node()]) of
        ok -> ok;
        {error, {already_exists, _}} -> ok;
        {error, {Node, {already_exists, Node}}} -> ok
    end,
    mnesia:start(),

    %% Start the supervisor
    yawl_bridge_sup:start_link().

stop(_State) ->
    mnesia:stop().