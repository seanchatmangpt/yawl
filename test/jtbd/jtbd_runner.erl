-module(jtbd_runner).
-export([run_all/0, run/1]).

run_all() ->
  Tests = [jtbd_1_dfg_discovery, jtbd_2_conformance, jtbd_3_constraints,
           jtbd_4_qlever_accumulation, jtbd_5_fault_isolation],
  Results = [{T, catch T:run()} || T <- Tests],
  Passed = length([ok || {_, {ok, _}} <- Results]),
  Failed = length(Results) - Passed,
  #{passed => Passed, failed => Failed, results => Results}.

run(TestName) ->
  TestName:run().
