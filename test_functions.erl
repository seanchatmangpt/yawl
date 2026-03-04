-module(test_functions).
-export([main/0]).

main() ->
    %% Test if we can call the NIF functions
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("NIF loaded: ~p~n", [code:is_loaded(process_mining_bridge)]),

            %% Test compute_dfg_from_events
            case process_mining_bridge:compute_dfg_from_events(["A->B->C", "A->C->D"]) of
                {ok, Result1} ->
                    io:format("compute_dfg_from_events result: ~s~n", [Result1]);
                {error, {nif_not_loaded, _}} ->
                    io:format("compute_dfg_from_events not loaded~n");
                {error, Error1} ->
                    io:format("compute_dfg_from_events error: ~p~n", [Error1])
            end,

            %% Test align_trace
            TestPetriNet = jsx:encode([
                {<<"places">>, [
                    {<<"id">>, <<"p_start">>, {<<"is_start">>, true}, {<<"initial_marking">>, 1}},
                    {<<"id">>, <<"p_end">>, {<<"is_end">>, true}, {<<"initial_marking">>, 0}}
                ]},
                {<<"transitions">>, [
                    {<<"id">>, <<"t_A">>, {<<"name">>, <<"A">>}},
                    {<<"id">>, <<"t_B">>, {<<"name">>, <<"B">>}},
                    {<<"id">>, <<"t_C">>, {<<"name">>, <<"C">>}}
                ]},
                {<<"arcs">>, [
                    {<<"source">>, <<"p_start">>, <<"target">>, <<"t_A">>},
                    {<<"source">>, <<"t_A">>, <<"target">>, <<"t_B">>},
                    {<<"source">>, <<"t_B">>, <<"target">>, <<"t_C">>},
                    {<<"source">>, <<"t_C">>, <<"target">>, <<"p_end">>}
                ]}
            ]),
            case process_mining_bridge:align_trace(["A", "B", "C"], TestPetriNet) of
                {ok, Result2} ->
                    io:format("align_trace result: ~s~n", [Result2]);
                {error, {nif_not_loaded, _}} ->
                    io:format("align_trace not loaded~n");
                {error, Error2} ->
                    io:format("align_trace error: ~p~n", [Error2])
            end,

            %% Test existing function to see if NIF is working at all
            case process_mining_bridge:nop() of
                {ok, ok} ->
                    io:format("Existing NIF function works~n");
                {error, {nif_not_loaded, _}} ->
                    io:format("NIF not loaded at all~n");
                {error, Error3} ->
                    io:format("Existing NIF error: ~p~n", [Error3])
            end,

            process_mining_bridge:stop();
        {error, Reason} ->
            io:format("Failed to start: ~p~n", [Reason])
    end.