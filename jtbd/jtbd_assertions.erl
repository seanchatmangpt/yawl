-module(jtbd_assertions).
-export([
    assert_file_exists/1,
    assert_valid_json/1,
    assert_json_key/2,
    assert_json_array/2,
    assert_array_length/3,
    assert_number_range/4,
    assert_no_uuid/1
]).

-include_lib("kernel/include/logger.hrl").

assert_file_exists(Path) ->
    case filelib:is_file(Path) of
        true -> {ok, #{assertion => file_exists, path => Path}};
        false -> {error, #{assertion => file_exists, path => Path, reason => not_found}}
    end.

assert_valid_json(Path) ->
    {ok, Binary} = file:read_file(Path),
    try jsx:decode(Binary, [return_maps]) of
        _ -> {ok, #{assertion => valid_json, path => Path}}
    catch
        error:_ -> {error, #{assertion => valid_json, path => Path, reason => invalid_json}}
    end.

assert_json_key(Path, Key) ->
    {ok, Binary} = file:read_file(Path),
    Json = jsx:decode(Binary, [return_maps]),
    KeyBin = if is_atom(Key) -> atom_to_binary(Key); true -> Key end,
    case maps:is_key(KeyBin, Json) of
        true -> {ok, #{assertion => json_key, path => Path, key => Key}};
        false -> {error, #{assertion => json_key, path => Path, key => Key, reason => missing_key}}
    end.

assert_json_array(Path, Key) ->
    {ok, Binary} = file:read_file(Path),
    Json = jsx:decode(Binary, [return_maps]),
    KeyBin = if is_atom(Key) -> atom_to_binary(Key); true -> Key end,
    case maps:get(KeyBin, Json, undefined) of
        List when is_list(List) -> {ok, #{assertion => json_array, path => Path, key => Key, length => length(List)}};
        _ -> {error, #{assertion => json_array, path => Path, key => Key, reason => not_array}}
    end.

assert_array_length(Path, Key, Min) ->
    {ok, Binary} = file:read_file(Path),
    Json = jsx:decode(Binary, [return_maps]),
    KeyBin = if is_atom(Key) -> atom_to_binary(Key); true -> Key end,
    List = maps:get(KeyBin, Json, []),
    Len = length(List),
    if Len >= Min -> {ok, #{assertion => array_length, path => Path, key => Key, length => Len, min => Min}};
       true -> {error, #{assertion => array_length, path => Path, key => Key, length => Len, min => Min, reason => too_short}}
    end.

assert_number_range(Path, Key, Min, Max) ->
    {ok, Binary} = file:read_file(Path),
    Json = jsx:decode(Binary, [return_maps]),
    KeyBin = if is_atom(Key) -> atom_to_binary(Key); true -> Key end,
    Value = maps:get(KeyBin, Json),
    if Value >= Min, Value =< Max -> {ok, #{assertion => number_range, path => Path, key => Key, value => Value}};
       true -> {error, #{assertion => number_range, path => Path, key => Key, value => Value, min => Min, max => Max}}
    end.

assert_no_uuid(Path) ->
    {ok, Binary} = file:read_file(Path),
    Text = binary_to_list(Binary),
    UUIDPattern = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    case re:run(Text, UUIDPattern, [caseless]) of
        nomatch -> {ok, #{assertion => no_uuid, path => Path}};
        {match, _} -> {error, #{assertion => no_uuid, path => Path, reason => uuid_leaked}}
    end.

all_assertions_pass(Results) ->
    Failures = [R || R <- Results, element(1, R) =:= error],
    length(Failures) =:= 0.