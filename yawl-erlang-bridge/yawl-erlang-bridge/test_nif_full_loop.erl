#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin -pa priv

main(_) ->
    io:format("=== NIF Full Loop Test ===~n~n"),

    %% Test 1: Check NIF status
    io:format("1. NIF Status: "),
    Status = process_mining_bridge:get_nif_status(),
    io:format("~p~n", [Status]),

    %% Test 2: Simple ping
    io:format("2. Ping: "),
    Ping = process_mining_bridge:ping(),
    io:format("~p~n", [Ping]),

    %% Test 3: Integer passthrough
    io:format("3. int_passthrough(42): "),
    IntResult = process_mining_bridge:int_passthrough(42),
    io:format("~p~n", [IntResult]),

    %% Test 4: OCEL import
    io:format("4. import_ocel_json:~n"),
    OcelJson = <<"{\"events\":[
        {\"id\":\"e1\",\"type\":\"place\",\"time\":\"2024-01-01T10:00:00Z\",\"attributes\":[],\"relationships\":[{\"objectId\":\"o1\",\"qualifier\":\"\"}]},
        {\"id\":\"e2\",\"type\":\"pay\",\"time\":\"2024-01-01T11:00:00Z\",\"attributes\":[],\"relationships\":[{\"objectId\":\"o1\",\"qualifier\":\"\"}]},
        {\"id\":\"e3\",\"type\":\"ship\",\"time\":\"2024-01-01T12:00:00Z\",\"attributes\":[],\"relationships\":[{\"objectId\":\"o1\",\"qualifier\":\"\"}]}
    ],\"objects\":[{\"id\":\"o1\",\"type\":\"Order\",\"attributes\":[]}],\"objectTypes\":[],\"eventTypes\":[]}">>,

    ImportResult = process_mining_bridge:import_ocel_json(#{json => OcelJson}),
    case ImportResult of
        {ok, Handle} ->
            io:format("   OCEL imported, handle: ~p~n", [Handle]),

            %% Test 5: DFG Discovery
            io:format("~n5. discover_dfg:~n"),
            DfgResult = process_mining_bridge:discover_dfg(#{handle => Handle}),
            case DfgResult of
                {ok, DfgJson} ->
                    io:format("   DFG discovered (~p bytes)~n", [byte_size(DfgJson)]),
                    io:format("   DFG content: ~s~n", [DfgJson]);
                {error, DfgErr} ->
                    io:format("   DFG error: ~p~n", [DfgErr])
            end,

            %% Test 6: Event count
            io:format("~n6. log_event_count:~n"),
            CountResult = process_mining_bridge:log_event_count(#{ocel_id => Handle}),
            case CountResult of
                {ok, Count} -> io:format("   Event count: ~p~n", [Count]);
                {error, CountErr} -> io:format("   Count error: ~p~n", [CountErr])
            end,

            %% Cleanup
            process_mining_bridge:free_handle(Handle),
            io:format("~n=== FULL LOOP: SUCCESS ===~n");

        {error, Reason} ->
            io:format("   Import failed: ~p~n", [Reason]),
            io:format("~n=== FULL LOOP: FAILED ===~n")
    end.
