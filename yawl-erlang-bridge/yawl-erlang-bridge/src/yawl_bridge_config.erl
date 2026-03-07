%% @doc Child Spec Validation Module
%% Validates supervisor child specifications at startup
%% Following 80/20 OTP best practices - fail fast on misconfiguration
-module(yawl_bridge_config).
-export([validate_child_spec/1,
         validate_supervisor_config/1,
         validate_all/0]).

-define(REQUIRED_KEYS, [id, start, restart, shutdown, type, modules]).
-define(VALID_RESTART_STRATEGIES, [permanent, transient, temporary]).
-define(VALID_TYPES, [worker, supervisor]).

%% @doc Validate a single child spec
validate_child_spec(Spec) when is_map(Spec) ->
    Checks = [
        fun check_required_keys/1,
        fun check_id/1,
        fun check_start/1,
        fun check_restart/1,
        fun check_shutdown/1,
        fun check_type/1,
        fun check_modules/1
    ],

    Results = lists:map(fun(Check) -> Check(Spec) end, Checks),
    Errors = [E || E <- Results, E =/= ok],

    case Errors of
        [] -> {ok, valid};
        _ -> {error, Errors}
    end;

validate_child_spec(Spec) ->
    {error, [{invalid_spec_format, Spec}]}.

%% @doc Validate supervisor configuration
validate_supervisor_config(#{strategy := Strategy, intensity := Intensity, period := Period}) ->
    Checks = [
        check_strategy(Strategy),
        check_intensity(Intensity),
        check_period(Period)
    ],
    Errors = [E || E <- Checks, E =/= ok],

    case Errors of
        [] -> {ok, valid};
        _ -> {error, Errors}
    end;

validate_supervisor_config(Config) ->
    {error, [{invalid_supervisor_config, Config}]}.

%% @doc Validate all known child specs
validate_all() ->
    ChildSpecs = get_all_child_specs(),
    Results = lists:map(fun({Name, Spec}) ->
        case validate_child_spec(Spec) of
            {ok, valid} -> {Name, ok};
            {error, Errors} -> {Name, {error, Errors}}
        end
    end, ChildSpecs),

    Errors = [{N, E} || {N, {error, E}} <- Results],
    case Errors of
        [] -> {ok, all_valid};
        _ -> {error, Errors}
    end.

%%====================================================================
%% Internal Validation Functions
%%====================================================================

check_required_keys(Spec) ->
    Missing = [K || K <- ?REQUIRED_KEYS, not maps:is_key(K, Spec)],
    case Missing of
        [] -> ok;
        _ -> {missing_keys, Missing}
    end.

check_id(#{id := Id}) when is_atom(Id) -> ok;
check_id(#{id := Id}) -> {invalid_id, Id, must_be_atom};
check_id(_) -> {missing_key, id}.

check_start(#{start := {Module, Function, Args}})
  when is_atom(Module), is_atom(Function), is_list(Args) ->
    %% Verify function is exported
    case erlang:function_exported(Module, Function, length(Args)) of
        true -> ok;
        false -> {start_function_not_exported, {Module, Function, length(Args)}}
    end;
check_start(#{start := Start}) -> {invalid_start, Start};
check_start(_) -> {missing_key, start}.

check_restart(#{restart := Restart}) ->
    case lists:member(Restart, ?VALID_RESTART_STRATEGIES) of
        true -> ok;
        false -> {invalid_restart, Restart, ?VALID_RESTART_STRATEGIES}
    end;
check_restart(_) -> {missing_key, restart}.

check_shutdown(#{shutdown := brutal_kill}) -> ok;
check_shutdown(#{shutdown := Timeout}) when is_integer(Timeout), Timeout >= 0 -> ok;
check_shutdown(#{shutdown := infinity}) -> ok;
check_shutdown(#{shutdown := Shutdown}) -> {invalid_shutdown, Shutdown};
check_shutdown(_) -> {missing_key, shutdown}.

check_type(#{type := Type}) ->
    case lists:member(Type, ?VALID_TYPES) of
        true -> ok;
        false -> {invalid_type, Type, ?VALID_TYPES}
    end;
check_type(_) -> {missing_key, type}.

check_modules(#{modules := Modules}) when is_list(Modules) ->
    %% Verify all modules are atoms and exist
    InvalidModules = [M || M <- Modules, not is_atom(M)],
    case InvalidModules of
        [] -> ok;
        _ -> {invalid_modules, InvalidModules}
    end;
check_modules(#{modules := Modules}) -> {invalid_modules, Modules};
check_modules(_) -> {missing_key, modules}.

check_strategy(one_for_one) -> ok;
check_strategy(one_for_all) -> ok;
check_strategy(rest_for_one) -> ok;
check_strategy(simple_one_for_one) -> ok;
check_strategy(Strategy) -> {invalid_strategy, Strategy}.

check_intensity(Intensity) when is_integer(Intensity), Intensity >= 0 -> ok;
check_intensity(Intensity) -> {invalid_intensity, Intensity, must_be_non_negative_integer}.

check_period(Period) when is_integer(Period), Period > 0 -> ok;
check_period(Period) -> {invalid_period, Period, must_be_positive_integer}.

%%====================================================================
%% Child Spec Retrieval
%%====================================================================

get_all_child_specs() ->
    [
        {process_mining_bridge, process_mining_bridge_spec()},
        {data_modelling_bridge, data_modelling_bridge_spec()},
        {mnesia_registry, mnesia_registry_spec()}
    ].

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
