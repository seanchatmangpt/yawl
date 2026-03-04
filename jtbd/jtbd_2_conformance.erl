%% JTBD 2: OCEL → Petri Net → Conformance Scoring
%% Tests complete conformance checking pipeline for sprint process analysis

-module(jtbd_2_conformance).

%% API exports
-export([run/0]).

%%====================================================================
%% Test Implementation
%%====================================================================

%% @doc Run the JTBD 2 test pipeline
%% Returns {ok, #{output => OutputPath, score => Score}} | {error, Reason}
run() ->
    io:format("Starting JTBD 2: Conformance Test~n", []),

    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    OutputPath = "/tmp/jtbd/output/pi-sprint-conformance.json",

    %% Ensure output directory exists
    ensure_output_dir("/tmp/jtbd/output"),

    case process_mining_bridge:import_ocel_json(InputPath) of
        {ok, OcelHandle} ->
            %% Step 1: Discover DFG
            case process_mining_bridge:discover_dfg(OcelHandle) of
                {ok, DfgJson} ->
                    %% Step 2: Discover Petri net
                    case process_mining_bridge:discover_petri_net(OcelHandle) of
                        {ok, PetriNetJson} ->
                            %% Step 3: Run conformance checking
                            case process_mining_bridge:check_conformance(OcelHandle, PetriNetJson) of
                                {ok, ConformanceResult} ->
                                    %% Step 4: Write output
                                    case write_output_file(OutputPath, ConformanceResult) of
                                        ok ->
                                            %% Clean up
                                            process_mining_bridge:free_handle(OcelHandle),

                                            %% Extract score
                                            Score = extract_conformance_score(ConformanceResult),

                                            {ok, #{
                                                output => OutputPath,
                                                score => Score,
                                                metrics => ConformanceResult
                                            }};
                                        {error, WriteReason} ->
                                            process_mining_bridge:free_handle(OcelHandle),
                                            {error, {write_failed, WriteReason}}
                                    end;
                                {error, ConfReason} ->
                                    process_mining_bridge:free_handle(OcelHandle),
                                    {error, {conformance_failed, ConfReason}}
                            end;
                        {error, PetriReason} ->
                            process_mining_bridge:free_handle(OcelHandle),
                            {error, {petri_net_failed, PetriReason}}
                    end;
                {error, DfgReason} ->
                    process_mining_bridge:free_handle(OcelHandle),
                    {error, {dfg_failed, DfgReason}}
            end;
        {error, ImportReason} ->
            {error, {import_failed, ImportReason}}
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Extract conformance score from result
extract_conformance_score(Binary) ->
    try
        Decoded = jsx:decode(Binary, [{return_maps, true}]),
        case maps:get(<<"fitness">>, Decoded, undefined) of
            undefined when is_map(Decoded) ->
                case maps:get(<<"conformance_score">>, Decoded, undefined) of
                    undefined -> calculate_derived_score(Decoded);
                    Score -> Score
                end;
            undefined -> 0.0;
            Score -> Score
        end
    catch
        _:_ -> 0.0
    end.

%% @doc Calculate derived conformance score from available metrics
calculate_derived_score(Metrics) ->
    Fitness = maps:get(fitness, Metrics, undefined),
    Precision = maps:get(precision, Metrics, undefined),
    Recall = maps:get(recall, Metrics, undefined),

    case {Fitness, Precision, Recall} of
        {F, _, _} when is_number(F) ->
            F;  % Use fitness if available
        {_, P, R} when is_number(P) and is_number(R) ->
            (P + R) / 2;  % Average of precision and recall
        {_, P, _} when is_number(P) ->
            P;  % Use precision if recall not available
        {_, _, R} when is_number(R) ->
            R;  % Use recall if precision not available
        _ ->
            0.0  % Default if no metrics found
    end.

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