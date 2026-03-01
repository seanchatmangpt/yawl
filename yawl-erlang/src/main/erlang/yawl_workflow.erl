%%%-------------------------------------------------------------------
%%% @doc YAWL workflow engine gen_server.
%%% Manages case lifecycle with state mutations for case management.
%%% Implements case launch, workitem tracking, and case completion.
%%% Operates with immediate consistency semantics.
%%% @end
%%%-------------------------------------------------------------------
-module(yawl_workflow).
-behaviour(gen_server).

-export([start_link/0, launch_case/1, get_status/1, complete_workitem/2]).
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-record(state, {
    cases     = #{} :: map(),
    workitems = #{} :: map(),
    seq       = 0   :: non_neg_integer()
}).

-record(case_data, {
    id          :: binary(),
    spec        :: any(),
    status      :: atom(),
    started_at  :: integer(),
    completed_at = undefined :: integer() | undefined,
    workitems   = [] :: [binary()]
}).

-record(workitem_data, {
    id          :: binary(),
    case_id     :: binary(),
    name        :: binary(),
    status      :: atom(),
    started_at  :: integer(),
    completed_at = undefined :: integer() | undefined
}).

start_link() ->
    gen_server:start_link({local, ?MODULE}, ?MODULE, [], []).

%% Launch a new case from specification
launch_case(Spec) ->
    gen_server:call(?MODULE, {launch_case, Spec}).

%% Get status of a case
get_status(CaseId) ->
    gen_server:call(?MODULE, {get_status, CaseId}).

%% Complete workitem, optionally marking case complete
complete_workitem(ItemId, CaseId) ->
    gen_server:call(?MODULE, {complete_workitem, ItemId, CaseId}).

init([]) ->
    {ok, #state{}}.

%% REAL IMPLEMENTATION: Create case, generate workitems from spec
handle_call({launch_case, Spec}, _From, #state{cases = Cases, workitems = Items, seq = Seq} = State) ->
    NewSeq = Seq + 1,
    CaseId = <<"case_", (integer_to_binary(NewSeq))/binary>>,
    Now = erlang:system_time(millisecond),

    %% Parse spec and generate workitems
    WorkitemSpec = maps:get(workitems, Spec, []),
    {WorkitemIds, NewItems} = lists:foldl(
        fun(WiName, {Acc, ItemsAcc}) ->
            WiSeq = NewSeq * 10000 + length(Acc),
            WiId = <<"wi_", (integer_to_binary(WiSeq))/binary>>,
            WiData = #workitem_data{
                id = WiId,
                case_id = CaseId,
                name = WiName,
                status = enabled,
                started_at = Now
            },
            {[WiId | Acc], maps:put(WiId, WiData, ItemsAcc)}
        end,
        {[], Items},
        WorkitemSpec
    ),

    CaseRecord = #case_data{
        id = CaseId,
        spec = Spec,
        status = running,
        started_at = Now,
        workitems = lists:reverse(WorkitemIds)
    },

    {reply, {ok, CaseId}, State#state{
        cases = maps:put(CaseId, CaseRecord, Cases),
        workitems = NewItems,
        seq = NewSeq
    }};

%% REAL IMPLEMENTATION: Return case status from state
handle_call({get_status, CaseId}, _From, #state{cases = Cases, workitems = Items} = State) ->
    case maps:find(CaseId, Cases) of
        {ok, #case_data{status = Status, workitems = WiIds}} ->
            WiStates = lists:map(
                fun(WiId) ->
                    case maps:find(WiId, Items) of
                        {ok, #workitem_data{status = WiStatus}} -> WiStatus;
                        error -> unknown
                    end
                end,
                WiIds
            ),
            {reply, {ok, #{
                case_id => CaseId,
                case_status => Status,
                workitem_count => length(WiIds),
                workitem_states => WiStates
            }}, State};
        error ->
            {reply, {error, {case_not_found, CaseId}}, State}
    end;

%% REAL IMPLEMENTATION: Mark workitem complete and check for case completion
handle_call({complete_workitem, ItemId, CaseId}, _From,
            #state{workitems = Items, cases = Cases} = State) ->
    case maps:find(ItemId, Items) of
        {ok, #workitem_data{case_id = StoredCaseId} = WiData} when StoredCaseId =:= CaseId ->
            Now = erlang:system_time(millisecond),
            UpdatedWi = WiData#workitem_data{status = completed, completed_at = Now},
            NewItems = maps:put(ItemId, UpdatedWi, Items),

            %% Check if all workitems in case are complete
            case maps:find(CaseId, Cases) of
                {ok, #case_data{workitems = WiIds} = CaseData} ->
                    AllCompleted = lists:all(
                        fun(WiId) ->
                            case maps:find(WiId, NewItems) of
                                {ok, #workitem_data{status = S}} -> S =:= completed;
                                error -> false
                            end
                        end,
                        WiIds
                    ),

                    NewCaseStatus = case AllCompleted of
                        true  -> completed;
                        false -> running
                    end,

                    CompletedAt = case AllCompleted of
                        true  -> Now;
                        false -> undefined
                    end,

                    UpdatedCase = CaseData#case_data{
                        status = NewCaseStatus,
                        completed_at = CompletedAt
                    },

                    NewCases = maps:put(CaseId, UpdatedCase, Cases),
                    {reply, {ok, NewCaseStatus}, State#state{
                        workitems = NewItems,
                        cases = NewCases
                    }};
                error ->
                    {reply, {error, {case_not_found, CaseId}}, State#state{
                        workitems = NewItems
                    }}
            end;
        {ok, #workitem_data{case_id = StoredCaseId}} ->
            {reply, {error, {case_mismatch, {ItemId, CaseId, StoredCaseId}}}, State};
        error ->
            {reply, {error, {workitem_not_found, ItemId}}, State}
    end.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.
