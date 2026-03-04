#!/usr/bin/env escript
%% -*- erlang -*-

%% EScript to run JTBD 2 conformance test
%% Usage: escript run_jtbd_2.erl

main(_) ->
    io:format("=== JTBD 2 Conformance Test Runner ===~n"),

    %% Add the path to our test modules
    code:add_patha("/Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/test/jtbd"),
    code:add_patha("/Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/test"),

    %% Try to run the test
    case jtbd_2_conformance:run() of
        {ok, Result} ->
            io:format("✓ JTBD 2 test completed successfully!~n"),
            io:format("  Output path: ~p~n", [maps:get(output, Result)]),
            io:format("  Conformance score: ~.3f~n", [maps:get(score, Result)]),

            %% Display conformance metrics
            Metrics = maps:get(metrics, Result),
            io:format("  Metrics: ~p~n", [Metrics]),

            %% Verify output file
            OutputPath = maps:get(output, Result),
            case file:read_file(OutputPath) of
                {ok, Content} ->
                    io:format("  Output file size: ~p bytes~n", [byte_size(Content)]);
                {error, Reason} ->
                    io:format("  Error reading output file: ~p~n", [Reason])
            end;

        {error, Reason} ->
            io:format("✗ JTBD 2 test failed: ~p~n", [Reason]),
            case Reason of
                {error, {'UnsupportedOperationException', Msg}} ->
                    io:format("  This is expected if token_replay is not implemented~n"),
                    io:format("  Message: ~s~n", [Msg]);
                _ ->
                    io:format("  Reason: ~p~n", [Reason])
            end
    end.