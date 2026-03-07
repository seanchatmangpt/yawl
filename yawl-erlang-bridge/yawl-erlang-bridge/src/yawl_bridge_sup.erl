%% @doc Top-Level Supervisor for YAWL Bridge
%% Implements OTP 80/20 best practices:
%% - one_for_one strategy for isolated restarts
%% - Intensity 5/60s (allows transient failures, prevents restart storms)
%% - All workers trap_exit for clean shutdown
%% - Named processes for easy monitoring
-module(yawl_bridge_sup).
-behaviour(supervisor).

%% API
-export([start_link/0,
         get_child_status/0,
         restart_child/1,
         get_health/0]).

%% Supervisor callbacks
-export([init/1]).

-define(INTENSITY, 5).
-define(PERIOD, 60).

start_link() ->
    supervisor:start_link({local, ?MODULE}, ?MODULE, []).

%% @doc Get status of all child processes
get_child_status() ->
    Children = supervisor:which_children(?MODULE),
    lists:map(fun({Id, Pid, Type, Modules}) ->
        #{
            id => Id,
            pid => Pid,
            type => Type,
            modules => Modules,
            alive => Pid =/= restarting andalso Pid =/= undefined andalso is_process_alive(Pid)
        }
    end, Children).

%% @doc Restart a specific child
restart_child(ChildId) ->
    case supervisor:terminate_child(?MODULE, ChildId) of
        ok -> supervisor:restart_child(?MODULE, ChildId);
        {error, Reason} -> {error, Reason}
    end.

%% @doc Get supervisor health status
get_health() ->
    Children = get_child_status(),
    AllAlive = lists:all(fun(#{alive := Alive}) -> Alive end, Children),
    #{
        supervisor => ?MODULE,
        status => if AllAlive -> healthy; true -> degraded end,
        children => Children,
        intensity => ?INTENSITY,
        period => ?PERIOD
    }.

init([]) ->
    %% Get configuration from application env
    Intensity = application:get_env(process_mining_bridge, supervisor_intensity, ?INTENSITY),
    Period = application:get_env(process_mining_bridge, supervisor_period, ?PERIOD),

    SupFlags = #{
        strategy => one_for_one,
        intensity => Intensity,
        period => Period
    },

    %% Child specs in dependency order
    %% Infrastructure services first, then business logic
    ChildSpecs = [
        %% Telemetry and monitoring (start first)
        telemetry_spec(),
        nif_guard_spec(),

        %% Core bridge services
        process_mining_bridge_spec(),
        data_modelling_bridge_spec(),
        mnesia_registry_spec(),

        %% Backup and health check (start last)
        mnesia_backup_spec(),
        health_check_spec()
    ],

    %% Validate all child specs at startup
    lists:foreach(fun validate_child_spec/1, ChildSpecs),

    lager:info("Starting YAWL Bridge Supervisor with ~p children", [length(ChildSpecs)]),
    {ok, {SupFlags, ChildSpecs}}.

%%====================================================================
%% Child Specifications
%%====================================================================

telemetry_spec() ->
    #{
        id => yawl_bridge_telemetry,
        start => {yawl_bridge_telemetry, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [yawl_bridge_telemetry]
    }.

nif_guard_spec() ->
    #{
        id => yawl_bridge_nif_guard,
        start => {yawl_bridge_nif_guard, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [yawl_bridge_nif_guard]
    }.

process_mining_bridge_spec() ->
    #{
        id => process_mining_bridge,
        start => {process_mining_bridge, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [process_mining_bridge]
    }.

data_modelling_bridge_spec() ->
    #{
        id => data_modelling_bridge,
        start => {data_modelling_bridge, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [data_modelling_bridge]
    }.

mnesia_registry_spec() ->
    #{
        id => mnesia_registry,
        start => {mnesia_registry, start_link, []},
        restart => permanent,
        shutdown => 10000,  %% Extra time for Mnesia cleanup
        type => worker,
        modules => [mnesia_registry]
    }.

mnesia_backup_spec() ->
    BackupDir = application:get_env(process_mining_bridge, backup_dir, "/tmp/yawl_mnesia_backups"),
    BackupInterval = application:get_env(process_mining_bridge, backup_interval, 300000),
    #{
        id => yawl_bridge_mnesia_backup,
        start => {yawl_bridge_mnesia_backup, start_link, [[
            {backup_dir, BackupDir},
            {interval, BackupInterval}
        ]]},
        restart => permanent,
        shutdown => 10000,
        type => worker,
        modules => [yawl_bridge_mnesia_backup]
    }.

health_check_spec() ->
    #{
        id => yawl_bridge_health_v2,
        start => {yawl_bridge_health_v2, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [yawl_bridge_health_v2]
    }.

%%====================================================================
%% Validation
%%====================================================================

validate_child_spec(#{id := Id, start := {M, F, A}} = Spec) ->
    %% Check required keys
    Required = [id, start, restart, shutdown, type, modules],
    Missing = [K || K <- Required, not maps:is_key(K, Spec)],
    case Missing of
        [] ->
            %% Check start function arity
            case erlang:function_exported(M, F, length(A)) of
                true -> ok;
                false -> lager:warning("Start function ~p:~p/~p may not be exported",
                    [M, F, length(A)])
            end;
        _ ->
            lager:error("Child spec ~p missing keys: ~p", [Id, Missing]),
            throw({invalid_child_spec, Id, {missing_keys, Missing}})
    end;
validate_child_spec(Spec) ->
    throw({invalid_child_spec, Spec}).

