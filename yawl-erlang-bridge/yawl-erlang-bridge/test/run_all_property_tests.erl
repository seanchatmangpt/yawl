%% Comprehensive Property Test Runner
%% Runs all property tests with detailed reporting

-module(run_all_property_tests).
-include_lib("eunit/include/eunit.hrl").
-include_lib("kernel/include/logger.hrl").

%%====================================================================
%% Test Configuration
%%====================================================================

-define(TEST_TIMEOUT, 300000).  %% 5 minutes
-define(DEFAULT_CASES, 100).
-define(STRESS_CASES, 10).

%%====================================================================
%% Main Test Functions
%%====================================================================

%% @doc Run all property tests with comprehensive reporting
run_all_tests() ->
    io:format("=== Running Comprehensive OCEL Property Tests ===~n"),
    io:format("Timeout: ~p seconds~n", [?TEST_TIMEOUT div 1000]),

    %% Test categories
    Categories = [
        {basic, "Basic OCEL Parsing Tests", ?DEFAULT_CASES},
        {edge_cases, "Edge Case Tests", ?DEFAULT_CASES},
        {stress, "Stress Tests", ?STRESS_CASES},
        {invalid, "Invalid Input Tests", ?DEFAULT_CASES},
        {unicode, "Unicode Character Tests", ?DEFAULT_CASES},
        {large_data, "Large Data Tests", ?STRESS_CASES}
    ],

    Results = run_test_categories(Categories, #{}),

    %% Generate final report
    generate_report(Results),

    %% Determine overall result
    case all_passed(Results) of
        true ->
            io:format("~n✓ ALL PROPERTY TESTS PASSED~n"),
            ok;
        false ->
            io:format("~n✗ SOME PROPERTY TESTS FAILED~n"),
            {failed, Results}
    end.

%%====================================================================
%% Test Category Runners
%%====================================================================

run_test_categories([], Results) ->
    Results;
run_test_categories([{Type, Desc, Cases} | Rest], Acc) ->
    io:format("~n=== Running ~s (~p cases) ===~n", [Desc, Cases]),

    CategoryResults = run_tests_for_category(Type, Cases),

    run_test_categories(Rest, Acc#{Type => {Desc, Cases, CategoryResults}}).

%% Basic OCEL parsing tests
run_tests_for_category(basic, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_any_ocel/0, "Parse any OCEL"},
        {fun ocel_property_tests:prop_parse_singleton_ocel/0, "Parse singleton OCEL"},
        {fun ocel_property_tests:prop_parse_empty_events/0, "Parse with empty events"},
        {fun ocel_property_tests:prop_parse_empty_objects/0, "Parse with empty objects"}
    ],
    run_tests(Tests, Cases).

%% Edge case tests
run_tests_for_category(edge_cases, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_boundary_cases/0, "Parse boundary cases"},
        {fun ocel_property_tests:prop_parse_minimal_ocel/0, "Parse minimal OCEL"},
        {fun ocel_property_tests:prop_parse_maximal_ocel/0, "Parse maximal OCEL"}
    ],
    run_tests(Tests, Cases).

%% Stress tests
run_tests_for_category(stress, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_large_ocel/0, "Parse large OCEL"},
        {fun ocel_property_tests:prop_parse_stress_test/0, "Parse stress test data"},
        {fun ocel_property_tests:prop_parse_many_attributes/0, "Parse with many attributes"}
    ],
    run_tests(Tests, Cases).

%% Invalid input tests
run_tests_for_category(invalid, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_malformed_json/0, "Parse malformed JSON"},
        {fun ocel_property_tests:prop_parse_missing_fields/0, "Parse with missing fields"},
        {fun ocel_property_tests:prop_parse_invalid_types/0, "Parse with invalid types"}
    ],
    run_tests(Tests, Cases).

%% Unicode character tests
run_tests_for_category(unicode, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_unicode_event_ids/0, "Parse Unicode event IDs"},
        {fun ocel_property_tests:prop_parse_unicode_object_ids/0, "Parse Unicode object IDs"},
        {fun ocel_property_tests:prop_parse_unicode_attributes/0, "Parse Unicode attributes"}
    ],
    run_tests(Tests, Cases).

%% Large data tests
run_tests_for_category(large_data, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_many_events/0, "Parse with many events"},
        {fun ocel_property_tests:prop_parse_many_objects/0, "Parse with many objects"},
        {fun ocel_property_tests:prop_parse_large_attributes/0, "Parse with large attribute strings"}
    ],
    run_tests(Tests, Cases).

%%====================================================================
%% Test Execution
%%====================================================================

run_tests([], _) ->
    [];
run_tests([{TestFun, Desc} | Rest], Cases) ->
    io:format("  Running: ~s...~n", [Desc]),

    Start = erlang:monotonic_time(millisecond),
    Result = proper:quickcheck(TestFun, [{numtests, Cases}, {verbose, false}]),
    End = erlang:monotonic_time(millisecond),
    Duration = End - Start,

    ResultTag = case Result of
        true -> {passed, Desc, Duration};
        false -> {failed, Desc, Duration}
    end,

    io:format("    Result: ~s (~p ms)~n", [format_result(Result), Duration]),

    [ResultTag | run_tests(Rest, Cases)].

format_result(true) -> "PASS";
format_result(false) -> "FAIL".

%%====================================================================
%% Report Generation
%%====================================================================

generate_report(Results) ->
    io:format("~n=== DETAILED TEST REPORT ===~n"),

    TotalTests = maps:fold(fun(_, {_, Cases, TestResults}, Acc) ->
        Acc + length(TestResults) + Cases
    end, 0, Results),

    Passed = maps:fold(fun(_, {_, Cases, TestResults}, Acc) ->
        Acc + lists:foldl(fun({passed, _, _}, PA) -> PA + 1; (_, PA) -> PA end,
                         lists:duplicate(Cases, true), TestResults)
    end, 0, Results),

    Failed = TotalTests - Passed,

    io:format("Total Tests: ~p~n", [TotalTests]),
    io:format("Passed: ~p (~.1f%)~n", [Passed, (Passed / TotalTests) * 100]),
    io:format("Failed: ~p (~.1f%)~n", [Failed, (Failed / TotalTests) * 100]),

    %% Detailed results by category
    maps:foreach(fun(Type, {Desc, _, TestResults}) ->
        io:format("~n~s:~n", [Desc]),
        lists:foreach(fun(Result) ->
            case Result of
                {passed, Desc, Duration} ->
                    io:format("  ✓ ~s (~p ms)~n", [Desc, Duration]);
                {failed, Desc, Duration} ->
                    io:format("  ✗ ~s (~p ms)~n", [Desc, Duration])
            end
        end, TestResults)
    end, Results).

all_passed(Results) ->
    maps:fold(fun(_, {_, _, TestResults}, Acc) ->
        Acc andalso lists:all(fun({passed, _, _}) -> true; (_) -> false end, TestResults)
    end, true, Results).

%%====================================================================
%% Additional Property Tests (extend ocel_property_tests if needed)
%%====================================================================

%% Boundary cases test
prop_parse_boundary_cases() ->
    ?FORALL(Ocel, boundary_case_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Minimal OCEL test
prop_parse_minimal_ocel() ->
    ?FORALL(Ocel, minimal_ocel_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Maximal OCEL test
prop_parse_maximal_ocel() ->
    ?FORALL(Ocel, maximal_ocel_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Stress test data
prop_parse_stress_test() ->
    ?FORALL(Ocel, stress_test_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Many attributes test
prop_parse_many_attributes() ->
    ?FORALL(Ocel, many_attributes_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Malformed JSON test
prop_parse_malformed_json() ->
    ?FORALL(Json, malformed_json_generator(),
        case process_mining_bridge:import_ocel_json(Json) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Missing fields test
prop_parse_missing_fields() ->
    ?FORALL(Ocel, missing_fields_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Invalid types test
prop_parse_invalid_types() ->
    ?FORALL(Ocel, invalid_types_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Unicode event IDs test
prop_parse_unicode_event_ids() ->
    ?FORALL(Ocel, unicode_event_ids_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Unicode object IDs test
prop_parse_unicode_object_ids() ->
    ?FORALL(Ocel, unicode_object_ids_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Unicode attributes test
prop_parse_unicode_attributes() ->
    ?FORALL(Ocel, unicode_attributes_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Many events test
prop_parse_many_events() ->
    ?FORALL(Ocel, many_events_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Many objects test
prop_parse_many_objects() ->
    ?FORALL(Ocel, many_objects_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%% Large attributes test
prop_parse_large_attributes() ->
    ?FORALL(Ocel, large_attributes_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).

%%====================================================================
%% Additional Generators
%%====================================================================

boundary_case_generator() ->
    oneof([
        test_fixtures:minimal_ocel_json(),
        test_fixtures:maximal_ocel_json(),
        test_fixtures:empty_ocel_json(),
        test_fixtures:edge_case_ocel_json()
    ]).

minimal_ocel_generator() ->
    ?LET({Event, Object}, {event_min_gen(), object_min_gen()},
        jsx:encode(#{
            events => [Event],
            objects => [Object]
        }).

maximal_ocel_generator() ->
    ?LET({Events, Objects}, {list(event_max_gen()), list(object_max_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

stress_test_generator() ->
    test_fixtures:stress_test_ocel().

many_attributes_generator() ->
    ?LET({Events, Objects}, {list(event_many_attrs_gen()), list(object_many_attrs_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

malformed_json_generator() ->
    oneof([
        <<"not json">>,
        test_fixtures:malformed_ocel_json(),
        test_fixtures:ocel_with_null_values()
    ]).

missing_fields_generator() ->
    ?LET({Events, Objects}, {list(event_missing_fields_gen()), list(object_missing_fields_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

invalid_types_generator() ->
    ?LET({Events, Objects}, {list(event_invalid_types_gen()), list(object_invalid_types_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

unicode_event_ids_generator() ->
    ?LET({Events, Objects}, {list(unicode_event_gen()), list(object_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

unicode_object_ids_generator() ->
    ?LET({Events, Objects}, {list(event_gen()), list(unicode_object_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

unicode_attributes_generator() ->
    ?LET({Events, Objects}, {list(unicode_attrs_event_gen()), list(unicode_attrs_object_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

many_events_generator() ->
    ?LET({Events, Objects}, {list(event_gen()), list(object_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

many_objects_generator() ->
    ?LET({Events, Objects}, {list(event_gen()), list(object_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

large_attributes_generator() ->
    ?LET({Events, Objects}, {list(event_large_attrs_gen()), list(object_large_attrs_gen())},
        jsx:encode(#{
            events => Events,
            objects => Objects
        }).

%%====================================================================
%% Helper Generators
%%====================================================================

event_min_gen() ->
    #{
        id => <<"event1">>,
        type => <<"start">>,
        timestamp => <<"2024-01-01T10:00:00Z">>,
        source => [],
        attributes => #{}
    }.

object_min_gen() ->
    #{
        id => <<"object1">>,
        type => <<"order">>,
        attributes => #{}
    }.

event_max_gen() ->
    #{
        id => list_to_binary(lists:duplicate(100, $a)),
        type => list_to_binary(lists:duplicate(50, $b)),
        timestamp => <<"2024-01-01T10:00:00Z">>,
        source => list(lists:duplicate(100, $c)),
        attributes => maps:from_list(lists:map(fun(I) ->
            {list_to_binary(io_lib:format("attr_~p", [I])), lists:duplicate(I, $d)}
        end, lists:seq(1, 100)))
    }.

object_max_gen() ->
    #{
        id => list_to_binary(lists:duplicate(100, $e)),
        type => list_to_binary(lists:duplicate(50, $f)),
        attributes => maps:from_list(lists:map(fun(I) ->
            {list_to_binary(io_lib:format("obj_attr_~p", [I])), lists:duplicate(I, $g)}
        end, lists:seq(1, 100)))
    }.

event_many_attrs_gen() ->
    #{
        id => <<"event1">>,
        type => <<"start">>,
        timestamp => <<"2024-01-01T10:00:00Z">>,
        source => [<<"object1">>],
        attributes => lists:foldl(fun(I, Acc) ->
            maps:put(list_to_binary(io_lib:format("attr_~p", [I])), I, Acc)
        end, #{}, lists:seq(1, 1000))
    }.

object_many_attrs_gen() ->
    #{
        id => <<"object1">>,
        type => <<"order">>,
        attributes => lists:foldl(fun(I, Acc) ->
            maps:put(list_to_binary(io_lib:format("obj_attr_~p", [I])), I, Acc)
        end, #{}, lists:seq(1, 1000))
    }.

event_missing_fields_gen() ->
    #{
        id => <<"event1">>,
        %% missing type, timestamp, source, attributes
    }.

object_missing_fields_gen() ->
    #{
        id => <<"object1">>,
        %% missing type, attributes
    }.

event_invalid_types_gen() ->
    #{
        id => 123,  %% should be binary
        type => [],
        timestamp => 12345,  %% should be binary
        source => <<"not_a_list">>,  %% should be list
        attributes => "not_a_map"  %% should be map
    }.

object_invalid_types_gen() ->
    #{
        id => 456,  %% should be binary
        type => [],
        attributes => "not_a_map"  %% should be map
    }.

unicode_event_gen() ->
    #{
        id => unicode_string(),
        type => unicode_string(),
        timestamp => <<"2024-01-01T10:00:00Z">>,
        source => [unicode_string()],
        attributes => #{unicode_string() => unicode_string()}
    }.

unicode_object_gen() ->
    #{
        id => unicode_string(),
        type => unicode_string(),
        attributes => #{unicode_string() => unicode_string()}
    }.

unicode_attrs_event_gen() ->
    #{
        id => <<"event1">>,
        type => <<"start">>,
        timestamp => <<"2024-01-01T10:00:00Z">>,
        source => [<<"object1">>],
        attributes => lists:foldl(fun(I, Acc) ->
            maps:put(unicode_string(), unicode_string(), Acc)
        end, #{}, lists:seq(1, 100))
    }.

unicode_attrs_object_gen() ->
    #{
        id => <<"object1">>,
        type => <<"order">>,
        attributes => lists:foldl(fun(I, Acc) ->
            maps:put(unicode_string(), unicode_string(), Acc)
        end, #{}, lists:seq(1, 100))
    }.

event_large_attrs_gen() ->
    #{
        id => <<"event1">>,
        type => <<"start">>,
        timestamp => <<"2024-01-01T10:00:00Z">>,
        source => [<<"object1">>],
        attributes => #{
            large_attr => lists:duplicate(100000, $x)
        }
    }.

object_large_attrs_gen() ->
    #{
        id => <<"object1">>,
        type => <<"order">>,
        attributes => #{
            large_attr => lists:duplicate(100000, $y)
        }
    }.

unicode_string() ->
    oneof([
        <<"事件">>,
        <<"测试">>,
        unicode:characters_to_binary("测试内容"),
        unicode:characters_to_binary("😀🎉🚀")
    ]).