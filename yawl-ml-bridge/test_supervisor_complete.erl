%%-------------------------------------------------------------------
%% Complete test for ML Bridge supervision tree
%%-------------------------------------------------------------------
-module(test_supervisor_complete).
-export([run/0]).

run() ->
    io:format("=== Complete ML Bridge Supervision Tree Test ===~n~n"),

    % Start supervisor
    case start_supervisor() of
        ok ->
            io:format("✓ Supervisor started successfully~n"),
            % Test child processes
            test_children(),
            % Test API calls
            test_apis(),
            % Test restart behavior
            test_restart(),
            % Test health checks
            test_health(),
            % Cleanup
            cleanup();
        {error, Reason} ->
            io:format("✗ Failed to start supervisor: ~p~n", [Reason])
    end,

    io:format("~n=== Test Complete ===~n").

start_supervisor() ->
    case ml_bridge_sup:start_link() of
        {ok, _} -> ok;
        {error, {already_started, _}} -> ok;
        {error, Reason} -> {error, Reason}
    end.

test_children() ->
    io:format("~n1. Testing child processes...~n"),
    Children = supervisor:which_children(ml_bridge_sup),
    io:format("   Child processes: ~p~n", [Children]),

    Expected = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],
    ActualIds = [Id || {Id, _, _, _} <- Children],

    case lists:sort(Expected) == lists:sort(ActualIds) of
        true ->
            io:format("   ✓ All expected children present: ~p~n", [ActualIds]),
            lists:foreach(fun(C) ->
                check_child_status(C)
            end, ActualIds);
        false ->
            io:format("   ✗ Missing children: ~p~n", [Expected -- ActualIds])
    end.

check_child_status(Child) ->
    case supervisor:get_childspec(ml_bridge_sup, Child) of
        {ok, Spec} ->
            Type = maps:get(type, Spec),
            io:format("   ✓ ~p: ~p worker~n", [Child, Type]);
        undefined ->
            io:format("   ✗ ~p not found in supervisor~n", [Child])
    end.

test_apis() ->
    io:format("~n2. Testing API calls...~n"),

    % Test dspy_bridge APIs
    io:format("   Testing DSPy bridge...~n"),
    case dspy_bridge:configure_groq() of
        ok -> io:format("     ✓ configure_groq/0~n");
        {error, Reason} -> io:format("     ! configure_groq/0 error: ~p~n", [Reason])
    end,

    case dspy_bridge:status() of
        {ok, _} -> io:format("     ✓ dspy_bridge:status/0~n");
        {error, _} -> io:format("     ! dspy_bridge:status/0 error~n")
    end,

    % Test tpot2_bridge APIs
    io:format("   Testing TPOT2 bridge...~n"),
    case tpot2_bridge:default_config() of
        Config when is_map(Config) ->
            io:format("     ✓ default_config/0: ~p~n", [Config]);
        _ ->
            io:format("     ✗ default_config/0 failed~n")
    end,

    case tpot2_bridge:status() of
        {ok, _} -> io:format("     ✓ tpot2_bridge:status/0~n");
        {error, _} -> io:format("     ! tpot2_bridge:status/0 error~n")
    end.

test_restart() ->
    io:format("~n3. Testing supervisor restart behavior...~n"),

    case whereis(dspy_bridge) of
        undefined ->
            io:format("   ! dspy_bridge not running~n");
        Pid ->
            io:format("   Stopping dspy_bridge (pid: ~p)...~n", [Pid]),

            case supervisor:terminate_child(ml_bridge_sup, dspy_bridge) of
                ok ->
                    timer:sleep(2000),
                    case whereis(dspy_bridge) of
                        NewPid when is_pid(NewPid), NewPid =/= Pid ->
                            io:format("   ✓ dspy_bridge restarted with new pid: ~p~n", [NewPid]);
                        NewPid when is_pid(NewPid) ->
                            io:format("   ⚠ dspy_bridge same pid (no restart needed)~n");
                        undefined ->
                            io:format("   ✗ dspy_bridge was not restarted~n")
                    end;
                {error, Reason} ->
                    io:format("   ✗ Failed to terminate dspy_bridge: ~p~n", [Reason])
            end
    end.

test_health() ->
    io:format("~n4. Testing health checks...~n"),

    Bridges = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],
    HealthyCount = lists:foldl(fun(Bridge, Acc) ->
        case Bridge:status() of
            {ok, _} ->
                io:format("   ✓ ~p: healthy~n", [Bridge]),
                Acc + 1;
            {error, Reason} ->
                io:format("   ~p: error - ~p~n", [Bridge, Reason]),
                Acc
        end
    end, 0, Bridges),

    io:format("   Total healthy bridges: ~p/~p~n", [HealthyCount, length(Bridges)]).

cleanup() ->
    io:format("\n5. Cleaning up...~n"),

    % Stop children
    Children = [dspy_bridge, tpot2_bridge, yawl_ml_bridge],
    lists:foreach(fun(Child) ->
        case supervisor:terminate_child(ml_bridge_sup, Child) of
            ok -> io:format("   ✓ Stopped ~p~n", [Child]);
            {error, Reason} -> io:format("   ! Failed to stop ~p: ~p~n", [Child, Reason])
        end
    end, Children),

    % Stop supervisor
    case supervisor:stop_child(ml_bridge_sup) of
        ok -> io:format("   ✓ Supervisor stopped~n");
        {error, Reason} -> io:format("   ! Failed to stop supervisor: ~p~n", [Reason])
    end.