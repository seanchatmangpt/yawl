%%===================================================================
%% JTBD 3 Test: OC-DECLARE Constraints Discovery
%%
%% This test validates the discovery of object-centric DECLARE constraints
%% from an OCEL event log. The test follows these steps:
%% 1. Import OCEL JSON from /tmp/jtbd/input/pi-sprint-ocel.json
%% 2. Optionally apply slim_link_ocel optimization
%% 3. Discover OC-DECLARE constraints
%% 4. Write results to /tmp/jtbd/output/pi-sprint-constraints.json
%%

-module(jtbd_3_constraints).

%% API exports
-export([run/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the JTBD 3 constraint discovery test
%% Returns {ok, #{constraints => Constraints}} | {error, Reason}
run() ->
    io:format("Starting JTBD 3: OC-DECLARE Constraints Discovery Test~n", []),

    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    OutputPath = "/tmp/jtbd/output/pi-sprint-constraints.json",

    %% Ensure output directory exists
    ensure_output_dir("/tmp/jtbd/output"),

    case process_mining_bridge:import_ocel_json(InputPath) of
        {ok, OcelHandle} ->
            %% Step 1: Optionally apply slim_link_ocel optimization
            try
                process_mining_bridge:slim_link_ocel(OcelHandle),
                io:format("Applied slim_link_ocel optimization~n", [])
            catch
                _:_ ->
                    io:format("slim_link_ocel optimization failed, continuing without it~n", [])
            end,

            %% Step 2: Discover OC-DECLARE constraints
            case process_mining_bridge:discover_oc_declare_constraints(OcelHandle) of
                {ok, ConstraintsJson} ->
                    %% Step 3: Write output
                    case write_output_file(OutputPath, ConstraintsJson) of
                        ok ->
                            %% Clean up
                            process_mining_bridge:free_handle(OcelHandle),

                            %% Parse and analyze constraints
                            case jsx:decode(ConstraintsJson, [{return_maps, true}]) of
                                #{<<"constraints">> := ConstraintsList} ->
                                    {ok, #{
                                        constraints => ConstraintsList,
                                        total_count => length(ConstraintsList),
                                        constraint_types => extract_constraint_types(ConstraintsList),
                                        stats => analyze_constraint_stats(ConstraintsList)
                                    }};
                                _ ->
                                    {ok, #{
                                        constraints => [],
                                        total_count => 0,
                                        constraint_types => #{},
                                        stats => #{}
                                    }}
                            end;
                        {error, WriteReason} ->
                            process_mining_bridge:free_handle(OcelHandle),
                            {error, {write_failed, WriteReason}}
                    end;
                {error, DeclReason} ->
                    process_mining_bridge:free_handle(OcelHandle),
                    {error, {declare_failed, DeclReason}}
            end;
        {error, ImportReason} ->
            {error, {import_failed, ImportReason}}
    end.

%%====================================================================
%% Analysis Functions
%%====================================================================

%% @doc Extract constraint types from constraint list
extract_constraint_types(Constraints) ->
    Types = lists:foldl(fun(Constraint, Acc) ->
        case jsx:get_value(<<"type">>, Constraint) of
            undefined -> Acc;
            Type ->
                maps:update_with(Type, fun(V) -> V + 1 end, 1, Acc)
            end
    end, #{}, Constraints),

    %% Convert to list for JSON output
    maps:fold(fun(Type, Count, Acc) ->
        [{Type, Count} | Acc]
    end, [], Types).

%% @doc Analyze constraint statistics
analyze_constraint_stats(Constraints) ->
    Active = length([C || C <- Constraints, jsx:get_value(<<"active">>, C, false) =:= true]),
    Inactive = length([C || C <- Constraints, jsx:get_value(<<"active">>, C, false) =:= false]),

    #{
        active => Active,
        inactive => Inactive,
        total => length(Constraints)
    }.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Write output file
write_output_file(Path, Data) ->
    case filelib:ensure_dir(Path) of
        ok ->
            case file:write_file(Path, Data) of
                ok -> ok;
                {error, Reason} -> {error, Reason}
            end;
        {error, Reason} ->
            {error, Reason}
    end.

%% @doc Ensure output directory exists
ensure_output_dir(Dir) ->
    filelib:ensure_dir(Dir ++ "/").