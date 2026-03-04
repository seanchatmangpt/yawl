%%%-------------------------------------------------------------------
%%% @doc Test GIL handling for TPOT2 operations
%%%
%%% Tests timeout handling, concurrent operations, and thread safety
%%%-------------------------------------------------------------------
-module(gil_handling_test).
-compile([export_all]).

-include_lib("eunit/include/eunit.hrl").

%%====================================================================
%% Test Cases
%%====================================================================

gil_handling_test_() ->
    [
        {"Test concurrent TPOT2 operations", 
            fun test_concurrent_operations/0},
        {"Test operation timeout", 
            fun test_operation_timeout/0},
        {"Test operation monitoring", 
            fun test_operation_monitoring/0},
        {"Test operation cancellation", 
            fun test_operation_cancellation/0}
    ].

%% Test multiple concurrent operations
test_concurrent_operations() ->
    % Start multiple operations
    OpIds = lists:map(fun(_) ->
        X = [[1,2,3], [4,5,6], [7,8,9]],
        Y = [0, 1, 0],
        Config = #{generations => 5, population_size => 20, timeout_minutes => 1},
        {ok, OpId} = yawl_ml_bridge:tpot2_optimize(X, Y, Config),
        OpId
    end, lists:seq(1, 3)),
    
    % Wait a bit for operations to start
    timer:sleep(1000),
    
    % Check that all operations are active
    {ok, ActiveOps} = yawl_ml_bridge:tpot2_get_active_operations(),
    ?assert(length(ActiveOps) >= 3, "Expected at least 3 active operations"),
    
    % Clean up
    lists:foreach(fun(OpId) ->
        ok = yawl_ml_bridge:tpot2_cancel_operation(OpId)
    end, OpIds).

%% Test timeout handling
test_operation_timeout() ->
    % Start a short operation
    X = [[1,2,3], [4,5,6]],
    Y = [0, 1],
    Config = #{generations => 1, population_size => 10, timeout_minutes => 0.1}, % 6 seconds
    {ok, OpId} = yawl_ml_bridge:tpot2_optimize(X, Y, Config),
    
    % Wait for timeout
    timer:sleep(7000), % Wait longer than timeout
    
    % Check operation status (should be timeout)
    {ok, Status} = yawl_ml_bridge:tpot2_monitor_operation(OpId),
    ?assert(maps:get(status, Status) == "timeout", "Operation should timeout"),
    
    % Clean up
    ok = yawl_ml_bridge:tpot2_cancel_operation(OpId).

%% Test operation monitoring
test_operation_monitoring() ->
    % Start a short operation
    X = [[1,2,3], [4,5,6]],
    Y = [0, 1],
    Config = #{generations => 2, population_size => 10, timeout_minutes => 1},
    {ok, OpId} = yawl_ml_bridge:tpot2_optimize(X, Y, Config),
    
    % Monitor progress
    timer:sleep(1000),
    {ok, Status1} = yawl_ml_bridge:tpot2_monitor_operation(OpId),
    Progress1 = maps:get(progress, Status1),
    ?assert(Progress1 > 0.0, "Progress should be > 0% after 1 second"),
    
    % Wait more
    timer:sleep(2000),
    {ok, Status2} = yawl_ml_bridge:tpot2_monitor_operation(OpId),
    Progress2 = maps:get(progress, Status2),
    ?assert(Progress2 > Progress1, "Progress should increase over time"),
    
    % Clean up
    ok = yawl_ml_bridge:tpot2_cancel_operation(OpId).

%% Test operation cancellation
test_operation_cancellation() ->
    % Start a long operation
    X = [[1,2,3], [4,5,6]],
    Y = [0, 1],
    Config = #{generations => 10, population_size => 50, timeout_minutes => 5},
    {ok, OpId} = yawl_ml_bridge:tpot2_optimize(X, Y, Config),
    
    % Cancel immediately
    ok = yawl_ml_bridge:tpot2_cancel_operation(OpId),
    
    % Check operation is no longer active
    timer:sleep(100),
    {ok, ActiveOps} = yawl_ml_bridge:tpot2_get_active_operations(),
    ?assert(not lists:member(OpId, [maps:get(id, Op) || Op <- ActiveOps]), 
            "Cancelled operation should not be active"),
    
    % Verify cancellation
    {error, "Operation not found"} = yawl_ml_bridge:tpot2_monitor_operation(OpId).
