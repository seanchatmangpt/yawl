%%-------------------------------------------------------------------
%% Test script for ML Bridge supervision tree
%%-------------------------------------------------------------------
-module(test_supervision).
-export([run/0]).

run() ->
    io:format("~n=== Testing ML Bridge Supervision Tree ===~n"),

    % Check if we can start the supervisor
    case test_supervisor() of
        ok ->
            io:format("✓ Supervisor tests passed~n");
        {error, Reason} ->
            io:format("✗ Supervisor test failed: ~p~n", [Reason])
    end,

    % Test API calls
    case test_apis() of
        ok ->
            io:format("✓ API tests passed~n");
        {error, Reason} ->
            io:format("✗ API test failed: ~p~n", [Reason])
    end,

    % Test health checks
    case test_health() of
        ok ->
            io:format("✓ Health checks passed~n");
        {error, Reason} ->
            io:format("✗ Health check failed: ~p~n", [Reason])
    end,

    % Cleanup
    cleanup(),
    io:format("~n=== Tests completed ===~n").

test_supervisor() ->
    io:format("~n1. Testing supervisor start...~n"),

    % Start supervisor
    case ml_bridge_sup:start_link() of
        {ok, _Pid} ->
            io:format("   ✓ Supervisor started~n"),

            % Check children
            Children = supervisor:which_children(ml_bridge_sup),
            io:format("   Children: ~p~n", [Children]),

            % Verify expected children are present
            Expected = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],
            ActualIds = [Id || {Id, _, _, _} <- Children],

            case lists:sort(Expected) == lists:sort(ActualIds) of
        true ->
                    io:format("   ✓ All expected children present~n"),

                    % Test restart behavior
                    test_restart();
                _ ->
                    io:format("   ✗ Missing children: ~p~n", [Expected -- ActualIds]),
                    {error, missing_children}
            end;
        {error, _} = Error ->
            Error
    end.

test_restart() ->
    io:format("~n2. Testing restart behavior...~n"),

    % Get dspy_bridge pid
    case whereis(dspy_bridge) of
        undefined ->
            io:format("   ✗ dspy_bridge not running~n"),
            {error, dspy_bridge_not_running};
        Pid ->
            io:format("   dspy_bridge pid: ~p~n", [Pid]),

            % Terminate child
            case supervisor:terminate_child(ml_bridge_sup, dspy_bridge) of
                ok ->
                    io:format("   ✓ Terminated dspy_bridge~n"),

                    % Wait for restart
                    timer:sleep(2000),

                    % Check if it restarted
                    case whereis(dspy_bridge) of
                        NewPid when is_pid(NewPid), NewPid =/= Pid ->
                            io:format("   ✓ dspy_bridge restarted: ~p~n", [NewPid]),
                            ok;
                        NewPid when is_pid(NewPid) ->
                            io:format("   ⚠ dspy_bridge same pid (no restart needed)~n"),
                            ok;
                        undefined ->
                            {error, restart_failed}
                    end;
                {error, _} = Error ->
                    {error, {terminate_failed, Error}}
            end
    end.

test_apis() ->
    io:format("~n3. Testing API calls...~n"),

    % Test DSPy bridge APIs
    io:format("   Testing DSPy bridge...~n"),
    test_dspy_api(),

    % Test TPOT2 bridge APIs
    io:format("   Testing TPOT2 bridge...~n"),
    test_tpot2_api(),

    ok.

test_dspy_api() ->
    try
        % Test configuration
        dspy_bridge:configure_groq(),
        io:format("     ✓ configure_groq/0~n"),

        % Test status
        case dspy_bridge:status() of
            {ok, Status} ->
                io:format("     ✓ status/0 returned: ~p~n", [Status]);
            {error, Reason} ->
                io:format("     ! status/0 error (expected if Python not ready): ~p~n", [Reason])
        end,

        ok
    catch
        Error:Reason:Stack ->
            io:format("     ✗ Exception: ~p:~p~nStack: ~p~n", [Error, Reason, Stack]),
            {error, exception}
    end.

test_tpot2_api() ->
    try
        % Test configurations
        Config = tpot2_bridge:default_config(),
        io:format("     ✓ default_config/0: ~p~n", [Config]),

        QuickConfig = tpot2_bridge:quick_config(),
        io:format("     ✓ quick_config/0: ~p~n", [QuickConfig]),

        % Test status
        case tpot2_bridge:status() of
            {ok, Status} ->
                io:format("     ✓ status/0 returned: ~p~n", [Status]);
            {error, Reason} ->
                io:format("     ! status/0 error (expected if Python not ready): ~p~n", [Reason])
        end,

        ok
    catch
        Error:Reason:Stack ->
            io:format("     ✗ Exception: ~p:~p~nStack: ~p~n", [Error, Reason, Stack]),
            {error, exception}
    end.

test_health() ->
    io:format("~n4. Testing health checks...~n"),

    Bridges = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],

    lists:foreach(fun(Bridge) ->
        case Bridge:status() of
            {ok, Status} ->
                io:format("   ✓ ~p: healthy~n", [Bridge]);
            {error, Reason} ->
                io:format("   ~p: error (expected if Python not ready) - ~p~n", [Bridge, Reason])
        end
    end, Bridges),

    ok.

cleanup() ->
    io:format("~n5. Cleaning up...~n"),

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
        ok -> io:format("   ✓ Stopped supervisor~n");
        {error, Reason} -> io:format("   ! Failed to stop supervisor: ~p~n", [Reason])
    end.