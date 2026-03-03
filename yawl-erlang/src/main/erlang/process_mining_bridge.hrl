%% @copyright 2026 YAWL Foundation
%% @author YAWL Erlang Team
%% @doc Process mining bridge module - record definitions

-ifndef(PROCESS_MINING_BRIDGE_HRL).
-define(PROCESS_MINING_BRIDGE_HRL, true).

%%====================================================================
%% Records
%%====================================================================

%% Bridge state record
-record(state, {
    %% Process mining registry (ETS table name)
    registry :: atom(),

    %% Configuration
    config :: map(),

    %% Statistics
    stats :: map(),

    %% Process registry for active operations
    active_ops :: map()
}).

%% Import OCEL configuration record
-record(import_ocel_config, {
    file_path :: string(),
    event_key :: string(),
    lifecycle_key :: string(),
    object_types :: list(),
    attributes :: list(),
    timestamp_format :: string()
}).

%% Slim link configuration record
-record(slim_link_config, {
    target_table :: atom(),
    strategy :: direct | agglomerative,
    similarity_threshold :: float(),
    max_iterations :: integer()
}).

%% Declare discovery configuration record
-record(discover_config, {
    algorithm :: declare | alpha | inductive,
    threshold :: float(),
    sample_size :: integer(),
    max_duration :: integer()  %% in milliseconds
}).

%% Token replay configuration record
-record(replay_config, {
    alignment_mode :: exact | flexible | optimal,
    fitness_threshold :: float(),
    precision_threshold :: float(),
    cost_threshold :: float()
}).

%%====================================================================
%% Macro Definitions
%%====================================================================

%% Default values
-define(DEFAULT_REGISTRY, process_mining_registry).
-DEFAULT_SIMILARITY_THRESHOLD(0.8).
-DEFAULT_FITNESS_THRESHOLD(0.95).
-DEFAULT_PRECISION_THRESHOLD(0.90).
-DEFAULT_COST_THRESHOLD(0.1).

%% Default configurations
-DEFAULT_IMPORT_CONFIG(#import_ocel_config{
    event_key = "event",
    lifecycle_key = "lifecycle",
    object_types = ["activity", "object"],
    attributes = [],
    timestamp_format = "RFC3339"
}).

-DEFAULT_SLIM_LINK_CONFIG(#slim_link_config{
    strategy = direct,
    similarity_threshold = ?DEFAULT_SIMILARITY_THRESHOLD,
    max_iterations = 100
}).

-DEFAULT_DISCOVER_CONFIG(#discover_config{
    algorithm = declare,
    threshold = 0.9,
    sample_size = 1000,
    max_duration = 30000  %% 30 seconds
}).

-DEFAULT_REPLAY_CONFIG(#replay_config{
    alignment_mode = optimal,
    fitness_threshold = ?DEFAULT_FITNESS_THRESHOLD,
    precision_threshold = ?DEFAULT_PRECISION_THRESHOLD,
    cost_threshold = ?DEFAULT_COST_THRESHOLD
}).

%%====================================================================
%% Error Definitions
%%====================================================================

-define(ERR_NIF_NOT_LOADED, {error, nif_not_loaded}).
-define(ERR_INVALID_CONFIG, {error, invalid_config}).
-define(ERR_FILE_NOT_FOUND, {error, file_not_found}).
-define(ERR_INVALID_DATA, {error, invalid_data}).
-define(ERR_TIMEOUT, {error, timeout}).
-define(ERR_INTERNAL, {error, internal_error}).

-endif.