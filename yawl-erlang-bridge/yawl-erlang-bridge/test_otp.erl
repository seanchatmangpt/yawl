%% Simple test to verify OTP supervisor tree
-module(test_otp).

-export([start/0]).

start() ->
    %% Start Mnesia
    mnesia:create_schema([node()]),
    mnesia:start(),

    %% Start the supervisor
    case process_mining_bridge_app:start(normal, []) of
        {ok, Pid} ->
            io:format("Supervisor started: ~p~n", [Pid]),

            %% Check child processes
            Children = supervisor:which_children(yawl_bridge_sup),
            io:format("Children: ~p~n", [Children]),

            %% Check health
            Health = yawl_bridge_health:check(),
            io:format("Health check: ~p~n", [Health]),

            %% Stop the application
            application:stop(process_mining_bridge),
            mnesia:stop(),
            ok;
        {error, Reason} ->
            io:format("Failed to start: ~p~n", [Reason]),
            error
    end.