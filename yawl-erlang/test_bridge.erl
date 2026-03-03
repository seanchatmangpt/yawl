%% Test script for process_mining_bridge
-module(test_bridge).
-export([run/0]).

run() ->
    %% Start Mnesia
    case mnesia:start() of
        {ok, _} -> ok;
        {error, {already_started, _}} -> ok;
        Error -> io:format("Mnesia start failed: ~p~n", [Error])
    end,

    %% Wait for Mnesia
    timer:sleep(1000),

    %% Start the process mining bridge
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format("Bridge started: ~p~n", [Pid]),

            %% Test import
            Path = "/tmp/test_ocel.json",
            OcelJson = ocel2_json_example(),
            file:write_file(Path, OcelJson),

            case process_mining_bridge:import_ocel_json_path(Path) of
                {ok, OcelId} ->
                    io:format("Import successful: ~p~n", [OcelId]);
                {error, Reason} ->
                    io:format("Import failed: ~p~n", [Reason])
            end,

            %% Stop the bridge
            process_mining_bridge:stop(),

            %% Stop Mnesia
            mnesia:stop(),
            ok;
        Error ->
            io:format("Failed to start bridge: ~p~n", [Error])
    end.

ocel2_json_example() ->
    <<"
{
    \"global_event_attributes\": {
        \"concept:name\": \"Event concept\",
        \"time:timestamp\": \"Timestamp\"
    },
    \"global_trace_attributes\": {
        \"concept:name\": \"Trace concept\"
    },
    \"events\": [
        {
            \"concept:name\": \"A\",
            \"time:timestamp\": \"2023-01-01T10:00:00\",
            \"lifecycle:transition\": \"complete\",
            \"concept:name\": \"Start\"
        },
        {
            \"concept:name\": \"B\",
            \"time:timestamp\": \"2023-01-01T10:05:00\",
            \"lifecycle:transition\": \"complete\",
            \"concept:name\": \"Process\"
        },
        {
            \"concept:name\": \"C\",
            \"time:timestamp\": \"2023-01-01T10:10:00\",
            \"lifecycle:transition\": \"complete\",
            \"concept:name\": \"Complete\"
        }
    ],
    \"objects\": [
        {
            \"id\": \"trace1\",
            \"type\": \"trace\",
            \"concept:name\": \"Trace 1\"
        }
    ],
    \"relationships\": [
        {
            \"id\": \"rel1\",
            \"type\": \"trace_to_event\",
            \"source\": \"trace1\",
            \"target\": \"A\"
        },
        {
            \"id\": \"rel2\",
            \"type\": \"trace_to_event\",
            \"source\": \"trace1\",
            \"target\": \"B\"
        },
        {
            \"id\": \"rel3\",
            \"type\": \"trace_to_event\",
            \"source\": \"trace1\",
            \"target\": \"C\"
        }
    ]
}
">>.