%% @doc Enhanced Health Check Module
%% Comprehensive health monitoring for load balancers and monitoring systems
%% Following 80/20 OTP best practices
-module(yawl_bridge_health_v2).
-behaviour(gen_server).

%% API
-export([start_link/0,
         check/0,
         check/1,
         liveness/0,
         readiness/0,
         startup/0,
         get_components/0]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(COMPONENTS, [
    {process_mining_bridge, fun check_process_mining/0},
    {data_modelling_bridge, fun check_data_modelling/0},
    {mnesia_registry, fun check_mnesia_registry/0},
    {mnesia, fun check_mnesia/0},
    {nif, fun check_nif/0}
]).

-define(STARTUP_TIMEOUT, 30000).
-define(READINESS_TIMEOUT, 5000).

-record(state, {
    startup_complete :: boolean(),
    startup_time :: integer() | undefined,
    checks :: map()
}).

%%====================================================================
%% API Functions
%%====================================================================

start_link() ->
    gen_server:start_link({local, ?MODULE}, ?MODULE, [], []).

%% @doc Full health check (for monitoring)
check() ->
    check(all).

%% @doc Health check for specific category
check(Category) ->
    gen_server:call(?MODULE, {check, Category}, ?READINESS_TIMEOUT).

%% @doc Liveness probe (is process alive?)
liveness() ->
    case whereis(?MODULE) of
        undefined -> #{status => dead};
        Pid when is_pid(Pid) -> #{status => alive, pid => Pid}
    end.

%% @doc Readiness probe (can serve traffic?)
readiness() ->
    gen_server:call(?MODULE, readiness, ?READINESS_TIMEOUT).

%% @doc Startup probe (has application started?)
startup() ->
    gen_server:call(?MODULE, startup, ?STARTUP_TIMEOUT).

%% @doc Get list of health check components
get_components() ->
    [Name || {Name, _} <- ?COMPONENTS].

%%====================================================================
%% gen_server Callbacks
%%====================================================================

init([]) ->
    process_flag(trap_exit, true),

    State = #state{
        startup_complete = false,
        startup_time = undefined,
        checks = #{}
    },

    %% Schedule periodic health checks
    erlang:send_after(1000, self(), periodic_check),

    %% Mark startup complete after initialization
    self() ! startup_complete,

    {ok, State}.

handle_call({check, all}, _From, State) ->
    Results = run_all_checks(),
    OverallStatus = determine_status(Results),
    {reply, #{
        status => OverallStatus,
        timestamp => erlang:system_time(millisecond),
        components => Results,
        uptime_ms => get_uptime(State)
    }, State};

handle_call({check, Component}, _From, State) when is_atom(Component) ->
    Result = check_component(Component),
    {reply, Result, State};

handle_call(readiness, _From, State) ->
    case State#state.startup_complete of
        true ->
            Results = run_critical_checks(),
            Status = determine_status(Results),
            {reply, #{ready => Status =:= healthy, status => Status, components => Results}, State};
        false ->
            {reply, #{ready => false, status => starting}, State}
    end;

handle_call(startup, _From, State) ->
    {reply, #{started => State#state.startup_complete}, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(periodic_check, State) ->
    Results = run_all_checks(),
    NewChecks = maps:merge(State#state.checks, Results),

    %% Alert on unhealthy components
    maps:fold(fun(Name, #{status := Status}, _) ->
        case Status of
            unhealthy -> lager:warning("Health check unhealthy: ~p", [Name]);
            _ -> ok
        end
    end, ok, Results),

    erlang:send_after(10000, self(), periodic_check),
    {noreply, State#state{checks = NewChecks}};

handle_info(startup_complete, State) ->
    {noreply, State#state{
        startup_complete = true,
        startup_time = erlang:system_time(millisecond)
    }};

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%====================================================================
%% Health Check Functions
%%====================================================================

run_all_checks() ->
    maps:from_list(lists:map(fun({Name, CheckFn}) ->
        {Name, CheckFn()}
    end, ?COMPONENTS)).

run_critical_checks() ->
    Critical = [process_mining_bridge, mnesia],
    maps:from_list(lists:map(fun(Name) ->
        case lists:keyfind(Name, 1, ?COMPONENTS) of
            {Name, CheckFn} -> {Name, CheckFn()};
            false -> {Name, #{status => unknown}}
        end
    end, Critical)).

check_component(Name) ->
    case lists:keyfind(Name, 1, ?COMPONENTS) of
        {Name, CheckFn} -> CheckFn();
        false -> #{status => unknown, error => component_not_found}
    end.

check_process_mining() ->
    try
        case whereis(process_mining_bridge) of
            undefined ->
                #{status => unhealthy, reason => process_not_running};
            Pid ->
                case is_process_alive(Pid) of
                    true ->
                        #{status => healthy, pid => Pid};
                    false ->
                        #{status => unhealthy, reason => process_dead}
                end
        end
    catch
        _:Reason -> #{status => unhealthy, error => Reason}
    end.

check_data_modelling() ->
    try
        case whereis(data_modelling_bridge) of
            undefined ->
                #{status => unhealthy, reason => process_not_running};
            Pid ->
                case is_process_alive(Pid) of
                    true -> #{status => healthy, pid => Pid};
                    false -> #{status => unhealthy, reason => process_dead}
                end
        end
    catch
        _:Reason -> #{status => unhealthy, error => Reason}
    end.

check_mnesia_registry() ->
    try
        case whereis(mnesia_registry) of
            undefined ->
                #{status => unhealthy, reason => process_not_running};
            Pid ->
                case is_process_alive(Pid) of
                    true -> #{status => healthy, pid => Pid};
                    false -> #{status => unhealthy, reason => process_dead}
                end
        end
    catch
        _:Reason -> #{status => unhealthy, error => Reason}
    end.

check_mnesia() ->
    try
        case mnesia:system_info(is_running) of
            yes ->
                Tables = mnesia:system_info(tables),
                TableStatus = check_tables(Tables),
                #{
                    status => healthy,
                    tables => length(Tables),
                    table_status => TableStatus
                };
            no ->
                #{status => unhealthy, reason => mnesia_not_running};
            stopping ->
                #{status => unhealthy, reason => mnesia_stopping};
            starting ->
                #{status => degraded, reason => mnesia_starting}
        end
    catch
        _:Reason -> #{status => unhealthy, error => Reason}
    end.

check_nif() ->
    try
        case erlang:module_loaded(process_mining_bridge_nif) of
            true -> #{status => healthy, loaded => true};
            false -> #{status => degraded, loaded => false, reason => nif_not_loaded}
        end
    catch
        _:Reason -> #{status => unhealthy, error => Reason}
    end.

check_tables(Tables) ->
    lists:map(fun(Table) ->
        try
            Size = mnesia:table_info(Table, size),
            Memory = mnesia:table_info(Table, memory),
            #{name => Table, size => Size, memory => Memory, status => ok}
        catch
            _:Reason -> #{name => Table, status => error, error => Reason}
        end
    end, Tables -- [schema]).

determine_status(Results) ->
    Statuses = [maps:get(status, V, unknown) || V <- maps:values(Results)],
    case {lists:member(unhealthy, Statuses), lists:member(degraded, Statuses)} of
        {true, _} -> unhealthy;
        {false, true} -> degraded;
        {false, false} -> healthy
    end.

get_uptime(#state{startup_time = undefined}) ->
    0;
get_uptime(#state{startup_time = StartTime}) ->
    erlang:system_time(millisecond) - StartTime.
