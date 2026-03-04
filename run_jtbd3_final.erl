-module(jtbd3_final).
-export([main/0]).

main() ->
    io:format("=== JTBD 3: OC-DECLARE Constraints Test ===~n"),
    
    %% Ensure process_mining_bridge is loaded
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("✓ process_mining_bridge loaded~n");
        _ ->
            io:format("✗ Failed to load process_mining_bridge~n"),
            halt(1)
    end,
    
    %% Step 1: Import OCEL
    io:format("~nStep 1: Importing OCEL...~n"),
    OcelPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
    case process_mining_bridge:import_ocel_json_path(OcelPath) of
        {ok, OcelId} ->
            io:format("✓ OCEL imported with ID: ~p~n", [OcelId]),
            
            %% Step 2: Slim-link OCEL
            io:format("~nStep 2: Slim-linking OCEL...~n"),
            case process_mining_bridge:slim_link_ocel(OcelId) of
                {ok, SlimId} ->
                    io:format("✓ OCEL slim-linked with ID: ~p~n", [SlimId]);
                    
                {error, Error2} ->
                    io:format("✗ Failed to slim-link OCEL: ~p~n", [Error2]),
                    halt(1)
            end;
            
        {error, Error1} ->
            io:format("✗ Failed to import OCEL: ~p~n", [Error1]),
            halt(1)
    end,
    
    %% Step 3: Discover OC-DECLARE constraints
    io:format("~nStep 3: Discovering OC-DECLARE constraints...~n"),
    case process_mining_bridge:discover_oc_declare(SlimId) of
        {ok, Constraints} ->
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
            end;
            
        {error, Error3} ->
            io:format("✗ Failed to discover constraints: ~p~n", [Error3]),
            halt(1)
    end.