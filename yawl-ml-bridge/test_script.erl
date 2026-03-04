%% Test script
-module(test_script).
-export([main/0]).

main() ->
    io:format("=== Testing ML Bridge Supervision Tree ===\n\n"),

    % 1. Start supervisor
    case ml_bridge_sup:start_link() of
        {ok, _} -> io:format("✓ Supervisor started\n");
        {error, {already_started, _}} -> io:format("✓ Supervisor already running\n");
        {error, Reason} -> io:format("✗ Failed: ~p\n", [Reason])
    end,

    % 2. Check children
    Children = supervisor:which_children(ml_bridge_sup),
    Expected = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],
    ActualIds = [Id || {Id, _, _, _} <- Children],
    io:format("Child processes: ~p\n", [Children]),

    case lists:sort(Expected) of
        lists:sort(ActualIds) ->
            io:format("✓ All children present\n"),
            % 3. Test supervisor restart
            case whereis(dspy_bridge) of
                undefined -> io:format("! dspy_bridge not running\n");
                Pid ->
                    io:format("Stopping dspy_bridge (pid: ~p)...", [Pid]),
                    supervisor:terminate_child(ml_bridge_sup, dspy_bridge),
                    timer:sleep(2000),
                    case whereis(dspy_bridge) of
                        NewPid when is_pid(NewPid) ->
                            io:format("✓ Restarted: ~p\n", [NewPid]);
                        undefined ->
                            io:format("✗ Not restarted\n")
                    end
            end;
        _ ->
            io:format("✗ Missing: ~p\n", [Expected -- ActualIds])
    end,

    % 4. Test APIs
    io:format("\nTesting status APIs:\n"),
    Bridges = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],
    lists:foreach(fun(B) ->
        case B:status() of
            {ok, Status} -> io:format("✓ ~p: ok\n", [B]);
            {error, Error} -> io:format("! ~p: error ~p\n", [B, Error])
        end
    end, Bridges),

    % 5. Cleanup
    io:format("\nCleaning up:\n"),
    lists:foreach(fun(C) ->
        case supervisor:terminate_child(ml_bridge_sup, C) of
            ok -> io:format("✓ ~p\n", [C]);
            {error, _} -> io:format("! ~p\n", [C])
        end
    end, Children),

    case supervisor:stop_child(ml_bridge_sup) of
        ok -> io:format("✓ Supervisor stopped\n");
        {error, _} -> io:format("! Supervisor\n")
    end,

    io:format("\n=== Test complete ===\n").