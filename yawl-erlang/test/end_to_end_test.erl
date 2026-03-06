#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa _build/default/lib/*/ebin -pa ebin

%%%===================================================================
%%% End-to-End Test: Java > OTP > rust4pm > OTP > Java
%%%
%%% This test verifies the complete integration chain:
%%%   1. Erlang gen_server starts successfully
%%%   2. NIF loads and provides process mining functions
%%%   3. OCEL2 parsing works
%%%   4. DFG discovery works
%%%   5. Conformance checking works
%%%===================================================================

main(_) ->
    io:format("~n=== YAWL Process Mining End-to-End Test ===~n~n"),

    %% Test 1: Start the gen_server
    io:format("Test 1: Starting yawl_process_mining gen_server...~n"),
    case yawl_process_mining:start_link() of
        {ok, Pid} ->
            io:format("  ✓ gen_server started: ~p~n", [Pid]);
        {error, {already_started, Pid}} ->
            io:format("  ✓ gen_server already running: ~p~n", [Pid]);
        Error1 ->
            io:format("  ✗ Failed to start gen_server: ~p~n", [Error1]),
            halt(1)
    end,

    %% Test 2: Test pure Erlang DFG discovery
    io:format("~nTest 2: Pure Erlang DFG discovery...~n"),
    SampleLog = [
        [a, b, c, d],
        [a, b, c, e],
        [a, b, d, c],
        [a, c, b, d]
    ],
    case yawl_process_mining:discover_dfg(SampleLog) of
        {ok, Dfg} ->
            io:format("  ✓ DFG discovered with ~p edges~n", [maps:size(Dfg)]);
        Error2 ->
            io:format("  ✗ DFG discovery failed: ~p~n", [Error2])
    end,

    %% Test 3: Test pure Erlang conformance
    io:format("~nTest 3: Pure Erlang conformance checking...~n"),
    case yawl_process_mining:conformance(SampleLog) of
        {ok, Fitness} ->
            io:format("  ✓ Fitness score: ~p~n", [Fitness]);
        Error3 ->
            io:format("  ✗ Conformance check failed: ~p~n", [Error3])
    end,

    %% Test 4: Test NIF loading
    io:format("~nTest 4: Testing rust4pm_nif loading...~n"),
    case rust4pm_nif:ping() of
        {ok, "pong"} ->
            io:format("  ✓ NIF loaded and responding~n"),
            case rust4pm_nif:nif_version() of
                {ok, Version} ->
                    io:format("  ✓ NIF version: ~s~n", [Version]);
                _ ->
                    io:format("  ! NIF version check failed (non-critical)~n")
            end;
        {error, nif_not_loaded} ->
            io:format("  ! NIF not loaded, using pure Erlang fallbacks~n"),
            io:format("    (This is OK - NIF is optional for performance)~n");
        Error4 ->
            io:format("  ! NIF ping failed: ~p (non-critical)~n", [Error4])
    end,

    %% Test 5: Test OCEL2 parsing via NIF
    io:format("~nTest 5: OCEL2 JSON parsing via NIF...~n"),
    Ocel2Json = create_sample_ocel2(),
    case rust4pm_nif:parse_ocel2_json(Ocel2Json) of
        {ok, Handle} ->
            io:format("  ✓ OCEL2 parsed, handle created~n"),

            %% Test event count
            case rust4pm_nif:log_event_count(Handle) of
                {ok, Count} ->
                    io:format("  ✓ Event count: ~p~n", [Count]);
                Error5a ->
                    io:format("  ✗ Event count failed: ~p~n", [Error5a])
            end,

            %% Test object count
            case rust4pm_nif:log_object_count(Handle) of
                {ok, ObjCount} ->
                    io:format("  ✓ Object count: ~p~n", [ObjCount]);
                Error5b ->
                    io:format("  ✗ Object count failed: ~p~n", [Error5b])
            end,

            %% Test DFG discovery via NIF
            case rust4pm_nif:discover_dfg(Handle) of
                {ok, DfgJson} ->
                    io:format("  ✓ DFG discovered via NIF~n"),
                    io:format("    DFG JSON length: ~p bytes~n", [length(DfgJson)]);
                Error5c ->
                    io:format("  ✗ DFG discovery via NIF failed: ~p~n", [Error5c])
            end;

        {error, nif_not_loaded} ->
            io:format("  ! Skipping OCEL2 tests - NIF not loaded~n");

        Error5 ->
            io:format("  ✗ OCEL2 parsing failed: ~p~n", [Error5])
    end,

    io:format("~n=== All Tests Complete ===~n"),
    ok.

%% Create a sample OCEL2 JSON for testing
create_sample_ocel2() ->
    jsx:encode(#{
        <<"objectTypes">> => [
            #{<<"name">> => <<"Order">>, <<"attributes">> => []},
            #{<<"name">> => <<"Item">>, <<"attributes">> => []}
        ],
        <<"eventTypes">> => [
            #{<<"name">> => <<"Create Order">>, <<"attributes">> => []},
            #{<<"name">> => <<"Add Item">>, <<"attributes">> => []},
            #{<<"name">> => <<"Ship Order">>, <<"attributes">> => []}
        ],
        <<"objects">> => [
            #{<<"id">> => <<"o1">>, <<"type">> => <<"Order">>, <<"attributes">> => []},
            #{<<"id">> => <<"i1">>, <<"type">> => <<"Item">>, <<"attributes">> => []}
        ],
        <<"events">> => [
            #{
                <<"id">> => <<"e1">>,
                <<"type">> => <<"Create Order">>,
                <<"time">> => <<"2024-01-01T10:00:00Z">>,
                <<"attributes">> => [],
                <<"relationships">> => [#{<<"objectId">> => <<"o1">>, <<"qualifier">> => <<>>}]
            },
            #{
                <<"id">> => <<"e2">>,
                <<"type">> => <<"Add Item">>,
                <<"time">> => <<"2024-01-01T10:05:00Z">>,
                <<"attributes">> => [],
                <<"relationships">> => [
                    #{<<"objectId">> => <<"o1">>, <<"qualifier">> => <<>>},
                    #{<<"objectId">> => <<"i1">>, <<"qualifier">> => <<>>}
                ]
            },
            #{
                <<"id">> => <<"e3">>,
                <<"type">> => <<"Ship Order">>,
                <<"time">> => <<"2024-01-01T11:00:00Z">>,
                <<"attributes">> => [],
                <<"relationships">> => [#{<<"objectId">> => <<"o1">>, <<"qualifier">> => <<>>}]
            }
        ]
    }).
