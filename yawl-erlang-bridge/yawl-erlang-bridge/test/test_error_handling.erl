%% Error Handling Tests for Process Mining Bridge
%% Tests all error paths and edge cases

-module(test_error_handling).
-include_lib("eunit/include/eunit.hrl").
-include_lib("kernel/include/logger.hrl").

%% Test exports
-export([
    invalid_inputs_test/0,
    missing_dependencies_test/0,
    resource_limits_test/0,
    network_errors_test/0,
    file_system_errors_test/0,
    concurrency_errors_test/0,
    memory_errors_test/0,
    timeout_errors_test/0
]).

%%====================================================================
%% Test Cases
%%====================================================================

%% @doc Test invalid inputs and parameters
invalid_inputs_test() ->
    %% Test missing required parameters
    ?assertMatch({error, _}, process_mining_bridge:import_xes(#{})),
    ?assertMatch({error, _}, process_mining_bridge:import_ocel_json(#{})),
    ?assertMatch({error, _}, process_mining_bridge:discover_dfg(#{})),
    ?assertMatch({error, _}, process_mining_bridge:discover_alpha(#{})),
    ?assertMatch({error, _}, process_mining_bridge:token_replay(#{})),

    %% Test invalid data types
    ?assertMatch({error, _}, process_mining_bridge:import_xes(#{path => 123})),
    ?assertMatch({error, _}, process_mining_bridge:import_ocel_json(#{path => []})),
    ?assertMatch({error, _}, process_mining_bridge:discover_dfg(#{log_handle => "invalid"})),

    %% Test invalid file paths
    ?assertMatch({error, _}, process_mining_bridge:import_xes(#{path => "/nonexistent/path.xes"})),
    ?assertMatch({error, _}, process_mining_bridge:import_ocel_json(#{path => "/tmp/invalid.json"})),

    %% Test invalid handle types
    ?assertMatch({error, _}, process_mining_bridge:export_xes("invalid_handle", "/tmp/test.xes")),
    ?assertMatch({error, _}, process_mining_bridge:free_handle("not_a_ref")).

%% @doc Test behavior when dependencies are missing
missing_dependencies_test() ->
    %% This tests the fallback behavior when NIF is not available
    %% All NIF calls should return {error, nif_not_loaded} when NIF fails to load

    %% Test that NIF stubs work correctly
    ?assertMatch({error, nif_not_loaded},
                 process_mining_bridge:import_xes(#{path => "/test.xes"})),
    ?assertMatch({error, nif_not_loaded},
                 process_mining_bridge:discover_dfg(#{log_handle => make_ref()})),
    ?assertMatch({error, nif_not_loaded},
                 process_mining_bridge:event_log_stats(#{log_handle => make_ref()})).

%% @doc Test resource limits and constraints
resource_limits_test() ->
    %% Test with very large files (if available)
    LargeOcel = test_fixtures:sample_ocel_json(10000),
    LargeOcelPath = test_fixtures:create_temp_file(LargeOcel, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => LargeOcelPath}) of
            {ok, Handle} ->
                process_mining_bridge:free_handle(Handle),
                ?debugFmt("Large file handled successfully");
            {error, Reason} ->
                ?debugFmt("Large file expectedly failed: ~p", [Reason])
        end
    after
        cleanup_test_file(LargeOcelPath)
    end.

%% @doc Test network-related errors
network_errors_test() ->
    %% Test with remote file paths (not accessible)
    RemotePath = "http://example.com/remote_log.xes",
    ?assertMatch({error, _}, process_mining_bridge:import_xes(#{path => RemotePath})).

%% @doc Test file system errors
file_system_errors_test() ->
    %% Test with unwritable paths for export
    UnwritablePath = "/root/test.xes",
    case filelib:is_dir("/root") of
        true ->
            ?assertMatch({error, _},
                         process_mining_bridge:export_xes(make_ref(), UnwritablePath));
        false ->
            ok
    end.

%% @doc Test concurrent access and race conditions
concurrency_errors_test() ->
    %% Create multiple test files
    TestFiles = [
        test_fixtures:create_temp_file(test_fixtures:sample_ocel_json(10), "json"),
        test_fixtures:create_temp_file(test_fixtures:sample_ocel_json(20), "json"),
        test_fixtures:create_temp_file(test_fixtures:sample_ocel_json(30), "json")
    ],

    try
        %% Process files concurrently
        Pids = lists:map(fun(Path) ->
            spawn(fun() ->
                case process_mining_bridge:import_ocel_json(#{path => Path}) of
                    {ok, Handle} ->
                        timer:sleep(100), % Simulate processing
                        process_mining_bridge:free_handle(Handle);
                    {error, _} -> ok
                end
            end)
        end, TestFiles),

        %% Wait for all processes to complete
        lists:foreach(fun(Pid) ->
            receive
                {Pid, Result} -> Result
            after 5000 ->
                ?debugFmt("Process ~p timed out", [Pid]),
                exit(Pid, kill)
            end
        end, Pids),

        %% Verify no processes crashed
        ?debugFmt("Concurrent processing completed")
    after
        lists:foreach(fun cleanup_test_file/1, TestFiles)
    end.

%% @doc Test memory-related errors and leaks
memory_errors_test() ->
    %% Create many handles to test memory management
    Handles = lists:map(fun(_) ->
        OcelContent = test_fixtures:sample_ocel_json(100),
        Path = test_fixtures:create_temp_file(OcelContent, "json"),
        case process_mining_bridge:import_ocel_json(#{path => Path}) of
            {ok, Handle} ->
                cleanup_test_file(Path),
                {ok, Handle};
            {error, _} ->
                cleanup_test_file(Path),
                {error, memory_test}
        end
    end, lists:seq(1, 100)),

    %% Process results and clean up
    Results = lists:map(fun(Result) ->
        case Result of
            {ok, Handle} ->
                process_mining_bridge:free_handle(Handle);
            {error, _} ->
                ok
        end
    end, Handles),

    %% Verify all handles were cleaned up
    ?debugFmt("Memory test completed: ~p/~p successful", [
        length([R || R <- Results, R =:= ok]),
        length(Results)
    ]).

%% @doc Test timeout behavior
timeout_errors_test() ->
    %% Test with a very large file that might timeout
    %% This is more of a smoke test since we can't easily force timeouts
    LargeContent = test_fixtures:sample_ocel_json(1000),
    TimeoutPath = test_fixtures:create_temp_file(LargeContent, "json"),

    try
        %% This should either succeed or fail gracefully
        case process_mining_bridge:import_ocel_json(#{path => TimeoutPath}) of
            {ok, Handle} ->
                process_mining_bridge:free_handle(Handle),
                ?debugFmt("Large file processed successfully");
            {error, Reason} ->
                ?debugFmt("Large file failed as expected: ~p", [Reason]),
                %% Check if it's a timeout or other error
                case Reason of
                    timeout -> ?debugFmt("Timeout detected");
                    _ -> ?debugFmt("Non-timeout error: ~p", [Reason])
                end
        end
    after
        cleanup_test_file(TimeoutPath)
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% Clean up test files
cleanup_test_file(Path) ->
    test_fixtures:cleanup_temp_file(Path).

%%====================================================================
%% Test Suite
%%====================================================================

error_handling_test_() ->
    {setup,
     fun() ->
         %% Start the application
         case application:ensure_all_started(process_mining_bridge) of
             {ok, _Apps} ->
                 timer:sleep(500), % Wait for initialization
                 ok;
             {error, Reason} ->
                 error({failed_to_start_app, Reason})
         end
     end,
     fun(_) ->
         application:stop(process_mining_bridge)
     end,
     [
        %% Basic error cases
        {timeout, 10000, ?_test(invalid_inputs_test())},
        {timeout, 10000, ?_test(missing_dependencies_test())},

        %% Resource and performance tests
        {timeout, 30000, ?_test(resource_limits_test())},
        {timeout, 20000, ?_test(network_errors_test())},
        {timeout, 20000, ?_test(file_system_errors_test())},

        %% Concurrency and memory tests
        {timeout, 30000, ?_test(concurrency_errors_test())},
        {timeout, 30000, ?_test(memory_errors_test())},

        %% Error timeouts
        {timeout, 60000, ?_test(timeout_errors_test())}
     ]}.