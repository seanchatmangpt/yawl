%% Test module for rust4pm NIF
-module(rust4pm_test).
-export([test_all/0, test_parse_ocel2_json/1, test_slim_link/2]).

-include_lib("eunit/include/eunit.hrl").

test_all() ->
    io:format("Testing rust4pm NIF functions~n"),

    % Test parse_ocel2_json
    test_parse_ocel2_json(),

    % Test slim_link
    test_slim_link(),

    % Test discover_oc_declare
    test_discover_oc_declare(),

    % Test token_replay
    test_token_replay(),

    io:format("All tests completed~n").

test_parse_ocel2_json() ->
    % Create sample OCEL2 JSON
    Ocel2Json = jsx:encode(#{<<"global_info">> => #{},
                           <<"events">> => [
                               #{<<"id">> => <<"event1">>,
                                 <<"timestamp">> => <<"2024-01-01T00:00:00Z">>,
                                 <<"activity">> => <<"A">>,
                                 <<"variant">> => <<"complete">>,
                                 <<"attributes">> => #{}}
                           ],
                           <<"objects">> => [
                               #{<<"id">> => <<"obj1">>,
                                 <<"type_name">> => <<"Document">>,
                                 <<"attributes">> => #{}}
                           ]}),

    case rust4pm:parse_ocel2_json(Ocel2Json) of
        {ok, Result} ->
            io:format("parse_ocel2_json test passed~n"),
            % Verify parsed result
            case jsx:is_obj(Result) of
                true -> ok;
                false -> error(parse_result_not_binary)
            end;
        {error, Error} ->
            io:format("parse_ocel2_json test failed: ~p~n", [Error]),
            error(parse_failed)
    end.

test_slim_link() ->
    Source = <<"source1">>,
    Target = <<"target1">>,

    case rust4pm:slim_link(Source, Target) of
        {ok, Result} ->
            io:format("slim_link test passed~n"),
            % Verify result structure
            case jsx:is_obj(Result) of
                true ->
                    case jsx:get_key(Result, <<"source">>) of
                        {ok, Source} -> ok;
                        _ -> error(source_mismatch)
                    end;
                false -> error(result_not_binary)
            end;
        {error, Error} ->
            io:format("slim_link test failed: ~p~n", [Error]),
            error(slim_link_failed)
    end.

test_discover_oc_declare() ->
    CaseId = <<"case123">>,
    EventType = <<"approve">>,
    Parameters = jsx:encode(#{threshold => 2, min_duration => 1000}),

    case rust4pm:discover_oc_declare(CaseId, EventType, Parameters) of
        {ok, Result} ->
            io:format("discover_oc_declare test passed~n"),
            % Verify result structure
            case jsx:is_obj(Result) of
                true ->
                    case jsx:get_key(Result, <<"case_id">>) of
                        {ok, CaseId} -> ok;
                        _ -> error(case_id_mismatch)
                    end;
                false -> error(result_not_binary)
            end;
        {error, Error} ->
            io:format("discover_oc_declare test failed: ~p~n", [Error]),
            error(discover_failed)
    end.

test_token_replay() ->
    Net = jsx:encode(#{nodes => [<<"A">>, <<"B">>], edges => []}),
    CaseId = <<"case456">>,

    case rust4pm:token_replay(Net, CaseId) of
        {ok, Result} ->
            io:format("token_replay test passed~n"),
            % Verify result structure
            case jsx:is_obj(Result) of
                true ->
                    case jsx:get_key(Result, <<"case_id">>) of
                        {ok, CaseId} -> ok;
                        _ -> error(case_id_mismatch)
                    end;
                false -> error(result_not_binary)
            end;
        {error, Error} ->
            io:format("token_replay test failed: ~p~n", [Error]),
            error(replay_failed)
    end.

% EUnit tests
parse_ocel2_json_test_() ->
    Ocel2Json = jsx:encode(#{<<"global_info">> => #{},
                           <<"events">> => [#{<<"id">> => <<"test">>}]}),
    [{"Test parse OCEL2 JSON",
      fun() ->
          case rust4pm:parse_ocel2_json(Ocel2Json) of
              {ok, _} -> ok;
              {error, _} = Error -> Error
          end
      end}].

slim_link_test_() ->
    [{"Test slim link",
      fun() ->
          case rust4pm:slim_link(<<"a">>, <<"b">>) of
              {ok, _} -> ok;
              {error, _} = Error -> Error
          end
      end}].