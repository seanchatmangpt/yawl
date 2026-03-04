%% Edge Case Test: Failed Task Handling
%% Tests that the system properly handles tasks that return error states

-module(edge_case_failed_task).
-export([run/0, run_test/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the failed task edge case test
%% Returns {ok, Result} | {error, Reason}
run() ->
    io:format("Running edge case: Failed Task Handling~n", []),
    run_test().

%% @doc Test the failed task scenario
run_test() ->
    %% Create test environment
    TestDir = "/tmp/yawl-test/edge-cases/failed_task",
    ensure_test_dir(TestDir),

    %% Create mock OCEL data with a task that will fail
    MalformedOcel = create_malformed_task_ocel(),
    OcelPath = filename:join(TestDir, "malformed_task_ocel.json"),
    file:write_file(OcelPath, MalformedOcel),

    try
        %% Step 1: Try to import the malformed OCEL data
        case process_mining_bridge:import_ocel_json(OcelPath) of
            {ok, OcelHandle} ->
                %% Step 2: Try to discover DFG (should fail)
                case process_mining_bridge:discover_dfg(OcelHandle) of
                    {error, _} = Error ->
                        %% Step 3: Verify error is properly handled
                        case verify_error_handling(Error) of
                            ok ->
                                %% Clean up
                                process_mining_bridge:free_handle(OcelHandle),
                                {ok, #{
                                    test => "failed_task",
                                    error => Error,
                                    status => "error_handled_properly"
                                }};
                            {error, VerificationError} ->
                                process_mining_bridge:free_handle(OcelHandle),
                                {error, {verification_failed, VerificationError}}
                        end;
                    {ok, _} ->
                        process_mining_bridge:free_handle(OcelHandle),
                        {error, dfg_succeeded_unexpectedly}
                end;
            {error, ImportError} ->
                %% Verify import error is expected
                case verify_import_error(ImportError) of
                    ok ->
                        {ok, #{
                            test => "failed_task",
                            error => ImportError,
                            status => "import_error_expected"
                        }};
                    {error, VerificationError} ->
                        {error, {import_error_verification_failed, VerificationError}}
                end
        end
    catch
        Class:Reason ->
            {error, {unexpected_exception, Class, Reason, erlang:get_stacktrace()}}
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Create malformed OCEL data with missing required task
create_malformed_task_ocel() ->
    jsx:encode(#{
        << "events">> => [
            #{
                << "id">> => << "event1">>,
                << "activity">> => << "start">>,
                << "timestamp">> => << "2023-01-01T00:00:00">>,
                << "attributes">> => #{}
            },
            #{
                << "id">> => << "event2">>,
                << "activity">> => << "complete">>,
                << "timestamp">> => << "2023-01-01T00:01:00">>,
                << "attributes">> => #{}
            }
        ],
        << "objects">> => [
            #{
                << "id">> => << "obj1">>,
                << "type">> => << "task">>,
                << "attributes">> => #{}
            }
        ],
        << "object_types">> => [
            #{
                << "id">> => << "task">>,
                << "attributes">> => [
                    #{
                        << "id">> => << "name">>,
                        << "type">> => << "string">>
                    }
                ]
            }
        ]
    }).

%% @doc Ensure test directory exists
ensure_test_dir(Dir) ->
    filelib:ensure_dir(Dir ++ "/"),
    case file:make_dir(Dir) of
        ok -> ok;
        {error, eexist} -> ok;
        {error, Reason} -> {error, Reason}
    end.

%% @doc Verify that error handling is appropriate
verify_error_handling({error, Reason}) ->
    %% Check that the error message is meaningful
    ErrorStr = lists:flatten(io_lib:format("~p", [Reason])),
    case string:str(ErrorStr, "invalid") > 0 orelse
         string:str(ErrorStr, "missing") > 0 orelse
         string:str(ErrorStr, "format") > 0 of
        true -> ok;
        false -> {error, error_not_meaningful}
    end.

%% @doc Verify that import error is as expected
verify_import_error({error, Reason}) ->
    %% Import should fail due to malformed data
    case Reason of
        {_, _} -> ok;
        _ when is_list(Reason) ->
            case string:str(Reason, "invalid") > 0 orelse
                 string:str(Reason, "malformed") > 0 of
                true -> ok;
                false -> {error, import_error_not_meaningful}
            end;
        _ -> {error, unknown_import_error_format}
    end.