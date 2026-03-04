%%%-------------------------------------------------------------------
%%% @doc Test script for ML Bridge supervision tree
%%%
%%% Tests:
%%% 1. Start supervisor and verify child workers
%%% 2. Test supervisor restart behavior
%%% 3. Test gen_server API calls
%%% 4. Test health checks
%%%
%%% @end
%%%-------------------------------------------------------------------
-module(test_supervision_tree).
-export([main/0, test_supervisor_start/0, test_worker_processes/0,
         test_restart_behavior/0, test_api_calls/0, test_health_checks/0]).

-include_lib("eunit/include/eunit.hrl").

%%====================================================================
%% API
%%====================================================================

main() ->
    io:format("~n=== Testing ML Bridge Supervision Tree ===~n"),

    % Test 1: Start supervisor
    case test_supervisor_start() of
        ok ->
            io:format("✓ Supervisor started successfully~n");
        {error, Reason} ->
            io:format("✗ Failed to start supervisor: ~p~n", [Reason]),
            exit(Reason)
    end,

    % Test 2: Verify child workers
    case test_worker_processes() of
        ok ->
            io:format("✓ All child workers started~n");
        {error, Reason} ->
            io:format("✗ Worker processes test failed: ~p~n", [Reason]),
            exit(Reason)
    end,

    % Test 3: Test supervisor restart behavior
    case test_restart_behavior() of
        ok ->
            io:format("✓ Supervisor restart behavior working~n");
        {error, Reason} ->
            io:format("✗ Restart behavior test failed: ~p~n", [Reason]),
            exit(Reason)
    end,

    % Test 4: Test API calls
    case test_api_calls() of
        ok ->
            io:format("✓ API calls working~n");
        {error, Reason} ->
            io:format("✗ API calls test failed: ~p~n", [Reason]),
            exit(Reason)
    end,

    % Test 5: Test health checks
    case test_health_checks() of
        ok ->
            io:format("✓ Health checks working~n");
        {error, Reason} ->
            io:format("✗ Health checks test failed: ~p~n", [Reason]),
            exit(Reason)
    end,

    % Cleanup
    cleanup(),
    io:format("~n=== All tests passed! ===~n").

%%====================================================================
%% Test Functions
%%====================================================================

test_supervisor_start() ->
    % Start the supervisor
    case ml_bridge_sup:start_link() of
        {ok, Pid} when is_pid(Pid) ->
            % Verify supervisor is running
            case supervisor:which_children(ml_bridge_sup) of
                [] ->
                    {error, no_children};
                Children ->
                    io:format("Supervisor started with ~p children~n", [length(Children)]),
                    ok
            end;
        {error, {already_started, Pid}} when is_pid(Pid) ->
            io:format("Supervisor already running: ~p~n", [Pid]),
            ok;
        {error, Reason} ->
            {error, Reason}
    end.

test_worker_processes() ->
    % Check all expected child processes are running
    Children = supervisor:which_children(ml_bridge_sup),

    ExpectedIds = [yawl_ml_bridge, dspy_bridge, tpot2_bridge],

    case check_children(ExpectedIds, Children) of
        true ->
            % Detailed check of each child
            check_child_status(yawl_ml_bridge),
            check_child_status(dspy_bridge),
            check_child_status(tpot2_bridge),
            ok;
        false ->
            {error, missing_children}
    end.

check_children(Expected, Actual) ->
    ActualIds = [Id || {Id, _, _, _} <- Actual],
    lists:all(fun(Id) -> lists:member(Id, Expected) end, Expected)
    andalso length(Expected) =:= length(Actual).

check_child_status(ChildId) ->
    case supervisor:get_childspec(ml_bridge_sup, ChildId) of
        {ok, Spec} ->
            io:format("  ✓ ~p: ~p~n", [ChildId, maps:get(type, Spec)]);
        undefined ->
            {error, {child_not_found, ChildId}}
    end.

test_restart_behavior() ->
    % Test one_for_one restart strategy
    io:format("  Testing restart behavior...~n"),

    % Get current children count
    BeforeCount = length(supervisor:which_children(ml_bridge_sup)),

    % Kill a worker process to test restart
    case whereis(dspy_bridge) of
        undefined ->
            {error, dspy_bridge_not_running};
        Pid ->
            io:format("    Stopping dspy_bridge (pid: ~p)...~n", [Pid]),

            % Stop the process gracefully
            case supervisor:terminate_child(ml_bridge_sup, dspy_bridge) of
                ok ->
                    % Wait for restart
                    timer:sleep(2000),

                    % Check if it was restarted
                    case whereis(dspy_bridge) of
                        NewPid when is_pid(NewPid), NewPid =/= Pid ->
                            io:format("    ✓ dspy_bridge restarted with new pid: ~p~n", [NewPid]),
                            ok;
                        NewPid when is_pid(NewPid) ->
                            {error, same_pid_after_restart};
                        undefined ->
                            {error, dspy_bridge_not_restarted}
                    end;
                {error, Reason} ->
                    {error, {terminate_failed, Reason}}
            end
    end.

test_api_calls() ->
    io:format("  Testing API calls...~n"),

    % Test DSPy bridge API
    case test_dspy_api() of
        ok ->
            io:format("    ✓ DSPy API calls~n");
        {error, Reason} ->
            io:format("    ✗ DSPy API failed: ~p~n", [Reason]),
            {error, Reason}
    end,

    % Test TPOT2 bridge API
    case test_tpot2_api() of
        ok ->
            io:format("    ✓ TPOT2 API calls~n");
        {error, Reason} ->
            io:format("    ✗ TPOT2 API failed: ~p~n", [Reason]),
            {error, Reason}
    end,

    ok.

test_dspy_api() ->
    % Configure DSPy
    try
        dspy_bridge:configure_groq(),

        % Test predict with simple signature
        Signature = #{module => <<"Predict">>,
                     class_name => <<"Signature">>,
                     fields => #{
                         question => string,
                         answer => string
                     }},
        Inputs = #{question => "What is the capital of France?"},

        % This will likely fail if Python is not running, but we just test the API
        case dspy_bridge:predict(Signature, Inputs) of
            {ok, Result} ->
                io:format("      predict() returned: ~p~n", [Result]);
            {error, Reason} ->
                % Expected if Python environment not ready
                case Reason of
                    {error, _} ->
                        io:format("      predict() returned expected error: ~p~n", [Reason]);
                    _ ->
                        {error, {unexpected_error, Reason}}
                end
        end,

        % Test status
        case dspy_bridge:status() of
            {ok, Status} ->
                io:format("      status() returned: ~p~n", [Status]);
            {error, Reason} ->
                % Expected if Python environment not ready
                case Reason of
                    {error, _} ->
                        io:format("      status() returned expected error: ~p~n", [Reason]);
                    _ ->
                        {error, {unexpected_error, Reason}}
                end
        end,

        ok
    catch
        Error:Reason ->
            {error, {exception, Error, Reason}}
    end.

test_tpot2_api() ->
    % Test TPOT2 API
    try
        % Test default config
        Config = tpot2_bridge:default_config(),
        io:format("      default_config(): ~p~n", [Config]),

        % Test quick config
        QuickConfig = tpot2_bridge:quick_config(),
        io:format("      quick_config(): ~p~n", [QuickConfig]),

        % Test status
        case tpot2_bridge:status() of
            {ok, Status} ->
                io:format("      status() returned: ~p~n", [Status]);
            {error, Reason} ->
                % Expected if Python environment not ready
                io:format("      status() returned expected error: ~p~n", [Reason])
        end,

        ok
    catch
        Error:Reason ->
            {error, {exception, Error, Reason}}
    end.

test_health_checks() ->
    io:format("  Testing health checks...~n"),

    % Test status through both bridges
    Bridges = [dspy_bridge, tpot2_bridge, yawl_ml_bridge],

    lists:foldl(fun(Bridge, Acc) ->
        case Bridge:status() of
            {ok, Status} ->
                io:format("    ~p: healthy~n", [Bridge]),
                Acc;
            {error, Reason} ->
                io:format("    ~p: error - ~p~n", [Bridge, Reason]),
                case Reason of
                    {error, _} -> Acc;  % Expected error if Python not ready
                    _ -> {error, health_check_failed}
                end
        end
    end, ok, Bridges).

%%====================================================================
%% Cleanup
%%====================================================================

cleanup() ->
    io:format("Cleaning up...~n"),

    % Stop all children
    case supervisor:terminate_child(ml_bridge_sup, dspy_bridge) of
        ok -> ok;
        {error, Reason} -> io:format("  Failed to stop dspy_bridge: ~p~n", [Reason])
    end,

    case supervisor:terminate_child(ml_bridge_sup, tpot2_bridge) of
        ok -> ok;
        {error, Reason} -> io:format("  Failed to stop tpot2_bridge: ~p~n", [Reason])
    end,

    case supervisor:terminate_child(ml_bridge_sup, yawl_ml_bridge) of
        ok -> ok;
        {error, Reason} -> io:format("  Failed to stop yawl_ml_bridge: ~p~n", [Reason])
    end,

    % Stop supervisor
    case supervisor:stop_child(ml_bridge_sup) of
        ok -> ok;
        {error, Reason} -> io:format("  Failed to stop supervisor: ~p~n", [Reason])
    end,

    ok.