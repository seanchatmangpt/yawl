{application, yawl_ml_bridge,
 [{description, "YAWL ML Bridge - DSPy and TPOT2 via Erlang/OTP"},
  {vsn, "6.0.0"},
  {registered, [yawl_ml_bridge, ml_bridge_sup, dspy_bridge, tpot2_bridge]},
  {mod, {ml_bridge_sup, []}},
  {applications,
   [kernel,
    stdlib,
    logger
   ]},
  {env,[]},
  {modules, [
    yawl_ml_bridge,
    ml_bridge_sup,
    dspy_bridge,
    tpot2_bridge
  ]},
  {licenses, ["LGPL-3.0"]},
  {links, [{"GitHub", "https://github.com/yawlfoundation/yawl"}]}
 ]}.
