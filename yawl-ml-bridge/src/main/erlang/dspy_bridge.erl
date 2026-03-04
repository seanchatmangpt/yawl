%%%-------------------------------------------------------------------
%%% @doc DSPy Bridge - Erlang interface to Python DSPy library
%%%
%%% Provides fault-tolerant access to DSPy (dspy==3.1.3) for LLM
%%% optimization via yawl_ml_bridge NIF.
%%%
%%% == Architecture ==
%%% Java -> Erlang -> yawl_ml_bridge (NIF) -> Rust -> PyO3 -> Python (dspy)
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
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(SERVER, ?MODULE).
-define(TIMEOUT, 60000).

-record(state, {
    config :: map(),
    examples :: list()
}).

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
    gen_server:call(?SERVER, {predict, Signature, Inputs, Examples}, ?TIMEOUT).

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
    yawl_ml_bridge:status().

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
    ConfigJson = iolist_to_binary(json:encode(Config)),
    case yawl_ml_bridge:dspy_init(ConfigJson) of
        {ok, _} ->
            {reply, ok, State#state{config = Config}};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({predict, Signature, Inputs, Examples}, _From, State) ->
    SigJson = iolist_to_binary(json:encode(Signature)),
    InJson = iolist_to_binary(json:encode(Inputs)),
    ExJson = case Examples of
        [] -> <<"none">>;
        _ -> iolist_to_binary(json:encode(Examples))
    end,
    case yawl_ml_bridge:dspy_predict(SigJson, InJson, ExJson) of
        {ok, ResultJson} ->
            Result = json:decode(ResultJson),
            {reply, {ok, Result}, State};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({load_examples, Examples}, _From, State) ->
    ExJson = iolist_to_binary(json:encode(Examples)),
    case yawl_ml_bridge:dspy_load_examples(ExJson) of
        {ok, _Count} ->
            {reply, ok, State#state{examples = Examples}};
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
