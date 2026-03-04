-module(yawl_bridge_health).
-export([check/0]).

check() ->
    #{
        process_mining => whereis(process_mining_bridge) =/= undefined,
        data_modelling => whereis(data_modelling_bridge) =/= undefined,
        mnesia => mnesia:system_info(is_running) =:= yes,
        nodes => mnesia:system_info(db_nodes),
        tables => get_table_status(),
        memory => get_memory_usage()
    }.

get_table_status() ->
    Tables = mnesia:table_info(all, all),
    lists:map(fun(Table) ->
        #{
            name => Table,
            type => mnesia:table_info(Table, type),
            size => mnesia:table_info(Table, size),
            memory => mnesia:table_info(Table, memory)
        }
    end, Tables).

get_memory_usage() ->
    #{
        total_memory => erlang:memory(total),
        processes => erlang:memory(processes),
        system => erlang:memory(system),
        mnesia => mnesia:table_info(mnesia_db, memory)
    }.