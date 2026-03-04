%%%-------------------------------------------------------------------
%%% @doc TPOT2 Bridge - Erlang interface to Python TPOT2 library
%%%
%%% Provides fault-tolerant access to TPOT2 genetic programming
%%% optimization via yawl_ml_bridge NIF.
%%%
%%% == Architecture ==
%%% Java -> Erlang -> yawl_ml_bridge (NIF) -> Rust -> PyO3 -> Python (tpot2)
%%%
%%% @end
%%%-------------------------------------------------------------------
-module(tpot2_bridge).
-behaviour(gen_server).

%% API
-export([
    start_link/0,
    stop/0,
    optimize/2,
    optimize/3,
    get_best_pipeline/1,
    get_fitness/1,
    default_config/0,
    quick_config/0,
    status/0
]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(SERVER, ?MODULE).
-define(TIMEOUT, 600000).  % 10 minutes for optimization

-record(state, {
    optimizers :: map()
}).

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
    gen_server:call(?SERVER, {optimize, X, Y, Config}, ?TIMEOUT).

%% @doc Get best pipeline from optimizer
-spec get_best_pipeline(binary()) -> {ok, binary()} | {error, term()}.
get_best_pipeline(OptimizerId) ->
    yawl_ml_bridge:tpot2_get_best_pipeline(OptimizerId).

%% @doc Get fitness score from optimizer
-spec get_fitness(binary()) -> {ok, float()} | {error, term()}.
get_fitness(OptimizerId) ->
    yawl_ml_bridge:tpot2_get_fitness(OptimizerId).

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
    yawl_ml_bridge:status().

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

init([]) ->
    State = #state{
        optimizers = #{}
    },
    {ok, State}.

handle_call({optimize, X, Y, Config}, From, State) ->
    % Run optimization in background to avoid blocking
    Self = self(),
    spawn(fun() ->
        XJson = iolist_to_binary(json:encode(X)),
        YJson = iolist_to_binary(json:encode(Y)),
        ConfigJson = iolist_to_binary(json:encode(Config)),
        Result = yawl_ml_bridge:tpot2_optimize(XJson, YJson, ConfigJson),
        Self ! {optimization_complete, From, Result}
    end),
    {noreply, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_request}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info({optimization_complete, From, Result}, State) ->
    case Result of
        {ok, ResultJson} ->
            Decoded = json:decode(ResultJson),
            gen_server:reply(From, {ok, Decoded});
        {error, Reason} ->
            gen_server:reply(From, {error, Reason})
    end,
    {noreply, State};

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.
