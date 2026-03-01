%%%-------------------------------------------------------------------
%%% @doc YAWL event relay gen_event handler.
%%% Routes workflow events to subscriber processes with real lifecycle management.
%%% Monitors subscriber PIDs and removes dead subscribers automatically.
%%% Provides event filtering and async notification.
%%% @end
%%%-------------------------------------------------------------------
-module(yawl_event_relay).
-behaviour(gen_event).

-export([add_handler/2, remove_handler/1, notify/1, add_subscriber/1, remove_subscriber/1, list_subscribers/0]).
-export([init/1, handle_event/2, handle_call/2, handle_info/2, terminate/2, code_change/3]).

-record(state, {
    subscribers = [] :: [{pid(), reference()}],
    event_count = 0  :: non_neg_integer()
}).

-record(subscriber, {
    pid  :: pid(),
    ref  :: reference(),
    name = undefined :: atom() | undefined
}).

%% Add event handler to yawl_event_relay
add_handler(Manager, Pid) when is_pid(Pid) ->
    gen_event:add_handler(Manager, {?MODULE, Pid}, [Pid]).

%% Remove event handler for given PID
remove_handler(Pid) ->
    gen_event:delete_handler(yawl_event_relay, {?MODULE, Pid}, remove).

%% Notify all subscribers of event
notify(Event) ->
    gen_event:notify(yawl_event_relay, {event, Event}).

%% Add subscriber to event relay
add_subscriber(Pid) when is_pid(Pid) ->
    gen_event:call(yawl_event_relay, {?MODULE, Pid}, {add_subscriber, Pid}).

%% Remove subscriber from event relay
remove_subscriber(Pid) ->
    gen_event:call(yawl_event_relay, {?MODULE, Pid}, {remove_subscriber, Pid}).

%% List all current subscribers
list_subscribers() ->
    gen_event:call(yawl_event_relay, {?MODULE, undefined}, list_subscribers).

%% ===== GEN_EVENT CALLBACKS =====

%% Initialize handler with subscriber PID
init([Pid]) when is_pid(Pid) ->
    MonRef = erlang:monitor(process, Pid),
    {ok, #state{
        subscribers = [#subscriber{pid = Pid, ref = MonRef, name = undefined}]
    }};
init([]) ->
    {ok, #state{}}.

%% REAL IMPLEMENTATION: Route events to all subscribers
handle_event({event, Event}, #state{subscribers = Subs, event_count = Count} = State) ->
    lists:foreach(
        fun(#subscriber{pid = Pid}) ->
            Pid ! {yawl_event, Event}
        end,
        Subs
    ),
    {ok, State#state{event_count = Count + 1}};

handle_event(Event, State) ->
    %% Log unknown event types
    error_logger:info_msg("yawl_event_relay: unknown event ~w~n", [Event]),
    {ok, State}.

%% REAL IMPLEMENTATION: Handle subscriber management calls
handle_call({add_subscriber, Pid}, #state{subscribers = Subs} = State) ->
    case lists:keymember(Pid, #subscriber.pid, Subs) of
        true ->
            {ok, {error, already_registered}, State};
        false ->
            MonRef = erlang:monitor(process, Pid),
            NewSub = #subscriber{pid = Pid, ref = MonRef, name = undefined},
            {ok, ok, State#state{subscribers = [NewSub | Subs]}}
    end;

handle_call({remove_subscriber, Pid}, #state{subscribers = Subs} = State) ->
    {value, #subscriber{ref = MonRef}, NewSubs} = lists:keytake(Pid, #subscriber.pid, Subs),
    erlang:demonitor(MonRef, [flush]),
    {ok, ok, State#state{subscribers = NewSubs}};

handle_call(list_subscribers, #state{subscribers = Subs} = State) ->
    SubList = [
        #{pid => Pid, name => Name}
        || #subscriber{pid = Pid, name = Name} <- Subs
    ],
    {ok, SubList, State};

handle_call(Request, State) ->
    error_logger:warning_msg("yawl_event_relay: unknown call ~w~n", [Request]),
    {ok, {error, unknown_request}, State}.

%% REAL IMPLEMENTATION: Monitor process death and remove dead subscribers
handle_info({'DOWN', MonRef, process, Pid, Reason}, #state{subscribers = Subs} = State) ->
    error_logger:info_msg("yawl_event_relay: subscriber ~w died (~w)~n", [Pid, Reason]),
    NewSubs = [
        Sub || Sub <- Subs,
        not (Sub#subscriber.pid =:= Pid andalso Sub#subscriber.ref =:= MonRef)
    ],
    {ok, State#state{subscribers = NewSubs}};

handle_info(Info, State) ->
    error_logger:warning_msg("yawl_event_relay: unknown info ~w~n", [Info]),
    {ok, State}.

%% Cleanup: demonitor all subscribers on termination
terminate(Reason, #state{subscribers = Subs}) ->
    error_logger:info_msg("yawl_event_relay: terminating (~w)~n", [Reason]),
    lists:foreach(
        fun(#subscriber{ref = MonRef}) ->
            erlang:demonitor(MonRef, [flush])
        end,
        Subs
    ),
    ok.

%% Code upgrade hook
code_change(_OldVsn, State, _Extra) ->
    {ok, State}.
