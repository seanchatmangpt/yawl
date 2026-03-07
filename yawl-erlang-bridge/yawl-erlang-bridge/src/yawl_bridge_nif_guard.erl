%% @doc NIF Resource Management and Error Handling
%% Implements safe NIF loading, resource tracking, and error taxonomy
%% Following 80/20 OTP best practices
-module(yawl_bridge_nif_guard).
-behaviour(gen_server).

%% API
-export([start_link/0,
         load_nif/2,
         call_nif/3,
         call_nif/4,
         get_nif_status/0,
         get_resource_stats/0,
         cleanup_resources/0]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(RESOURCES_TABLE, yawl_nif_resources).
-define(NIF_TIMEOUT_MS, 1000).  %% Max 1ms for regular NIFs
-define(DIRTY_NIF_TIMEOUT_MS, 5000).  %% 5s for dirty NIFs

%% Error taxonomy for NIF operations
-type nif_error() ::
    {nif_not_loaded, module()} |
    {nif_load_failed, term()} |
    {nif_timeout, integer()} |
    {nif_crash, term()} |
    {nif_resource_error, term()} |
    {nif_invalid_argument, term()}.

-record(state, {
    loaded_nifs :: map(),
    resources :: ets:tid(),
    resource_types :: map(),
    stats :: map()
}).

-record(nif_resource, {
    id :: reference(),
    type :: atom(),
    created :: integer(),
    size :: integer(),
    nif_module :: module()
}).

%%====================================================================
%% API Functions
%%====================================================================

start_link() ->
    gen_server:start_link({local, ?MODULE}, ?MODULE, [], []).

%% @doc Load a NIF with error handling
load_nif(Module, LibPath) ->
    gen_server:call(?MODULE, {load_nif, Module, LibPath}, 10000).

%% @doc Call a NIF function with timeout protection
call_nif(Module, Function, Args) ->
    call_nif(Module, Function, Args, ?NIF_TIMEOUT_MS).

call_nif(Module, Function, Args, Timeout) ->
    case get_nif_status() of
        #{Module := loaded} ->
            try
                %% Use spawn + monitor for timeout protection
                {Pid, MonitorRef} = spawn_monitor(fun() ->
                    Result = apply(Module, Function, Args),
                    exit({ok, Result})
                end),

                receive
                    {'DOWN', MonitorRef, process, Pid, {ok, Result}} ->
                        Result;
                    {'DOWN', MonitorRef, process, Pid, DownReason} ->
                        {error, {nif_crash, DownReason}}
                after Timeout ->
                    demonitor(MonitorRef, [flush]),
                    exit(Pid, kill),
                    {error, {nif_timeout, Timeout}}
                end
            catch
                error:nif_not_loaded ->
                    {error, {nif_not_loaded, Module}};
                error:CaughtReason:CaughtStacktrace ->
                    lager:error("NIF error ~p:~p(~p): ~p~n~p",
                        [Module, Function, Args, CaughtReason, CaughtStacktrace]),
                    {error, {nif_crash, CaughtReason}}
            end;
        _ ->
            {error, {nif_not_loaded, Module}}
    end.

%% @doc Get NIF loading status
get_nif_status() ->
    gen_server:call(?MODULE, get_nif_status).

%% @doc Get resource statistics
get_resource_stats() ->
    gen_server:call(?MODULE, get_resource_stats).

%% @doc Cleanup stale resources
cleanup_resources() ->
    gen_server:call(?MODULE, cleanup_resources).

%%====================================================================
%% gen_server Callbacks
%%====================================================================

init([]) ->
    process_flag(trap_exit, true),

    ResourcesTable = ets:new(?RESOURCES_TABLE, [
        set,
        named_table,
        public,
        {keypos, #nif_resource.id}
    ]),

    State = #state{
        loaded_nifs = #{},
        resources = ResourcesTable,
        resource_types = #{},
        stats = #{
            nifs_loaded => 0,
            nifs_failed => 0,
            resources_created => 0,
            resources_freed => 0,
            nif_calls => 0,
            nif_errors => 0
        }
    },

    %% Schedule periodic resource cleanup
    erlang:send_after(60000, self(), cleanup_check),

    {ok, State}.

handle_call({load_nif, Module, LibPath}, _From, State) ->
    case load_nif_impl(Module, LibPath) of
        ok ->
            NewNifs = maps:put(Module, loaded, State#state.loaded_nifs),
            NewStats = maps:update(nifs_loaded,
                maps:get(nifs_loaded, State#state.stats) + 1,
                State#state.stats),
            lager:info("NIF loaded: ~p from ~p", [Module, LibPath]),
            {reply, ok, State#state{loaded_nifs = NewNifs, stats = NewStats}};

        {error, {reload, _}} ->
            %% Already loaded
            NewNifs = maps:put(Module, loaded, State#state.loaded_nifs),
            {reply, ok, State#state{loaded_nifs = NewNifs}};

        {error, Reason} ->
            NewNifs = maps:put(Module, {failed, Reason}, State#state.loaded_nifs),
            NewStats = maps:update(nifs_failed,
                maps:get(nifs_failed, State#state.stats) + 1,
                State#state.stats),
            lager:error("NIF load failed: ~p from ~p: ~p", [Module, LibPath, Reason]),
            {reply, {error, Reason}, State#state{loaded_nifs = NewNifs, stats = NewStats}}
    end;

handle_call(get_nif_status, _From, State) ->
    {reply, State#state.loaded_nifs, State};

handle_call(get_resource_stats, _From, State) ->
    Resources = ets:tab2list(?RESOURCES_TABLE),
    Stats = #{
        total_resources => length(Resources),
        by_type => count_by_type(Resources),
        memory_estimate => estimate_memory(Resources),
        stats => State#state.stats
    },
    {reply, {ok, Stats}, State};

handle_call(cleanup_resources, _From, State) ->
    Cleaned = cleanup_stale_resources(),
    {reply, {ok, #{cleaned => Cleaned}}, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(cleanup_check, State) ->
    cleanup_stale_resources(),
    erlang:send_after(60000, self(), cleanup_check),
    {noreply, State};

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    %% Cleanup all resources
    cleanup_all_resources(),
    ets:delete(?RESOURCES_TABLE),
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%====================================================================
%% Internal Functions
%%====================================================================

load_nif_impl(Module, LibPath) ->
    try
        %% Check if erlang:load_nif is available
        case erlang:load_nif(LibPath, 0) of
            ok -> ok;
            {error, {reload, _}} -> ok;
            {error, LoadReason} -> {error, LoadReason}
        end
    catch
        error:badarg ->
            %% NIF loading not available (e.g., in test mode)
            lager:warning("NIF loading not available for ~p", [Module]),
            {error, nif_not_available};
        error:CaughtReason ->
            {error, CaughtReason}
    end.

cleanup_stale_resources() ->
    Now = erlang:system_time(millisecond),
    MaxAge = 3600000,  %% 1 hour

    Resources = ets:tab2list(?RESOURCES_TABLE),
    Stale = [R || R <- Resources,
                  Now - R#nif_resource.created > MaxAge],

    lists:foreach(fun(R) ->
        ets:delete(?RESOURCES_TABLE, R#nif_resource.id)
    end, Stale),

    length(Stale).

cleanup_all_resources() ->
    ets:delete_all_objects(?RESOURCES_TABLE).

count_by_type(Resources) ->
    lists:foldl(fun(R, Acc) ->
        Type = R#nif_resource.type,
        maps:update_with(Type, fun(V) -> V + 1 end, 1, Acc)
    end, #{}, Resources).

estimate_memory(Resources) ->
    lists:sum([R#nif_resource.size || R <- Resources]).

%%====================================================================
%% Resource Registration API (for NIFs to use)
%%====================================================================

%% Called by NIF to register a resource
register_resource(Type, Size, NifModule) ->
    Id = make_ref(),
    Resource = #nif_resource{
        id = Id,
        type = Type,
        created = erlang:system_time(millisecond),
        size = Size,
        nif_module = NifModule
    },
    ets:insert(?RESOURCES_TABLE, Resource),
    {ok, Id}.

%% Called by NIF to unregister a resource
unregister_resource(Id) ->
    ets:delete(?RESOURCES_TABLE, Id),
    ok.
