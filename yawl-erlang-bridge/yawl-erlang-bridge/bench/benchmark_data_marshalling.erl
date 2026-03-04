%% @doc Benchmark data marshalling performance between Erlang and Rust
%% Focus on the cost of serializing/deserializing different data types
-module(benchmark_data_marshalling).

-export([start/0, start/1]).
-export([
    echo_json/1,
    echo_term/1,
    echo_binary/1,
    echo_ocel_event/1,
    large_list_transfer/1
]).

%%====================================================================
%% API Functions
%%====================================================================

%% @doc Start benchmark with default iterations (10,000)
start() ->
    start(10000).

%% @doc Start benchmark with specified iterations
-spec start(integer()) -> ok.
start(Iterations) ->
    io:format("=== Data Marshalling Benchmark ===~n"),
    io:format("Iterations: ~p~n~n", [Iterations]),
    
    % Test JSON marshalling
    JsonEvent = generate_ocel_event(),
    io:format("1. JSON event marshalling...~n"),
    {JsonTime, JsonOps} = json_transfer_benchmark(Iterations, JsonEvent),
    io:format("   JSON transfer avg: ~.3f μs/op (JSON size: ~B bytes)~n", [
        (JsonTime * 1000000) / JsonOps,
        byte_size(JsonEvent)
    ]),
    
    % Test term marshalling
    TermEvent = generate_ocel_event_term(),
    io:format("2. Erlang term marshalling...~n"),
    {TermTime, TermOps} = term_transfer_benchmark(Iterations, TermEvent),
    io:format("   Term transfer avg: ~.3f μs/op~n", [
        (TermTime * 1000000) / TermOps
    ]),
    
    % Test binary transfers
    BinaryData = crypto:strong_rand_bytes(1024), % 1KB random binary
    io:format("3. Binary transfer (1KB)...~n"),
    {BinTime, BinOps} = binary_transfer_benchmark(Iterations, BinaryData),
    io:format("   Binary transfer avg: ~.3f μs/op (~B bytes)~n", [
        (BinTime * 1000000) / BinOps,
        byte_size(BinaryData)
    ]),
    
    % Test large list transfers
    LargeList = lists:seq(1, 1000), % 1000 element list
    io:format("4. Large list transfer (1000 elements)...~n"),
    {ListTime, ListOps} = large_list_transfer_benchmark(Iterations, LargeList),
    io:format("   Large list transfer avg: ~.3f μs/op~n", [
        (ListTime * 1000000) / ListOps
    ]),
    
    % Calculate bandwidth
    JsonSizeMB = byte_size(JsonEvent) / 1024 / 1024,
    Bandwidth = (JsonOps * JsonSizeMB) / (JsonTime / 1000000.0),
    io:format("   JSON bandwidth: ~.2f MB/s~n", [Bandwidth]),
    
    io:format("=== Benchmark Complete ===~n"),
    ok.

%%====================================================================
%% NIF Stubs
%%====================================================================

echo_json(_Json) ->
    error(nif_not_loaded).

echo_term(_Term) ->
    error(nif_not_loaded).

echo_binary(_Binary) ->
    error(nif_not_loaded).

echo_ocel_event(_Event) ->
    error(nif_not_loaded).

large_list_transfer(_List) ->
    error(nif_not_loaded).

%%====================================================================
%% Benchmark Functions
%%====================================================================

json_transfer_benchmark(Iterations, JsonEvent) ->
    Start = erlang:monotonic_time(microsecond),
    
    do_json_calls(Iterations, JsonEvent),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_json_calls(0, _Json) ->
    ok;
do_json_calls(N, JsonVal) ->
    echo_json(JsonVal),
    do_json_calls(N - 1, JsonVal).

term_transfer_benchmark(Iterations, TermEvent) ->
    Start = erlang:monotonic_time(microsecond),
    
    do_term_calls(Iterations, TermEvent),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_term_calls(0, _Term) ->
    ok;
do_term_calls(N, TermVal) ->
    echo_term(TermVal),
    do_term_calls(N - 1, TermVal).

binary_transfer_benchmark(Iterations, BinaryData) ->
    Start = erlang:monotonic_time(microsecond),
    
    do_binary_calls(Iterations, BinaryData),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_binary_calls(0, _Binary) ->
    ok;
do_binary_calls(N, BinVal) ->
    echo_binary(BinVal),
    do_binary_calls(N - 1, BinVal).

large_list_transfer_benchmark(Iterations, LargeList) ->
    Start = erlang:monotonic_time(microsecond),
    
    do_large_list_calls(Iterations, LargeList),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_large_list_calls(0, _List) ->
    ok;
do_large_list_calls(N, ListVal) ->
    large_list_transfer(ListVal),
    do_large_list_calls(N - 1, ListVal).

%%====================================================================
%% Test Data Generation
%%====================================================================

generate_ocel_event() ->
    % Generate a realistic OCEL event as JSON string
    Event = #{
        event_id => "evt_" + integer_to_list(rand:uniform(100000)),
        case_id => "case_" + integer_to_list(rand:uniform(10000)),
        activity => lists:nth(rand:uniform(5), ["Start", "Approve", "Review", "Complete", "Reject"]),
        timestamp => iso8601:format(calendar:universal_time()),
        object => "obj_" + integer_to_list(rand:uniform(1000)),
        attributes => #{
            cost => rand:uniform(1000) * 100.0,
            approver => "user_" + integer_to_list(rand:uniform(100)),
            priority => lists:nth(rand:uniform(3), ["low", "medium", "high"])
        }
    },
    jiffy:encode(Event).

generate_ocel_event_term() ->
    % Same as JSON but as Erlang term
    Event = #{
        event_id => "evt_" ++ integer_to_list(rand:uniform(100000)),
        case_id => "case_" ++ integer_to_list(rand:uniform(10000)),
        activity => lists:nth(rand:uniform(5), ["Start", "Approve", "Review", "Complete", "Reject"]),
        timestamp => erlang:timestamp(),
        object => "obj_" ++ integer_to_list(rand:uniform(1000)),
        attributes => #{
            cost => rand:uniform(1000) * 100.0,
            approver => "user_" ++ integer_to_list(rand:uniform(100)),
            priority => lists:nth(rand:uniform(3), ["low", "medium", "high"])
        }
    },
    Event.
