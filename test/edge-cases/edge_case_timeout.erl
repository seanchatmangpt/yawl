%% Edge Case Test: Timeout Handling
%% Tests that the system properly handles tasks that exceed time limits

-module(edge_case_timeout).
-export([run/0, run_test/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the timeout edge case test
%% Returns {ok, Result} | {error, Reason}
run() ->
    io:format("Running edge case: Timeout Handling~n", []),
    run_test().

%% @doc Test the timeout scenario
run_test() ->
    %% Create test environment
    TestDir = "/tmp/yawl-test/edge-cases/timeout",
    ensure_test_dir(TestDir),

    %% Create large OCEL data that will cause timeout
    LargeOcel = create_large_ocel_data(),
    OcelPath = filename:join(TestDir, "large_ocel.json"),
    file:write_file(OcelPath, LargeOcel),

    try
        %% Step 1: Import large OCEL data
        case process_mining_bridge:import_ocel_json(OcelPath) of
            {ok, OcelHandle} ->
                %% Step 2: Set a timeout and try DFG discovery
                process_flag(trap_exit, true),
                Pid = spawn_link(fun() ->
                    process_mining_bridge:discover_dfg(OcelHandle)
                end),

                %% Wait with timeout
                receive
                    {Pid, Result} ->
                        process_mining_bridge:free_handle(OcelHandle),
                        {ok, #{
                            test => "timeout",
                            result => Result,
                            status => timeout_test_completed
                        }};
                    {'EXIT', Pid, Reason} ->
                        process_mining_bridge:free_handle(OcelHandle),
                        {ok, #{
                            test => "timeout",
                            reason => Reason,
                            status => process_died
                        }}
                after 30000 -> % 30 second timeout
                    case is_process_alive(Pid) of
                        true ->
                            %% Kill the process
                            exit(Pid, kill),
                            process_mining_bridge:free_handle(OcelHandle),
                            {ok, #{
                                test => "timeout",
                                status => timeout_detected,
                                timeout_duration => 30000
                            }};
                        false ->
                            process_mining_bridge:free_handle(OcelPath),
                            {error, process_already_died}
                    end
                end;
            {error, ImportError} ->
                {error, {import_failed, ImportError}}
        end
    catch
        Class:Reason ->
            {error, {unexpected_exception, Class, Reason, erlang:get_stacktrace()}}
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Create large OCEL data that will cause timeout
create_large_ocel_data() ->
    %% Generate 1000 events to simulate large dataset
    Events = lists:map(fun(I) ->
        #{
            << "id">> => iolist_to_binary([<<"event">>, integer_to_list(I)]),
            << "activity">> => iolist_to_binary([<<"activity">>, integer_to_list(I rem 10)]),
            << "timestamp">> => iolist_to_binary([<<"2023-01-01T00:0">>,
                                               integer_to_list(I div 60),
                                               <<":00">>]),
            << "attributes">> => #{
                << "case_id">> => iolist_to_binary([<<"case">>, integer_to_list(I div 100)]),
                << "resource">> => iolist_to_binary([<<"resource">>, integer_to_list(I)])
            }
        }
    end, lists:seq(1, 1000)),

    jsx:encode(#{
        << "events">> => Events,
        << "objects">> => [],
        << "object_types">> => []
    }).

%% @doc Ensure test directory exists
ensure_test_dir(Dir) ->
    filelib:ensure_dir(Dir ++ "/"),
    case file:make_dir(Dir) of
        ok -> ok;
        {error, eexist} -> ok;
        {error, Reason} -> {error, Reason}
    end.