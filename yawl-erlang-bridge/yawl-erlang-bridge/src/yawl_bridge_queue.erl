%% @doc Message Queue Overflow Protection
%% Implements backpressure and drop strategies for gen_servers
%% Following 80/20 OTP best practices
-module(yawl_bridge_queue).
-behaviour(gen_server).

%% API
-export([start_link/2,
         call/2,
         call/3,
         cast/2,
         get_stats/1,
         set_limit/2]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(DEFAULT_LIMIT, 1000).
-define(DEFAULT_STRATEGY, drop_oldest).

-record(state, {
    name :: atom(),
    limit :: integer(),
    strategy :: drop_oldest | drop_newest | backpressure | reject,
    queue :: queue:queue(),
    count :: integer(),
    dropped :: integer(),
    stats :: map()
}).

%%====================================================================
%% API Functions
%%====================================================================

%% @doc Start a protected queue server
start_link(Name, Options) ->
    gen_server:start_link({local, Name}, ?MODULE, [Name, Options], []).

%% @doc Synchronous call with queue protection
call(Name, Request) ->
    call(Name, Request, 5000).

call(Name, Request, Timeout) ->
    case get_queue_length(Name) of
        Len when Len < ?DEFAULT_LIMIT ->
            gen_server:call(Name, Request, Timeout);
        _ ->
            {error, queue_full}
    end.

%% @doc Asynchronous cast with queue protection
cast(Name, Message) ->
    case get_queue_length(Name) of
        Len when Len < ?DEFAULT_LIMIT ->
            gen_server:cast(Name, Message);
        _ ->
            {error, queue_full}
    end.

%% @doc Get queue statistics
get_stats(Name) ->
    gen_server:call(Name, get_stats).

%% @doc Set queue limit dynamically
set_limit(Name, NewLimit) ->
    gen_server:call(Name, {set_limit, NewLimit}).

%%====================================================================
%% gen_server Callbacks
%%====================================================================

init([Name, Options]) ->
    process_flag(trap_exit, true),

    Limit = proplists:get_value(limit, Options, ?DEFAULT_LIMIT),
    Strategy = proplists:get_value(strategy, Options, ?DEFAULT_STRATEGY),

    State = #state{
        name = Name,
        limit = Limit,
        strategy = Strategy,
        queue = queue:new(),
        count = 0,
        dropped = 0,
        stats = #{
            total_enqueued => 0,
            total_dequeued => 0,
            total_dropped => 0,
            high_water_mark => 0
        }
    },

    %% Start queue monitoring
    erlang:send_after(1000, self(), check_queue),

    {ok, State}.

handle_call(get_stats, _From, State) ->
    Stats = #{
        name => State#state.name,
        current_length => State#state.count,
        limit => State#state.limit,
        strategy => State#state.strategy,
        dropped => State#state.dropped,
        stats => State#state.stats
    },
    {reply, {ok, Stats}, State};

handle_call({set_limit, NewLimit}, _From, State) when is_integer(NewLimit), NewLimit > 0 ->
    {reply, ok, State#state{limit = NewLimit}};
handle_call({set_limit, _}, _From, State) ->
    {reply, {error, invalid_limit}, State};

handle_call(Request, From, State) ->
    case enqueue({call, Request, From}, State) of
        {ok, NewState} ->
            {noreply, NewState};
        {drop, NewState} ->
            {reply, {error, dropped}, NewState}
    end;

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast({cast, Message}, State) ->
    case enqueue({cast, Message}, State) of
        {ok, NewState} -> {noreply, NewState};
        {drop, NewState} -> {noreply, NewState}
    end;

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(check_queue, State) ->
    %% Update high water mark
    Stats = State#state.stats,
    NewStats = Stats#{
        high_water_mark => max(maps:get(high_water_mark, Stats), State#state.count)
    },

    %% Log warning if queue is getting full
    if State#state.count > State#state.limit * 0.8 ->
        lager:warning("Queue ~p at ~p% capacity (~p/~p)",
            [State#state.name,
             trunc(State#state.count / State#state.limit * 100),
             State#state.count,
             State#state.limit]);
       true -> ok
    end,

    erlang:send_after(1000, self(), check_queue),
    {noreply, State#state{stats = NewStats}};

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%====================================================================
%% Internal Functions
%%====================================================================

enqueue(Item, #state{count = Count, limit = Limit, stats = Stats} = State) when Count < Limit ->
    NewQueue = queue:in(Item, State#state.queue),
    NewStats = Stats#{
        total_enqueued => maps:get(total_enqueued, Stats) + 1
    },
    {ok, State#state{queue = NewQueue, count = Count + 1, stats = NewStats}};

enqueue(Item, #state{stats = Stats} = State) ->
    %% Queue is full, apply strategy
    case State#state.strategy of
        drop_oldest ->
            {{value, _Old}, Queue1} = queue:out(State#state.queue),
            NewQueue = queue:in(Item, Queue1),
            NewStats = Stats#{
                total_dropped => maps:get(total_dropped, Stats) + 1
            },
            {drop, State#state{queue = NewQueue, dropped = State#state.dropped + 1, stats = NewStats}};

        drop_newest ->
            %% Drop the new item
            NewStats = Stats#{
                total_dropped => maps:get(total_dropped, Stats) + 1
            },
            {drop, State#state{dropped = State#state.dropped + 1, stats = NewStats}};

        backpressure ->
            %% Block until queue has room
            timer:sleep(10),
            enqueue(Item, State);

        reject ->
            {drop, State}
    end.

get_queue_length(Name) ->
    case erlang:process_info(whereis(Name), message_queue_len) of
        {message_queue_len, Len} -> Len;
        _ -> 0
    end.
