%%%-------------------------------------------------------------------
%%% @doc JTBD Test Runner - Runs all 5 Jobs To Be Done tests
%%%
%%% Usage:
%%%   1. Compile: erlc -o ../ebin *.erl
%%%   2. Run: erl -pa ../ebin -eval "jtbd_runner:run_all()." -noshell
%%%
%%% Or via Makefile:
%%%   make jtbd
%%% @end
%%%-------------------------------------------------------------------
-module(jtbd_runner).

%% API
-export([run_all/0, run/1, summary/1]).

-define(JTBD_TESTS, [
    {jtbd_1_dfg_discovery, "OCEL → DFG Discovery"},
    {jtbd_2_conformance, "OCEL → Petri Net → Conformance"},
    {jtbd_3_constraints, "OC-DECLARE Constraints"},
    {jtbd_4_qlever_accumulation, "Loop Accumulation with Embedded QLever"},
    {jtbd_5_fault_isolation, "Fault Isolation / Crash Recovery"}
]).

%%%===================================================================
%%% API
%%%===================================================================

%% @doc Run all 5 JTBD tests and return results
run_all() ->
    io:format("~n", []),
    io:format("╔══════════════════════════════════════════════════════════════╗~n", []),
    io:format("║           YAWL Process Mining Bridge - JTBD Tests            ║~n", []),
    io:format("║                  5 Jobs To Be Done                           ║~n", []),
    io:format("╚══════════════════════════════════════════════════════════════╝~n", []),
    io:format("~n", []),

    %% Ensure output directories exist
    ok = filelib:ensure_dir("/tmp/jtbd/output/."),

    %% Run each test
    Results = lists:map(fun({Module, Description}) ->
        run_test(Module, Description)
    end, ?JTBD_TESTS),

    %% Print summary
    Summary = summary(Results),
    io:format("~n~s", [Summary]),

    %% Return results map
    #{
        total => length(Results),
        passed => length([1 || #{status := passed} <- Results]),
        failed => length([1 || #{status := failed} <- Results]),
        results => Results
    }.

%% @doc Run a single test by module name
run(TestName) when is_atom(TestName) ->
    Description = case lists:keyfind(TestName, 1, ?JTBD_TESTS) of
        {_, Desc} -> Desc;
        false -> atom_to_list(TestName)
    end,
    run_test(TestName, Description).

%%%===================================================================
%%% Internal Functions
%%%===================================================================

%% @private
run_test(Module, Description) ->
    io:format("▶ ~s~n", [Description]),
    io:format("  Module: ~p~n", [Module]),
    StartTime = erlang:monotonic_time(millisecond),
    EndTime = 0,
    Duration = 0,

    try
        case Module:run() of
            {ok, Result} ->
                EndTime = erlang:monotonic_time(millisecond),
                Duration = EndTime - StartTime,
                io:format("  ✓ PASSED (~pms)~n~n", [Duration]),
                #{
                    module => Module,
                    description => Description,
                    status => passed,
                    duration_ms => Duration,
                    result => Result
                };
            {error, Reason} ->
                EndTime = erlang:monotonic_time(millisecond),
                Duration = EndTime - StartTime,
                io:format("  ✗ FAILED (~pms): ~p~n~n", [Duration, Reason]),
                #{
                    module => Module,
                    description => Description,
                    status => failed,
                    duration_ms => Duration,
                    error => Reason
                }
        end
    catch
        Type:Error:Stacktrace ->
            EndTime = erlang:monotonic_time(millisecond),
            Duration = EndTime - StartTime,
            io:format("  ✗ EXCEPTION (~pms): ~p:~p~n", [Duration, Type, Error]),
            io:format("    Stack: ~p~n~n", [hd(Stacktrace)]),
            #{
                module => Module,
                description => Description,
                status => exception,
                duration_ms => Duration,
                error => {Type, Error},
                stacktrace => Stacktrace
            }
    end.

%% @doc Generate a summary string from results
summary(Results) ->
    Passed = length([1 || #{status := passed} <- Results]),
    Failed = length([1 || #{status := Status} <- Results, Status =/= passed]),
    Total = length(Results),

    PassRate = case Total of
        0 -> 0.0;
        _ -> (Passed / Total) * 100
    end,

    io_lib:format(
        "╔══════════════════════════════════════════════════════════════╗~n"
        "║                      JTBD TEST SUMMARY                       ║~n"
        "╠══════════════════════════════════════════════════════════════╣~n"
        "║  Total:   ~2B    Passed: ~2B    Failed: ~2B    Rate: ~5.1f%~s~n"
        "╚══════════════════════════════════════════════════════════════╝~n",
        [Total, Passed, Failed, PassRate, "%"]
    ).
