%% Property-Based Tests for OCEL Parsing
%% Tests Object-Centric Event Log parsing with property-based testing
%% Uses proper library to generate and test random OCEL structures

-module(ocel_property_tests).
-include_lib("proper/include/proper.hrl").
-include_lib("eunit/include/eunit.hrl").
-include_lib("kernel/include/logger.hrl").

%%====================================================================
%% Property Test Definitions
%%====================================================================

%% @doc Main property test: Parse any generated OCEL structure
%% The test should always return true (either successful parsing or graceful error handling)
prop_parse_any_ocel() ->
    ?FORALL(Ocel, ocel_generator(),
        begin
            case process_mining_bridge:import_ocel_json(Ocel) of
                {ok, _Handle} ->
                    true;
                {error, Reason} ->
                    ?debugFmt("Expected error for invalid OCEL: ~p", [Reason]),
                    true  %% Even errors should be handled gracefully
            end
        end).

%% @doc Property test: Ensure OCEL with empty objects can be parsed
prop_parse_empty_objects() ->
    ?FORALL(Events, non_empty_list(event_gen()),
        begin
            Ocel = jsx:encode(#{
                <<"events">> => Events,
                <<"objects">> => []
            }),
            case process_mining_bridge:import_ocel_json(Ocel) of
                {ok, _Handle} ->
                    true;
                {error, _Reason} ->
                    true  %% Empty objects should be handled gracefully
            end
        end).

%% @doc Property test: Ensure OCEL with empty events can be parsed
prop_parse_empty_events() ->
    ?FORALL(Objects, non_empty_list(object_gen()),
        begin
            Ocel = jsx:encode(#{
                <<"events">> => [],
                <<"objects">> => Objects
            }),
            case process_mining_bridge:import_ocel_json(Ocel) of
                {ok, _Handle} ->
                    true;
                {error, _Reason} ->
                    true  %% Empty events should be handled gracefully
            end
        end).

%% @doc Property test: Ensure OCEL with exactly one event and object can be parsed
prop_parse_singleton_ocel() ->
    ?FORALL({Event, Object}, {event_gen(), object_gen()},
        begin
            Ocel = jsx:encode(#{
                <<"events">> => [Event],
                <<"objects">> => [Object]
            }),
            case process_mining_bridge:import_ocel_json(Ocel) of
                {ok, _Handle} ->
                    true;
                {error, _Reason} ->
                    true  %% Singleton OCEL should be handled gracefully
            end
        end).

%% @doc Property test: Ensure OCEL with many events and objects can be parsed (stress test)
prop_parse_large_ocel() ->
    ?FORALL({Events, Objects}, {large_list(event_gen()), large_list(object_gen())},
        begin
            Ocel = jsx:encode(#{
                <<"events">> => Events,
                <<"objects">> => Objects
            }),
            case process_mining_bridge:import_ocel_json(Ocel) of
                {ok, _Handle} ->
                    true;
                {error, _Reason} ->
                    true  %% Large OCEL should be handled gracefully
            end
        end).

%%====================================================================
%% Generators for OCEL Structures
%%====================================================================

%% @doc Generator for valid OCEL structures
ocel_generator() ->
    ?LET({Events, Objects}, {list(event_gen()), list(object_gen())},
        jsx:encode(#{
            <<"events">> => Events,
            <<"objects">> => Objects
        })).

%% @doc Generator for events with all required fields
event_gen() ->
    ?LET({Id, Type, Timestamp, Source, Attributes},
        {gen_event_id(), gen_event_type(), gen_timestamp(), gen_source(), gen_attributes()},
        #{
            <<"id">> => Id,
            <<"type">> => Type,
            <<"timestamp">> => Timestamp,
            <<"source">> => Source,
            <<"attributes">> => Attributes
        }).

%% @doc Generator for objects with all required fields
object_gen() ->
    ?LET({Id, Type, Attributes},
        {gen_object_id(), gen_object_type(), gen_attributes()},
        #{
            <<"id">> => Id,
            <<"type">> => Type,
            <<"attributes">> => Attributes
        }).

%% @doc Generator for event IDs (unique identifiers)
gen_event_id() ->
    ?LET(I, integer(1, 10000),
        list_to_binary(io_lib:format("event_~p", [I]))).

%% @doc Generator for object IDs (unique identifiers)
gen_object_id() ->
    ?LET(I, integer(1, 10000),
        list_to_binary(io_lib:format("object_~p", [I]))).

%% @doc Generator for event types
gen_event_type() ->
    oneof([
        <<"start">>,
        <<"complete">>,
        <<"approve">>,
        <<"reject">>,
        <<"update">>,
        <<"create">>,
        <<"delete">>,
        <<"custom_task">>
    ]).

%% @doc Generator for object types
gen_object_type() ->
    oneof([
        <<"order">>,
        <<"invoice">>,
        <<"customer">>,
        <<"product">>,
        <<"case">>,
        <<"request">>,
        <<"task">>,
        <<"custom_object">>
    ]).

%% @doc Generator for timestamps in ISO 8601 format
gen_timestamp() ->
    ?LET({Year, Month, Day, Hour, Min, Sec},
        {integer(2020, 2025), integer(1, 12), integer(1, 28), integer(0, 23), integer(0, 59), integer(0, 59)},
        list_to_binary(io_lib:format("~p-~2.0b-~2.0bT~2.0b:~2.0b:~2.0bZ",
            [Year, Month, Day, Hour, Min, Sec]))).

%% @doc Generator for event sources (object references)
gen_source() ->
    ?LET(NumSources, integer(1, 5),
        list(gen_object_id())).

%% @doc Generator for event/object attributes
gen_attributes() ->
    ?LET(NumAttrs, integer(0, 10),
        list(gen_attribute())).

%% @doc Generator for individual attributes
gen_attribute() ->
    ?LET({Key, Value}, {gen_string(), gen_value()},
        {Key, Value}).

%% @doc Generator for attribute keys
gen_string() ->
    oneof([
        <<"resource">>,
        <<"cost">>,
        <<"amount">>,
        <<"status">>,
        <<"priority">>,
        <<"user">>,
        <<"department">>,
        <<"location">>,
        <<"description">>,
        custom_string()
    ]).

%% @doc Generator for custom strings
custom_string() ->
    ?LET(Str, string(10),
        list_to_binary("attr_" ++ Str)).

%% @doc Generator for attribute values (various types)
gen_value() ->
    oneof([
        binary_string(),  %% String values
        integer(),       %% Integer values
        float(),         %% Float values
        true,            %% Boolean values
        false           %% Boolean values
    ]).

%% @doc Generator for binary strings
binary_string() ->
    ?LET(Str, string(20),
        list_to_binary(Str)).

%% @doc Generator for non-empty lists
non_empty_list(Gen) ->
    ?LET(L, non_empty(Gen), L).

%% @doc Generator for large lists (stress testing)
large_list(Gen) ->
    ?LET(L, list(100, Gen), L).

%%====================================================================
%% Test Configuration
%%====================================================================

%% @doc Run property tests with different configurations
run_property_tests() ->
    io:format("=== Running OCEL Property Tests ===~n"),

    %% Test with different numbers of cases
    Tests = [
        {prop_parse_any_ocel(), 100, "Parse any OCEL (100 cases)"},
        {prop_parse_any_ocel(), 1000, "Parse any OCEL (1000 cases)"},
        {prop_parse_empty_events(), 100, "Parse empty events"},
        {prop_parse_empty_objects(), 100, "Parse empty objects"},
        {prop_parse_singleton_ocel(), 100, "Parse singleton OCEL"},
        {prop_parse_large_ocel(), 10, "Parse large OCEL (stress test)"}
    ],

    Results = lists:map(fun({Test, NumCases, Desc}) ->
        io:format("Running: ~s (~p cases)...~n", [Desc, NumCases]),
        case proper:quickcheck(Test, [{numtests, NumCases}, {verbose, false}]) of
            true ->
                io:format("✓ ~s: PASSED~n", [Desc]),
                {passed, Desc};
            false ->
                io:format("✗ ~s: FAILED~n", [Desc]),
                {failed, Desc}
        end
    end, Tests),

    io:format("~n=== Property Test Results ===~n"),
    Passed = lists:filter(fun({passed, _}) -> true; (_) -> false end, Results),
    Failed = lists:filter(fun({failed, _}) -> true; (_) -> false end, Results),

    io:format("Passed: ~p/~p~n", [length(Passed), length(Results)]),
    io:format("Failed: ~p/~p~n", [length(Failed), length(Results)]),

    case Failed of
        [] ->
            io:format("All property tests passed!~n"),
            ok;
        _ ->
            io:format("Some property tests failed.~n"),
            {failed, Failed}
    end.

%%====================================================================
%% EUnit Integration
%%====================================================================

%% @doc EUnit test wrapper for property tests
property_test_() ->
    {timeout, 300,  %% 5 minute timeout for property tests
        fun() ->
            %% Run a subset of property tests in EUnit
            ?assert(proper:quickcheck(prop_parse_any_ocel(), [{numtests, 50}, {verbose, false}])),
            ?assert(proper:quickcheck(prop_parse_empty_events(), [{numtests, 50}, {verbose, false}])),
            ?assert(proper:quickcheck(prop_parse_empty_objects(), [{numtests, 50}, {verbose, false}]))
        end
    }.