run() ->
    io:format("Testing NIF functions...~n"),
    process_mining_bridge:init_nif(),
    io:format("init_nif called~n"),
    
    R1 = process_mining_bridge:nop(),
    io:format("nop() = ~p~n", [R1]),
    
    R2 = process_mining_bridge:int_passthrough(42),
    io:format("int_passthrough(42) = ~p~n", [R2]),
    
    R3 = process_mining_bridge:atom_passthrough(ok),
    io:format("atom_passthrough(ok) = ~p~n", [R3]).
