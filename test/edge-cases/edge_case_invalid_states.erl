%% Edge Case Test: Invalid State Transitions
%% Tests that the system properly detects and handles invalid workflow state transitions

-module(edge_case_invalid_states).
-export([run/0, run_test/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the invalid state transitions edge case test
%% Returns {ok, Result} | {error, Reason}
run() ->
    io:format("Running edge case: Invalid State Transitions~n", []),
    run_test().

%% @doc Test invalid state transition scenarios
run_test() ->
    TestDir = "/tmp/yawl-test/edge-cases/invalid_states",
    ensure_test_dir(TestDir),

    %% Test various invalid transition scenarios
    TestCases = [
        {invalid_start_state, create_invalid_start_state_ocel()},
        {invalid_end_state, create_invalid_end_state_ocel()},
        {invalid_sequence, create_invalid_sequence_ocel()},
        {duplicate_state, create_duplicate_state_ocel()},
        {missing_start_state, create_missing_start_state_ocel()},
        {unreachable_state, create_unreachable_state_ocel()}
    ],

    Results = lists:map(fun({Type, OcelContent}) ->
        TestPath = filename:join(TestDir, Type ++ ".json"),
        file:write_file(TestPath, OcelContent),

        case process_mining_bridge:import_ocel_json(TestPath) of
            {ok, OcelHandle} ->
                %% Try DFG discovery
                case process_mining_bridge:discover_dfg(OcelHandle) of
                    {ok, DfgJson} ->
                        %% Check for invalid state transitions
                        case validate_state_transitions(DfgJson) of
                            {ok, ValidationResults} ->
                                process_mining_bridge:free_handle(OcelHandle),
                                {Type, {validation_passed, ValidationResults}};
                            {error, ValidationErrors} ->
                                process_mining_bridge:free_handle(OcelHandle),
                                {Type, {validation_failed, ValidationErrors}}
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
    ValidationsPassed = [R || {_, {validation_passed, _}} <- Results],
    ValidationsFailed = [R || {_, {validation_failed, _}} <- Results],
    DfgFailures = [R || {_, {dfg_failed, _}} <- Results],
    ImportFailures = [R || {_, {import_failed, _}} <- Results],

    {ok, #{
        test => "invalid_states",
        test_cases => length(TestCases),
        validations_passed => length(ValidationsPassed),
        validations_failed => length(ValidationsFailed),
        dfg_failures => length(DfgFailures),
        import_failures => length(ImportFailures),
        results => Results,
        status => case {ValidationsFailed, DfgFailures} of
                    {[_], [_]} -> "partial_validation";
                    {_, _} when length(ValidationsFailed) > 0 -> "errors_detected";
                    _ -> "all_validations_passed"
                end
    }}.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Create OCEL with invalid start state (no start activity)
create_invalid_start_state_ocel() ->
    Events = [
        #{
            << "id">> => << "event1">>,
            << "activity">> => << "processing">>, % Missing proper start state
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{}
        },
        #{
            << "id">> => << "event2">>,
            << "activity">> => << "completion">>,
            << "timestamp">> => << "2023-01-01T00:01:00">>,
            << "attributes">> => #{}
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with invalid end state (no proper end)
create_invalid_end_state_ocel() ->
    Events = [
        #{
            << "id">> => << "event1">>,
            << "activity">> => << "start">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{}
        },
        #{
            << "id">> => << "event2">>,
            << "activity">> => << "processing">>, % No end state
            << "timestamp">> => << "2023-01-01T00:01:00">>,
            << "attributes">> => #{}
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with invalid sequence (out-of-order)
create_invalid_sequence_ocel() ->
    Events = [
        #{
            << "id">> => << "event2">>,
            << "activity">> => << "middle">>,
            << "timestamp">> => << "2023-01-01T00:00:30">>,
            << "attributes">> => #{}
        },
        #{
            << "id">> => << "event1">>,
            << "activity">> => << "start">>,
            << "timestamp">> => << "2023-01-01T00:01:00">>, % After middle!
            << "attributes">> => #{}
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with duplicate states
create_duplicate_state_ocel() ->
    Events = [
        #{
            << "id">> => << "event1">>,
            << "activity">> => << "start">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{}
        },
        #{
            << "id">> => << "event2">>,
            << "activity">> => << "start">>, % Duplicate activity
            << "timestamp">> => << "2023-01-01T00:01:00">>,
            << "attributes">> => #{}
        },
        #{
            << "id">> => << "event3">>,
            << "activity">> => << "end">>,
            << "timestamp">> => << "2023-01-01T00:02:00">>,
            << "attributes">> => #{}
        }
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with missing start state
create_missing_start_state_ocel() ->
    Events = [
        #{
            << "id">> => << "event1">>,
            << "activity">> => << "processing">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{}
        }
        % No start activity
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Create OCEL with unreachable state
create_unreachable_state_ocel() ->
    Events = [
        #{
            << "id">> => << "event1">>,
            << "activity">> => << "start">>,
            << "timestamp">> => << "2023-01-01T00:00:00">>,
            << "attributes">> => #{}
        },
        #{
            << "id">> => << "event2">>,
            << "activity">> => << "processing">>,
            << "timestamp">> => << "2023-01-01T00:01:00">>,
            << "attributes">> => #{}
        }
        % Missing transition to "end" state - it's unreachable
    ],

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Validate state transitions in DFG
validate_state_transitions(DfgJson) ->
    try
        DfgData = jsx:decode(DfgJson, [{return_maps, true}]),
        case maps:get(<<"edges">>, DfgData, []) of
            [] ->
                {error, no_edges_to_validate};
            Edges ->
                ValidationResults = validate_each_transition(Edges),
                {ok, ValidationResults}
        end
    catch
        _:_ ->
            {error, json_parse_error}
    end.

%% @doc Validate each transition in the DFG
validate_each_transitions(Edges) ->
    lists:foldl(fun(Edge, Results) ->
        case validate_transition(Edge) of
            {valid, _} ->
                Results;
            {invalid, Reason} ->
                [Reason | Results]
        end
    end, [], Edges).

%% @brief Validate a single transition
validate_transition(Edge) ->
    case Edge of
        #{<<"source">> := Source, <<"target">> := Target} ->
            case validate_activity(Source) of
                valid ->
                    case validate_activity(Target) of
                        valid ->
                            case check_transition_logic(Source, Target) of
                                valid -> {valid, {Source, Target}};
                                invalid -> {invalid, {invalid_transition, Source, Target, logic_violation}}
                            end;
                        invalid -> {invalid, {invalid_transition, Source, Target, invalid_target}}
                    end;
                invalid -> {invalid, {invalid_transition, Source, Target, invalid_source}}
            end;
        _ ->
            {invalid, {malformed_edge, Edge}}
    end.

%% @brief Validate an activity name
validate_activity(Activity) ->
    %% Basic validation - activities shouldn't be empty or null
    case Activity of
        undefined -> invalid;
        null -> invalid;
        <<>> -> invalid;
        A when is_binary(A) andalso byte_size(A) > 0 ->
            %% Additional validation could be added here
            case Activity of
                << "start" >> -> valid;
                << "end" >> -> valid;
                _ -> valid
            end;
        _ -> invalid
    end.

%% @brief Check transition logic between activities
check_transition_logic(Source, Target) ->
    %% Basic rules for valid transitions
    case {Source, Target} of
        {<< "start" >>, _} -> valid;
        {_, << "end" >>} -> valid;
        {Source, Target} when Source =/= Target -> valid;
        _ -> invalid
    end.

%% @doc Ensure test directory exists
ensure_test_dir(Dir) ->
    filelib:ensure_dir(Dir ++ "/"),
    case file:make_dir(Dir) of
        ok -> ok;
        {error, eexist} -> ok;
        {error, Reason} -> {error, Reason}
    end.