%%%-------------------------------------------------------------------
%%% @doc DSPy Bridge V2 - Enhanced Erlang interface to Python DSPy library
%%%
%%% Provides fault-tolerant access to DSPy (dspy==3.1.3) for LLM
%%% optimization via yawl_ml_bridge NIF.
%%%
%%% == Architecture (JOR4J Pattern) ==
%%% Java -> Erlang -> yawl_ml_bridge (NIF) -> Rust -> PyO3 -> Python (dspy)
%%%
%%% == New Features (V2) ==
%%% - predict_chain/3: Full chain-of-thought pipeline
%%% - bootstrap_compile/3: BootstrapFewShot compilation
%%% - save_program/2: Serialize compiled program
%%% - load_program/1: Load saved program
%%% - list_programs/0: List cached programs
%%%
%%% @end
%%%-------------------------------------------------------------------
-module(dspy_bridge_v2).
-behaviour(gen_server).

%% API
-export([
    start_link/0,
    stop/0,
    %% Basic prediction
    predict/2,
    predict/3,
    predict_chain/2,
    predict_chain/3,
    %% Configuration
    configure/1,
    configure_groq/0,
    configure_openai/0,
    configure_anthropic/0,
    %% Few-shot
    load_examples/1,
    %% BootstrapFewShot
    bootstrap_compile/2,
    bootstrap_compile/3,
    %% Persistence
    save_program/2,
    load_program/1,
    list_programs/0,
    delete_program/1,
    %% Status
    status/0
]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(SERVER, ?MODULE).
-define(TIMEOUT, 60000).
-define(COMPILE_TIMEOUT, 300000).  % 5 minutes for compilation
-define(CACHE_DIR, "dspy_cache").

-record(state, {
    config :: map(),
    examples :: list(),
    programs :: map()  % Cache of compiled programs
}).

%%%===================================================================
%%% API
%%%===================================================================

start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

stop() ->
    gen_server:stop(?SERVER).

%%--------------------------------------------------------------------
%% @doc Basic prediction using signature and inputs
%% @end
%%--------------------------------------------------------------------
-spec predict(map(), map()) -> {ok, map()} | {error, term()}.
predict(Signature, Inputs) ->
    predict(Signature, Inputs, []).

%% @doc Predict with few-shot examples
-spec predict(map(), map(), list()) -> {ok, map()} | {error, term()}.
predict(Signature, Inputs, Examples) ->
    gen_server:call(?SERVER, {predict, Signature, Inputs, Examples}, ?TIMEOUT).

%%--------------------------------------------------------------------
%% @doc Chain-of-thought prediction
%% @end
%%--------------------------------------------------------------------
-spec predict_chain(map(), map()) -> {ok, map()} | {error, term()}.
predict_chain(Signature, Inputs) ->
    predict_chain(Signature, Inputs, []).

%% @doc Chain-of-thought with examples
-spec predict_chain(map(), map(), list()) -> {ok, map()} | {error, term()}.
predict_chain(Signature, Inputs, Examples) ->
    gen_server:call(?SERVER, {predict_chain, Signature, Inputs, Examples}, ?TIMEOUT).

%%--------------------------------------------------------------------
%% @doc Configure DSPy with custom config
%% @end
%%--------------------------------------------------------------------
-spec configure(map()) -> ok | {error, term()}.
configure(Config) ->
    gen_server:call(?SERVER, {configure, Config}).

%% @doc Configure for Groq (llama-3.3-70b-versatile)
-spec configure_groq() -> ok | {error, term()}.
configure_groq() ->
    configure(#{
        provider => <<"groq">>,
        model => <<"llama-3.3-70b-versatile">>,
        temperature => 0.0
    }).

%% @doc Configure for OpenAI (gpt-4)
-spec configure_openai() -> ok | {error, term()}.
configure_openai() ->
    configure(#{
        provider => <<"openai">>,
        model => <<"gpt-4">>,
        temperature => 0.0
    }).

%% @doc Configure for Anthropic (claude-3-opus)
-spec configure_anthropic() -> ok | {error, term()}.
configure_anthropic() ->
    configure(#{
        provider => <<"anthropic">>,
        model => <<"claude-3-opus-20240229">>,
        temperature => 0.0
    }).

%%--------------------------------------------------------------------
%% @doc Load few-shot examples
%% @end
%%--------------------------------------------------------------------
-spec load_examples(list()) -> ok | {error, term()}.
load_examples(Examples) ->
    gen_server:call(?SERVER, {load_examples, Examples}).

%%--------------------------------------------------------------------
%% @doc Compile program with BootstrapFewShot
%% @end
%%--------------------------------------------------------------------
-spec bootstrap_compile(map(), list()) -> {ok, binary()} | {error, term()}.
bootstrap_compile(Signature, Trainset) ->
    bootstrap_compile(Signature, Trainset, #{}).

%% @doc Compile with options
-spec bootstrap_compile(map(), list(), map()) -> {ok, binary()} | {error, term()}.
bootstrap_compile(Signature, Trainset, Options) ->
    gen_server:call(?SERVER, {bootstrap_compile, Signature, Trainset, Options}, ?COMPILE_TIMEOUT).

%%--------------------------------------------------------------------
%% @doc Save compiled program to disk
%% @end
%%--------------------------------------------------------------------
-spec save_program(binary(), binary()) -> ok | {error, term()}.
save_program(ProgramId, Path) ->
    gen_server:call(?SERVER, {save_program, ProgramId, Path}).

%%--------------------------------------------------------------------
%% @doc Load compiled program from disk
%% @end
%%--------------------------------------------------------------------
-spec load_program(binary()) -> {ok, binary()} | {error, term()}.
load_program(Path) ->
    gen_server:call(?SERVER, {load_program, Path}).

%%--------------------------------------------------------------------
%% @doc List cached programs
%% @end
%%--------------------------------------------------------------------
-spec list_programs() -> {ok, list()} | {error, term()}.
list_programs() ->
    gen_server:call(?SERVER, list_programs).

%%--------------------------------------------------------------------
%% @doc Delete a cached program
%% @end
%%--------------------------------------------------------------------
-spec delete_program(binary()) -> ok | {error, term()}.
delete_program(ProgramId) ->
    gen_server:call(?SERVER, {delete_program, ProgramId}).

%%--------------------------------------------------------------------
%% @doc Get bridge status
%% @end
%%--------------------------------------------------------------------
-spec status() -> {ok, map()} | {error, term()}.
status() ->
    gen_server:call(?SERVER, status).

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

init([]) ->
    %% Ensure cache directory exists
    ok = filelib:ensure_dir(?CACHE_DIR ++ "/"),
    State = #state{
        config = #{},
        examples = [],
        programs = #{}
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
    Result = do_predict(Signature, Inputs, Examples, predict),
    {reply, Result, State};

handle_call({predict_chain, Signature, Inputs, Examples}, _From, State) ->
    Result = do_predict(Signature, Inputs, Examples, chain_of_thought),
    {reply, Result, State};

handle_call({load_examples, Examples}, _From, State) ->
    ExJson = iolist_to_binary(json:encode(Examples)),
    case yawl_ml_bridge:dspy_load_examples(ExJson) of
        {ok, _Count} ->
            {reply, ok, State#state{examples = Examples}};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({bootstrap_compile, Signature, Trainset, Options}, _From, State) ->
    Result = do_bootstrap_compile(Signature, Trainset, Options),
    case Result of
        {ok, ProgramId, CompiledProgram} ->
            NewPrograms = maps:put(ProgramId, CompiledProgram, State#state.programs),
            {reply, {ok, ProgramId}, State#state{programs = NewPrograms}};
        {error, _} = Error ->
            {reply, Error, State}
    end;

handle_call({save_program, ProgramId, Path}, _From, State) ->
    case maps:find(ProgramId, State#state.programs) of
        {ok, Program} ->
            case file:write_file(Path, Program) of
                ok -> {reply, ok, State};
                {error, Reason} -> {reply, {error, Reason}, State}
            end;
        error ->
            {reply, {error, program_not_found}, State}
    end;

handle_call({load_program, Path}, _From, State) ->
    case file:read_file(Path) of
        {ok, Program} ->
            ProgramId = generate_program_id(),
            NewPrograms = maps:put(ProgramId, Program, State#state.programs),
            {reply, {ok, ProgramId}, State#state{programs = NewPrograms}};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call(list_programs, _From, State) ->
    ProgramList = maps:keys(State#state.programs),
    {reply, {ok, ProgramList}, State};

handle_call({delete_program, ProgramId}, _From, State) ->
    NewPrograms = maps:remove(ProgramId, State#state.programs),
    {reply, ok, State#state{programs = NewPrograms}};

handle_call(status, _From, State) ->
    Status = #{
        config => State#state.config,
        examples_count => length(State#state.examples),
        programs_count => maps:size(State#state.programs),
        bridge => yawl_ml_bridge:status()
    },
    {reply, {ok, Status}, State};

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
%%% Internal functions
%%%===================================================================

do_predict(Signature, Inputs, Examples, Mode) ->
    SigJson = iolist_to_binary(json:encode(Signature)),
    InJson = iolist_to_binary(json:encode(Inputs)),
    ExJson = case Examples of
        [] -> <<"none">>;
        _ -> iolist_to_binary(json:encode(Examples))
    end,
    PredictFun = case Mode of
        predict -> fun yawl_ml_bridge:dspy_predict/3;
        chain_of_thought -> fun yawl_ml_bridge:dspy_predict_chain/3
    end,
    case PredictFun(SigJson, InJson, ExJson) of
        {ok, ResultJson} ->
            Result = json:decode(ResultJson),
            {ok, Result};
        {error, Reason} ->
            {error, Reason}
    end.

do_bootstrap_compile(Signature, Trainset, Options) ->
    SigJson = iolist_to_binary(json:encode(Signature)),
    TrainJson = iolist_to_binary(json:encode(Trainset)),
    OptionsJson = iolist_to_binary(json:encode(Options)),
    case yawl_ml_bridge:dspy_bootstrap_compile(SigJson, TrainJson, OptionsJson) of
        {ok, CompiledJson} ->
            ProgramId = generate_program_id(),
            {ok, ProgramId, CompiledJson};
        {error, Reason} ->
            {error, Reason}
    end.

generate_program_id() ->
    <<Int:64/unsigned>> = crypto:strong_rand_bytes(8),
    iolist_to_binary(io_lib:format("dspy_prog_~16.16.0b", [Int])).
