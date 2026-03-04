#!/usr/bin/env escript

main(_) ->
    io:format('=== JTBD 3: OC-DECLARE CONSTRAINTS ===~n~n'),
    
    %% Start mnesia first
    case application:ensure_started(mnesia) of
        ok ->
            io:format('Mnesia started successfully~n'),
            %% Start the application
            case application:ensure_started(process_mining_bridge) of
                ok ->
                    io:format('Application started successfully~n'),
                    %% Step 1: Import OCEL
                    InputPath = "/tmp/jtbd/input/pi-sprint-ocel.json",
                    case process_mining_bridge:import_ocel_json(InputPath) of
                        {ok, OcelId} ->
                            io:format('OCEL_ID: ~p~n', [OcelId]),
                            
                            %% Step 2: Try slim_link (may not exist)
                            SlimResult = try
                                process_mining_bridge:slim_link_ocel(OcelId)
                            catch
                                error:undef -> {error, slim_link_not_implemented}
                            end,
                            io:format('SLIM_LINK_RESULT: ~p~n', [SlimResult]),
                            
                            %% Use OcelId or SlimId depending on result
                            WorkId = case SlimResult of
                                {ok, SlimId} -> SlimId;
                                _ -> OcelId
                            end,
                            
                            %% Step 3: Discover constraints
                            io:format('~nStep 3: Discovering constraints...~n'),
                            ConstraintResult = try
                                process_mining_bridge:discover_oc_declare(WorkId)
                            catch
                                error:undef -> {error, discover_oc_declare_not_implemented};
                                error:{nif_not_loaded, _} -> {error, nif_not_loaded}
                            end,
                            io:format('CONSTRAINT_RESULT: ~p~n', [ConstraintResult]),
                            
                            case ConstraintResult of
                                {ok, Constraints} when is_list(Constraints) ->
                                    io:format('CONSTRAINT_COUNT: ~p~n', [length(Constraints)]),
                                    case Constraints of
                                        [First|_] -> io:format('FIRST_CONSTRAINT: ~p~n', [First]);
                                        [] -> io:format('NO_CONSTRAINTS_DISCOVERED~n')
                                    end,
                                    %% Write to file
                                    Output = jsx:encode(#{constraints => Constraints}),
                                    file:write_file('/tmp/jtbd/output/pi-sprint-constraints.json', Output),
                                    io:format('WRITTEN: /tmp/jtbd/output/pi-sprint-constraints.json~n');
                                {error, Reason} ->
                                    io:format('CONSTRAINT_ERROR: ~p~n', [Reason]),
                                    %% Write placeholder
                                    Placeholder = jsx:encode(#{error => Reason, note => <<"Feature not implemented">>}),
                                    file:write_file('/tmp/jtbd/output/pi-sprint-constraints.json', Placeholder)
                            end;
                            
                        {error, Reason1} ->
                            io:format('IMPORT_ERROR: ~p~n', [Reason1]),
                            %% Write placeholder for import error
                            Placeholder = jsx:encode(#{error => Reason1, note => <<"OCEL import failed">>}),
                            file:write_file('/tmp/jtbd/output/pi-sprint-constraints.json', Placeholder)
                    end;
                {error, StartReason} ->
                    io:format('Failed to start application: ~p~n', [StartReason])
            end;
        {error, MnesiaReason} ->
            io:format('Failed to start mnesia: ~p~n', [MnesiaReason])
    end.
