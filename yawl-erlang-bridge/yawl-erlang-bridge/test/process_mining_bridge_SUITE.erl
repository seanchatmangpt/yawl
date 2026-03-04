-module(process_mining_bridge_SUITE).
-compile(export_all).

-include_lib("common_test/include/ct.hrl").

%% Test cases
all() ->
    [
        {group, basic_operations},
        {group, memory_management},
        {group, probe_functions},
        {group, error_handling}
    ].

groups() ->
    [
        {basic_operations, [], [
            test_import_ocel,
            test_log_event_count,
            test_log_object_count,
            test_discover_dfg,
            test_check_conformance
        ]},
        {memory_management, [], [
            test_slim_link_ocel,
            test_log_free,
            test_events_free,
            test_objects_free,
            test_dfg_free,
            test_error_free
        ]},
        {probe_functions, [], [
            test_sizeof_ocel_log_handle,
            test_sizeof_parse_result,
            test_sizeof_ocel_event_c,
            test_sizeof_ocel_events_result,
            test_sizeof_ocel_object_c,
            test_sizeof_ocel_objects_result,
            test_sizeof_dfg_result_c,
            test_sizeof_conformance_result_c,
            test_offsetof_ocel_log_handle_ptr,
            test_offsetof_parse_result_handle,
            test_offsetof_parse_result_error,
            test_offsetof_ocel_event_c_event_id,
            test_offsetof_ocel_event_c_event_type,
            test_offsetof_ocel_event_c_timestamp_ms,
            test_offsetof_ocel_event_c_attr_count,
            test_offsetof_dfg_result_c_json,
            test_offsetof_dfg_result_c_error,
            test_offsetof_conformance_result_c_fitness
        ]},
        {error_handling, [], [
            test_import_invalid_path,
            test_nonexistent_ocel_id,
            test_invalid_conformance_check
        ]}
    ].

init_per_suite(Config) ->
    %% Start the application
    case application:ensure_all_started(process_mining_bridge) of
        {ok, _Apps} ->
            %% Wait for the bridge to be ready
            timer:sleep(1000),
            Config;
        {error, _} ->
            {skip, "Failed to start process_mining_bridge application"}
    end.

end_per_suite(_Config) ->
    application:stop(process_mining_bridge).

init_per_group(_GroupName, Config) ->
    Config.

end_per_group(_GroupName, _Config) ->
    ok.

init_per_testcase(_TestCase, Config) ->
    Config.

end_per_testcase(_TestCase, _Config) ->
    %% Clean up any created resources
    cleanup_test_resources(),
    ok.

%% Basic operations tests
test_import_ocel(_Config) ->
    %% Test importing OCEL JSON file
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [], \"objects\": []}">>) of
        ok ->
            Result = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            ct:pal("Import OCEL result: ~p", [Result]),
            file:delete(Path),
            case Result of
                {ok, _OcelId} ->
                    ok;
                {error, _} = Error ->
                    ct:fail("Import failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_log_event_count(_Config) ->
    %% Test getting event count
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [{\"id\": \"e1\"}], \"objects\": []}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            Result = process_mining_bridge:log_event_count(#{ocel_id => OcelId}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                {ok, 1} ->
                    ok;
                {ok, _} = Unexpected ->
                    ct:fail("Expected 1 event, got: ~p", [Unexpected]);
                {error, _} = Error ->
                    ct:fail("Log event count failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_log_object_count(_Config) ->
    %% Test getting object count
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events": [], \"objects\": [{\"id\": \"o1\"}]}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            Result = process_mining_bridge:log_object_count(#{ocel_id => OcelId}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                {ok, 1} ->
                    ok;
                {ok, _} = Unexpected ->
                    ct:fail("Expected 1 object, got: ~p", [Unexpected]);
                {error, _} = Error ->
                    ct:fail("Log object count failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_discover_dfg(_Config) ->
    %% Test discovering DFG
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [{\"id\": \"e1\", \"source\": [\"o1\"]}], \"objects\": [{\"id\": \"o1\"}]}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            {ok, SlimOcelId} = process_mining_bridge:slim_link_ocel(#{ocel_id => OcelId}),
            Result = process_mining_bridge:discover_dfg(#{slim_ocel_id => SlimOcelId}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                {ok, _Json} ->
                    ok;
                {error, _} = Error ->
                    ct:fail("Discover DFG failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_check_conformance(_Config) ->
    %% Test conformance checking
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [], \"objects\": []}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            PetriNetId = "test-net",
            Result = process_mining_bridge:check_conformance(#{ocel_id => OcelId, petri_net_id => PetriNetId}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                {ok, _ConformanceResult} ->
                    ok;
                {error, _} = Error ->
                    ct:fail("Check conformance failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

%% Memory management tests
test_slim_link_ocel(_Config) ->
    %% Test slim linking OCEL
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [], \"objects\": []}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            Result = process_mining_bridge:slim_link_ocel(#{ocel_id => OcelId}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                {ok, _SlimOcelId} ->
                    ok;
                {error, _} = Error ->
                    ct:fail("Slim link OCEL failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_log_free(_Config) ->
    %% Test freeing OCEL log
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [], \"objects\": []}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            Result = process_mining_bridge:log_free(#{ocel_id => OcelId}),
            file:delete(Path),
            case Result of
                ok ->
                    ok;
                {error, _} = Error ->
                    ct:fail("Log free failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_events_free(_Config) ->
    %% Test freeing events
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [], \"objects\": []}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            {ok, Handle} = process_mining_bridge:log_get_events(#{ocel_id => OcelId}),
            Result = process_mining_bridge:events_free(#{events_handle => Handle}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                ok ->
                    ok;
                {error, _} = Error ->
                    ct:fail("Events free failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_objects_free(_Config) ->
    %% Test freeing objects
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [], \"objects\": []}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            {ok, Handle} = process_mining_bridge:log_get_objects(#{ocel_id => OcelId}),
            Result = process_mining_bridge:objects_free(#{objects_handle => Handle}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                ok ->
                    ok;
                {error, _} = Error ->
                    ct:fail("Objects free failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_dfg_free(_Config) ->
    %% Test freeing DFG
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [], \"objects\": []}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            {ok, SlimOcelId} = process_mining_bridge:slim_link_ocel(#{ocel_id => OcelId}),
            {ok, Handle} = process_mining_bridge:discover_dfg(#{slim_ocel_id => SlimOcelId}),
            Result = process_mining_bridge:dfg_free(#{dfg_handle => Handle}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                ok ->
                    ok;
                {error, _} = Error ->
                    ct:fail("DFG free failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

test_error_free(_Config) ->
    %% Test freeing error message
    Path = "/tmp/test_ocel.json",
    case file:write_file(Path, <<"{\"events\": [], \"objects\": []}">>) of
        ok ->
            {ok, OcelId} = process_mining_bridge:import_ocel_json_path(#{path => Path}),
            {ok, Handle} = process_mining_bridge:log_get_objects(#{ocel_id => OcelId}),
            Result = process_mining_bridge:error_free(#{error_handle => Handle}),
            file:delete(Path),
            process_mining_bridge:log_free(#{ocel_id => OcelId}),
            case Result of
                ok ->
                    ok;
                {error, _} = Error ->
                    ct:fail("Error free failed: ~p", [Error])
            end;
        {error, Reason} ->
            ct:fail("Failed to create test file: ~p", [Reason])
    end.

%% Probe functions tests
test_sizeof_ocel_log_handle(_Config) ->
    Result = process_mining_bridge:sizeof_ocel_log_handle(),
    case Result of
        {ok, _Size} -> ok;
        {error, Reason} -> ct:fail("Sizeof OCEL log handle failed: ~p", [Reason])
    end.

test_sizeof_parse_result(_Config) ->
    Result = process_mining_bridge:sizeof_parse_result(),
    case Result of
        {ok, _Size} -> ok;
        {error, Reason} -> ct:fail("Sizeof parse result failed: ~p", [Reason])
    end.

test_sizeof_ocel_event_c(_Config) ->
    Result = process_mining_bridge:sizeof_ocel_event_c(),
    case Result of
        {ok, _Size} -> ok;
        {error, Reason} -> ct:fail("Sizeof OCEL event C failed: ~p", [Reason])
    end.

test_sizeof_ocel_events_result(_Config) ->
    Result = process_mining_bridge:sizeof_ocel_events_result(),
    case Result of
        {ok, _Size} -> ok;
        {error, Reason} -> ct:fail("Sizeof OCEL events result failed: ~p", [Reason])
    end.

test_sizeof_ocel_object_c(_Config) ->
    Result = process_mining_bridge:sizeof_ocel_object_c(),
    case Result of
        {ok, _Size} -> ok;
        {error, Reason} -> ct:fail("Sizeof OCEL object C failed: ~p", [Reason])
    end.

test_sizeof_ocel_objects_result(_Config) ->
    Result = process_mining_bridge:sizeof_ocel_objects_result(),
    case Result of
        {ok, _Size} -> ok;
        {error, Reason} -> ct:fail("Sizeof OCEL objects result failed: ~p", [Reason])
    end.

test_sizeof_dfg_result_c(_Config) ->
    Result = process_mining_bridge:sizeof_dfg_result_c(),
    case Result of
        {ok, _Size} -> ok;
        {error, Reason} -> ct:fail("Sizeof DFG result C failed: ~p", [Reason])
    end.

test_sizeof_conformance_result_c(_Config) ->
    Result = process_mining_bridge:sizeof_conformance_result_c(),
    case Result of
        {ok, _Size} -> ok;
        {error, Reason} -> ct:fail("Sizeof conformance result C failed: ~p", [Reason])
    end.

test_offsetof_ocel_log_handle_ptr(_Config) ->
    Result = process_mining_bridge:offsetof_ocel_log_handle_ptr(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof OCEL log handle ptr failed: ~p", [Reason])
    end.

test_offsetof_parse_result_handle(_Config) ->
    Result = process_mining_bridge:offsetof_parse_result_handle(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof parse result handle failed: ~p", [Reason])
    end.

test_offsetof_parse_result_error(_Config) ->
    Result = process_mining_bridge:offsetof_parse_result_error(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof parse result error failed: ~p", [Reason])
    end.

test_offsetof_ocel_event_c_event_id(_Config) ->
    Result = process_mining_bridge:offsetof_ocel_event_c_event_id(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof OCEL event C event ID failed: ~p", [Reason])
    end.

test_offsetof_ocel_event_c_event_type(_Config) ->
    Result = process_mining_bridge:offsetof_ocel_event_c_event_type(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof OCEL event C event type failed: ~p", [Reason])
    end.

test_offsetof_ocel_event_c_timestamp_ms(_Config) ->
    Result = process_mining_bridge:offsetof_ocel_event_c_timestamp_ms(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof OCEL event C timestamp ms failed: ~p", [Reason])
    end.

test_offsetof_ocel_event_c_attr_count(_Config) ->
    Result = process_mining_bridge:offsetof_ocel_event_c_attr_count(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof OCEL event C attr count failed: ~p", [Reason])
    end.

test_offsetof_dfg_result_c_json(_Config) ->
    Result = process_mining_bridge:offsetof_dfg_result_c_json(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof DFG result C JSON failed: ~p", [Reason])
    end.

test_offsetof_dfg_result_c_error(_Config) ->
    Result = process_mining_bridge:offsetof_dfg_result_c_error(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof DFG result C error failed: ~p", [Reason])
    end.

test_offsetof_conformance_result_c_fitness(_Config) ->
    Result = process_mining_bridge:offsetof_conformance_result_c_fitness(),
    case Result of
        {ok, _Offset} -> ok;
        {error, Reason} -> ct:fail("Offsetof conformance result C fitness failed: ~p", [Reason])
    end.

%% Error handling tests
test_import_invalid_path(_Config) ->
    %% Test importing invalid file path
    Result = process_mining_bridge:import_ocel_json_path(#{path => "/nonexistent/path.json"}),
    case Result of
        {error, _} -> ok;
        {ok, _} -> ct:fail("Should have failed with invalid path")
    end.

test_nonexistent_ocel_id(_Config) ->
    %% Test operations with non-existent OCEL ID
    Result = process_mining_bridge:log_event_count(#{ocel_id => "nonexistent"}),
    case Result of
        {error, not_found} -> ok;
        {ok, _} -> ct:fail("Should have failed with non-existent OCEL ID");
        {error, _} = Error -> ct:fail("Expected not_found error, got: ~p", [Error])
    end.

test_invalid_conformance_check(_Config) ->
    %% Test conformance check with invalid parameters
    Result = process_mining_bridge:check_conformance(#{ocel_id => "invalid", petri_net_id => "invalid"}),
    case Result of
        {error, not_found} -> ok;
        {ok, _} -> ct:fail("Should have failed with invalid parameters");
        {error, _} = Error ->
            ct:pal("Conformance check error: ~p", [Error]),
            ok
    end.

%% Helper functions
cleanup_test_resources() ->
    %% Clean up any test resources
    lager:info("Cleaning up test resources"),
    ok.