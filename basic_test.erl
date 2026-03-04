%% Basic test without missing functions
-module(basic_test).
-export([run/0]).

run() ->
    io:format("Basic test running...~n"),
    
    %% Check if we can call functions that don't require the missing NIF
    case code:ensure_loaded(process_mining_bridge) of
        {module, Mod} ->
            io:format("✓ Module loaded: ~p~n", [Mod]),
            
            %% Try to call a function that might work
            case erlang:function_exported(Mod, get_nif_status, 0) of
                true ->
                    case Mod:get_nif_status() of
                        {ok, Status} ->
                            io:format("✓ NIF status: ~p~n", [Status]);
                        {error, Error} ->
                            io:format("✗ NIF error: ~p~n", [Error])
                    end;
                false ->
                    io:format("✗ get_nif_status not exported~n")
            end;
        {error, Error} ->
            io:format("✗ Module load error: ~p~n", [Error])
    end,
    
    io:format("Basic test completed~n").
