-record(ocel_id, {
    uuid :: binary(),
    rust_ptr :: pid(),
    timestamp :: erlang:timestamp()
}).

-record(slim_ocel_id, {
    uuid :: binary(),
    rust_ptr :: pid(),
    parent_ocel_id :: binary(),
    timestamp :: erlang:timestamp()
}).

-record(petri_net_id, {
    uuid :: binary(),
    rust_ptr :: pid(),
    timestamp :: erlang:timestamp()
}).