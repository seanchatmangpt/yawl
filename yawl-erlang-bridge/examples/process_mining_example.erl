%% Process Mining Bridge Example
%% This module demonstrates how to use the process_mining_bridge gen_server

-module(process_mining_example).
-export([run_example/0]).

run_example() ->
    %% Start the process mining bridge
    {ok, _Pid} = process_mining_bridge:start_link(),
    io:format("Process Mining Bridge started~n"),

    %% Example: Import an OCEL file
    OcelPath = "/path/to/your/ocel.json",
    case process_mining_bridge:import_ocel(OcelPath) of
        {ok, OcelId} ->
            io:format("Successfully imported OCEL: ~p~n", [OcelId]),

            %% Example: Slim the OCEL link
            case process_mining_bridge:slim_link(OcelId) of
                {ok, SlimOcelId} ->
                    io:format("Slimmed OCEL created: ~p~n", [SlimOcelId]),

                    %% Example: Discover DFG
                    case process_mining_bridge:discover_dfg(SlimOcelId) of
                        {ok, DFGJson} ->
                            io:format("Discovered DFG: ~s~n", [DFGJson]),

                            %% Example: Token replay on a Petri net
                            case process_mining_bridge:token_replay(OcelId, "petri_net_id") of
                                {ok, ReplayResult} ->
                                    io:format("Token replay result: ~p~n", [ReplayResult]),

                                    %% Example: Free the OCEL when done
                                    process_mining_bridge:free_ocel(OcelId),
                                    io:format("OCEL freed~n");
                                {error, Reason} ->
                                    io:format("Token replay failed: ~p~n", [Reason])
                            end;
                        {error, Reason} ->
                            io:format("DFG discovery failed: ~p~n", [Reason])
                    end;
                {error, Reason} ->
                    io:format("Slim link failed: ~p~n", [Reason])
            end;
        {error, Reason} ->
            io:format("Import failed: ~p~n", [Reason])
    end.