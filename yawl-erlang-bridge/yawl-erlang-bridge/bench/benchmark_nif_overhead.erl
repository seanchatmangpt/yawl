%% @doc Benchmark pure NIF call overhead without any computation
%% This module measures the cost of crossing the Erlang-Rust boundary
-module(benchmark_nif_overhead).

-export([start/0, start/1]).
-export([
    empty_call/0,
    int_passthrough/1,
    atom_passthrough/1,
    small_list_passthrough/1,
    tuple_passthrough/1
]).

%%====================================================================
%% API Functions
%%====================================================================

%% @doc Start benchmark with default iterations (100,000)
start() ->
    start(100000).

%% @doc Start benchmark with specified iterations
-spec start(integer()) -> ok.
start(Iterations) ->
    io:format("=== NIF Call Overhead Benchmark ===~n"),
    io:format("Iterations: ~p~n~n", [Iterations]),
    
    % Test empty calls (minimal overhead)
    io:format("1. Empty NIF call benchmark...~n"),
    {EmptyTime, EmptyOps} = empty_call_benchmark(Iterations),
    io:format("   Empty call avg: ~.3f μs/op~n", [
        (EmptyTime * 1000000) / EmptyOps
    ]),
    
    % Test integer transfers
    io:format("2. Integer transfer benchmark...~n"),
    {IntTime, IntOps} = int_transfer_benchmark(Iterations),
    io:format("   Integer transfer avg: ~.3f μs/op~n", [
        (IntTime * 1000000) / IntOps
    ]),
    
    % Test atom transfers
    io:format("3. Atom transfer benchmark...~n"),
    {AtomTime, AtomOps} = atom_transfer_benchmark(Iterations),
    io:format("   Atom transfer avg: ~.3f μs/op~n", [
        (AtomTime * 1000000) / AtomOps
    ]),
    
    % Test list transfers
    io:format("4. List transfer benchmark...~n"),
    {ListTime, ListOps} = list_transfer_benchmark(Iterations),
    io:format("   List transfer avg: ~.3f μs/op~n", [
        (ListTime * 1000000) / ListOps
    ]),
    
    % Test tuple transfers
    io:format("5. Tuple transfer benchmark...~n"),
    {TupleTime, TupleOps} = tuple_transfer_benchmark(Iterations),
    io:format("   Tuple transfer avg: ~.3f μs/op~n", [
        (TupleTime * 1000000) / TupleOps
    ]),
    
    io:format("=== Benchmark Complete ===~n"),
    ok.

%%====================================================================
%% NIF Stubs (these will be implemented in Rust)
%%====================================================================

empty_call() ->
    error(nif_not_loaded).

int_passthrough(_N) ->
    error(nif_not_loaded).

atom_passthrough(_Atom) ->
    error(nif_not_loaded).

small_list_passthrough(_List) ->
    error(nif_not_loaded).

tuple_passthrough(_Tuple) ->
    error(nif_not_loaded).

%%====================================================================
%% Benchmark Functions
%%====================================================================

empty_call_benchmark(Iterations) ->
    Start = erlang:monotonic_time(microsecond),
    
    % Perform iterations
    do_empty_calls(Iterations),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_empty_calls(0) ->
    ok;
do_empty_calls(N) ->
    empty_call(),
    do_empty_calls(N - 1).

int_transfer_benchmark(Iterations) ->
    TestInt = 42,
    Start = erlang:monotonic_time(microsecond),
    
    do_int_calls(Iterations, TestInt),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_int_calls(0, _N) ->
    ok;
do_int_calls(N, IntVal) ->
    int_passthrough(IntVal),
    do_int_calls(N - 1, IntVal).

atom_transfer_benchmark(Iterations) ->
    TestAtom = test_atom,
    Start = erlang:monotonic_time(microsecond),
    
    do_atom_calls(Iterations, TestAtom),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_atom_calls(0, _Atom) ->
    ok;
do_atom_calls(N, AtomVal) ->
    atom_passthrough(AtomVal),
    do_atom_calls(N - 1, AtomVal).

list_transfer_benchmark(Iterations) ->
    TestList = [a, b, c, d, e],
    Start = erlang:monotonic_time(microsecond),
    
    do_list_calls(Iterations, TestList),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_list_calls(0, _List) ->
    ok;
do_list_calls(N, ListVal) ->
    small_list_passthrough(ListVal),
    do_list_calls(N - 1, ListVal).

tuple_transfer_benchmark(Iterations) ->
    TestTuple = {a, b, c, d, e},
    Start = erlang:monotonic_time(microsecond),
    
    do_tuple_calls(Iterations, TestTuple),
    
    End = erlang:monotonic_time(microsecond),
    {(End - Start) / 1000000.0, Iterations}.

do_tuple_calls(0, _Tuple) ->
    ok;
do_tuple_calls(N, TupleVal) ->
    tuple_passthrough(TupleVal),
    do_tuple_calls(N - 1, TupleVal).
