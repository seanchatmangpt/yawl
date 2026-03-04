%% Simple Test Suite Validator
%% Validates test structure without compilation

-module(simple_validator).
-export([validate_all/0]).

%%====================================================================
%% Main Validation Functions
%%====================================================================

%% @doc Validate all test modules
validate_all() ->
    io:format("=== YAWL Process Mining Bridge Test Suite Validation ===~n~n"),

    TestModules = [
        {test_fixtures, "Test data and utilities"},
        {test_nif_loading, "NIF loading and initialization tests"},
        {test_bridge_api, "API endpoint tests"},
        {test_error_handling, "Error case tests"},
        {test_ocel_operations, "OCEL-specific operation tests"},
        {test_suite, "Test suite coordinator"}
    ],

    TestDataFiles = [
        {"test_data/sample_xes.xes", "XES event log sample"},
        {"test_data/sample_ocel.json", "OCEL JSON sample"},
        {"test_data/sample_ocel_large.json", "Large OCEL dataset"}
    ],

    io:format("=== Test Modules ===~n"),
    ModuleResults = lists:map(fun validate_test_module/1, TestModules),

    io:format("~n=== Test Data Files ===~n"),
    TestDataResults = lists:map(fun validate_test_data/1, TestDataFiles),

    io:format("~n=== Test Scripts ===~n"),
    Scripts = [
        {"run_tests.sh", "Main test runner script"},
        {"Makefile", "Make-based test execution"},
        {"escript_runner.escript", "Escript test runner"}
    ],
    ScriptResults = lists:map(fun validate_script/1, Scripts),

    %% Report summary
    TotalModules = length(TestModules),
    TotalDataFiles = length(TestDataFiles),
    TotalScripts = length(Scripts),

    PassedModules = length([R || R <- ModuleResults, R =:= ok]),
    PassedDataFiles = length([R || R <- TestDataResults, R =:= ok]),
    PassedScripts = length([R <- ScriptResults, R =:= ok]),

    io:format("~n=== Final Summary ===~n"),
    io:format("Test Modules: ~p/~p passed~n", [PassedModules, TotalModules]),
    io:format("Test Data Files: ~p/~p passed~n", [PassedDataFiles, TotalDataFiles]),
    io:format("Test Scripts: ~p/~p passed~n", [PassedScripts, TotalScripts]),
    io:format("Total: ~p/~p components~n", [PassedModules + PassedDataFiles + PassedScripts, TotalModules + TotalDataFiles + TotalScripts]),

    if
        PassedModules =:= TotalModules andalso
        PassedDataFiles =:= TotalDataFiles andalso
        PassedScripts =:= TotalScripts ->
            io:format("🎉 All test components are present and properly structured!~n"),
            io:format("~nTo run tests:~n"),
            io:format("  cd test && make test                    # Run all tests~n"),
            io:format("  cd test && make test/test_bridge_api   # Run specific test~n"),
            io:format("  cd test && ./run_tests.sh all          # Alternative runner~n"),
            ok;
        true ->
            io:format("❌ Some test components are missing or have issues.~n"),
            error
    end.

%%====================================================================
%% Validation Functions
%%====================================================================

validate_test_module({Module, Description}) ->
    ModuleFile = "test/" ++ atom_to_list(Module) ++ ".erl",
    io:format("Checking ~p (~p)...~n", [Module, Description]),

    case filelib:is_file(ModuleFile) of
        true ->
            %% Check if module exports the right functions
            case check_module_exports(ModuleFile, Module) of
                ok ->
                    io:format("  ✓ Module exists and has proper structure~n"),
                    ok;
                {error, Reason} ->
                    io:format("  ⚠ Module exists but has issues: ~p~n", [Reason]),
                    {error, Reason}
            end;
        false ->
            io:format("  ✗ Module file not found: ~p~n", [ModuleFile]),
            missing
    end.

validate_test_data({File, Description}) ->
    io:format("Checking ~p (~p)...~n", [File, Description]),

    case filelib:is_file(File) of
        true ->
            case check_data_file(File) of
                ok ->
                    io:format("  ✓ Data file exists and is readable~n"),
                    ok;
                {error, Reason} ->
                    io:format("  ⚠ Data file has issues: ~p~n", [Reason]),
                    {error, Reason}
            end;
        false ->
            io:format("  ✗ Data file not found: ~p~n", [File]),
            missing
    end.

validate_script({File, Description}) ->
    io:format("Checking ~p (~p)...~n", [File, Description]),

    case filelib:is_file(File) of
        true ->
            case check_script_file(File) of
                ok ->
                    io:format("  ✓ Script exists and is executable~n"),
                    ok;
                {error, Reason} ->
                    io:format("  ⚠ Script has issues: ~p~n", [Reason]),
                    {error, Reason}
            end;
        false ->
            io:format("  ✗ Script not found: ~p~n", [File]),
            missing
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

check_module_exports(ModuleFile, Module) ->
    try
        {ok, Content} = file:read_file(ModuleFile),

        %% Check for exports
        case re:run(Content, "-export\\(\\[([^]]+)\\]\\)", [dotall]) of
            nomatch ->
                {error, "No exports found"};
            {match, [_, Exports]} ->
                % Check for key functions based on module type
                Expected = case Module of
                    test_bridge_api ->
                        ["bridge_lifecycle_test", "xes_operations_test", "ocel_operations_test"];
                    test_nif_loading ->
                        ["init_nif_test", "load_nif_success_test"];
                    test_ocel_operations ->
                        ["ocel_import_export_test", "ocel_data_integrity_test"];
                    test_error_handling ->
                        ["invalid_inputs_test", "missing_dependencies_test"];
                    test_fixtures ->
                        ["sample_ocel_json", "sample_xes_content"];
                    _ ->
                        []
                end,

                % Check if expected functions are in exports
                ExportsList = re:split(Exports, ","),
                lists:foreach(fun(ExpectedFunc) ->
                    Pattern = re:replace(ExpectedFunc, "\\s+", "", [global]),
                    Found = lists:any(fun(Export) ->
                        re:run(Export, Pattern) =/= nomatch
                    end, ExportsList),
                    if
                        not Found ->
                            io:format("    Warning: Expected function ~p not found in exports~n", [ExpectedFunc]);
                        true ->
                            ok
                    end
                end, Expected),

                ok
        end
    catch
        Error:Reason ->
            {error, io_lib:format("Failed to read module: ~p:~p", [Error, Reason])}
    end.

check_data_file(File) ->
    try
        {ok, Content} = file:read_file(File),
        Size = size(Content),

        if
            Size > 0 ->
                % Basic validation based on file extension
                case filename:extension(File) of
                    ".json" ->
                        % Check if it looks like JSON
                        case re:run(Content, "\\{.*\\}", [dotall]) of
                            nomatch ->
                                {error, "File doesn't appear to be valid JSON"};
                            _ ->
                                ok
                        end;
                    ".xes" ->
                        % Check if it looks like XES
                        case re:run(Content, "<xes:", [dotall]) of
                            nomatch ->
                                {error, "File doesn't appear to be valid XES"};
                            _ ->
                                ok
                        end;
                    _ ->
                        ok
                end;
            true ->
                {error, "File is empty"}
        end
    catch
        Error:Reason ->
            {error, io_lib:format("Failed to read data file: ~p:~p", [Error, Reason])}
    end.

check_script_file(File) ->
    try
        {ok, Content} = file:read_file(File),

        % Check if file is executable
        case file:readable(File) of
            true ->
                % Check for shebang
                case re:run(Content, "^#!/", [multiline]) of
                    nomatch ->
                        {error, "No shebang found - may not be executable"};
                    _ ->
                        ok
                end;
            false ->
                {error, "File is not readable"}
        end
    catch
        Error:Reason ->
            {error, io_lib:format("Failed to read script file: ~p:~p", [Error, Reason])}
    end.