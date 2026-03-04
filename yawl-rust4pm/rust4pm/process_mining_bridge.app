{application, process_mining_bridge, [
    {applications, [
        kernel,
        stdlib,
        mnesia,
        lager,
        telemetry,
        logger
    ]},
    {description, "YAWL Process Mining Bridge"},
    {vsn, "1.0.0"},
    {modules, [
        process_mining_bridge,
        process_mining_bridge_app,
        process_mining_bridge_sup,
        yawl_bridge_health
    ]},
    {registered, [
        process_mining_bridge,
        process_mining_bridge_sup
    ]},
    {mod, {process_mining_bridge_app, []}},
    {env, [
        {log_level, info},
        {mnesia_dir, "mnesia"},
        {backup_interval, 300000},  % 5 minutes
        {health_check_interval, 30000},  % 30 seconds
        {bridge_config, #{
            process_mining => #{
                endpoint => "http://localhost:8080",
                timeout => 30000,
                retries => 3
            }
        }}
    ]},
    {start_phases, []},
    {included_applications, []}
]}.