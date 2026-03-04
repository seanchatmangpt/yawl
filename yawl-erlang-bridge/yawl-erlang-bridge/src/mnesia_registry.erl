-module(mnesia_registry).
-behaviour(gen_server).
-export([
    start_link/0,
    %% OCEL API
    register_ocel/2, lookup_ocel/1, unregister_ocel/1,
    %% Slim OCEL API
    register_slim_ocel/2, lookup_slim_ocel/1, unregister_slim_ocel/1,
    %% Petri Net API
    register_petri_net/2, lookup_petri_net/1, unregister_petri_net/1,
    %% Conformance API
    register_conformance/2, lookup_conformance/1, unregister_conformance/1,
    %% Utility functions
    clear_stale_entries/0, get_registry_stats/0
]).
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(TABLES, [
    ocel_registry,
    slim_ocel_registry,
    petri_net_registry,
    conformance_registry
]).

%% Record definitions
-record(ocel_registry, {
    ocel_id :: term(),
    uuid :: binary(),
    rust_pointer :: binary(),
    timestamp :: integer()
}).

-record(slim_ocel_registry, {
    slim_ocel_id :: term(),
    uuid :: binary(),
    rust_pointer :: binary(),
    parent_ocel_id :: term(),
    timestamp :: integer()
}).

-record(petri_net_registry, {
    petri_net_id :: term(),
    uuid :: binary(),
    rust_pointer :: binary(),
    timestamp :: integer()
}).

-record(conformance_registry, {
    conformance_id :: term(),
    uuid :: binary(),
    rust_pointer :: binary(),
    timestamp :: integer()
}).

-record(state, {
    tables :: list(),
    backup_path :: string(),
    stats :: map(),
    initialized :: boolean()
}).

start_link() ->
    gen_server:start_link({local, mnesia_registry}, ?MODULE, [], []).

%%====================================================================
%% Public API Functions
%%====================================================================

%% OCEL registration functions
register_ocel(OcelId, RustPointer) when is_binary(RustPointer) ->
    gen_server:call(mnesia_registry, {register_ocel, OcelId, RustPointer}).

lookup_ocel(OcelId) ->
    gen_server:call(mnesia_registry, {lookup_ocel, OcelId}).

unregister_ocel(OcelId) ->
    gen_server:call(mnesia_registry, {unregister_ocel, OcelId}).

%% Slim OCEL registration functions
register_slim_ocel(SlimOcelId, RustPointer) when is_binary(RustPointer) ->
    gen_server:call(mnesia_registry, {register_slim_ocel, SlimOcelId, RustPointer}).

lookup_slim_ocel(SlimOcelId) ->
    gen_server:call(mnesia_registry, {lookup_slim_ocel, SlimOcelId}).

unregister_slim_ocel(SlimOcelId) ->
    gen_server:call(mnesia_registry, {unregister_slim_ocel, SlimOcelId}).

%% Petri Net registration functions
register_petri_net(PetriNetId, RustPointer) when is_binary(RustPointer) ->
    gen_server:call(mnesia_registry, {register_petri_net, PetriNetId, RustPointer}).

lookup_petri_net(PetriNetId) ->
    gen_server:call(mnesia_registry, {lookup_petri_net, PetriNetId}).

unregister_petri_net(PetriNetId) ->
    gen_server:call(mnesia_registry, {unregister_petri_net, PetriNetId}).

%% Conformance registration functions
register_conformance(ConformanceId, RustPointer) when is_binary(RustPointer) ->
    gen_server:call(mnesia_registry, {register_conformance, ConformanceId, RustPointer}).

lookup_conformance(ConformanceId) ->
    gen_server:call(mnesia_registry, {lookup_conformance, ConformanceId}).

unregister_conformance(ConformanceId) ->
    gen_server:call(mnesia_registry, {unregister_conformance, ConformanceId}).

%% Utility functions
clear_stale_entries() ->
    gen_server:call(mnesia_registry, clear_stale_entries).

get_registry_stats() ->
    gen_server:call(mnesia_registry, get_registry_stats).

%%====================================================================
%% gen_server Callbacks
%%====================================================================

init([]) ->
    process_flag(trap_exit, true),

    %% Initialize state
    State = #state{
        tables = ?TABLES,
        backup_path = "/tmp/yawl_bridge_mnesia_backup",
        stats = #{
            operations => 0,
            backup_size => 0,
            errors => 0,
            ocel_count => 0,
            slim_ocel_count => 0,
            petri_net_count => 0,
            conformance_count => 0
        },
        initialized = false
    },

    %% Ensure backup directory exists
    filelib:ensure_dir(State#state.backup_path ++ "/"),

    %% Initialize Mnesia tables
    case initialize_mnesia_tables(State) of
        {ok, NewState} ->
            %% Start backup timer
            erlang:send_after(300000, self(), backup_tables),
            %% Start health check timer
            erlang:send_after(60000, self(), check_health),
            lager:info("Mnesia registry initialized successfully"),
            {ok, NewState#state{initialized = true}};
        {error, Reason} ->
            lager:error("Failed to initialize Mnesia tables: ~p", [Reason]),
            {stop, Reason}
    end.

handle_call(get_table_info, _From, State) ->
    TableInfo = lists:map(fun(Table) ->
        #{
            name => Table,
            size => mnesia:table_info(Table, size),
            memory => mnesia:table_info(Table, memory),
            type => mnesia:table_info(Table, type)
        }
    end, State#state.tables),

    {reply, {ok, TableInfo}, State};

%% OCEL registration calls
handle_call({register_ocel, OcelId, RustPointer}, _From, State) ->
    case register_ocel_impl(OcelId, RustPointer) of
        {ok, _} ->
            NewStats = maps:update(ocel_count, maps:get(ocel_count, State#state.stats) + 1, State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call({lookup_ocel, OcelId}, _From, State) ->
    case lookup_ocel_impl(OcelId) of
        {ok, RustPointer} ->
            {reply, {ok, RustPointer}, State};
        {error, not_found} ->
            {reply, {error, not_found}, State};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call({unregister_ocel, OcelId}, _From, State) ->
    case unregister_ocel_impl(OcelId) of
        ok ->
            NewStats = maps:update(ocel_count, maps:get(ocel_count, State#state.stats) - 1, State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

%% Slim OCEL registration calls
handle_call({register_slim_ocel, SlimOcelId, RustPointer}, _From, State) ->
    case register_slim_ocel_impl(SlimOcelId, RustPointer) of
        {ok, _} ->
            NewStats = maps:update(slim_ocel_count, maps:get(slim_ocel_count, State#state.stats) + 1, State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call({lookup_slim_ocel, SlimOcelId}, _From, State) ->
    case lookup_slim_ocel_impl(SlimOcelId) of
        {ok, RustPointer} ->
            {reply, {ok, RustPointer}, State};
        {error, not_found} ->
            {reply, {error, not_found}, State};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call({unregister_slim_ocel, SlimOcelId}, _From, State) ->
    case unregister_slim_ocel_impl(SlimOcelId) of
        ok ->
            NewStats = maps:update(slim_ocel_count, maps:get(slim_ocel_count, State#state.stats) - 1, State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

%% Petri Net registration calls
handle_call({register_petri_net, PetriNetId, RustPointer}, _From, State) ->
    case register_petri_net_impl(PetriNetId, RustPointer) of
        {ok, _} ->
            NewStats = maps:update(petri_net_count, maps:get(petri_net_count, State#state.stats) + 1, State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call({lookup_petri_net, PetriNetId}, _From, State) ->
    case lookup_petri_net_impl(PetriNetId) of
        {ok, RustPointer} ->
            {reply, {ok, RustPointer}, State};
        {error, not_found} ->
            {reply, {error, not_found}, State};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call({unregister_petri_net, PetriNetId}, _From, State) ->
    case unregister_petri_net_impl(PetriNetId) of
        ok ->
            NewStats = maps:update(petri_net_count, maps:get(petri_net_count, State#state.stats) - 1, State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

%% Conformance registration calls
handle_call({register_conformance, ConformanceId, RustPointer}, _From, State) ->
    case register_conformance_impl(ConformanceId, RustPointer) of
        {ok, _} ->
            NewStats = maps:update(conformance_count, maps:get(conformance_count, State#state.stats) + 1, State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call({lookup_conformance, ConformanceId}, _From, State) ->
    case lookup_conformance_impl(ConformanceId) of
        {ok, RustPointer} ->
            {reply, {ok, RustPointer}, State};
        {error, not_found} ->
            {reply, {error, not_found}, State};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call({unregister_conformance, ConformanceId}, _From, State) ->
    case unregister_conformance_impl(ConformanceId) of
        ok ->
            NewStats = maps:update(conformance_count, maps:get(conformance_count, State#state.stats) - 1, State#state.stats),
            {reply, ok, State#state{stats = NewStats}};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call(clear_stale_entries, _From, State) ->
    case clear_stale_entries_impl() of
        ok ->
            lager:info("Cleared stale registry entries"),
            {reply, ok, State};
        {error, Reason} ->
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {reply, {error, Reason}, State#state{stats = NewStats}}
    end;

handle_call(get_registry_stats, _From, State) ->
    {reply, {ok, State#state.stats}, State};

handle_call(create_backup, _From, State) ->
    case backup_tables_manual(State) of
        {ok, BackupInfo} ->
            {reply, {ok, BackupInfo}, State};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call(restore_backup, _From, State) ->
    case restore_tables_manual(State) of
        {ok, RestoreInfo} ->
            {reply, {ok, RestoreInfo}, State};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call(get_stats, _From, State) ->
    {reply, {ok, State#state.stats}, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast({create_table, TableName, Attributes, Options}, State) ->
    case mnesia:create_table(TableName, Attributes ++ Options) of
        {atomic, ok} ->
            lager:info("Created table: ~p", [TableName]),
            NewTables = [TableName | State#state.tables],
            {noreply, State#state{tables = NewTables}};
        {aborted, Reason} ->
            lager:error("Failed to create table ~p: ~p", [TableName, Reason]),
            NewStats = maps:update(errors, maps:get(errors, State#state.stats) + 1, State#state.stats),
            {noreply, State#state{stats = NewStats}}
    end;

handle_cast({write, TableName, Record}, State) ->
    mnesia:transaction(fun() ->
        mnesia:write(Record)
    end),
    NewStats = maps:update(operations, maps:get(operations, State#state.stats) + 1, State#state.stats),
    {noreply, State#state{stats = NewStats}};

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(backup_tables, State) ->
    case backup_tables_manual(State) of
        {ok, _} ->
            %% Schedule next backup
            erlang:send_after(300000, self(), backup_tables);
        {error, Reason} ->
            lager:warning("Backup failed: ~p, retrying in 60s", [Reason]),
            erlang:send_after(60000, self(), backup_tables)
    end,
    {noreply, State};

handle_info(check_health, State) ->
    case check_mnesia_health() of
        healthy ->
            erlang:send_after(60000, self(), check_health);
        unhealthy ->
            lager:error("Mnesia health check failed"),
            %% Attempt recovery
            attempt_recovery()
    end,
    {noreply, State};

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    %% Perform final backup
    case backup_tables_manual(_State) of
        {ok, _} ->
            lager:info("Final backup completed");
        {error, Reason} ->
            lager:error("Final backup failed: ~p", [Reason])
    end,
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

backup_tables_manual(State) ->
    try
        BackupName = "backup_" ++ integer_to_list(erlang:system_time(second)),
        BackupPath = State#state.backup_path ++ "/" ++ BackupName,

        %% Create backup directory
        filelib:ensure_dir(BackupPath ++ "/"),

        %% Perform backup
        case mnesia:backup(BackupPath) of
            {ok, _} ->
                %% Backup info
                BackupSize = filelib:file_size(BackupPath ++ "/mnesia.backup"),
                Info = #{
                    path => BackupPath,
                    size => BackupSize,
                    timestamp => erlang:system_time(millisecond)
                },
                {ok, Info};
            {error, Reason} ->
                {error, Reason}
        end
    catch
        _:_ ->
            {error, unknown}
    end.

restore_tables_manual(State) ->
    try
        %% Find latest backup
        BackupDir = State#state.backup_path,
        case file:list_dir(BackupDir) of
            {ok, Files} ->
                BackupFiles = lists:filter(fun(F) ->
                    string:prefix(F, "backup_") =/= nomatch
                end, Files),
                case lists:sort(BackupFiles) of
                    [] ->
                        {error, no_backups_found};
                    [Latest | _] ->
                        BackupPath = BackupDir ++ "/" ++ Latest ++ "/mnesia.backup",
                        case mnesia:backup(BackupPath) of
                            {ok, _} ->
                                Info = #{
                                    path => BackupPath,
                                    restored => true
                                },
                                {ok, Info};
                            {error, Reason} ->
                                {error, Reason}
                        end
                end;
            {error, Reason} ->
                {error, Reason}
        end
    catch
        _:_ ->
            {error, unknown}
    end.

check_mnesia_health() ->
    try
        %% Check if Mnesia is running
        case mnesia:system_info(is_running) of
            yes ->
                %% Check tables
                lists:foreach(fun(Table) ->
                    case mnesia:table_info(Table, status) of
                        {error, Reason} ->
                            throw({table_error, Table, Reason});
                        _ ->
                            ok
                    end
                end, ?TABLES),
                healthy;
            no ->
                unhealthy
        end
    catch
        _:Reason ->
            unhealthy
    end.

attempt_recovery() ->
    try
        %% Wait a bit for recovery
        timer:sleep(5000),

        %% Check if recovered
        case check_mnesia_health() of
            healthy ->
                lager:info("Mnesia recovered successfully");
            unhealthy ->
                lager:error("Mnesia recovery failed"),
                %% Try manual repair
                case mnesia:force_recovery() of
                    {atomic, ok} ->
                        lager:info("Manual repair completed");
                    {aborted, Reason} ->
                        lager:error("Manual repair failed: ~p", [Reason])
                end
        end
    catch
        _:_ ->
            lager:error("Recovery attempt failed")
    end.

%%====================================================================
%% Internal Implementation Functions
%%====================================================================

initialize_mnesia_tables(State) ->
    try
        %% Ensure Mnesia is running
        case mnesia:system_info(is_running) of
            yes ->
                ok;
            no ->
                mnesia:start(),
                timer:sleep(1000) %% Give Mnesia time to start
        end,

        %% Create tables if they don't exist
        lists:foreach(fun(Table) ->
            case mnesia:table_info(Table, exists) of
                true ->
                    lager:debug("Table ~p already exists", [Table]);
                false ->
                    create_table(Table)
            end
        end, ?TABLES),

        {ok, State}
    catch
        _:Reason ->
            {error, Reason}
    end.

create_table(ocel_registry) ->
    mnesia:create_table(ocel_registry, [
        {attributes, record_info(fields, ocel_registry)},
        {type, set},
        {disc_copies, [node()]},
        {index, [#ocel_registry.timestamp]}
    ]);

create_table(slim_ocel_registry) ->
    mnesia:create_table(slim_ocel_registry, [
        {attributes, record_info(fields, slim_ocel_registry)},
        {type, set},
        {disc_copies, [node()]},
        {index, [#slim_ocel_registry.timestamp]}
    ]);

create_table(petri_net_registry) ->
    mnesia:create_table(petri_net_registry, [
        {attributes, record_info(fields, petri_net_registry)},
        {type, set},
        {disc_copies, [node()]},
        {index, [#petri_net_registry.timestamp]}
    ]);

create_table(conformance_registry) ->
    mnesia:create_table(conformance_registry, [
        {attributes, record_info(fields, conformance_registry)},
        {type, set},
        {disc_copies, [node()]},
        {index, [#conformance_registry.timestamp]}
    ]).

%% OCEL implementation functions
register_ocel_impl(OcelId, RustPointer) ->
    UUID = uuid:uuid_to_binary(uuid:uuid4()),
    Timestamp = erlang:system_time(millisecond),
    Record = #ocel_registry{
        ocel_id = OcelId,
        uuid = UUID,
        rust_pointer = RustPointer,
        timestamp = Timestamp
    },
    mnesia:dirty_write(Record).

lookup_ocel_impl(OcelId) ->
    case mnesia:dirty_read(ocel_registry, OcelId) of
        [#ocel_registry{rust_pointer = RustPointer}] ->
            {ok, RustPointer};
        [] ->
            {error, not_found};
        Other ->
            {error, {unexpected_result, Other}}
    end.

unregister_ocel_impl(OcelId) ->
    case mnesia:dirty_delete(ocel_registry, OcelId) of
        ok ->
            ok;
        {aborted, no_exists} ->
            ok;
        {aborted, Reason} ->
            {error, Reason}
    end.

%% Slim OCEL implementation functions
register_slim_ocel_impl(SlimOcelId, RustPointer) ->
    UUID = uuid:uuid_to_binary(uuid:uuid4()),
    Timestamp = erlang:system_time(millisecond),
    Record = #slim_ocel_registry{
        slim_ocel_id = SlimOcelId,
        uuid = UUID,
        rust_pointer = RustPointer,
        timestamp = Timestamp
    },
    mnesia:dirty_write(Record).

lookup_slim_ocel_impl(SlimOcelId) ->
    case mnesia:dirty_read(slim_ocel_registry, SlimOcelId) of
        [#slim_ocel_registry{rust_pointer = RustPointer}] ->
            {ok, RustPointer};
        [] ->
            {error, not_found};
        Other ->
            {error, {unexpected_result, Other}}
    end.

unregister_slim_ocel_impl(SlimOcelId) ->
    case mnesia:dirty_delete(slim_ocel_registry, SlimOcelId) of
        ok ->
            ok;
        {aborted, no_exists} ->
            ok;
        {aborted, Reason} ->
            {error, Reason}
    end.

%% Petri Net implementation functions
register_petri_net_impl(PetriNetId, RustPointer) ->
    UUID = uuid:uuid_to_binary(uuid:uuid4()),
    Timestamp = erlang:system_time(millisecond),
    Record = #petri_net_registry{
        petri_net_id = PetriNetId,
        uuid = UUID,
        rust_pointer = RustPointer,
        timestamp = Timestamp
    },
    mnesia:dirty_write(Record).

lookup_petri_net_impl(PetriNetId) ->
    case mnesia:dirty_read(petri_net_registry, PetriNetId) of
        [#petri_net_registry{rust_pointer = RustPointer}] ->
            {ok, RustPointer};
        [] ->
            {error, not_found};
        Other ->
            {error, {unexpected_result, Other}}
    end.

unregister_petri_net_impl(PetriNetId) ->
    case mnesia:dirty_delete(petri_net_registry, PetriNetId) of
        ok ->
            ok;
        {aborted, no_exists} ->
            ok;
        {aborted, Reason} ->
            {error, Reason}
    end.

%% Conformance implementation functions
register_conformance_impl(ConformanceId, RustPointer) ->
    UUID = uuid:uuid_to_binary(uuid:uuid4()),
    Timestamp = erlang:system_time(millisecond),
    Record = #conformance_registry{
        conformance_id = ConformanceId,
        uuid = UUID,
        rust_pointer = RustPointer,
        timestamp = Timestamp
    },
    mnesia:dirty_write(Record).

lookup_conformance_impl(ConformanceId) ->
    case mnesia:dirty_read(conformance_registry, ConformanceId) of
        [#conformance_registry{rust_pointer = RustPointer}] ->
            {ok, RustPointer};
        [] ->
            {error, not_found};
        Other ->
            {error, {unexpected_result, Other}}
    end.

unregister_conformance_impl(ConformanceId) ->
    case mnesia:dirty_delete(conformance_registry, ConformanceId) of
        ok ->
            ok;
        {aborted, no_exists} ->
            ok;
        {aborted, Reason} ->
            {error, Reason}
    end.

%% Utility implementation functions
clear_stale_entries_impl() ->
    try
        %% Remove entries older than 24 hours
        Now = erlang:system_time(millisecond),
        CutoffTime = Now - (24 * 60 * 60 * 1000),

        %% Clean each table
        lists:foreach(fun(Table) ->
            F = fun() ->
                case Table of
                    ocel_registry ->
                        mnesia:foldl(fun(#ocel_registry{timestamp = Timestamp}, Acc) ->
                            if Timestamp < CutoffTime ->
                                mnesia:delete({ocel_registry, ocel_id}),
                                Acc + 1;
                            true ->
                                Acc
                            end
                        end, 0, ocel_registry);
                    slim_ocel_registry ->
                        mnesia:foldl(fun(#slim_ocel_registry{timestamp = Timestamp}, Acc) ->
                            if Timestamp < CutoffTime ->
                                mnesia:delete({slim_ocel_registry, slim_ocel_id}),
                                Acc + 1;
                            true ->
                                Acc
                            end
                        end, 0, slim_ocel_registry);
                    petri_net_registry ->
                        mnesia:foldl(fun(#petri_net_registry{timestamp = Timestamp}, Acc) ->
                            if Timestamp < CutoffTime ->
                                mnesia:delete({petri_net_registry, petri_net_id}),
                                Acc + 1;
                            true ->
                                Acc
                            end
                        end, 0, petri_net_registry);
                    conformance_registry ->
                        mnesia:foldl(fun(#conformance_registry{timestamp = Timestamp}, Acc) ->
                            if Timestamp < CutoffTime ->
                                mnesia:delete({conformance_registry, conformance_id}),
                                Acc + 1;
                            true ->
                                Acc
                            end
                        end, 0, conformance_registry)
                end
            end,
            {atomic, RemovedCount} = mnesia:transaction(F),
            lager:info("Removed ~p stale entries from ~p", [RemovedCount, Table])
        end, ?TABLES),

        ok
    catch
        _:Reason ->
            {error, Reason}
    end.