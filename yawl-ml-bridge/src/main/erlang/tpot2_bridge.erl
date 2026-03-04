%%%-------------------------------------------------------------------
%%% @doc TPOT2 Bridge - Erlang NIF interface to Python TPOT2 library
%%%
%%% Provides fault-tolerant access to TPOT2 genetic programming
%%% optimization via Rust NIF with PyO3.
%%%
%%% == Architecture ==
%%% Java → Erlang → NIF → Rust → PyO3 → Python (tpot2)
%%%
%%% == Usage ==
%%%   {ok, _} = tpot2_bridge:start_link(),
%%%   {ok, Result} = tpot2_bridge:optimize(X, Y, Config).
%%%
%%% @end
%%%-------------------------------------------------------------------
-module(tpot2_bridge).
-behaviour(gen_server).

%% API
-export([
    start_link/0,
    stop/0,
    optimize/3,
    optimize/4,
    get_best_pipeline/1,
    get_fitness/1,
    default_config/0,
    quick_config/0,
    status/0
]).

%% gen_server callbacks
-export([
    init/1,
    handle_call/3,
    handle_cast/2,
    handle_info/2,
    terminate/2,
    code_change/3
]).

-define(SERVER, ?MODULE).
-define(NIF_LIB, "yawl_ml_bridge").

-record(state, {
    optimizers :: map()
}).

-record(optimizer, {
    id :: binary(),
    best_pipeline :: binary(),
    fitness :: float(),
    generations :: integer()
}).

%%%===================================================================
%%% NIF Loading
%%%===================================================================

-on_load(init_nif/0).

init_nif() ->
    PrivDir = case code:priv_dir(?MODULE) of
        {error, _} ->
            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
            filename:join(AppDir, "priv");
        Dir ->
            Dir
    end,
    NifPath = filename:join(PrivDir, ?NIF_LIB),
    case erlang:load_nif(NifPath, 0) of
        ok ->
            ok;
        {error, {load_failed, Reason}} ->
            logger:warning("TPOT2 NIF load failed: ~p, using fallbacks", [Reason]),
            ok;
        {error, {reload, _}} ->
            ok;
        {error, Reason} ->
            logger:warning("TPOT2 NIF load error: ~p, using fallbacks", [Reason]),
            ok
    end.

%%%===================================================================
%%% NIF Stubs - Loaded from Rust
%%%===================================================================

-spec tpot2_init(binary()) -> {ok, atom()} | {error, term()}.
tpot2_init(_ConfigJson) ->
    erlang:nif_error(nif_not_loaded).

-spec tpot2_optimize(binary(), binary(), binary()) -> {ok, binary()} | {error, term()}.
tpot2_optimize(_XJson, _YJson, _ConfigJson) ->
    erlang:nif_error(nif_not_loaded).

-spec tpot2_get_best_pipeline(binary()) -> {ok, binary()} | {error, term()}.
tpot2_get_best_pipeline(_OptimizerId) ->
    erlang:nif_error(nif_not_loaded).

-spec tpot2_get_fitness(binary()) -> {ok, float()} | {error, term()}.
tpot2_get_fitness(_OptimizerId) ->
    erlang:nif_error(nif_not_loaded).

%%%===================================================================
%%% API
%%%===================================================================

start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

stop() ->
    gen_server:stop(?SERVER).

%% @doc Run optimization with default config
-spec optimize(list(), list()) -> {ok, map()} | {error, term()}.
optimize(X, Y) ->
    optimize(X, Y, default_config()).

%% @doc Run optimization with custom config
-spec optimize(list(), list(), map()) -> {ok, map()} | {error, term()}.
optimize(X, Y, Config) ->
    XJson = jsx:encode(X),
    YJson = jsx:encode(Y),
    ConfigJson = jsx:encode(Config),

    case tpot2_optimize(XJson, YJson, ConfigJson) of
        {ok, ResultJson} ->
            Result = jsx:decode(ResultJson, [return_maps]),
            {ok, Result};
        {error, Reason} ->
            {error, Reason}
    end.

%% @doc Get best pipeline from optimizer
-spec get_best_pipeline(binary()) -> {ok, binary()} | {error, term()}.
get_best_pipeline(OptimizerId) ->
    tpot2_get_best_pipeline(OptimizerId).

%% @doc Get fitness score from optimizer
-spec get_fitness(binary()) -> {ok, float()} | {error, term()}.
get_fitness(OptimizerId) ->
    tpot2_get_fitness(OptimizerId).

%% @doc Default configuration (50 generations, 100 population)
-spec default_config() -> map().
default_config() ->
    #{
        generations => 50,
        population_size => 100,
        timeout_minutes => 10
    }.

%% @doc Quick configuration (10 generations, 50 population)
-spec quick_config() -> map().
quick_config() ->
    #{
        generations => 10,
        population_size => 50,
        timeout_minutes => 2
    }.

%% @doc Get bridge status
-spec status() -> {ok, map()} | {error, term()}.
status() ->
    case dspy_bridge:status() of
        {ok, Status} ->
            {ok, Status};
        {error, Reason} ->
            {error, Reason}
    end.

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

init([]) ->
    State = #state{
        optimizers = #{}
    },
    {ok, State}.

handle_call({optimize, X, Y, Config}, From, State) ->
    % Run optimization in background
    Self = self(),
    spawn(fun() ->
        Result = optimize(X, Y, Config),
        Self ! {optimization_complete, From, Result}
    end),
    {noreply, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_request}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info({optimization_complete, From, Result}, State) ->
    gen_server:reply(From, Result),
    {noreply, State};

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.
