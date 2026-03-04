%% Additional test functions that need to be stub implementations
%% These are referenced in the test files but not implemented in the main module
-module(process_mining_bridge_stubs).

-define(UNSUPPORTED_OP, {'UnsupportedOperationException', 'Function not implemented yet'}).

%% @doc Get event count from log (stub implementation)
-spec log_event_count(map()) -> {ok, integer()} | {error, term()}.
log_event_count(Params) ->
    case maps:get(ocel_id, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Get object count from log (stub implementation)
-spec log_object_count(map()) -> {ok, integer()} | {error, term()}.
log_object_count(Params) ->
    case maps:get(ocel_id, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Get events from OCEL log (stub implementation)
-spec log_get_events(map()) -> {ok, reference()} | {error, term()}.
log_get_events(Params) ->
    case maps:get(ocel_id, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Get objects from OCEL log (stub implementation)
-spec log_get_objects(map()) -> {ok, reference()} | {error, term()}.
log_get_objects(Params) ->
    case maps:get(ocel_id, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Free events handle (stub implementation)
-spec events_free(map()) -> ok | {error, term()}.
events_free(Params) ->
    case maps:get(events_handle, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Free objects handle (stub implementation)
-spec objects_free(map()) -> ok | {error, term()}.
objects_free(Params) ->
    case maps:get(objects_handle, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.