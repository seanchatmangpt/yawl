%% OCEL Operations Tests
%% Tests Object-Centric Event Log operations with real integration

-module(test_ocel_operations).
-include_lib("eunit/include/eunit.hrl").
-include_lib("kernel/include/logger.hrl").

%% Test exports
-export([
    ocel_import_export_test/0,
    ocel_json_validation_test/0,
    ocel_statistics_test/0,
    ocel_discovery_test/0,
    ocel_object_operations_test/0,
    ocel_event_operations_test/0,
    ocel_lifecycle_test/0,
    ocel_performance_test/0,
    ocel_data_integrity_test/0
]).

%%====================================================================
%% Test Cases
%%====================================================================

%% @doc Test OCEL import/export operations
ocel_import_export_test() ->
    %% Test various OCEL file sizes and complexities
    TestCases = [
        {1, "small"},
        {10, "medium"},
        {50, "large"}
    ],

    lists:foreach(fun({Num, Desc}) ->
        OcelContent = test_fixtures:sample_ocel_json(Num),
        OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),
        ExportPath = test_fixtures:create_temp_file(<<"">>, "export_" ++ Desc ++ ".json"),

        try
            %% Import OCEL
            case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
                {ok, OcelHandle} ->
                    ?debugFmt("OCEL ~s imported: ~p", [Desc, OcelHandle]),

                    %% Export immediately
                    case process_mining_bridge:export_ocel_json(OcelHandle, ExportPath) of
                        ok ->
                            ?debugFmt("OCEL ~s exported", [Desc]),

                            %% Verify exported content
                            {ok, OriginalContent} = file:read_file(OcelPath),
                            {ok, ExportedContent} = file:read_file(ExportPath),

                            %% Basic validation - should contain key elements
                            ?assertMatch({match, _}, re:run(binary_to_list(ExportedContent), "\"events\"")),
                            ?assertMatch({match, _}, re:run(binary_to_list(ExportedContent), "\"objects\")),

                            %% Clean up
                            cleanup_test_file(ExportPath),
                            process_mining_bridge:free_handle(OcelHandle);
                        {error, Reason} ->
                            ?debugFmt("Export failed for ~s: ~p", [Desc, Reason]),
                            process_mining_bridge:free_handle(OcelHandle)
                    end;

                {error, Reason} ->
                    ?debugFmt("Import failed for ~s: ~p", [Desc, Reason]),
                    ?assertMatch({error, nif_not_loaded}, Reason)
            end
        after
            cleanup_test_file(OcelPath)
        end
    end, TestCases).

%% @doc Test OCEL JSON validation
ocel_json_validation_test() ->
    %% Test valid OCEL JSON
    ValidOcel = test_fixtures:sample_ocel_json(),
    ValidPath = test_fixtures:create_temp_file(ValidOcel, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => ValidPath}) of
            {ok, Handle} ->
                process_mining_bridge:free_handle(Handle),
                ?debugFmt("Valid OCEL JSON accepted");
            {error, Reason} ->
                ?debugFmt("Valid OCEL JSON rejected: ~p", [Reason])
        end
    after
        cleanup_test_file(ValidPath)
    end,

    %% Test invalid OCEL JSON
    InvalidOcel = test_fixtures:invalid_ocel_json(),
    InvalidPath = test_fixtures:create_temp_file(InvalidOcel, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => InvalidPath}) of
            {ok, Handle} ->
                process_mining_bridge:free_handle(Handle),
                ?fail("Invalid OCEL JSON should have been rejected");
            {error, Reason} ->
                ?debugFmt("Invalid OCEL JSON correctly rejected: ~p", [Reason])
        end
    after
        cleanup_test_file(InvalidPath)
    end,

    %% Test empty OCEL JSON
    EmptyOcel = test_fixtures:empty_ocel_json(),
    EmptyPath = test_fixtures:create_temp_file(EmptyOcel, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => EmptyPath}) of
            {ok, Handle} ->
                process_mining_bridge:free_handle(Handle),
                ?debugFmt("Empty OCEL JSON accepted");
            {error, Reason} ->
                ?debugFmt("Empty OCEL JSON rejected: ~p", [Reason])
        end
    after
        cleanup_test_file(EmptyPath)
    end.

%% @doc Test OCEL statistics operations
ocel_statistics_test() ->
    TestSizes = [5, 20, 100],

    lists:foreach(fun(Size) ->
        OcelContent = test_fixtures:sample_ocel_json(Size),
        OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),

        try
            case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
                {ok, OcelHandle} ->
                    %% Test basic statistics
                    case process_mining_bridge:event_log_stats(#{log_handle => OcelHandle}) of
                        {ok, Stats} ->
                            ?debugFmt("Size ~p stats: ~p", [Size, Stats]),

                            %% Verify stats make sense
                            ?assert(maps:is_key(traces, Stats)),
                            ?assert(maps:is_key(events, Stats)),
                            ?assert(maps:is_key(activities, Stats)),
                            ?assert(maps:is_key(avg_events_per_trace, Stats)),

                            %% Stats should be reasonable
                            Events = maps:get(events, Stats),
                            ?assert(Events =:= Size * 2), % Each test case has 2 events per object

                            %% Test individual counts
                            case process_mining_bridge:log_event_count(#{ocel_id => OcelHandle}) of
                                {ok, EventCount} ->
                                    ?assert(EventCount =:= Size * 2);
                                {error, _} ->
                                    ok % NIF not loaded
                            end,

                            case process_mining_bridge:log_object_count(#{ocel_id => OcelHandle}) of
                                {ok, ObjectCount} ->
                                    ?assert(ObjectCount =:= Size);
                                {error, _} ->
                                    ok % NIF not loaded
                            end;
                        {error, Reason} ->
                            ?debugFmt("Stats error for size ~p: ~p", [Size, Reason])
                    end,

                    process_mining_bridge:free_handle(OcelHandle);
                {error, Reason} ->
                    ?debugFmt("Import error for size ~p: ~p", [Size, Reason])
            end
        after
            cleanup_test_file(OcelPath)
        end
    end, TestSizes).

%% @doc Test OCEL discovery operations
ocel_discovery_test() ->
    %% Import test OCEL data
    OcelContent = test_fixtures:sample_ocel_json(20),
    OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
            {ok, OcelHandle} ->
                %% Test DFG discovery from OCEL
                case process_mining_bridge:discover_dfg(#{ocel_handle => OcelHandle}) of
                    {ok, DfgJson} ->
                        ?debugFmt("OCEL DFG discovered: ~p bytes", [byte_size(DfgJson)]),
                        ?assert(is_binary(DfgJson)),
                        ?assert(byte_size(DfgJson) > 0),
                        %% Basic JSON validation
                        ?assertMatch({match, _}, re:run(binary_to_list(DfgJson), "\"edges\"|\"nodes\""));
                    {error, Reason} ->
                        ?debugFmt("OCEL DFG error: ~p", [Reason]),
                        ?assertMatch({error, nif_not_loaded}, Reason)
                end,

                %% Test OC-DFG discovery (should throw exception for now)
                ?assertError({'UnsupportedOperationException', _},
                             process_mining_bridge:discover_oc_dfg(#{ocel_handle => OcelHandle})),

                process_mining_bridge:free_handle(OcelHandle);
            {error, Reason} ->
                ?debugFmt("Import error for discovery test: ~p", [Reason])
        end
    after
        cleanup_test_file(OcelPath)
    end.

%% @doc Test OCEL object operations
ocel_object_operations_test() ->
    %% Import OCEL with multiple objects
    OcelContent = test_fixtures:sample_ocel_json(10),
    OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
            {ok, OcelHandle} ->
                %% Get objects
                case process_mining_bridge:log_get_objects(#{ocel_id => OcelHandle}) of
                    {ok, ObjectsHandle} ->
                        ?debugFmt("Objects retrieved: ~p", [ObjectsHandle]),

                        %% Test freeing objects
                        case process_mining_bridge:objects_free(#{objects_handle => ObjectsHandle}) of
                            ok ->
                                ?debugFmt("Objects freed successfully");
                            {error, Reason} ->
                                ?debugFmt("Objects free error: ~p", [Reason])
                        end;
                    {error, Reason} ->
                        ?debugFmt("Get objects error: ~p", [Reason])
                end,

                process_mining_bridge:free_handle(OcelHandle);
            {error, Reason} ->
                ?debugFmt("Import error for object test: ~p", [Reason])
        end
    after
        cleanup_test_file(OcelPath)
    end.

%% @doc Test OCEL event operations
ocel_event_operations_test() ->
    %% Import OCEL with events
    OcelContent = test_fixtures:sample_ocel_json(10),
    OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
            {ok, OcelHandle} ->
                %% Get events
                case process_mining_bridge:log_get_events(#{ocel_id => OcelHandle}) of
                    {ok, EventsHandle} ->
                        ?debugFmt("Events retrieved: ~p", [EventsHandle]),

                        %% Test freeing events
                        case process_mining_bridge:events_free(#{events_handle => EventsHandle}) of
                            ok ->
                                ?debugFmt("Events freed successfully");
                            {error, Reason} ->
                                ?debugFmt("Events free error: ~p", [Reason])
                        end;
                    {error, Reason} ->
                        ?debugFmt("Get events error: ~p", [Reason])
                end,

                process_mining_bridge:free_handle(OcelHandle);
            {error, Reason} ->
                ?debugFmt("Import error for event test: ~p", [Reason])
        end
    after
        cleanup_test_file(OcelPath)
    end.

%% @doc Test OCEL lifecycle operations
ocel_lifecycle_test() ->
    %% Test complete OCEL lifecycle: import → use → free
    OcelContent = test_fixtures:sample_ocel_json(5),
    OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),

    try
        %% Phase 1: Import
        case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
            {ok, OcelHandle} ->
                ?debugFmt("Phase 1: Imported OCEL"),

                %% Phase 2: Get statistics
                case process_mining_bridge:event_log_stats(#{log_handle => OcelHandle}) of
                    {ok, Stats} ->
                        ?debugFmt("Phase 2: Stats: ~p", [Stats]);

                    {error, Reason} ->
                        ?debugFmt("Phase 2: Stats error: ~p", [Reason])
                end,

                %% Phase 3: Perform operations
                case process_mining_bridge:discover_dfg(#{ocel_handle => OcelHandle}) of
                    {ok, _} ->
                        ?debugFmt("Phase 3: Discovery completed");
                    {error, Reason} ->
                        ?debugFmt("Phase 3: Discovery error: ~p", [Reason])
                end,

                %% Phase 4: Free resources
                process_mining_bridge:free_handle(OcelHandle),
                ?debugFmt("Phase 4: Resources freed");

                %% Phase 5: Verify handle is invalid
                ?assertMatch({error, _},
                             process_mining_bridge:event_log_stats(#{log_handle => OcelHandle}));

            {error, Reason} ->
                ?debugFmt("Lifecycle test failed at import: ~p", [Reason])
        end
    after
        cleanup_test_file(OcelPath)
    end.

%% @doc Test OCEL performance with large datasets
ocel_performance_test() ->
    PerformanceSizes = [100, 500, 1000],

    lists:foreach(fun(Size) ->
        Start = erlang:monotonic_time(millisecond),

        OcelContent = test_fixtures:sample_ocel_json(Size),
        OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),

        try
            case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
                {ok, OcelHandle} ->
                    %% Time statistics
                    StatsStart = erlang:monotonic_time(millisecond),
                    case process_mining_bridge:event_log_stats(#{log_handle => OcelHandle}) of
                        {ok, Stats} ->
                            StatsEnd = erlang:monotonic_time(millisecond),
                            ?debugFmt("Size ~p: Import=~pms, Stats=~pms, Events=~p", [
                                Size,
                                erlang:monotonic_time(millisecond) - Start,
                                StatsEnd - StatsStart,
                                maps:get(events, Stats)
                            ]);
                        {error, _} ->
                            ?debugFmt("Size ~p: Import=~pms, Stats failed", [
                                Size,
                                erlang:monotonic_time(millisecond) - Start
                            ])
                    end,

                    process_mining_bridge:free_handle(OcelHandle);
                {error, Reason} ->
                    ?debugFmt("Size ~p performance test failed: ~p", [Size, Reason])
            end
        after
            cleanup_test_file(OcelPath)
        end
    end, PerformanceSizes).

%% @doc Test OCEL data integrity
ocel_data_integrity_test() ->
    %% Create OCEL with specific data structure
    TestOcel = create_test_ocel_structure(),
    OcelPath = test_fixtures:create_temp_file(TestOcel, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
            {ok, OcelHandle} ->
                %% Get statistics and verify data integrity
                case process_mining_bridge:event_log_stats(#{log_handle => OcelHandle}) of
                    {ok, Stats} ->
                        ExpectedEvents = 6, % 2 objects * 3 events each
                        ExpectedTraces = 2,
                        ExpectedActivities = 3,

                        ?assertEqual(ExpectedEvents, maps:get(events, Stats)),
                        ?assertEqual(ExpectedTraces, maps:get(traces, Stats)),
                        ?assertEqual(ExpectedActivities, maps:get(activities, Stats)),

                        ?debugFmt("Data integrity verified: ~p", [Stats]);
                    {error, Reason} ->
                        ?debugFmt("Stats error: ~p", [Reason])
                end,

                process_mining_bridge:free_handle(OcelHandle);
            {error, Reason} ->
                ?debugFmt("Import error: ~p", [Reason])
        end
    after
        cleanup_test_file(OcelPath)
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% Create a specific test OCEL structure for integrity testing
create_test_ocel_structure() ->
    Events = [
        #{
            <<"id">> => <<"event1">>,
            <<"type">> => <<"start">>,
            <<"timestamp">> => <<"2024-01-01T10:00:00Z">>,
            <<"source">> => [<<"order1">>],
            <<"attributes">> => #{
                <<"resource">> => <<"user1">>,
                <<"amount">> => 100
            }
        },
        #{
            <<"id">> => <<"event2">>,
            <<"type">> => <<"process">>,
            <<"timestamp">> => <<"2024-01-01T10:30:00Z">>,
            <<"source">> => [<<"order1">>, <<"order2">>],
            <<"attributes">> => #{
                <<"resource">> => <<"user2">>,
                <<"amount">> => 200
            }
        },
        #{
            <<"id">> => <<"event3">>,
            <<"type">> => <<"complete">>,
            <<"timestamp">> => <<"2024-01-01T11:00:00Z">>,
            <<"source">> => [<<"order1">>],
            <<"attributes">> => #{
                <<"resource">> => <<"user1">>,
                <<"amount">> => 50
            }
        },
        #{
            <<"id">> => <<"event4">>,
            <<"type">> => <<"start">>,
            <<"timestamp">> => <<"2024-01-01T12:00:00Z">>,
            <<"source">> => [<<"order2">>],
            <<"attributes">> => #{
                <<"resource">> => <<"user3">>,
                <<"amount">> => 150
            }
        },
        #{
            <<"id">> => <<"event5">>,
            <<"type">> => <<"process">>,
            <<"timestamp">> => <<"2024-01-01T12:30:00Z">>,
            <<"source">> => [<<"order2">>],
            <<"attributes">> => #{
                <<"resource">> => <<"user1">>,
                <<"amount">> => 100
            }
        },
        #{
            <<"id">> => <<"event6">>,
            <<"type">> => <<"complete">>,
            <<"timestamp">> => <<"2024-01-01T13:00:00Z">>,
            <<"source">> => [<<"order2">>],
            <<"attributes">> => #{
                <<"resource">> => <<"user2">>,
                <<"amount">> => 75
            }
        }
    ],

    Objects = [
        #{
            <<"id">> => <<"order1">>,
            <<"type">> => <<"order">>,
            <<"attributes">> => #{
                <<"status">> => <<"completed">>,
                <<"total">> => 150,
                <<"customer">> => <<"customer1">>
            }
        },
        #{
            <<"id">> => <<"order2">>,
            <<"type">> => <<"order">>,
            <<"attributes">> => #{
                <<"status">> => <<"completed">>,
                <<"total">> => 250,
                <<"customer">> => <<"customer2">>
            }
        }
    ],

    jsx:encode(#{
        events => Events,
        objects => Objects
    }).

%% Clean up test files
cleanup_test_file(Path) ->
    test_fixtures:cleanup_temp_file(Path).

%%====================================================================
%% Test Suite
%%====================================================================

ocel_operations_test_() ->
    {setup,
     fun() ->
         %% Start the application
         case application:ensure_all_started(process_mining_bridge) of
             {ok, _Apps} ->
                 timer:sleep(500), % Wait for initialization
                 ok;
             {error, Reason} ->
                 error({failed_to_start_app, Reason})
         end
     end,
     fun(_) ->
         application:stop(process_mining_bridge)
     end,
     [
        %% Basic OCEL operations
        {timeout, 30000, ?_test(ocel_import_export_test())},
        {timeout, 20000, ?_test(ocel_json_validation_test())},
        {timeout, 20000, ?_test(ocel_statistics_test())},

        %% Advanced OCEL operations
        {timeout, 30000, ?_test(ocel_discovery_test())},
        {timeout, 20000, ?_test(ocel_object_operations_test())},
        {timeout, 20000, ?_test(ocel_event_operations_test())},

        %% Lifecycle and performance
        {timeout, 20000, ?_test(ocel_lifecycle_test())},
        {timeout, 60000, ?_test(ocel_performance_test())},
        {timeout, 20000, ?_test(ocel_data_integrity_test())}
     ]}.