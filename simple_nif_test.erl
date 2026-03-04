#!/usr/bin/env escript
%% -*- erlang -*-
%% Simple test to check if NIF functions are available

main(_) ->
    io:format("Testing NIF availability...~n"),

    %% Try to load the NIF module directly
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("process_mining_bridge module loaded~n"),

            %% Check if functions exist
            Funs = [compute_dfg, align_trace, nop, int_passthrough, echo_json],
            lists:foreach(fun(F) ->
                case erlang:function_exported(process_mining_bridge, F, 1) of
                    true -> io:format("~p/1 exported: yes~n", [F]);
                    false -> io:format("~p/1 exported: no~n", [F])
                end
            end, Funs),

            %% Try to call a function
            case process_mining_bridge:nop() of
                {ok, Result} ->
                    io:format("nop() returned: ~p~n", [Result]);
                {error, Reason} ->
                    io:format("nop() failed: ~p~n", [Reason])
            end;
        Error ->
            io:format("Failed to load module: ~p~n", [Error])
    end.