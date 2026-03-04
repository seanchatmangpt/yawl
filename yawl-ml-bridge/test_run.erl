%%-------------------------------------------------------------------
%% Test script for ML Bridge supervision tree
%%-------------------------------------------------------------------
-module(test_run).
-export([run/0]).

run() ->
    io:format("=== Testing ML Bridge Supervision Tree ===~n~n"),

    % 1. Start supervisor
    io:format("1. Starting supervisor...~n"),
    case ml_bridge_sup:start_link() of
        {ok, _} ->
            io:format("   ✓ Supervisor started~n");
        {error, {already_started, _}} ->
            io:format("   ✓ Supervisor already running~n");
        {error, Reason} ->
            io:format("   ✗ Failed to start: ~p~n", [Reason])
    end,

    % 2. Check children
    io:format("~n2. Checking child processes...~n"),
    Children = supervisor:which_children(ml_bridge_sup),
    io:format("   Children: ~p~n", [Children]),

    Expected = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],
    ActualIds = [Id || {Id, _, _, _} <- Children],

    case lists:sort(Expected) == lists:sort(ActualIds) of
        true ->
            io:format("   ✓ All expected children present: ~p~n", [ActualIds]),
            % Test restart behavior
            test_restart();
        false ->
            io:format("   ✗ Missing children. Expected: ~p, Got: ~p~n",
                     [Expected, ActualIds])
    end,

    % 3. Test status APIs
    io:format("~n3. Testing status APIs...~n"),
    Bridges = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],
    lists:foreach(fun(Bridge) ->
        case Bridge:status() of
            {ok, Status} ->
                io:format("   ✓ ~p: status() ok~n", [Bridge]);
            {error, _} ->
                io:format("   ! ~p: status() error\n", [Bridge])
        end
    end, Bridges),

    % 4. Cleanup
    io:format("~n4. Cleaning up...~n"),
    lists:foreach(fun(Child) ->
        case supervisor:terminate_child(ml_bridge_sup, Child) of
            ok ->
                io:format("   ✓ Stopped ~p~n", [Child]);
            {error, _} ->
                io:format("   ! Failed to stop ~p\n", [Child])
        end
    end, Children),

    case supervisor:stop_child(ml_bridge_sup) of
        ok ->
            io:format("   ✓ Supervisor stopped~n");
        {error, _} ->
            io:format("   ! Failed to stop supervisor\n")
    end,

    io:format("~n=== Test complete ===~n").

test_restart() ->
    % Test supervisor restart behavior
    io:format("   Testing supervisor restart...~n"),

    case whereis(dspy_bridge) of
        undefined ->
            io:format("   ! dspy_bridge not running~n");
        Pid ->
            io:format("   Stopping dspy_bridge (pid: ~p)...~n", [Pid]),
            case supervisor:terminate_child(ml_bridge_sup, dspy_bridge) of
                ok ->
                    timer:sleep(2000), % Wait for restart
                    case whereis(dspy_bridge) of
                        NewPid when is_pid(NewPid), NewPid =/= Pid ->
                            io:format("   ✓ dspy_bridge restarted: ~p~n", [NewPid]);
                        NewPid when is_pid(NewPid) ->
                            io:format("   ⚠ dspy_bridge same pid (no restart needed)~n");
                        undefined ->
                            io:format("   ✗ dspy_bridge was not restarted~n")
                    end;
                {error, _} ->
                    io:format("   ✗ Failed to terminate\n")
            end
    end.