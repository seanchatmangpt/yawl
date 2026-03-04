-module(simple_test).
-export([run/0]).

run() ->
  io:format("Running simple test...~n"),
  timer:sleep(1000),
  {ok, #{result => "success"}}.