-module(yawl_bridge_sup_test).
-include_lib("eunit/include/eunit.hrl").

%% Record definitions for testing
-record(capability_registry, {
    id :: binary(),
    handle :: reference(),
    timestamp :: erlang:timestamp()
}).

yawl_bridge_sup_test_() ->
    {setup,
        fun() ->
            %% Start the test environment
            application:start(mnesia),
            mnesia:create_schema([node()]),
            mnesia:start(),
            process_mining_bridge_app:start(normal, []),
            ok
        end,
        fun(_) ->
            %% Cleanup
            application:stop(process_mining_bridge),
            mnesia:stop(),
            application:stop(mnesia)
        end,
        fun(_) -> [
            supervisor_test(),
            health_check_test(),
            mnesia_tables_test()
        ] end
    }.

supervisor_test() ->
    {"Supervisor should start all children",
        fun() ->
            %% Check that supervisor is running
            ?assert(is_pid(whereis(yawl_bridge_sup))),

            %% Check children processes
            ?assert(is_pid(whereis(process_mining_bridge))),
            ?assert(is_pid(whereis(data_modelling_bridge))),
            ?assert(is_pid(whereis(mnesia_registry)))
        end
    }.

health_check_test() ->
    {"Health check should return expected structure",
        fun() ->
            Result = yawl_bridge_health:check(),
            ?assert(is_map(Result)),
            ?assert(maps:is_key(process_mining, Result)),
            ?assert(maps:is_key(data_modelling, Result)),
            ?assert(maps:is_key(mnesia, Result)),
            ?assert(maps:is_key(nodes, Result)),
            ?assert(maps:is_key(tables, Result)),
            ?assert(maps:is_key(memory, Result))
        end
    }.

mnesia_tables_test() ->
    {"Mnesia tables should be created and accessible",
        fun() ->
            %% Check that the new tables were created
            ?assert(mnesia:table_info(ocel_registry, exists)),
            ?assert(mnesia:table_info(slim_ocel_registry, exists)),
            ?assert(mnesia:table_info(petri_net_registry, exists)),
            ?assert(mnesia:table_info(conformance_registry, exists)),

            %% Test OCEL registration
            OcelId = test_ocel_id,
            RustPointer = <<"test_pointer_1">>,
            ?assertEqual(ok, mnesia_registry:register_ocel(OcelId, RustPointer)),
            ?assertEqual({ok, RustPointer}, mnesia_registry:lookup_ocel(OcelId)),
            ?assertEqual(ok, mnesia_registry:unregister_ocel(OcelId)),
            ?assertEqual({error, not_found}, mnesia_registry:lookup_ocel(OcelId)),

            %% Test Slim OCEL registration
            SlimOcelId = test_slim_ocel_id,
            RustPointer2 = <<"test_pointer_2">>,
            ?assertEqual(ok, mnesia_registry:register_slim_ocel(SlimOcelId, RustPointer2)),
            ?assertEqual({ok, RustPointer2}, mnesia_registry:lookup_slim_ocel(SlimOcelId)),

            %% Test Petri Net registration
            PetriNetId = test_petri_net_id,
            RustPointer3 = <<"test_pointer_3">>,
            ?assertEqual(ok, mnesia_registry:register_petri_net(PetriNetId, RustPointer3)),
            ?assertEqual({ok, RustPointer3}, mnesia_registry:lookup_petri_net(PetriNetId)),

            %% Test Conformance registration
            ConformanceId = test_conformance_id,
            RustPointer4 = <<"test_pointer_4">>,
            ?assertEqual(ok, mnesia_registry:register_conformance(ConformanceId, RustPointer4)),
            ?assertEqual({ok, RustPointer4}, mnesia_registry:lookup_conformance(ConformanceId))
        end
    }.

mnesia_registry_api_test() ->
    {"Mnesia registry API should work correctly",
        fun() ->
            %% Test stats retrieval
            {ok, Stats} = mnesia_registry:get_registry_stats(),
            ?assert(is_map(Stats)),
            ?assert(maps:is_key(operations, Stats)),
            ?assert(maps:is_key(ocel_count, Stats)),
            ?assert(maps:is_key(errors, Stats)),

            %% Test clear stale entries
            ?assertEqual(ok, mnesia_registry:clear_stale_entries())
        end
    }.