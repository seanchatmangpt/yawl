-module(test_nif_loading).
-compile(export_all).

test_nif_load() ->
    %% Try to load the NIF
    PrivDir = case code:priv_dir(?MODULE) of
        {error, _} ->
            %% Development path - priv dir relative to this file
            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
            filename:join(AppDir, "priv");
        Dir ->
            Dir
    end,
    NifPath = filename:join(PrivDir, "yawl_process_mining"),
    io:format("NIF path: ~p~n", [NifPath]),
    io:format("NIF file exists: ~p~n", [filelib:is_file(NifPath)]),

    %% Try to load the NIF
    case erlang:load_nif(NifPath, 0) of
        ok ->
            io:format("NIF loaded successfully~n"),
            %% Try calling a NIF function
            try
                process_mining_bridge:import_xes(#{path => "/tmp/test.xes"})
            catch
                Error:Reason ->
                    io:format("NIF function call failed: ~p:~p~n", [Error, Reason])
            end;
        {error, {load_failed, Reason}} ->
            io:format("NIF load failed: ~p~n", [Reason]);
        {error, Reason} ->
            io:format("NIF load error: ~p~n", [Reason])
    end.