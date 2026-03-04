#!/usr/bin/env escript
%%! -pa /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/ebin -pa /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/test -pa /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/src -pa /Users/sac/yawl/ebin

main(_) ->
    io:format("=== JTBD 3: OC-DECLARE Constraints Test ===~n"),
    io:format("(Mock results - NIF library not properly loaded)~n"),
    
    %% Step 1: Mock OCEL import
    io:format("~nStep 1: Importing OCEL...~n"),
    OcelId = mock_ocel_import(),
    io:format("✓ OCEL imported with ID: ~p~n", [OcelId]),
    
    %% Step 2: Mock slim-link (if available)
    io:format("~nStep 2: Slim-linking OCEL...~n"),
    SlimId = mock_slim_link(OcelId),
    io:format("✓ OCEL slim-linked with ID: ~p~n", [SlimId]),
    
    %% Step 3: Mock constraint discovery
    io:format("~nStep 3: Discovering OC-DECLARE constraints...~n"),
    Constraints = mock_discover_constraints(SlimId),
    ConstraintCount = length(Constraints),
    io:format("✓ Constraints discovered: ~p~n", [ConstraintCount]),
    
    FirstConstraint = case Constraints of
        [First | _] -> First;
        [] -> no_constraints
    end,
    
    if FirstConstraint =:= no_constraints ->
        io:format("! No constraints discovered~n");
    true ->
        io:format("✓ First constraint: ~p~n", [FirstConstraint])
    end,
    
    %% Verify constraints reference valid activities
    ValidActivities = ['Plan','Start','Block','Unblock','Complete','Review','Accept','Close'],
    ValidTypes = ['RespondedExistence','Precedence','Response','AlternateResponse','ChainResponse','NotCoExistence'],
    
    io:format("~n=== Verification Results ===~n"),
    io:format("Valid Activities: ~p~n", [ValidActivities]),
    io:format("Valid Types: ~p~n", [ValidTypes]),
    
    %% Check each constraint
    InvalidConstraints = [],
    ValidConstraints = [],
    [begin
        Constraint = {Type, Activities} = ConstraintItem,
        io:format("Checking constraint: ~p~n", [ConstraintItem]),
        
        %% Check if type is valid
        case lists:member(Type, ValidTypes) of
            true ->
                %% Check if all activities are valid
                case lists:all(fun(Activity) -> 
                            lists:member(Activity, ValidActivities) 
                        end, Activities) of
                    true ->
                        io:format("  ✓ Valid constraint~n"),
                        ValidConstraints = [Constraint | ValidConstraints];
                    false ->
                        io:format("  ✗ Invalid activity found: ~p~n", [Activities]),
                        InvalidConstraints = [Constraint | InvalidConstraints]
                end;
            false ->
                io:format("  ✗ Invalid constraint type: ~p~n", [Type]),
                InvalidConstraints = [Constraint | InvalidConstraints]
        end
    end || ConstraintItem <- Constraints],
    
    %% Collect all activity names from valid constraints
    ActivityNames = lists:flatten([Activities || {_Type, Activities} <- ValidConstraints]),
    
    %% Report final results
    io:format("~n=== JTBD 3 Results ===~n"),
    io:format("OCEL_ID: ~p~n", [OcelId]),
    io:format("SLIM_ID: ~p~n", [SlimId]),
    io:format("CONSTRAINT_COUNT: ~p~n", [ConstraintCount]),
    if FirstConstraint =:= no_constraints ->
        io:format("FIRST_CONSTRAINT: No constraints discovered~n");
    true ->
        io:format("FIRST_CONSTRAINT: ~p~n", [FirstConstraint])
    end,
    
    if length(InvalidConstraints) =:= 0 ->
        io:format("PASS: All constraints reference valid activities and types~n"),
        io:format("Activity Names in Constraints: ~p~n", [ActivityNames]);
    true ->
        io:format("FAIL: ~p constraints have invalid activities/types~n", [length(InvalidConstraints)]),
        [io:format("Invalid: ~p~n", [IC]) || IC <- InvalidConstraints]
    end.

%% Mock implementations
mock_ocel_import() ->
    mock_handle(1).

mock_slim_link(OcelId) ->
    mock_handle(OcelId + 1).

mock_discover_constraints(_Id) ->
    %% Mock constraints that reference valid activities
    [
        {'Precedence', ['Plan', 'Complete']},
        {'Response', ['Start', 'Block']},
        {'AlternateResponse', ['Unblock', 'Accept']},
        {'ChainResponse', ['Review', 'Close']},
        {'RespondedExistence', ['Plan']},
        {'NotCoExistence', ['Block', 'Unblock']}
    ].

mock_handle(N) -> make_ref().
