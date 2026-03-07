%% @doc Supervisor Telemetry Module
%% Implements restart counting, timing, and metrics collection
%% for OTP supervisor trees following 80/20 best practices.
-module(yawl_bridge_telemetry).
-behaviour(gen_server).

%% API
-export([start_link/0,
         record_restart/2,
         get_metrics/0,
         reset_metrics/0,
         get_health_status/0]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(SUPERVISORS, [yawl_bridge_sup, process_mining_bridge_sup]).
-define(METRICS_TABLE, yawl_supervisor_metrics).

-record(state, {
    metrics :: ets:tid(),
    start_time :: integer(),
    check_interval :: integer()
}).

-record(supervisor_metric, {
    name :: atom(),
    restart_count :: integer(),
    last_restart :: integer() | undefined,
    restart_reasons :: list(),
    child_status :: map()
}).

%%====================================================================
%% API Functions
%%====================================================================

start_link() ->
    gen_server:start_link({local, ?MODULE}, ?MODULE, [], []).

%% @doc Record a supervisor restart event
record_restart(Supervisor, Reason) ->
    gen_server:cast(?MODULE, {restart, Supervisor, Reason}).

%% @doc Get all collected metrics
get_metrics() ->
    gen_server:call(?MODULE, get_metrics).

%% @doc Reset all metrics
reset_metrics() ->
    gen_server:call(?MODULE, reset_metrics).

%% @doc Get health status based on restart frequency
get_health_status() ->
    gen_server:call(?MODULE, get_health_status).

%%====================================================================
%% gen_server Callbacks
%%====================================================================

init([]) ->
    process_flag(trap_exit, true),

    %% Create ETS table for metrics
    MetricsTable = ets:new(?METRICS_TABLE, [
        set,
        named_table,
        public,
        {keypos, #supervisor_metric.name}
    ]),

    %% Initialize metrics for each supervisor
    lists:foreach(fun(Sup) ->
        ets:insert(?METRICS_TABLE, #supervisor_metric{
            name = Sup,
            restart_count = 0,
            restart_reasons = [],
            child_status = #{}
        })
    end, ?SUPERVISORS),

    %% Schedule periodic health check
    erlang:send_after(30000, self(), check_supervisors),

    {ok, #state{
        metrics = MetricsTable,
        start_time = erlang:system_time(millisecond),
        check_interval = 30000
    }}.

handle_call(get_metrics, _From, State) ->
    Metrics = lists:map(fun(Sup) ->
        case ets:lookup(?METRICS_TABLE, Sup) of
            [Metric] -> metric_to_map(Metric);
            [] -> #{name => Sup, error => not_found}
        end
    end, ?SUPERVISORS),

    Uptime = erlang:system_time(millisecond) - State#state.start_time,
    Reply = #{
        uptime_ms => Uptime,
        supervisors => Metrics,
        system => get_system_metrics()
    },
    {reply, {ok, Reply}, State};

handle_call(reset_metrics, _From, State) ->
    lists:foreach(fun(Sup) ->
        ets:insert(?METRICS_TABLE, #supervisor_metric{
            name = Sup,
            restart_count = 0,
            restart_reasons = [],
            child_status = #{}
        })
    end, ?SUPERVISORS),
    {reply, ok, State};

handle_call(get_health_status, _From, State) ->
    Health = calculate_health_status(State),
    {reply, {ok, Health}, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast({restart, Supervisor, Reason}, State) ->
    case ets:lookup(?METRICS_TABLE, Supervisor) of
        [Metric] ->
            NewMetric = Metric#supervisor_metric{
                restart_count = Metric#supervisor_metric.restart_count + 1,
                last_restart = erlang:system_time(millisecond),
                restart_reasons = lists:sublist([Reason | Metric#supervisor_metric.restart_reasons], 10)
            },
            ets:insert(?METRICS_TABLE, NewMetric),

            %% Log restart for observability
            lager:warning("Supervisor ~p restart: ~p (total: ~p)",
                [Supervisor, Reason, NewMetric#supervisor_metric.restart_count]);
        [] ->
            lager:error("Unknown supervisor: ~p", [Supervisor])
    end,
    {noreply, State};

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(check_supervisors, State) ->
    %% Check each supervisor's child status
    lists:foreach(fun(Sup) ->
        update_child_status(Sup)
    end, ?SUPERVISORS),

    %% Schedule next check
    erlang:send_after(State#state.check_interval, self(), check_supervisors),
    {noreply, State};

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, State) ->
    ets:delete(?METRICS_TABLE),
    lager:info("Telemetry module stopped after ~p ms",
        [erlang:system_time(millisecond) - State#state.start_time]),
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%====================================================================
%% Internal Functions
%%====================================================================

update_child_status(Supervisor) ->
    try
        Children = supervisor:which_children(Supervisor),
        Status = maps:from_list(lists:map(fun({Id, Pid, Type, Modules}) ->
            {Id, #{
                pid => Pid,
                type => Type,
                modules => Modules,
                alive => is_process_alive(Pid) orelse Pid =:= restarting
            }}
        end, Children)),

        case ets:lookup(?METRICS_TABLE, Supervisor) of
            [Metric] ->
                ets:insert(?METRICS_TABLE, Metric#supervisor_metric{child_status = Status});
            [] ->
                ok
        end
    catch
        _:Reason ->
            lager:error("Failed to get children for ~p: ~p", [Supervisor, Reason])
    end.

calculate_health_status(State) ->
    Metrics = lists:map(fun(Sup) ->
        case ets:lookup(?METRICS_TABLE, Sup) of
            [Metric] -> metric_to_map(Metric);
            [] -> #{name => Sup, error => not_found}
        end
    end, ?SUPERVISORS),

    Uptime = erlang:system_time(millisecond) - State#state.start_time,

    %% Calculate health based on restart frequency
    %% HEALTHY: < 1 restart per hour
    %% WARNING: 1-5 restarts per hour
    %% CRITICAL: > 5 restarts per hour
    {Status, Issues} = evaluate_health(Metrics, Uptime),

    #{
        status => Status,
        issues => Issues,
        uptime_ms => Uptime,
        supervisors => Metrics
    }.

evaluate_health(Metrics, UptimeMs) ->
    HoursRunning = UptimeMs / (1000 * 60 * 60),

    {Statuses, Issues} = lists:foldl(
        fun(#{restart_count := Count, name := Name}, {Acc, IssuesAcc}) ->
            RestartsPerHour = if HoursRunning > 0 -> Count / HoursRunning; true -> 0 end,

            {Status, Issue} = if
                RestartsPerHour < 1 -> {healthy, none};
                RestartsPerHour < 5 -> {warning, {high_restarts, Name, RestartsPerHour}};
                true -> {critical, {excessive_restarts, Name, RestartsPerHour}}
            end,

            {[Status | Acc], case Issue of none -> IssuesAcc; _ -> [Issue | IssuesAcc] end};
        (_, Acc) -> Acc
    end, {[], []}, Metrics),

    OverallStatus = case lists:member(critical, Statuses) of
        true -> critical;
        false -> case lists:member(warning, Statuses) of
            true -> warning;
            false -> healthy
        end
    end,

    {OverallStatus, Issues}.

metric_to_map(#supervisor_metric{
    name = Name,
    restart_count = Count,
    last_restart = LastRestart,
    restart_reasons = Reasons,
    child_status = Status
}) ->
    #{
        name => Name,
        restart_count => Count,
        last_restart_ms => LastRestart,
        recent_reasons => Reasons,
        children => Status
    }.

get_system_metrics() ->
    #{
        processes => erlang:system_info(process_count),
        memory_total => erlang:memory(total),
        memory_processes => erlang:memory(processes),
        run_queue => erlang:statistics(run_queue),
        io => erlang:statistics(input) + erlang:statistics(output)
    }.
