%% Edge Case Test: Circular Dependencies
%% Tests that the system properly detects and handles circular task dependencies

-module(edge_case_circular_deps).
-export([run/0, run_test/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the circular dependencies edge case test
%% Returns {ok, Result} | {error, Reason}
run() ->
    io:format("Running edge case: Circular Dependencies~n", []),
    run_test().

%% @doc Test circular dependency scenarios
run_test() ->
    TestDir = "/tmp/yawl-test/edge-cases/circular_deps",
    ensure_test_dir(TestDir),

    %% Create circular dependency scenarios
    TestCases = [
        {simple_cycle, create_simple_cycle_ocel()},
        {complex_cycle, create_complex_cycle_ocel()},
        {self_loop, create_self_loop_ocel()},
        {parallel_cycle, create_parallel_cycle_ocel()}
    ],

    Results = lists:map(fun({Type, OcelContent}) ->
        TestPath = filename:join(TestDir, Type ++ ".json"),
        file:write_file(TestPath, OcelContent),

        case process_mining_bridge:import_ocel_json(TestPath) of
            {ok, OcelHandle} ->
                %% Try DFG discovery
                case process_mining_bridge:discover_dfg(OcelHandle) of
                    {ok, DfgJson} ->
                        %% Check for circular dependencies
                        case detect_cycles_in_dfg(DfgJson) of
                            {ok, Cycles} ->
                                process_mining_bridge:free_handle(OcelHandle),
                                {Type, {cycles_detected, Cycles}};
                            {error, CycleCheckReason} ->
                                process_mining_bridge:free_handle(OcelHandle),
                                {Type, {cycle_check_failed, CycleCheckReason}}
                        end;
                    {error, _} = DfgError ->
                        process_mining_bridge:free_handle(OcelHandle),
                        {Type, {dfg_failed, DfgError}}
                end;
            {error, ImportError} ->
                {Type, {import_failed, ImportError}}
        end
    end, TestCases),

    %% Analyze results
    CyclesDetected = [R || {_, {cycles_detected, _}} <- Results],
    DfgFailures = [R || {_, {dfg_failed, _}} <- Results],
    ImportFailures = [R || {_, {import_failed, _}} <- Results],

    {ok, #{
        test => "circular_deps",
        test_cases => length(TestCases),
        cycles_detected => length(CyclesDetected),
        dfg_failures => length(DfgFailures),
        import_failures => length(ImportFailures),
        results => Results,
        status => case {CyclesDetected, DfgFailures} of
                    {[_], [_]} -> "partial_detection";
                    {_, _} when length(CyclesDetected) > 0 -> "cycles_detected";
                    _ -> "no_cycles_detected"
                end
    }}.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Create OCEL with simple circular dependency: A -> B -> A
create_simple_cycle_ocel() ->
    Events = [
        #{
            << "id">> => << "a1">>,
            << "activity">> => << "A">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "b1">>
            }
        },
        #{
            << "id">> => << "b1">>,
            << "activity">> => << "B">>,
            << "timestamp">> => << "2023-01-01T00:01:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "a1">> % Creates cycle: B -> A
            }
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with complex cycle: A -> B -> C -> A
create_complex_cycle_ocel() ->
    Events = [
        #{
            << "id">> => << "a1">>,
            << "activity">> => << "A">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "b1">>
            }
        },
        #{
            << "id">> => << "b1">>,
            << "activity">> => << "B">>,
            << "timestamp">> => << "2023-01-01T00:01:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "c1">>
            }
        },
        #{
            << "id">> => << "c1">>,
            << "activity">> => << "C">>,
            << "timestamp">> => << "2023-01-01T00:02:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "a1">> % Completes cycle: C -> A
            }
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with self-loop: A -> A
create_self_loop_ocel() ->
    Events = [
        #{
            << "id">> => << "a1">>,
            << "activity">> => << "A">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "a1">> % Self-loop
            }
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with parallel cycle: A -> B -> C and C -> D -> A
create_parallel_cycle_ocel() ->
    Events = [
        % First branch: A -> B -> C
        #{
            << "id">> => << "a1">>,
            << "activity">> => << "A">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "b1">>
            }
        },
        #{
            << "id">> => << "b1">>,
            << "activity">> => << "B">>,
            << "timestamp">> => << "2023-01-01T00:01:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "c1">>
            }
        },
        % Second branch: C -> D -> A (completes cycle)
        #{
            << "id">> => << "c1">>,
            << "activity">> => << "C">>,
            << "timestamp">> => << "2023-01-01T00:02:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "d1">>
            }
        },
        #{
            << "id">> => << "d1">>,
            << "activity">> => << "D">>,
            << "timestamp">> => << "2023-01-01T00:03:00">>,
            << "attributes">> => #{
                << "case_id">> => << "case1">>,
                << "follows">> => << "a1">> % Completes cycle: D -> A
            }
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Detect cycles in DFG
detect_cycles_in_dfg(DfgJson) ->
    try
        DfgData = jsx:decode(DfgJson, [{return_maps, true}]),
        case maps:get(<<"edges">>, DfgData, []) of
            [] ->
                {error, no_edges_found};
            Edges ->
                % Build adjacency list
                Graph = build_graph(Edges),
                % Find cycles
                Cycles = find_cycles(Graph),
                {ok, Cycles}
        end
    catch
        _:_ ->
            {error, json_parse_error}
    end.

%% @doc Build graph from DFG edges
build_graph(Edges) ->
    lists:foldl(fun(Edge, Graph) ->
        case Edge of
            #{<<"source">> := Source, <<"target">> := Target} ->
                maps:update(Source, [Target | maps:get(Source, Graph, [])], Graph);
            _ ->
                Graph
        end
    end, #{}, Edges).

%% @doc Find cycles in graph using DFS
find_cycles(Graph) ->
    Visited = sets:new(),
    RecStack = sets:new(),
    Cycles = [],

    AllNodes = maps:keys(Graph),
    lists:foldl(fun(Node, {VisitedAcc, RecStackAcc, CyclesAcc}) ->
        case sets:is_member(Node, VisitedAcc) of
            false ->
                {NewVisited, NewRecStack, NewCycles} =
                    dfs_cycle_detection(Node, VisitedAcc, RecStackAcc, Graph),
                {NewVisited, NewRecStack, CyclesAcc ++ NewCycles};
            true ->
                {VisitedAcc, RecStackAcc, CyclesAcc}
        end
    end, {Visited, RecStack, Cycles}, AllNodes).

%% @brief DFS cycle detection helper
dfs_cycle_detection(Node, Visited, RecStack, Graph) ->
    VisitedNew = sets:add_element(Node, Visited),
    RecStackNew = sets:add_element(Node, RecStack),

    case maps:get(Node, Graph, []) of
        [] ->
            {VisitedNew, sets:del_element(Node, RecStackNew), []};
        Neighbors ->
            lists:foldl(fun(Neighbor, {V, R, C}) ->
                case sets:is_member(Neighbor, RecStack) of
                    true ->
                        % Found a cycle
                        Cycle = [Node, Neighbor],
                        {V, R, C ++ [Cycle]};
                    false ->
                        case sets:is_member(Neighbor, V) of
                            true ->
                                {V, R, C};
                            false ->
                                {NewV, NewR, NewC} = dfs_cycle_detection(Neighbor, V, R, Graph),
                                {NewV, NewR, C ++ NewC}
                        end
                        end
            end, {VisitedNew, RecStackNew, []}, Neighbors)
    end.

%% @doc Ensure test directory exists
ensure_test_dir(Dir) ->
    filelib:ensure_dir(Dir ++ "/"),
    case file:make_dir(Dir) of
        ok -> ok;
        {error, eexist} -> ok;
        {error, Reason} -> {error, Reason}
    end.