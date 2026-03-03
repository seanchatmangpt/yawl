%%%-------------------------------------------------------------------
%%% @doc Simple Process Mining Bridge EUnit Tests
%%%
%%% Tests for process_mining_bridge module.
%%% @end
%%%-------------------------------------------------------------------
-module(process_mining_bridge_simple_tests).
-include_lib("eunit/include/eunit.hrl").

%%====================================================================
%% Test entry point
%%====================================================================

process_mining_bridge_simple_test_() ->
    {timeout, 30, fun setup/0, fun teardown/1, fun(_SetupData) -> [
        test_api_functions(),
        test_gen_server_startup()
    ] end}.

%%====================================================================
%% Test setup and cleanup
%%====================================================================

setup() ->
    %% Start the process mining bridge
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            {ok, Pid};
        {error, Reason} ->
            throw({failed_to_start, Reason})
    end.

teardown(Pid) ->
    %% Stop the process mining bridge
    process_mining_bridge:stop(),
    ok.

%%====================================================================
%% Individual test cases
%%====================================================================

test_api_functions() ->
    {"API functions - test that they exist and return error when NIF not loaded", fun() ->
        %% Test import
        Path = "/tmp/test_ocel.json",
        Result1 = process_mining_bridge:import_ocel_json_path(Path),
        ?assertEqual({error, nif_library_not_loaded}, Result1),

        %% Test slim link
        Result2 = process_mining_bridge:slim_link_ocel(<<"test_id">>),
        ?assertEqual({error, nif_library_not_loaded}, Result2),

        %% Test discover OC-DECLARE
        Result3 = process_mining_bridge:discover_oc_declare(<<"test_id">>),
        ?assertEqual({error, nif_library_not_loaded}, Result3),

        %% Test alpha_plus_plus_discover
        Result4 = process_mining_bridge:alpha_plus_plus_discover(<<"test_id">>),
        ?assertEqual({error, nif_library_not_loaded}, Result4),

        %% Test token replay
        Result5 = process_mining_bridge:token_replay(<<"test_id">>, <<"test_id">>),
        ?assertEqual({error, nif_library_not_loaded}, Result5),

        %% Test get fitness score
        Result6 = process_mining_bridge:get_fitness_score(<<"test_id">>),
        ?assertEqual({error, nif_library_not_loaded}, Result6)
    end}.

test_gen_server_startup() ->
    {"Gen server startup and stop", fun() ->
        %% The process should be running when we reach this point
        ?assert(is_process_alive(whereis(process_mining_bridge)))
    end}.