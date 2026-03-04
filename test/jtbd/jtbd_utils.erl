-module(jtbd_utils).
-export([read_input_file/1, write_output_file/2, timestamp_iso8601/0]).

read_input_file(Filename) ->
  Path = "/tmp/jtbd/input/" ++ Filename,
  {ok, Binary} = file:read_file(Path),
  Binary.

write_output_file(Filename, Content) ->
  Path = "/tmp/jtbd/output/" ++ Filename,
  ok = file:write_file(Path, Content),
  Path.

timestamp_iso8601() ->
  calendar:system_time_to_rfc3339(erlang:system_time(second)).
