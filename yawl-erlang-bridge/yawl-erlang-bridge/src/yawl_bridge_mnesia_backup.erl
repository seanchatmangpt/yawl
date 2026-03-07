%% @doc Mnesia Backup and Recovery Module
%% Implements automatic backup scheduling, verification, and recovery
%% Following 80/20 OTP best practices
-module(yawl_bridge_mnesia_backup).
-behaviour(gen_server).

%% API
-export([start_link/1,
         create_backup/0,
         create_backup/1,
         list_backups/0,
         verify_backup/1,
         restore_backup/1,
         restore_latest/0,
         get_backup_stats/0,
         set_backup_interval/1]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(DEFAULT_BACKUP_INTERVAL, 300000).  %% 5 minutes
-define(MAX_BACKUPS, 10).
-define(BACKUP_PREFIX, "yawl_mnesia_backup_").

-record(state, {
    backup_dir :: string(),
    interval :: integer(),
    last_backup :: integer() | undefined,
    backup_count :: integer(),
    stats :: map()
}).

-record(backup_info, {
    timestamp :: integer(),
    path :: string(),
    size :: integer(),
    tables :: [atom()],
    verified :: boolean()
}).

%%====================================================================
%% API Functions
%%====================================================================

start_link(Options) ->
    gen_server:start_link({local, ?MODULE}, ?MODULE, [Options], []).

%% @doc Create backup with default path
create_backup() ->
    create_backup(default_path()).

%% @doc Create backup with specific path
create_backup(Path) ->
    gen_server:call(?MODULE, {create_backup, Path}, 30000).

%% @doc List all available backups
list_backups() ->
    gen_server:call(?MODULE, list_backups).

%% @doc Verify a backup is valid
verify_backup(Path) ->
    gen_server:call(?MODULE, {verify_backup, Path}, 60000).

%% @doc Restore from specific backup
restore_backup(Path) ->
    gen_server:call(?MODULE, {restore_backup, Path}, 120000).

%% @doc Restore from latest backup
restore_latest() ->
    gen_server:call(?MODULE, restore_latest, 120000).

%% @doc Get backup statistics
get_backup_stats() ->
    gen_server:call(?MODULE, get_backup_stats).

%% @doc Set backup interval (milliseconds)
set_backup_interval(IntervalMs) when is_integer(IntervalMs), IntervalMs > 0 ->
    gen_server:call(?MODULE, {set_interval, IntervalMs}).

%%====================================================================
%% gen_server Callbacks
%%====================================================================

init([Options]) ->
    process_flag(trap_exit, true),

    BackupDir = proplists:get_value(backup_dir, Options, default_backup_dir()),
    Interval = proplists:get_value(interval, Options, ?DEFAULT_BACKUP_INTERVAL),

    %% Ensure backup directory exists
    filelib:ensure_dir(BackupDir ++ "/"),

    State = #state{
        backup_dir = BackupDir,
        interval = Interval,
        last_backup = undefined,
        backup_count = 0,
        stats = #{
            backups_created => 0,
            backups_failed => 0,
            restores_completed => 0,
            restores_failed => 0,
            bytes_backed_up => 0
        }
    },

    %% Schedule first backup
    erlang:send_after(Interval, self(), perform_backup),

    %% Cleanup old backups on startup
    self() ! cleanup_old_backups,

    lager:info("Mnesia backup module started: dir=~s interval=~pms", [BackupDir, Interval]),

    {ok, State}.

handle_call({create_backup, Path}, _From, State) ->
    case perform_backup_impl(Path, State) of
        {ok, BackupInfo} ->
            NewState = State#state{
                last_backup = BackupInfo#backup_info.timestamp,
                backup_count = State#state.backup_count + 1,
                stats = maps:update(backups_created,
                    maps:get(backups_created, State#state.stats) + 1,
                    State#state.stats)
            },
            {reply, {ok, BackupInfo}, NewState};
        {error, Reason} ->
            NewStats = maps:update(backups_failed,
                maps:get(backups_failed, State#state.stats) + 1,
                State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call(list_backups, _From, State) ->
    Backups = list_backups_impl(State#state.backup_dir),
    {reply, {ok, Backups}, State};

handle_call({verify_backup, Path}, _From, State) ->
    Result = verify_backup_impl(Path),
    {reply, Result, State};

handle_call({restore_backup, Path}, _From, State) ->
    case restore_backup_impl(Path) of
        ok ->
            NewStats = maps:update(restores_completed,
                maps:get(restores_completed, State#state.stats) + 1,
                State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(restores_failed,
                maps:get(restores_failed, State#state.stats) + 1,
                State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call(restore_latest, _From, State) ->
    case find_latest_backup(State#state.backup_dir) of
        {ok, LatestPath} ->
            case restore_backup_impl(LatestPath) of
                ok ->
                    NewStats = maps:update(restores_completed,
                        maps:get(restores_completed, State#state.stats) + 1,
                        State#state.stats),
                    {reply, {ok, LatestPath}, State#state{stats = NewStats}};
                {error, Reason} ->
                    {reply, {error, Reason}, State}
            end;
        {error, no_backups} ->
            {reply, {error, no_backups_found}, State}
    end;

handle_call(get_backup_stats, _From, State) ->
    Stats = #{
        backup_dir => State#state.backup_dir,
        interval_ms => State#state.interval,
        last_backup => State#state.last_backup,
        backup_count => State#state.backup_count,
        stats => State#state.stats
    },
    {reply, {ok, Stats}, State};

handle_call({set_interval, IntervalMs}, _From, State) ->
    {reply, ok, State#state{interval = IntervalMs}};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(perform_backup, State) ->
    Path = generate_backup_path(State#state.backup_dir),
    case perform_backup_impl(Path, State) of
        {ok, _BackupInfo} ->
            NewState = State#state{
                last_backup = erlang:system_time(millisecond),
                backup_count = State#state.backup_count + 1
            },
            erlang:send_after(State#state.interval, self(), perform_backup),
            {noreply, NewState};
        {error, Reason} ->
            lager:error("Scheduled backup failed: ~p", [Reason]),
            %% Retry sooner on failure
            erlang:send_after(60000, self(), perform_backup),
            {noreply, State}
    end;

handle_info(cleanup_old_backups, State) ->
    cleanup_old_backups_impl(State#state.backup_dir),
    %% Schedule periodic cleanup
    erlang:send_after(3600000, self(), cleanup_old_backups),
    {noreply, State};

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    %% Perform final backup
    Path = generate_backup_path(default_backup_dir()) ++ "_shutdown",
    case mnesia:backup(Path) of
        {ok, _} -> lager:info("Final shutdown backup created: ~s", [Path]);
        {error, Reason} -> lager:error("Shutdown backup failed: ~p", [Reason])
    end,
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%====================================================================
%% Internal Functions
%%====================================================================

perform_backup_impl(Path, _State) ->
    try
        %% Ensure Mnesia is running
        case mnesia:system_info(is_running) of
            yes ->
                %% Create backup
                case mnesia:backup(Path) of
                    {ok, _} ->
                        %% Verify backup was created
                        case filelib:is_file(Path) of
                            true ->
                                Size = filelib:file_size(Path),
                                Tables = mnesia:system_info(tables),
                                BackupInfo = #backup_info{
                                    timestamp = erlang:system_time(millisecond),
                                    path = Path,
                                    size = Size,
                                    tables = Tables -- [schema],
                                    verified = false
                                },
                                lager:info("Backup created: ~s (~p bytes)", [Path, Size]),
                                {ok, BackupInfo};
                            false ->
                                {error, backup_file_not_created}
                        end;
                    {error, MnesiaReason} ->
                        {error, {mnesia_backup_failed, MnesiaReason}}
                end;
            no ->
                {error, mnesia_not_running}
        end
    catch
        _:CaughtReason:Stacktrace ->
            lager:error("Backup exception: ~p~nStacktrace: ~p", [CaughtReason, Stacktrace]),
            {error, {backup_exception, CaughtReason}}
    end.

verify_backup_impl(Path) ->
    try
        case filelib:is_file(Path) of
            true ->
                %% Try to read backup metadata
                case mnesia:backup(Path) of
                    {ok, _} ->
                        {ok, #{path => Path, verified => true}};
                    {error, BackupReason} ->
                        {error, {backup_corrupt, BackupReason}}
                end;
            false ->
                {error, backup_not_found}
        end
    catch
        _:CaughtReason:Stacktrace ->
            lager:error("Verification exception: ~p~nStacktrace: ~p", [CaughtReason, Stacktrace]),
            {error, {verification_failed, CaughtReason}}
    end.

restore_backup_impl(Path) ->
    try
        %% Stop Mnesia
        mnesia:stop(),
        timer:sleep(1000),

        %% Restore from backup
        case mnesia:install_fallback(Path) of
            ok ->
                %% Start Mnesia with fallback
                mnesia:start(),
                timer:sleep(2000),

                %% Verify restoration
                case mnesia:system_info(is_running) of
                    yes ->
                        lager:info("Backup restored successfully: ~s", [Path]),
                        ok;
                    no ->
                        {error, mnesia_failed_to_start}
                end;
            {error, FallbackReason} ->
                {error, {fallback_install_failed, FallbackReason}}
        end
    catch
        _:CaughtReason:Stacktrace ->
            lager:error("Restore exception: ~p~nStacktrace: ~p", [CaughtReason, Stacktrace]),
            {error, {restore_exception, CaughtReason}}
    end.

list_backups_impl(BackupDir) ->
    case file:list_dir(BackupDir) of
        {ok, Files} ->
            BackupFiles = [F || F <- Files, string:prefix(F, ?BACKUP_PREFIX) =/= nomatch],
            lists:map(fun(F) ->
                Path = BackupDir ++ "/" ++ F,
                Size = filelib:file_size(Path),
                #{
                    filename => F,
                    path => Path,
                    size => Size,
                    created => filelib:last_modified(Path)
                }
            end, lists:sort(BackupFiles));
        {error, _} ->
            []
    end.

find_latest_backup(BackupDir) ->
    case list_backups_impl(BackupDir) of
        [] ->
            {error, no_backups};
        Backups ->
            %% Sort by creation time, newest first
            Sorted = lists:sort(fun(A, B) ->
                maps:get(created, A) >= maps:get(created, B)
            end, Backups),
            {ok, maps:get(path, hd(Sorted))}
    end.

cleanup_old_backups_impl(BackupDir) ->
    case list_backups_impl(BackupDir) of
        Backups when length(Backups) > ?MAX_BACKUPS ->
            %% Sort oldest first
            Sorted = lists:sort(fun(A, B) ->
                maps:get(created, A) < maps:get(created, B)
            end, Backups),

            %% Remove oldest
            ToRemove = lists:sublist(Sorted, length(Backups) - ?MAX_BACKUPS),
            lists:foreach(fun(B) ->
                file:delete(maps:get(path, B)),
                lager:info("Removed old backup: ~s", [maps:get(path, B)])
            end, ToRemove);
        _ ->
            ok
    end.

default_backup_dir() ->
    application:get_env(process_mining_bridge, backup_dir, "/tmp/yawl_mnesia_backups").

default_path() ->
    default_backup_dir() ++ "/" ++ ?BACKUP_PREFIX ++
        integer_to_list(erlang:system_time(second)).

generate_backup_path(BackupDir) ->
    BackupDir ++ "/" ++ ?BACKUP_PREFIX ++
        integer_to_list(erlang:system_time(second)).
