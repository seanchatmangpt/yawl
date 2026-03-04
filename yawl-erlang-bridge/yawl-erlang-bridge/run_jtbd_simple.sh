#!/bin/bash

# Set up environment
export ERL_LIBS="$PWD/deps"
cd /tmp/jtbd/input

# Start Erlang and run tests
erl -pa "$PWD/ebin" -pa "$PWD/test" -e "
  % Load the required modules
  code:add_patha(\"ebin\"),
  code:add_patha(\"test\"),
  
  % Compile jtbd modules
  compile([jtbd_utils]),
  compile([jtbd_runner]),
  
  % Create output directory
  filelib:ensure_dir(\"/tmp/jtbd/output/\"),
  
  % Copy test files to test directory
  case file:copy(\"/tmp/jtbd/input/pi-sprint-ocel.json\", \"test/pi-sprint-ocel.json\") of
    ok -> ok;
    {error, _} -> file:write_file(\"test/pi-sprint-ocel.json\", file:read_file(\"/tmp/jtbd/input/pi-sprint-ocel.json\"))
  end,
  
  % Run tests
  Results = jtbd_runner:run_all(),
  
  % Print results
  io:format(\"~n=== JTBD Test Results ===~n\"),
  io:format(\"Passed: ~p~n\", [maps:get(passed, Results)]),
  io:format(\"Failed: ~p~n\", [maps:get(failed, Results)]),
  
  % Print detailed results
  ResultsList = maps:get(results, Results),
  io:format(\"~nDetailed Results:~n\"),
  [begin
    TestName = atom_to_list(T),
    case R of
      {ok, ResultData} -> io:format(\"  ✓ ~s: ~p~n\", [TestName, ResultData]);
      {error, Error} -> io:format(\"  ✗ ~s: ~p~n\", [TestName, Error])
    end
  end || {T, R} <- ResultsList],
  
  % Exit with code based on results
  case maps:get(failed, Results) of
    0 -> halt(0);
    _ -> halt(1)
  end.
"