{application, yawl, [
  {description, "YAWL Erlang/OTP 28 Process Mining Bridge"},
  {vsn, "6.0.0"},
  {registered, [yawl_sup, yawl_workflow, yawl_process_mining]},
  {mod, {yawl_app, []}},
  {applications, [kernel, stdlib, sasl]},
  {modules, [
    yawl_app, yawl_sup,
    yawl_workflow, yawl_process_mining, yawl_event_relay
  ]},
  {env, []},
  {licenses, ["LGPL-2.1"]},
  {links, [{"GitHub", "https://github.com/yawlfoundation/yawl"}]}
]}.
