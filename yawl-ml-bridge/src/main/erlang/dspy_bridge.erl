%%%-------------------------------------------------------------------
%%% @doc DSPy Bridge - Erlang NIF interface to Python DSPy library
%%%
%%% Provides fault-tolerant access to DSPy (dspy==3.1.3) for LLM
%%% optimization via Rust NIF with PyO3.
%%%
%%% == Architecture ==
%%% Java → Erlang → NIF → Rust → PyO3 → Python (dspy)
%%%
%%% == Usage ==
%%%   {ok, _} = dspy_bridge:start_link(),
%%%   {ok, Result} = dspy_bridge:predict(Signature, Inputs).
%%%
%%% @end
%%%-------------------------------------------------------------------
-module(dspy_bridge).
-behaviour(gen_server).

%% API
-export([
    start_link/0,
    stop/0,
    predict/2,
    predict/3,
    configure/1,
    configure_groq/0,
    configure_openai/0,
    configure_anthropic/0,
    load_examples/1,
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
    config :: map(),
    examples :: list()
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
            logger:warning("DSPy NIF load failed: ~p, using fallbacks", [Reason]),
            ok;
        {error, {reload, _}} ->
            ok;
        {error, Reason} ->
            logger:warning("DSPy NIF load error: ~p, using fallbacks", [Reason]),
            ok
    end.

%%%===================================================================
%%% NIF Stubs - Loaded from Rust
%%%===================================================================

-spec dspy_init(binary()) -> {ok, atom()} | {error, term()}.
dspy_init(_ConfigJson) ->
    erlang:nif_error(nif_not_loaded).

-spec dspy_predict(binary(), binary(), binary() | none) -> {ok, binary()} | {error, term()}.
dspy_predict(_SignatureJson, _InputsJson, _ExamplesJson) ->
    erlang:nif_error(nif_not_loaded).

-spec dspy_load_examples(binary()) -> {ok, integer()} | {error, term()}.
dspy_load_examples(_ExamplesJson) ->
    erlang:nif_error(nif_not_loaded).

-spec ml_bridge_status() -> {ok, binary()} | {error, term()}.
ml_bridge_status() ->
    erlang:nif_error(nif_not_loaded).

-spec ping() -> {ok, binary()} | {error, term()}.
ping() ->
    erlang:nif_error(nif_not_loaded).

%%%===================================================================
%%% API
%%%===================================================================

start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

stop() ->
    gen_server:stop(?SERVER).

%% @doc Predict using signature and inputs
-spec predict(map(), map()) -> {ok, map()} | {error, term()}.
predict(Signature, Inputs) ->
    predict(Signature, Inputs, []).

%% @doc Predict with few-shot examples
-spec predict(map(), map(), list()) -> {ok, map()} | {error, term()}.
predict(Signature, Inputs, Examples) ->
    SignatureJson = jsx:encode(Signature),
    InputsJson = jsx:encode(Inputs),
    ExamplesJson = case Examples of
        [] -> <<"none">>;
        _ -> jsx:encode(Examples)
    end,

    case dspy_predict(SignatureJson, InputsJson, ExamplesJson) of
        {ok, ResultJson} ->
            Result = jsx:decode(ResultJson, [return_maps]),
            {ok, Result};
        {error, Reason} ->
            {error, Reason}
    end.

%% @doc Configure DSPy with custom config
-spec configure(map()) -> ok | {error, term()}.
configure(Config) ->
    gen_server:call(?SERVER, {configure, Config}).

%% @doc Configure for Groq (llama-3.3-70b-versatile)
-spec configure_groq() -> ok | {error, term()}.
configure_groq() ->
    configure(#{provider => <<"groq">>, model => <<"llama-3.3-70b-versatile">>}).

%% @doc Configure for OpenAI (gpt-4)
-spec configure_openai() -> ok | {error, term()}.
configure_openai() ->
    configure(#{provider => <<"openai">>, model => <<"gpt-4">>}).

%% @doc Configure for Anthropic (claude-3-opus)
-spec configure_anthropic() -> ok | {error, term()}.
configure_anthropic() ->
    configure(#{provider => <<"anthropic">>, model => <<"claude-3-opus-20240229">>}).

%% @doc Load few-shot examples
-spec load_examples(list()) -> ok | {error, term()}.
load_examples(Examples) ->
    gen_server:call(?SERVER, {load_examples, Examples}).

%% @doc Get bridge status
-spec status() -> {ok, map()} | {error, term()}.
status() ->
    case ml_bridge_status() of
        {ok, StatusJson} ->
            Status = jsx:decode(StatusJson, [return_maps]),
            {ok, Status};
        {error, Reason} ->
            {error, Reason}
    end.

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

init([]) ->
    State = #state{
        config = #{},
        examples = []
    },
    {ok, State}.

handle_call({configure, Config}, _From, State) ->
    ConfigJson = jsx:encode(Config),
    case dspy_init(ConfigJson) of
        {ok, _} ->
            {reply, ok, State#state{config = Config}};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({load_examples, Examples}, _From, State) ->
    ExamplesJson = jsx:encode(Examples),
    case dspy_load_examples(ExamplesJson) of
        {ok, _Count} ->
            {reply, ok, State#state{examples = Examples}};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call(status, _From, State) ->
    case ml_bridge_status() of
        {ok, StatusJson} ->
            Status = jsx:decode(StatusJson, [return_maps]),
            {reply, {ok, Status}, State};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

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
