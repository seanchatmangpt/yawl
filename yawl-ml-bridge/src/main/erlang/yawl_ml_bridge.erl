%%%-------------------------------------------------------------------
%%% @doc YAWL ML Bridge - Erlang NIF interface to Python ML libraries
%%%
%%% Provides unified access to DSPy (dspy==3.1.3) and TPOT2 via Rust NIF with PyO3.
%%%
%%% == Architecture ==
%%%   Java -> Erlang/OTP -> yawl_ml_bridge (NIF) -> Rust -> PyO3 -> Python
%%%
%%% @end
%%%-------------------------------------------------------------------
-module(yawl_ml_bridge).
-behaviour(gen_server).

%% API
-export([
    start_link/0,
    start_link/1,
    stop/0,
    status/0,
    ping/0,
    %% DSPy
    dspy_init/1,
    dspy_predict/3,
    dspy_load_examples/1,
    %% TPOT2
    tpot2_init/1,
    tpot2_optimize/3,
    tpot2_get_best_pipeline/1,
    tpot2_get_fitness/1,
    %% NIF direct access (for testing)
    ml_bridge_status/0
]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(SERVER, ?MODULE).
-define(NIF_LIB, "yawl_ml_bridge").
-define(TIMEOUT, 30000).

-record(state, {
    dspy_ready :: boolean(),
    tpot2_available :: boolean()
}).

%%%===================================================================
%%% NIF Loading
%%%===================================================================

-on_load(init_nif/0).

init_nif() ->
    Priv = case code:priv_dir(?MODULE) of
        {error, _} ->
            case code:which(?MODULE) of
                non_existing -> "priv";
                Beam ->
                    filename:join(filename:dirname(filename:dirname(Beam)), "priv")
            end;
        P -> P
    end,
    Path = filename:join(Priv, ?NIF_LIB),
    io:format("Loading NIF from: ~s~n", [Path]),
    case erlang:load_nif(Path, 0) of
        ok -> io:format("NIF loaded successfully~n"), ok;
        {error, Reason} -> io:format("NIF load error: ~p~n", [Reason]), ok
    end.

%%%===================================================================
%%% NIF Stubs (loaded from Rust - names MUST match rustler::init! list)
%%%===================================================================

%% @private NIF stub - replaced by Rust when loaded
dspy_init(_ConfigJson) ->
    erlang:nif_error(nif_not_loaded).

%% @private NIF stub - replaced by Rust when loaded
dspy_predict(_SigJson, _InJson, _ExJson) ->
    erlang:nif_error(nif_not_loaded).

%% @private NIF stub - replaced by Rust when loaded
dspy_load_examples(_ExJson) ->
    erlang:nif_error(nif_not_loaded).

%% @private NIF stub - replaced by Rust when loaded
tpot2_init(_ConfigJson) ->
    erlang:nif_error(nif_not_loaded).

%% @private NIF stub - replaced by Rust when loaded
tpot2_optimize(_XJson, _YJson, _ConfigJson) ->
    erlang:nif_error(nif_not_loaded).

%% @private NIF stub - replaced by Rust when loaded
tpot2_get_best_pipeline(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @private NIF stub - replaced by Rust when loaded
tpot2_get_fitness(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @private NIF stub - replaced by Rust when loaded
ml_bridge_status() ->
    erlang:nif_error(nif_not_loaded).

%% @private NIF stub - replaced by Rust when loaded
ping() ->
    erlang:nif_error(nif_not_loaded).

%%%===================================================================
%%% API - Public Functions (wrappers around NIFs with JSON conversion)
%%%===================================================================

start_link() ->
    start_link([]).

start_link(Options) ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [Options], []).

stop() ->
    gen_server:stop(?SERVER).

%% @doc Health check - ping the NIF (direct NIF call, no wrapper needed)

%% @doc Get bridge status (Python, DSPy, TPOT2 availability)
status() ->
    case ml_bridge_status() of
        {ok, StatusJson} -> {ok, json:decode(StatusJson)};
        {error, Reason} -> {error, Reason}
    end.

%%% DSPy API - wrappers that convert maps to JSON binaries

%% @doc Initialize DSPy with provider config (map or binary JSON)
%% Accepts map and converts to JSON, or passes binary JSON directly
dspy_init_api(ConfigMap) when is_map(ConfigMap) ->
    ConfigJson = encode_json(ConfigMap),
    dspy_init(ConfigJson).

%% @doc Run DSPy prediction (Erlang-term API)
dspy_predict_api(Signature, Inputs, Examples) ->
    SigJson = encode_json(Signature),
    InJson = encode_json(Inputs),
    ExJson = case Examples of
        [] -> <<"none">>;
        _ -> encode_json(Examples)
    end,
    case dspy_predict(SigJson, InJson, ExJson) of
        {ok, ResultJson} -> {ok, decode_json(ResultJson)};
        {error, Reason} -> {error, Reason}
    end.

%% @doc Load few-shot examples
dspy_load_examples_api(Examples) when is_list(Examples) ->
    ExJson = encode_json(Examples),
    dspy_load_examples(ExJson).

%%% TPOT2 API - wrappers that convert terms to JSON

%% @doc Initialize TPOT2
tpot2_init_api(ConfigMap) when is_map(ConfigMap) ->
    ConfigJson = encode_json(ConfigMap),
    tpot2_init(ConfigJson).

%% @doc Run TPOT2 optimization (Erlang-term API)
tpot2_optimize_api(X, Y, Config) when is_list(X), is_list(Y), is_map(Config) ->
    XJson = encode_json(X),
    YJson = encode_json(Y),
    ConfigJson = encode_json(Config),
    case tpot2_optimize(XJson, YJson, ConfigJson) of
        {ok, ResultJson} -> {ok, decode_json(ResultJson)};
        {error, Reason} -> {error, Reason}
    end.

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

init([_Options]) ->
    State = #state{
        dspy_ready = false,
        tpot2_available = false
    },
    {ok, State}.

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_request}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%%===================================================================
%%% Internal Functions
%%%===================================================================

encode_json(Term) ->
    iolist_to_binary(json:encode(Term)).

decode_json(Binary) ->
    json:decode(Binary).
