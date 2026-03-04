%% Validation script for JTBD 3 test output
%% Validates the JSON output file structure and content

-module(validate_jtbd_3).
-export([validate_output/0, validate_constraint/1]).

validate_output() ->
    OutputPath = "/tmp/jtbd/output/pi-sprint-constraints.json",
    case filelib:is_file(OutputPath) of
        false ->
            io:format("❌ Output file missing: ~s~n", [OutputPath]),
            {error, output_file_missing};
        true ->
            case file:read_file(OutputPath) of
                {ok, Binary} ->
                    case jsx:decode(Binary, [{return_maps, true}]) of
                        #{<<"constraints">> := Constraints, <<"constraint_count">> := Count}
                          when is_list(Constraints) andalso is_integer(Count) ->

                            %% Validate each constraint
                            ValidationResults = [validate_constraint(C) || C <- Constraints],
                            Failed = [R || R <- ValidationResults, R =/= ok],

                            case Failed of
                                [] ->
                                    io:format("✅ Output validation passed~n"),
                                    io:format("✅ Found ~p valid constraints~n", [Count]),
                                    {ok, #{constraint_count => Count, constraints_valid => true}};
                                _ ->
                                    io:format("❌ ~p constraint validation failures: ~p~n",
                                             [length(Failed), Failed]),
                                    {error, {constraint_validation_failed, Failed}}
                            end;
                        InvalidJson ->
                            io:format("❌ Invalid JSON format: ~p~n", [InvalidJson]),
                            {error, invalid_json_format}
                    end;
                {error, Reason} ->
                    io:format("❌ Failed to read output file: ~p~n", [Reason]),
                    {error, {read_failed, Reason}}
            end
    end.

validate_constraint(Constraint) when is_map(Constraint) ->
    %% Check for required fields
    TypeOk = maps:is_key(<<"type">>, Constraint) orelse
             maps:is_key(<<"constraint_type">>, Constraint),
    ActivityOk = maps:is_key(<<"activity_a">>, Constraint) orelse
                 maps:is_key(<<"template">>, Constraint),
    SupportConfidenceOk = maps:is_key(<<"support">>, Constraint) orelse
                         maps:is_key(<<"confidence">>, Constraint),

    case TypeOk and ActivityOk and SupportConfidenceOk of
        true ->
            ok;
        false ->
            Missing = case TypeOk of
                false -> [type];
                _ -> []
            end ++ case ActivityOk of
                false -> [activity_a];
                _ -> []
            end ++ case SupportConfidenceOk of
                false -> [support_confidence];
                _ -> []
            end,
            {error, {missing_fields, Missing}}
    end;
validate_constraint(InvalidConstraint) ->
    io:format("❌ Invalid constraint format: ~p~n", [InvalidConstraint]),
    {error, invalid_constraint_format}.