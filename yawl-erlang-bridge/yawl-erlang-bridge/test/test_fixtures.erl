%% Test Fixtures for Process Mining Bridge Tests
%% Provides sample OCEL data, XES data, and utility functions
%% Enhanced with property test support

-module(test_fixtures).
-include_lib("kernel/include/logger.hrl").

%% API
-export([
    %% Original fixtures
    sample_ocel_json/0,
    sample_ocel_json/1,
    invalid_ocel_json/0,
    empty_ocel_json/0,
    sample_xes_content/0,
    invalid_xes_content/0,
    create_test_file/2,
    create_temp_file/2,
    cleanup_temp_file/1,
    test_file_path/1,
    get_sample_log_path/0,
    get_sample_ocel_path/0,

    %% New property test fixtures
    minimal_ocel_json/0,
    maximal_ocel_json/0,
    edge_case_ocel_json/0,
    malformed_ocel_json/0,
    ocel_with_null_values/0,
    ocel_with_unicode/0,
    generate_ocel_with_events/2,  %% NumEvents, NumObjects
    generate_ocel_with_attributes/3,  %% NumEvents, NumObjects, NumAttrs
    stress_test_ocel/0
]).

%%====================================================================
%% Original Test Fixtures
%%====================================================================

%% Sample OCEL JSON content (valid)
sample_ocel_json() ->
    <<"
{
    \"events\": [
        {
            \"id\": \"event1\",
            \"type\": \"start\",
            \"timestamp\": \"2024-01-01T10:00:00Z\",
            \"source\": [\"object1\"],
            \"attributes\": {
                \"resource\": \"user1\",
                \"cost\": \"100\"
            }
        },
        {
            \"id\": \"event2\",
            \"type\": \"complete\",
            \"timestamp\": \"2024-01-01T10:30:00Z\",
            \"source\": [\"object1\"],
            \"attributes\": {
                \"resource\": \"user2\",
                \"cost\": \"50\"
            }
        }
    ],
    \"objects\": [
        {
            \"id\": \"object1\",
            \"type\": \"order\",
            \"attributes\": {
                \"status\": \"completed\",
                \"amount\": 150
            }
        },
        {
            \"id\": \"object2\",
            \"type\": \"order\",
            \"attributes\": {
                \"status\": \"pending\",
                \"amount\": 200
            }
        }
    ]
}
">>.

%% Sample OCEL JSON with custom number of objects
sample_ocel_json(NumEvents) ->
    Events = lists:map(fun(I) ->
        #{
            id => list_to_binary(io_lib:format("event~p", [I])),
            type => <<"task">>,
            <<"timestamp">> => list_to_binary(io_lib:format("2024-01-01T10:0~p:00Z", [I])),
            <<"source">> => [list_to_binary(io_lib:format("object~p", [I]))],
            <<"attributes">> => #{
                resource => list_to_binary(io_lib:format("user~p", [I])),
                cost => integer_to_list(I * 10)
            }
        }
    end, lists:seq(1, NumEvents)),

    Objects = lists:map(fun(I) ->
        #{
            id => list_to_binary(io_lib:format("object~p", [I])),
            type => <<"order">>,
            <<"attributes">> => #{
                status => <<"active">>,
                <<"amount">> => I * 100
            }
        }
    end, lists:seq(1, NumEvents)),

    jsx:encode(#{
        events => Events,
        objects => Objects
    }).

%% Invalid OCEL JSON (missing required fields)
invalid_ocel_json() ->
    <<"
{
    \"events\": [
        {
            \"id\": \"event1\",
            \"type\": \"start\"
            \"timestamp\": \"2024-01-01T10:00:00Z\",
            \"source\": [\"object1\"]
            \"attributes\": {
                \"resource\": \"user1\"
            }
        }
    ],
    \"objects\": [
        {
            \"id\": \"object1\",
            \"type\": \"order\"
            \"attributes\": {
                \"status\": \"completed\"
            }
        }
    ]
}
">>.

%% Empty OCEL JSON
empty_ocel_json() ->
    <<"
{
    \"events\": [],
    \"objects\": []
}
">>.

%% Sample XES content
sample_xes_content() ->
    <<"
<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<log xmlns=\"http://www.xes-standard.org/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.xes-standard.org/ http://www.xes-standard.org/xes.xsd\" version=\"1.0\">
    <trace>
        <string key=\"concept:name\" value=\"Trace1\"/>
        <date key=\"time:timestamp\" value=\"2024-01-01T10:00:00Z\"/>
        <event>
            <string key=\"concept:name\" value=\"A\"/>
            <date key=\"time:timestamp\" value=\"2024-01-01T10:00:00Z\"/>
        </event>
        <event>
            <string key=\"concept:name\" value=\"B\"/>
            <date key=\"time:timestamp\" value=\"2024-01-01T10:05:00Z\"/>
        </event>
    </trace>
</log>
">>.

%% Invalid XES content
invalid_xes_content() ->
    <<"
<invalid xml structure here">
">>.

%%====================================================================
%% New Property Test Fixtures
%%====================================================================

%% Minimal valid OCEL (1 event, 1 object)
minimal_ocel_json() ->
    jsx:encode(#{
        events => [
            #{
                id => <<"event1">>,
                type => <<"start">>,
                timestamp => <<"2024-01-01T10:00:00Z">>,
                source => [<<"object1">>],
                attributes => #{}
            }
        ],
        objects => [
            #{
                id => <<"object1">>,
                type => <<"order">>,
                attributes => #{}
            }
        ]
    }).

%% Maximal valid OCEL (many events, objects, attributes)
maximal_ocel_json() ->
    Events = lists:map(fun(I) ->
        Attributes = maps:from_list([
            {<<"resource">>, list_to_binary(io_lib:format("user~p", [I]))},
            {<<"cost">>, I * 100},
            {<<"amount">>, I * 1000.0},
            {<<"priority">>, high},
            {<<"status">>, <<"active">>},
            {<<"department">>, <<"sales">>},
            {<<"location">>, <<"us-east">>}
        ]),

        #{
            id => list_to_binary(io_lib:format("event_~6.10.0b", [I])),
            type => <<"complex_task">>,
            timestamp => list_to_binary(io_lib:format("2024-01-01T~2.0b:~2.0b:~2.0bZ", [I rem 24, I rem 60, I rem 60])),
            source => lists:map(fun(J) ->
                list_to_binary(io_lib:format("object_~6.10.0b", [J]))
            end, lists:seq(I, min(I + 3, 999))),
            attributes => Attributes
        }
    end, lists:seq(1, 1000)),

    Objects = lists:map(fun(I) ->
        Attributes = maps:from_list([
            {<<"status">>, <<"completed">>},
            {<<"amount">>, I * 1000.0},
            {<<"currency">>, <<"USD">>},
            {<<"created_by">>, list_to_binary(io_lib:format("user~p", [I]))},
            {<<"last_updated">>, <<"2024-01-01T10:00:00Z">>},
            {<<"metadata">>, #{
                <<"version">> => I,
                <<"tags">> => [<<"urgent">>, <<"priority">>]
            }}
        ]),

        #{
            id => list_to_binary(io_lib:format("object_~6.10.0b", [I])),
            type => <<"complex_order">>,
            attributes => Attributes
        }
    end, lists:seq(1, 1000)),

    jsx:encode(#{
        events => Events,
        objects => Objects
    }).

%% Edge case OCEL (various boundary conditions)
edge_case_ocel_json() ->
    jsx:encode(#{
        events => [
            %% Event with very long attributes
            #{
                id => <<"event1">>,
                type => <<"long_attributes">>,
                timestamp => <<"2024-01-01T10:00:00Z">>,
                source => [],
                attributes => lists:foldl(fun(I, Acc) ->
                    maps:put(list_to_binary(io_lib:format("attr_~p", [I])),
                             list_to_binary(lists:duplicate(100, $x)), Acc)
                end, #{}, lists:seq(1, 100))
            },
            %% Event with empty source
            #{
                id => <<"event2">>,
                type => <<"no_source">>,
                timestamp => <<"2024-01-01T10:00:00Z">>,
                source => [],
                attributes => #{}
            }
        ],
        objects => [
            %% Object with all data types
            #{
                id => <<"object1">>,
                type => <<"mixed_types">>,
                attributes => #{
                    string_attr => <<"value">>,
                    int_attr => 42,
                    float_attr => 3.14159,
                    bool_attr_true => true,
                    bool_attr_false => false,
                    null_attr => null
                }
            }
        ]
    }).

%% Malformed OCEL JSON (should handle gracefully)
malformed_ocel_json() ->
    <<"
{
    \"events\": [
        {
            \"id\": \"event1\",
            \"type\": \"start\",
            \"timestamp\": \"2024-01-01T10:00:00Z\",
            \"source\": [\"object1\"],
            \"attributes\": {
                \"resource\": \"user1\",
                \"cost\": \"100\"
            }
        }
    ],
    \"objects\": [
        {
            \"id\": \"object1\",
            \"type\": \"order\",
            \"attributes\": {
                \"status\": \"completed\"
            }
        }
    ]
    %% Missing closing brace
">>.

%% OCEL with null values (should handle gracefully)
ocel_with_null_values() ->
    jsx:encode(#{
        events => [
            #{
                id => <<"event1">>,
                type => <<"test">>,
                timestamp => null,
                source => null,
                attributes => null
            }
        ],
        objects => [
            #{
                id => null,
                type => null,
                attributes => null
            }
        ]
    }).

%% OCEL with Unicode characters (should handle gracefully)
ocel_with_unicode() ->
    jsx:encode(#{
        events => [
            #{
                id => <<"事件1">>,
                type => <<"开始">>,
                timestamp => <<"2024-01-01T10:00:00Z">>,
                source => [<<"对象1">>],
                attributes => #{
                    <<"资源">> => <<"用户1">>,
                    <<"描述">> => "测试包含中文字符的属性值",
                    <<"emoji">> => "😀🎉🚀"
                }
            }
        ],
        objects => [
            #{
                id => <<"对象1">>,
                type => <<"订单">>,
                attributes => #{
                    <<"状态">> => <<"已完成">>,
                    <<"备注">> => "这是一个包含Unicode字符的对象"
                }
            }
        ]
    }).

%% Generate OCEL with specific number of events and objects
generate_ocel_with_events(NumEvents, NumObjects) ->
    Events = lists:map(fun(I) ->
        #{
            id => list_to_binary(io_lib:format("event~p", [I])),
            type => <<"task">>,
            timestamp => list_to_binary(io_lib:format("2024-01-01T10:0~p:00Z", [I])),
            source => case I =< NumObjects of
                true -> [list_to_binary(io_lib:format("object~p", [I]))];
                false -> []
            end,
            attributes => #{
                resource => list_to_binary(io_lib:format("user~p", [I])),
                cost => integer_to_list(I * 10)
            }
        }
    end, lists:seq(1, NumEvents)),

    Objects = lists:map(fun(I) ->
        #{
            id => list_to_binary(io_lib:format("object~p", [I])),
            type => <<"order">>,
            attributes => #{
                status => case I =< NumEvents div 2 of
                    true -> <<"completed">>;
                    false -> <<"pending">>
                end,
                amount => I * 100
            }
        }
    end, lists:seq(1, NumObjects)),

    jsx:encode(#{
        events => Events,
        objects => Objects
    }).

%% Generate OCEL with specific number of attributes
generate_ocel_with_attributes(NumEvents, NumObjects, NumAttrs) ->
    Events = lists:map(fun(I) ->
        Attributes = lists:foldl(fun(J, Acc) ->
            maps:put(list_to_binary(io_lib:format("attr_~p", [J])),
                     list_to_binary(io_lib:format("value_~p", [J])), Acc)
        end, #{}, lists:seq(1, NumAttrs)),

        #{
            id => list_to_binary(io_lib:format("event~p", [I])),
            type => <<"task">>,
            timestamp => list_to_binary(io_lib:format("2024-01-01T10:0~p:00Z", [I])),
            source => [list_to_binary(io_lib:format("object~p", [I rem NumObjects + 1]))],
            attributes => Attributes
        }
    end, lists:seq(1, NumEvents)),

    Objects = lists:map(fun(I) ->
        Attributes = lists:foldl(fun(J, Acc) ->
            maps:put(list_to_binary(io_lib:format("obj_attr_~p", [J])),
                     I * J, Acc)
        end, #{}, lists:seq(1, NumAttrs)),

        #{
            id => list_to_binary(io_lib:format("object~p", [I])),
            type => <<"order">>,
            attributes => Attributes
        }
    end, lists:seq(1, NumObjects)),

    jsx:encode(#{
        events => Events,
        objects => Objects
    }).

%% Stress test OCEL (large number of events and objects)
stress_test_ocel() ->
    generate_ocel_with_events(5000, 2000).

%%====================================================================
%% Utility Functions
%%====================================================================

%% Create a test file with given content
create_test_file(Content, Filename) ->
    FilePath = filename:join("test_data", Filename),
    ok = filelib:ensure_dir(FilePath),
    file:write_file(FilePath, Content),
    FilePath.

%% Create a temporary file with given content
create_temp_file(Content, Filename) ->
    TempDir = case os:getenv("TEMPDIR") of
        undefined -> "/tmp";
        TD -> TD
    end,
    UniqueName = filename:join(TempDir, "pm_test_" ++ os:getpid() ++ "_" ++ Filename),
    ok = file:write_file(UniqueName, Content),
    UniqueName.

%% Clean up a temporary file
cleanup_temp_file(Filename) ->
    case file:delete(Filename) of
        ok -> ok;
        {error, enoent} -> ok;
        Error -> Error
    end.

%% Get path to test file
test_file_path(Filename) ->
    filename:join("test_data", Filename).

%% Get path to sample log file
get_sample_log_path() ->
    filename:join("test_data", "sample_log.xes").

%% Get path to sample OCEL file
get_sample_ocel_path() ->
    filename:join("test_data", "sample_ocel.json").