% Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
% The YAWL Foundation is a collaboration of individuals and
% organisations who are committed to improving workflow technology.
%
% This file is part of YAWL. YAWL is free software: you can
% redistribute it and/or modify it under the terms of the GNU Lesser
% General Public License as published by the Free Software Foundation.
%
% YAWL is distributed in the hope that it will be useful, but WITHOUT
% ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
% or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
% Public License for more details.
%
% You should have received a copy of the GNU Lesser General Public
% License along with YAWL. If not, see <http://www.gnu.org/licenses/>.

-module(process_mining_bridge_tests).
-behaviour(gen_server).

%% API
-export([start_link/0, stop/0]).
%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

%% Test data
-export([test_event_log/0, test_spec/0, test_case_id/0]).

-record(state, {
    test_cases = [],
    current_case = none,
    results = []
}).

-define(SERVER, ?MODULE).

%% =============================================================================
%% API Functions
%% =============================================================================

start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

stop() ->
    gen_server:stop(?SERVER).

%% Test data generators
test_event_log() ->
    [
        #{<<"activity">> => <<"Task_A">>, <<"timestamp">> => <<"2024-01-01T10:00:00Z">>, <<"case_id">> => <<"case1">>},
        #{<<"activity">> => <<"Task_B">>, <<"timestamp">> => <<"2024-01-01T11:00:00Z">>, <<"case_id">> => <<"case1">>},
        #{<<"activity">> => <<"Task_C">>, <<"timestamp">> => <<"2024-01-01T12:00:00Z">>, <<"case_id">> => <<"case1">>},
        #{<<"activity">> => <<"Task_A">>, <<"timestamp">> => <<"2024-01-01T10:30:00Z">>, <<"case_id">> => <<"case2">>},
        #{<<"activity">> => <<"Task_C">>, <<"timestamp">> => <<"2024-01-01T11:30:00Z">>, <<"case_id">> => <<"case2">>}
    ].

test_spec() ->
    #{
        <<"name">> => <<"Test Process">>,
        <<"start_task">> => <<"Start">>,
        <<"end_task">> => <<"End">>,
        <<"tasks">> => [<<"Task_A">>, <<"Task_B">>, <<"Task_C">>],
        <<"edges"> => [
            {<<"Start">>, <<"Task_A">>},
            {<<"Task_A">>, <<"Task_B">>},
            {<<"Task_B">>, <<"Task_C">>},
            {<<"Task_C">>, <<"End">>}
        ]
    }.

test_case_id() ->
    <<"test-case-" + integer_to_list(erlang:system_time(millisecond))>>.

%% =============================================================================
%% gen_server callbacks
%% =============================================================================

init([]) ->
    {ok, #state{}}.

handle_call(Request, From, State) ->
    try
        Result = handle_test_call(Request, From, State),
        {reply, Result, State}
    catch
        Error:Reason ->
            lager:error("Test call failed: ~p:~p~nStacktrace: ~p", [Error, Reason, erlang:get_stacktrace()]),
            {reply, {error, {Error, Reason}}, State}
    end.

handle_cast(Msg, State) ->
    lager:warning("Unexpected cast: ~p", [Msg]),
    {noreply, State}.

handle_info(Info, State) ->
    lager:info("Received info: ~p", [Info]),
    {noreply, State}.

terminate(Reason, _State) ->
    lager:info("Process mining bridge terminating: ~p", [Reason]),
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%% =============================================================================
%% Test Call Handlers
%% =============================================================================

handle_test_call({start_test_case, TestId}, _From, State) ->
    lager:info("Starting test case: ~p", [TestId]),
    {reply, ok, State#state{current_case = TestId, test_cases = [TestId | State#state.test_cases]}};

handle_test_call({end_test_case, TestId}, _From, State) ->
    lager:info("Ending test case: ~p", [TestId]),
    {reply, ok, State#state{current_case = none}};

handle_test_call({test_event_log, EventLog}, _From, State) ->
    lager:info("Processing event log with ~p events", [length(EventLog)]),
    try
        % Validate event log structure
        Validated = validate_event_log(EventLog),
        {reply, {ok, Validated}, State}
    catch
        Error:Reason ->
            {reply, {error, {Error, Reason}}, State}
    end;

handle_test_call({check_conformance, EventLog, Spec}, _From, State) ->
    lager:info("Checking conformance for case with spec: ~p", [maps:get(<<"name">>, Spec, <<"unknown">>)]),
    try
        Result = check_conformance(EventLog, Spec),
        {reply, Result, State}
    catch
        Error:Reason ->
            {reply, {error, {Error, Reason}}, State}
    end;

handle_test_call({replay_trace, EventLog}, _From, State) ->
    lager:info("Replaying trace with ~p events", [length(EventLog)]),
    try
        Result = replay_trace(EventLog),
        {reply, Result, State}
    catch
        Error:Reason ->
            {reply, {error, {Error, Reason}}, State}
    end;

handle_test_call({test_fault_injection, FaultType}, _From, State) ->
    lager:info("Testing fault injection: ~p", [FaultType]),
    try
        Result = test_fault_injection(FaultType),
        {reply, Result, State}
    catch
        Error:Reason ->
            {reply, {error, {Error, Reason}}, State}
    end;

handle_test_call({benchmark_operation, Operation, Count}, _From, State) ->
    lager:info("Benchmarking operation ~p with ~p iterations", [Operation, Count]),
    try
        Result = benchmark_operation(Operation, Count),
        {reply, Result, State}
    catch
        Error:Reason ->
            {reply, {error, {Error, Reason}}, State}
    end;

handle_test_call({get_test_results}, _From, State) ->
    {reply, {ok, State#state.results}, State};

handle_test_call({clear_test_results}, _From, State) ->
    lager:info("Clearing test results"),
    {reply, ok, State#state{results = []}};

handle_test_call(Request, _From, State) ->
    lager:warning("Unknown test call: ~p", [Request]),
    {reply, {error, unknown_request}, State}.

%% =============================================================================
%% Test Functions
%% =============================================================================

validate_event_log(EventLog) when is_list(EventLog) ->
    case validate_events(EventLog) of
        {ok, ValidatedEvents} ->
            #{status => ok, events => ValidatedEvents, count => length(ValidatedEvents)};
        {error, Reason} ->
            #{status => error, reason => Reason}
    end.

validate_events([]) ->
    {ok, []};
validate_events([Event | Rest]) ->
    case is_valid_event(Event) of
        true ->
            case validate_events(Rest) of
                {ok, Events} ->
                    {ok, [Event | Events]};
                Error ->
                    Error
            end;
        false ->
            {error, invalid_event}
    end.

is_valid_event(Event) when is_map(Event) ->
    RequiredFields = [<<"activity">>, <<"timestamp">>, <<"case_id">>],
    lists:all(fun(Field) -> maps:is_key(Field, Event) end, RequiredFields);
is_valid_event(_) ->
    false.

check_conformance(EventLog, Spec) ->
    % Extract tasks from event log
    Tasks = lists:usort([maps:get(<<"activity">>, Event) || Event <- EventLog]),
    SpecTasks = maps:get(<<"tasks">>, Spec, []),

    % Check if all tasks exist in specification
    MissingTasks = lists:subtract(Tasks, SpecTasks),
    ExtraTasks = lists:subtract(SpecTasks, Tasks),

    Result = #{
        case_id => test_case_id(),
        timestamp => list_to_binary(os:system_time(millisecond)),
        total_events => length(EventLog),
        tasks => Tasks,
        missing_tasks => MissingTasks,
        extra_tasks => ExtraTasks,
        is_conformant => length(MissingTasks) == 0,
        issues => case length(MissingTasks) > 0 of
                     true -> [list_to_binary("Missing tasks: " ++ binary_to_list(iolist_to_binary(lists:join(<<", ">>, MissingTasks))))];
                     false -> []
                 end
    },

    lager:info("Conformance check result: ~p", [Result]),
    Result.

replay_trace(EventLog) ->
    % Simulate trace replay
    CompletedTasks = 0,
    FailedTasks = 0,
    StartTimestamp = os:system_time(millisecond),

    % Process each event
    {FinalCompleted, FinalFailed, Duration} =
        lists:foldl(fun(Event, {AccCompleted, AccFailed, _Timestamp}) ->
            case simulate_task_execution(Event) of
                ok -> {AccCompleted + 1, AccFailed, os:system_time(millisecond)};
                error -> {AccCompleted, AccFailed + 1, os:system_time(millisecond)}
            end
        end, {0, 0, StartTimestamp}, EventLog),

    Result = #{
        case_id => test_case_id(),
        start_time => StartTimestamp,
        end_time => Duration,
        total_tasks => length(EventLog),
        completed_tasks => FinalCompleted,
        failed_tasks => FinalFailed,
        success_rate => FinalCompleted / length(EventLog)
    },

    lager:info("Trace replay result: ~p", [Result]),
    Result.

simulate_task_execution(Event) ->
    % Simulate task execution with 95% success rate
    case rand:uniform(100) of
        _ when rand:uniform(100) =< 95 ->
            ok;
        _ ->
            error
    end.

test_fault_injection FaultType ->
    case FaultType of
        memory ->
            % Simulate memory pressure
            large_list = [lists:seq(1, 100000) || _ <- lists:seq(1, 100)],
            {ok, {memory_allocated, erlang:memory([total])}};
        timeout ->
            % Simulate timeout
            timer:sleep(2000),
            ok;
        crash ->
            % Simulate crash
            exit(normal);
        panic ->
            % Simulate panic
            erlang:error(test_panic);
        Error ->
            {error, {unknown_fault_type, Error}}
    end.

benchmark_operation(Operation, Count) ->
    Start = os:system_time(nano_second),

    OperationFun = case Operation of
        ping -> fun() -> self() ! ping end;
        conformance -> fun() -> check_conformance(test_event_log(), test_spec()) end;
        replay -> fun() -> replay_trace(test_event_log()) end;
        encode -> fun() -> jiffy:encode(test_event_log()) end;
        decode -> fun() -> jiffy:decode(jiffy:encode(test_event_log())) end;
        _ -> fun() -> ok end
    end,

    % Run operation multiple times
    lists:foreach(fun(_) -> OperationFun() end, lists:seq(1, Count)),

    End = os:system_time(nano_second),
    Duration = End - Start,
    PerOperation = Duration div Count,

    #{
        operation => Operation,
        iterations => Count,
        total_time_ns => Duration,
        time_per_operation_ns => PerOperation,
        time_per_operation_us => PerOperation div 1000,
        time_per_operation_ms => PerOperation div 1000000
    }.

%% =============================================================================
%% Utility Functions
%% =============================================================================

-ifdef(TEST).
-include_lib("eunit/include/eunit.hrl").

benchmark_test() ->
    ProcessMiningBridge = process_mining_bridge_tests,
    Result = ProcessMiningBridge:benchmark_operation(ping, 1000),
    ?assert(is_map(Result)),
    ?assert(maps:get(operation, Result) =:= ping).

conformance_test() ->
    ProcessMiningBridge = process_mining_bridge_tests,
    EventLog = ProcessMiningBridge:test_event_log(),
    Spec = ProcessMiningBridge:test_spec(),
    Result = ProcessMiningBridge:check_conformance(EventLog, Spec),
    ?assert(is_map(Result)),
    ?assert(maps:is_key(case_id, Result)).
-endif.