//! gen-ttl: Generates RDF ontology + 5 bridge artifacts from process_mining bindings.
//!
//! Usage: cargo run --bin gen-ttl -- --output-dir <path>
//!
//! Outputs (relative to --output-dir):
//!   ontology/pm-bridge.ttl
//!   lib/java/ProcessMiningCapability.java
//!   lib/java/ProcessMiningBridge.java
//!   lib/java/CapabilityRegistry.java
//!   lib/test/ProcessMiningCapabilityTest.java
//!   lib/erlang/process_mining_bridge.erl

use serde::Serialize;
use serde_json::Value;
use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

/// Registry types that map to UUID references in the gen_server state map.
const BRIDGE_BIG_TYPES: &[&str] = &[
    "PetriNet",
    "DirectlyFollowsGraph",
    "OCDirectlyFollowsGraph",
    "SlimLinkedOCEL",
    "IndexLinkedOCEL",
    "OCEL",
    "EventLog",
];

/// Convert a Rust binding name to a safe Erlang atom.
fn safe_atom(name: &str) -> String {
    name.replace("+++", "_ppp")
        .replace("++", "_pp")
        .replace('+', "_plus")
        .replace('-', "_")
        .replace(' ', "_")
}

/// Map a binding name to one of the 6 capability groups.
fn group_for(name: &str) -> &'static str {
    if name.starts_with("locel_") {
        "locel"
    } else if name.starts_with("discover") {
        "discovery"
    } else if matches!(
        name,
        "get_dotted_chart" | "get_event_timestamps" | "get_object_attribute_changes"
    ) {
        "analysis"
    } else if name == "oc_declare_conformance" {
        "conformance"
    } else if name.starts_with("export_")
        || name.starts_with("import_")
        || matches!(
            name,
            "add_init_exit_events_to_ocel"
                | "flatten_ocel_on"
                | "log_to_activity_projection"
                | "export_xes_event_log_to_file_path"
                | "export_xes_trace_stream_to_file"
        )
    {
        "io"
    } else {
        "utility"
    }
}

/// Return the Rust feature gate for a capability, if any.
fn feature_for(name: &str) -> Option<&'static str> {
    if name.contains("image_png") || name.contains("image_svg") {
        Some("graphviz-export")
    } else {
        None
    }
}

/// Detect the registry kind from a JSON schema.
/// Registry kinds are big types stored by UUID in the gen_server state map.
fn detect_registry_kind(schema: &Value) -> Option<String> {
    // Prefer explicit x-registry-ref
    if let Some(v) = schema.get("x-registry-ref").and_then(|v| v.as_str()) {
        return Some(v.to_owned());
    }
    // Fall back to title against known big types
    if let Some(title) = schema.get("title").and_then(|v| v.as_str()) {
        if BRIDGE_BIG_TYPES.iter().any(|&t| t == title) {
            return Some(title.to_owned());
        }
    }
    None
}

/// Map a JSON schema to a JVM type string.
fn jvm_type_for(schema: &Value, registry_kind: &Option<String>) -> String {
    if registry_kind.is_some() {
        return "String".to_owned(); // UUID reference
    }
    let title = schema.get("title").and_then(|v| v.as_str()).unwrap_or("");
    match title {
        "EventIndex" | "ObjectIndex" => return "long".to_owned(),
        _ => {}
    }
    let type_str = schema.get("type").and_then(|v| v.as_str()).unwrap_or("string");
    match type_str {
        "integer" => {
            let fmt = schema.get("format").and_then(|v| v.as_str()).unwrap_or("");
            if fmt == "int64" { "long".to_owned() } else { "int".to_owned() }
        }
        "number" => "double".to_owned(),
        "boolean" => "boolean".to_owned(),
        _ => "String".to_owned(), // string, array, object → JSON string
    }
}

/// Humanise a snake_case name into a readable label.
fn humanise(name: &str) -> String {
    name.replace('_', " ")
        .split_whitespace()
        .map(|w| {
            let mut c = w.chars();
            match c.next() {
                None => String::new(),
                Some(f) => f.to_uppercase().collect::<String>() + c.as_str(),
            }
        })
        .collect::<Vec<_>>()
        .join(" ")
}

/// Convert snake_case to lowerCamelCase for Java method names.
fn to_lower_camel(name: &str) -> String {
    let mut parts = name.split('_');
    let first = parts.next().unwrap_or("").to_owned();
    let rest: String = parts
        .map(|p| {
            let mut c = p.chars();
            match c.next() {
                None => String::new(),
                Some(f) => f.to_uppercase().collect::<String>() + c.as_str(),
            }
        })
        .collect();
    first + &rest
}

/// Full specification of a single capability.
#[derive(Debug, Serialize)]
struct CapSpec {
    name: String,
    erl_function: String,
    group: String,
    label: String,
    feature_flag: Option<String>,
    arity: usize,
    args: Vec<ArgSpec>,
    ret: RetSpec,
    consumes_input: bool,
    has_registry_args: bool,
    has_registry_return: bool,
    fixture_path: String,
    java_method: String,
    java_enum_constant: String,
}

#[derive(Debug, Serialize)]
struct ArgSpec {
    name: String,
    jvm_type: String,
    registry_kind: Option<String>,
    required: bool,
}

#[derive(Debug, Serialize)]
struct RetSpec {
    jvm_type: String,
    registry_kind: Option<String>,
}

fn build_capabilities() -> Vec<CapSpec> {
    let bindings = process_mining::bindings::list_functions();
    let mut caps: Vec<CapSpec> = bindings
        .iter()
        .map(|b| {
            let name = b.name;
            let erl_function = safe_atom(name);
            let group = group_for(name).to_owned();
            let feature_flag = feature_for(name).map(|s| s.to_owned());

            // Build label from docs or humanise name
            let docs = (b.docs)();
            let label = docs
                .first()
                .map(|d| d.trim().to_owned())
                .filter(|d| !d.is_empty())
                .unwrap_or_else(|| humanise(name));

            // Build args
            let arg_pairs: Vec<(String, Value)> = (b.args)();
            let required_names: Vec<String> = (b.required_args)();
            let args: Vec<ArgSpec> = arg_pairs
                .iter()
                .map(|(arg_name, schema)| {
                    let registry_kind = detect_registry_kind(schema);
                    let jvm_type = jvm_type_for(schema, &registry_kind);
                    let required = required_names.contains(arg_name);
                    ArgSpec {
                        name: arg_name.clone(),
                        jvm_type,
                        registry_kind,
                        required,
                    }
                })
                .collect();

            // Build return spec
            let ret_schema = (b.return_type)();
            let ret_registry_kind = detect_registry_kind(&ret_schema);
            let ret_jvm_type = jvm_type_for(&ret_schema, &ret_registry_kind);
            let ret = RetSpec {
                jvm_type: ret_jvm_type,
                registry_kind: ret_registry_kind,
            };

            let has_registry_args = args.iter().any(|a| a.registry_kind.is_some());
            let has_registry_return = ret.registry_kind.is_some();

            // Only add_init_exit_events_to_ocel consumes its primary registry input
            let consumes_input = name == "add_init_exit_events_to_ocel";

            let arity = args.len();
            let fixture_path = format!("{}/{}.fixture.json", group, erl_function);
            let java_method = to_lower_camel(&erl_function);
            let java_enum_constant = erl_function.to_uppercase();

            CapSpec {
                name: name.to_owned(),
                erl_function,
                group,
                label,
                feature_flag,
                arity,
                args,
                ret,
                consumes_input,
                has_registry_args,
                has_registry_return,
                fixture_path,
                java_method,
                java_enum_constant,
            }
        })
        .collect();

    // Sort deterministically: group then name
    caps.sort_by(|a, b| {
        a.group.cmp(&b.group).then_with(|| a.erl_function.cmp(&b.erl_function))
    });
    caps
}

// ─── TTL Generation ───────────────────────────────────────────────────────────

fn gen_ttl(caps: &[CapSpec]) -> String {
    let mut out = String::with_capacity(32_768);
    out.push_str(
        "@prefix bridge: <https://yawl.io/bridge#> .\n\
         @prefix rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n\
         @prefix rdfs:   <http://www.w3.org/2000/01/rdf-schema#> .\n\
         @prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .\n\n\
         # Generated by gen-ttl. DO NOT EDIT.\n\
         # Source: process_mining crate bindings via list_functions()\n\n",
    );

    for cap in caps {
        let uri = format!("bridge:cap_{}", cap.erl_function);
        out.push_str(&format!("{uri}\n    a bridge:NativeCall ;\n"));
        out.push_str(&format!("    bridge:erlModule \"process_mining_bridge\" ;\n"));
        out.push_str(&format!("    bridge:erlFunction \"{}\" ;\n", cap.erl_function));
        out.push_str(&format!("    bridge:group \"{}\" ;\n", cap.group));
        out.push_str(&format!("    bridge:arity {} ;\n", cap.arity));
        let escaped_label = cap.label.replace('"', "\\\"");
        out.push_str(&format!("    bridge:label \"{escaped_label}\" ;\n"));
        if let Some(ref ff) = cap.feature_flag {
            out.push_str(&format!("    bridge:featureFlag \"{ff}\" ;\n"));
        }
        out.push_str(&format!(
            "    bridge:consumesInput {} ;\n",
            cap.consumes_input
        ));
        out.push_str(&format!(
            "    bridge:fixtureFile \"{}\" ;\n",
            cap.fixture_path
        ));

        // Arg nodes
        for (i, _arg) in cap.args.iter().enumerate() {
            let arg_uri = format!("bridge:arg_{}_{}", cap.erl_function, i);
            out.push_str(&format!("    bridge:hasArg {arg_uri} ;\n"));
        }

        // Return node
        let ret_uri = format!("bridge:ret_{}", cap.erl_function);
        out.push_str(&format!("    bridge:returns {ret_uri} .\n\n"));

        // Emit arg nodes
        for (i, arg) in cap.args.iter().enumerate() {
            let arg_uri = format!("bridge:arg_{}_{}", cap.erl_function, i);
            out.push_str(&format!("{arg_uri}\n    a bridge:Arg ;\n"));
            out.push_str(&format!("    bridge:argName \"{}\" ;\n", arg.name));
            out.push_str(&format!("    bridge:argPosition {i} ;\n"));
            out.push_str(&format!("    bridge:jvmType \"{}\" ;\n", arg.jvm_type));
            out.push_str(&format!("    bridge:argRequired {} ;\n", arg.required));
            if let Some(ref rk) = arg.registry_kind {
                out.push_str(&format!("    bridge:registryKind \"{rk}\" ;\n"));
            }
            out.push_str("    .\n\n");
        }

        // Return node
        out.push_str(&format!("{ret_uri}\n    a bridge:ReturnType ;\n"));
        out.push_str(&format!("    bridge:returnJvmType \"{}\" ;\n", cap.ret.jvm_type));
        if let Some(ref rk) = cap.ret.registry_kind {
            out.push_str(&format!("    bridge:returnRegistryKind \"{rk}\" ;\n"));
        }
        out.push_str("    .\n\n");
    }

    out
}

// ─── Java Enum ───────────────────────────────────────────────────────────────

fn gen_java_enum(caps: &[CapSpec]) -> String {
    let mut out = String::with_capacity(16_384);
    out.push_str(
        "// Generated by gen-ttl. DO NOT EDIT.\n\
         package org.yawlfoundation.yawl.erlang.processmining;\n\n\
         /**\n\
          * Enumeration of all process_mining bridge capabilities.\n\
          * Generated from process_mining::bindings::list_functions().\n\
          */\n\
         public enum ProcessMiningCapability {\n\n",
    );

    for (i, cap) in caps.iter().enumerate() {
        let escaped = cap.label.replace('"', "\\\"");
        let is_last = i == caps.len() - 1;
        if let Some(ref ff) = cap.feature_flag {
            out.push_str(&format!(
                "    {}(\"{}\", \"{}\", \"{escaped}\", \"{ff}\")",
                cap.java_enum_constant, cap.erl_function, cap.group
            ));
        } else {
            out.push_str(&format!(
                "    {}(\"{}\", \"{}\", \"{escaped}\")",
                cap.java_enum_constant, cap.erl_function, cap.group
            ));
        }
        if is_last {
            out.push_str(";\n\n");
        } else {
            out.push_str(",\n");
        }
    }

    out.push_str(
        "    private final String erlFunction;\n\
         private final String group;\n\
         private final String label;\n\
         private final String featureFlag;\n\n\
         ProcessMiningCapability(String erlFunction, String group, String label) {\n\
             this(erlFunction, group, label, null);\n\
         }\n\n\
         ProcessMiningCapability(String erlFunction, String group, String label, String featureFlag) {\n\
             this.erlFunction = erlFunction;\n\
             this.group = group;\n\
             this.label = label;\n\
             this.featureFlag = featureFlag;\n\
         }\n\n\
         public String erlFunction() { return erlFunction; }\n\
         public String group()       { return group; }\n\
         public String label()       { return label; }\n\
         public boolean hasFeatureFlag() { return featureFlag != null; }\n\
         public String featureFlag() { return featureFlag; }\n\
         }\n",
    );
    out
}

// ─── Java Bridge ─────────────────────────────────────────────────────────────

fn gen_java_bridge(caps: &[CapSpec]) -> String {
    let mut out = String::with_capacity(32_768);
    out.push_str(
        "// Generated by gen-ttl. DO NOT EDIT.\n\
         package org.yawlfoundation.yawl.erlang.processmining;\n\n\
         import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;\n\
         import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;\n\
         import org.yawlfoundation.yawl.erlang.term.ErlAtom;\n\
         import org.yawlfoundation.yawl.erlang.term.ErlBinary;\n\
         import org.yawlfoundation.yawl.erlang.term.ErlFloat;\n\
         import org.yawlfoundation.yawl.erlang.term.ErlInteger;\n\
         import org.yawlfoundation.yawl.erlang.term.ErlTerm;\n\
         import java.nio.charset.StandardCharsets;\n\
         import java.util.List;\n\n\
         /**\n\
          * Java bridge to the process_mining Erlang gen_server.\n\
          * Each method performs an RPC call to process_mining_bridge.\n\
          */\n\
         public class ProcessMiningBridge {\n\n\
             private static final String MODULE = \"process_mining_bridge\";\n\
             private final ErlangNode node;\n\n\
             public ProcessMiningBridge(ErlangNode node) {\n\
                 this.node = node;\n\
             }\n\n",
    );

    for cap in caps {
        // Build Java parameter list
        let params: Vec<String> = cap
            .args
            .iter()
            .map(|a| format!("{} {}", a.jvm_type, to_lower_camel(&a.name)))
            .collect();
        let param_str = params.join(", ");

        // Build ErlTerm args list
        let erl_args: Vec<String> = cap
            .args
            .iter()
            .map(|a| {
                let java_name = to_lower_camel(&a.name);
                match a.jvm_type.as_str() {
                    "long" => {
                        // ErlInteger has a long convenience constructor
                        format!("new ErlInteger({java_name})")
                    }
                    "int" => {
                        // Widen int to long for ErlInteger(long) constructor
                        format!("new ErlInteger((long) {java_name})")
                    }
                    "double" => {
                        format!("new ErlFloat({java_name})")
                    }
                    "boolean" => {
                        format!("new ErlAtom({java_name} ? \"true\" : \"false\")")
                    }
                    _ => {
                        format!("new ErlBinary({java_name}.getBytes(StandardCharsets.UTF_8))")
                    }
                }
            })
            .collect();

        let erl_args_str = if erl_args.is_empty() {
            "List.of()".to_owned()
        } else {
            format!("List.of({})", erl_args.join(", "))
        };

        out.push_str(&format!(
            "    /** {} */\n\
             public String {}({param_str}) throws ErlangRpcException {{\n\
                 ErlTerm result = node.rpc(MODULE, \"{}\", {erl_args_str});\n\
                 if (result instanceof ErlBinary eb) {{\n\
                     return new String(eb.data(), StandardCharsets.UTF_8);\n\
                 }}\n\
                 return result.toString();\n\
             }}\n\n",
            cap.label, cap.java_method, cap.erl_function
        ));
    }

    out.push_str("}\n");
    out
}

// ─── Capability Registry ─────────────────────────────────────────────────────

fn gen_capability_registry(caps: &[CapSpec]) -> String {
    let mut out = String::with_capacity(8_192);
    out.push_str(
        "// Generated by gen-ttl. DO NOT EDIT.\n\
         package org.yawlfoundation.yawl.erlang.processmining;\n\n\
         import java.util.List;\n\
         import java.util.Map;\n\n\
         /**\n\
          * Static registry of all process_mining bridge capabilities.\n\
          */\n\
         public final class CapabilityRegistry {\n\n\
             private CapabilityRegistry() {}\n\n\
             /** All capabilities keyed by Erlang function name. */\n\
             public static final Map<String, ProcessMiningCapability> BY_ERL_FUNCTION =\n\
                 Map.copyOf(buildByErlFunction());\n\n\
             /** All capabilities grouped by group name. */\n\
             public static final Map<String, List<ProcessMiningCapability>> BY_GROUP =\n\
                 Map.copyOf(buildByGroup());\n\n\
             private static java.util.HashMap<String, ProcessMiningCapability> buildByErlFunction() {\n\
                 var m = new java.util.HashMap<String, ProcessMiningCapability>();\n",
    );

    for cap in caps {
        out.push_str(&format!(
            "        m.put(\"{}\", ProcessMiningCapability.{});\n",
            cap.erl_function, cap.java_enum_constant
        ));
    }

    // Group by group name
    let mut groups: BTreeMap<&str, Vec<&str>> = BTreeMap::new();
    for cap in caps {
        groups.entry(&cap.group).or_default().push(&cap.java_enum_constant);
    }

    out.push_str(
        "        return m;\n\
             }\n\n\
             private static java.util.HashMap<String, List<ProcessMiningCapability>> buildByGroup() {\n\
                 var m = new java.util.HashMap<String, List<ProcessMiningCapability>>();\n",
    );

    for (group, constants) in &groups {
        let list = constants
            .iter()
            .map(|c| format!("ProcessMiningCapability.{c}"))
            .collect::<Vec<_>>()
            .join(", ");
        out.push_str(&format!(
            "        m.put(\"{group}\", List.of({list}));\n"
        ));
    }

    out.push_str(
        "        return m;\n\
             }\n\
         }\n",
    );
    out
}

// ─── JUnit Test ──────────────────────────────────────────────────────────────

fn gen_junit_test(caps: &[CapSpec]) -> String {
    let mut out = String::with_capacity(16_384);
    out.push_str(
        "// Generated by gen-ttl. DO NOT EDIT.\n\
         package org.yawlfoundation.yawl.erlang.processmining;\n\n\
         import org.junit.jupiter.api.*;\n\
         import static org.junit.jupiter.api.Assumptions.assumeTrue;\n\n\
         /**\n\
          * Smoke tests for the process_mining bridge.\n\
          * All tests are skipped unless -Dbeam.available=true is set.\n\
          */\n\
         @Tag(\"pm-bridge\")\n\
         class ProcessMiningCapabilityTest {\n\n\
             @BeforeAll\n\
             static void checkBeam() {\n\
                 assumeTrue(Boolean.getBoolean(\"beam.available\"),\n\
                     \"Skipping pm-bridge tests: beam.available=true not set\");\n\
             }\n\n",
    );

    for cap in caps {
        let test_name = format!("test_{}", cap.erl_function);
        out.push_str(&format!(
            "    @Test\n\
             void {test_name}() {{\n\
                 // Capability: {} ({})\n\
                 // Fixture:    {}\n\
                 // TODO: load fixture, call bridge, assert result\n\
                 throw new UnsupportedOperationException(\n\
                     \"Test not yet implemented for {}\");\n\
             }}\n\n",
            cap.label, cap.group, cap.fixture_path, cap.erl_function
        ));
    }

    out.push_str("}\n");
    out
}

// ─── Erlang gen_server ────────────────────────────────────────────────────────

fn gen_erlang_genserver(caps: &[CapSpec]) -> String {
    let mut out = String::with_capacity(32_768);
    out.push_str(
        "%% Generated by gen-ttl. DO NOT EDIT.\n\
         %% process_mining_bridge: gen_server wrapping the process_mining Rust NIF.\n\
         %% State: map of UUID (binary) → opaque resource.\n\
         -module(process_mining_bridge).\n\
         -behaviour(gen_server).\n\n\
         -export([start_link/0, stop/0]).\n\
         -export([init/1, handle_call/3, handle_cast/2, handle_info/2,\n\
                  terminate/2, code_change/3]).\n\n\
         %% Public capability exports (one per capability)\n\
         -export([\n",
    );

    for (i, cap) in caps.iter().enumerate() {
        let arity_plus1 = cap.arity + 1; // +1 for gen_server call overhead via API fn
        let is_last = i == caps.len() - 1;
        if is_last {
            out.push_str(&format!("    {}/{}\n", cap.erl_function, cap.arity));
        } else {
            out.push_str(&format!("    {}/{},\n", cap.erl_function, cap.arity));
        }
        let _ = arity_plus1; // suppress warning
    }

    out.push_str("]).\n\n-define(SERVER, ?MODULE).\n\n");

    // Public API functions
    out.push_str("%%% ─── Public API ──────────────────────────────────────────\n\n");
    out.push_str("start_link() ->\n    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).\n\n");
    out.push_str("stop() ->\n    gen_server:stop(?SERVER).\n\n");

    for cap in caps {
        let params = (0..cap.arity)
            .map(|i| {
                if cap.args[i].registry_kind.is_some() {
                    format!("Uuid{i}")
                } else {
                    format!("Arg{i}")
                }
            })
            .collect::<Vec<_>>()
            .join(", ");
        if params.is_empty() {
            out.push_str(&format!(
                "{}() ->\n    gen_server:call(?SERVER, {{{}}}).\n\n",
                cap.erl_function, cap.erl_function
            ));
        } else {
            out.push_str(&format!(
                "{}({params}) ->\n    gen_server:call(?SERVER, {{{}, {params}}}).\n\n",
                cap.erl_function, cap.erl_function
            ));
        }
    }

    // gen_server callbacks
    out.push_str("%%% ─── gen_server callbacks ───────────────────────────────\n\n");
    out.push_str("init([]) -> {ok, #{}}.\n\n");
    out.push_str("handle_cast(_Msg, State) -> {noreply, State}.\n\n");
    out.push_str("handle_info(_Info, State) -> {noreply, State}.\n\n");
    out.push_str("terminate(_Reason, _State) -> ok.\n\n");
    out.push_str("code_change(_OldVsn, State, _Extra) -> {ok, State}.\n\n");

    out.push_str("%%% ─── handle_call dispatch ───────────────────────────────\n\n");

    for cap in caps {
        emit_handle_call(&mut out, cap);
    }

    out.push_str(
        "%% Catch-all\n\
         handle_call(Unknown, _From, State) ->\n\
             {reply, {error, {unknown_call, Unknown}}, State}.\n\n",
    );

    // UUID helper
    out.push_str(
        "%%% ─── Internal helpers ───────────────────────────────────\n\n\
         %% Generate a UUID v4 using crypto (OTP 28, no external deps).\n\
         uuid() ->\n\
             <<A:32, B:16, C:16, D:16, E:48>> = crypto:strong_rand_bytes(16),\n\
             C1 = (C band 16#0FFF) bor 16#4000,\n\
             D1 = (D band 16#3FFF) bor 16#8000,\n\
             iolist_to_binary(io_lib:format(\n\
                 \"~8.16.0b-~4.16.0b-~4.16.0b-~4.16.0b-~12.16.0b\",\n\
                 [A, B, C1, D1, E])).\n",
    );

    out
}

fn emit_handle_call(out: &mut String, cap: &CapSpec) {
    // Determine shape
    let shape = determine_shape(cap);

    // Build pattern match parameters for handle_call
    let call_params = (0..cap.arity)
        .map(|i| {
            if cap.args[i].registry_kind.is_some() {
                format!("Uuid{i}")
            } else {
                format!("Arg{i}")
            }
        })
        .collect::<Vec<_>>();

    let call_pat = if call_params.is_empty() {
        format!("{{{}}}", cap.erl_function)
    } else {
        format!("{{{}, {}}}", cap.erl_function, call_params.join(", "))
    };

    match shape {
        Shape::Pure => {
            // No registry args, no registry return
            let nif_args = call_params.join(", ");
            out.push_str(&format!(
                "handle_call({call_pat}, _From, State) ->\n\
                 Result = process_mining_nif:{}({}),\n\
                 {{reply, Result, State}};\n\n",
                cap.erl_function, nif_args
            ));
        }
        Shape::Store => {
            // No registry args, but registry return → store result
            let nif_args = call_params.join(", ");
            out.push_str(&format!(
                "handle_call({call_pat}, _From, State) ->\n\
                 Resource = process_mining_nif:{}({}),\n\
                 Uuid = uuid(),\n\
                 State1 = maps:put(Uuid, Resource, State),\n\
                 {{reply, {{ok, Uuid}}, State1}};\n\n",
                cap.erl_function, nif_args
            ));
        }
        Shape::Resolve => {
            // Has registry args, no registry return.
            // _Resource: checked for existence only; NIF receives UUID directly.
            let first_registry_idx = cap
                .args
                .iter()
                .position(|a| a.registry_kind.is_some())
                .unwrap();
            let uuid_param = format!("Uuid{first_registry_idx}");
            let nif_args = call_params.join(", ");
            out.push_str(&format!(
                "handle_call({call_pat}, _From, State) ->\n\
                 case maps:get({uuid_param}, State, not_found) of\n\
                     not_found -> {{reply, {{error, not_found}}, State}};\n\
                     _Resource ->\n\
                         Result = process_mining_nif:{0}({nif_args}),\n\
                         {{reply, Result, State}}\n\
                 end;\n\n",
                cap.erl_function
            ));
        }
        Shape::ResolveStore => {
            // Registry args AND registry return.
            // _Resource: existence check only; NIF receives UUID + args.
            let first_registry_idx = cap
                .args
                .iter()
                .position(|a| a.registry_kind.is_some())
                .unwrap();
            let uuid_param = format!("Uuid{first_registry_idx}");
            let nif_args = call_params.join(", ");
            out.push_str(&format!(
                "handle_call({call_pat}, _From, State) ->\n\
                 case maps:get({uuid_param}, State, not_found) of\n\
                     not_found -> {{reply, {{error, not_found}}, State}};\n\
                     _Resource ->\n\
                         NewResource = process_mining_nif:{0}({nif_args}),\n\
                         NewUuid = uuid(),\n\
                         State1 = maps:put(NewUuid, NewResource, State),\n\
                         {{reply, {{ok, NewUuid}}, State1}}\n\
                 end;\n\n",
                cap.erl_function
            ));
        }
        Shape::ConsumeStore => {
            // consumesInput=true AND registry return: maps:take removes old entry.
            let first_registry_idx = cap
                .args
                .iter()
                .position(|a| a.registry_kind.is_some())
                .unwrap();
            let uuid_param = format!("Uuid{first_registry_idx}");
            let nif_args = call_params.join(", ");
            out.push_str(&format!(
                "handle_call({call_pat}, _From, State) ->\n\
                 case maps:take({uuid_param}, State) of\n\
                     error -> {{reply, {{error, not_found}}, State}};\n\
                     {{_OldResource, State0}} ->\n\
                         NewResource = process_mining_nif:{0}({nif_args}),\n\
                         NewUuid = uuid(),\n\
                         State1 = maps:put(NewUuid, NewResource, State0),\n\
                         {{reply, {{ok, NewUuid}}, State1}}\n\
                 end;\n\n",
                cap.erl_function
            ));
        }
        Shape::Consume => {
            // consumesInput=true, no registry return: maps:take removes old entry.
            let first_registry_idx = cap
                .args
                .iter()
                .position(|a| a.registry_kind.is_some())
                .unwrap();
            let uuid_param = format!("Uuid{first_registry_idx}");
            let nif_args = call_params.join(", ");
            out.push_str(&format!(
                "handle_call({call_pat}, _From, State) ->\n\
                 case maps:take({uuid_param}, State) of\n\
                     error -> {{reply, {{error, not_found}}, State}};\n\
                     {{_OldResource, State0}} ->\n\
                         Result = process_mining_nif:{0}({nif_args}),\n\
                         {{reply, Result, State0}}\n\
                 end;\n\n",
                cap.erl_function
            ));
        }
    }
}

#[derive(Debug)]
enum Shape {
    Pure,         // no registry in/out
    Store,        // no registry in, registry out → store
    Resolve,      // registry in, no registry out
    ResolveStore, // registry in, registry out
    ConsumeStore, // consumesInput=true, registry out
    Consume,      // consumesInput=true, no registry out
}

fn determine_shape(cap: &CapSpec) -> Shape {
    match (
        cap.consumes_input,
        cap.has_registry_args,
        cap.has_registry_return,
    ) {
        (true, _, true) => Shape::ConsumeStore,
        (true, _, false) => Shape::Consume,
        (false, false, true) => Shape::Store,
        (false, true, false) => Shape::Resolve,
        (false, true, true) => Shape::ResolveStore,
        (false, false, false) => Shape::Pure,
    }
}

// ─── SPARQL query stubs ───────────────────────────────────────────────────────

fn gen_sparql_01() -> &'static str {
    "PREFIX bridge: <https://yawl.io/bridge#>\n\
     PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n\
     # Query 01: capability-enum — for ProcessMiningCapability.java\n\
     # Returns all capabilities sorted by group then erlFunction.\n\n\
     SELECT ?erlFunction ?group ?label ?featureFlag\n\
     WHERE {\n\
         ?cap a bridge:NativeCall ;\n\
              bridge:erlFunction ?erlFunction ;\n\
              bridge:group ?group ;\n\
              bridge:label ?label .\n\
         OPTIONAL { ?cap bridge:featureFlag ?featureFlag }\n\
     }\n\
     ORDER BY ?group ?erlFunction\n"
}

fn gen_sparql_02() -> &'static str {
    "PREFIX bridge: <https://yawl.io/bridge#>\n\
     PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n\
     # Query 02: erlang-genserver — for process_mining_bridge.erl\n\
     # Returns per-capability info needed to emit handle_call clauses.\n\n\
     SELECT ?erlFunction ?group ?arity ?consumesInput\n\
            ?argName ?argPosition ?registryKind ?argRequired\n\
            ?returnRegistryKind\n\
     WHERE {\n\
         ?cap a bridge:NativeCall ;\n\
              bridge:erlFunction ?erlFunction ;\n\
              bridge:group ?group ;\n\
              bridge:arity ?arity ;\n\
              bridge:consumesInput ?consumesInput ;\n\
              bridge:returns ?ret .\n\
         OPTIONAL {\n\
             ?cap bridge:hasArg ?arg .\n\
             ?arg bridge:argName ?argName ;\n\
                  bridge:argPosition ?argPosition ;\n\
                  bridge:argRequired ?argRequired .\n\
             OPTIONAL { ?arg bridge:registryKind ?registryKind }\n\
         }\n\
         OPTIONAL { ?ret bridge:returnRegistryKind ?returnRegistryKind }\n\
     }\n\
     ORDER BY ?group ?erlFunction ?argPosition\n"
}

fn gen_sparql_03() -> &'static str {
    "PREFIX bridge: <https://yawl.io/bridge#>\n\
     PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n\n\
     # Query 03: java-bridge — for ProcessMiningBridge.java\n\
     # Returns per-capability-argument info for Java method generation.\n\n\
     SELECT ?erlFunction ?group ?label\n\
            ?argName ?argPosition ?jvmType ?registryKind\n\
            ?returnJvmType ?returnRegistryKind\n\
     WHERE {\n\
         ?cap a bridge:NativeCall ;\n\
              bridge:erlFunction ?erlFunction ;\n\
              bridge:group ?group ;\n\
              bridge:label ?label ;\n\
              bridge:returns ?ret .\n\
         OPTIONAL {\n\
             ?cap bridge:hasArg ?arg .\n\
             ?arg bridge:argName ?argName ;\n\
                  bridge:argPosition ?argPosition ;\n\
                  bridge:jvmType ?jvmType .\n\
             OPTIONAL { ?arg bridge:registryKind ?registryKind }\n\
         }\n\
         ?ret bridge:returnJvmType ?returnJvmType .\n\
         OPTIONAL { ?ret bridge:returnRegistryKind ?returnRegistryKind }\n\
     }\n\
     ORDER BY ?group ?erlFunction ?argPosition\n"
}

fn gen_sparql_04() -> &'static str {
    "PREFIX bridge: <https://yawl.io/bridge#>\n\n\
     # Query 04: test-skeletons — for ProcessMiningCapabilityTest.java\n\
     # Returns per-capability fixture paths for @Test method stubs.\n\n\
     SELECT ?erlFunction ?group ?label ?fixtureFile\n\
     WHERE {\n\
         ?cap a bridge:NativeCall ;\n\
              bridge:erlFunction ?erlFunction ;\n\
              bridge:group ?group ;\n\
              bridge:label ?label ;\n\
              bridge:fixtureFile ?fixtureFile .\n\
     }\n\
     ORDER BY ?group ?erlFunction\n"
}

fn gen_sparql_05() -> &'static str {
    "PREFIX bridge: <https://yawl.io/bridge#>\n\n\
     # Query 05: capability-registry — for CapabilityRegistry.java\n\
     # Returns all capabilities with full metadata.\n\n\
     SELECT ?erlFunction ?group ?label ?arity ?featureFlag ?fixtureFile\n\
     WHERE {\n\
         ?cap a bridge:NativeCall ;\n\
              bridge:erlFunction ?erlFunction ;\n\
              bridge:group ?group ;\n\
              bridge:label ?label ;\n\
              bridge:arity ?arity ;\n\
              bridge:fixtureFile ?fixtureFile .\n\
         OPTIONAL { ?cap bridge:featureFlag ?featureFlag }\n\
     }\n\
     ORDER BY ?group ?erlFunction\n"
}

// ─── Tera template stubs ──────────────────────────────────────────────────────

fn gen_tera_01() -> &'static str {
    "// Template 01: ProcessMiningCapability.java.tera\n\
     // Uses output of SPARQL query 01-capability-enum.sparql\n\
     // Rendered by gen-ttl directly; this file is documentation.\n\
     package org.yawlfoundation.yawl.erlang.processmining;\n\
     public enum ProcessMiningCapability {\n\
     {% for row in capabilities %}\n\
       {{ row.java_enum_constant }}(\"{{ row.erl_function }}\", \"{{ row.group }}\", \"{{ row.label }}\"\
     {%- if row.feature_flag %}, \"{{ row.feature_flag }}\"{%- endif %}\
     ){%- if not loop.last %},{%- else %};{%- endif %}\n\
     {% endfor %}\n\
       private final String erlFunction;\n\
       private final String group;\n\
       private final String label;\n\
       private final String featureFlag;\n\
       // ... constructor and getters\n\
     }\n"
}

fn gen_tera_02() -> &'static str {
    "%% Template 02: process_mining_bridge.erl.tera\n\
     %% Uses output of SPARQL query 02-erlang-genserver.sparql\n\
     %% Rendered by gen-ttl directly; this file is documentation.\n\
     -module(process_mining_bridge).\n\
     -behaviour(gen_server).\n\
     {% for cap in capabilities %}\n\
     handle_call({ {{- cap.erl_function -}}\
     {%- for a in cap.args -%}, {{ a.name }}{%- endfor -%} }, _From, State) ->\n\
       %% shape: {% if cap.consumes_input %}consume{% elif cap.has_registry_args %}resolve{% else %}store{% endif %}\n\
     {% endfor %}\n"
}

fn gen_tera_03() -> &'static str {
    "// Template 03: ProcessMiningBridge.java.tera\n\
     // Uses output of SPARQL query 03-java-bridge.sparql\n\
     // Rendered by gen-ttl directly; this file is documentation.\n\
     package org.yawlfoundation.yawl.erlang.processmining;\n\
     public class ProcessMiningBridge {\n\
     {% for cap in capabilities %}\n\
       public String {{ cap.java_method }}(\
     {%- for a in cap.args %}{{ a.jvm_type }} {{ a.name }}{% if not loop.last %}, {% endif %}{%- endfor %}\
     ) throws ErlangRpcException { ... }\n\
     {% endfor %}\n\
     }\n"
}

fn gen_tera_04() -> &'static str {
    "// Template 04: ProcessMiningCapabilityTest.java.tera\n\
     // Uses output of SPARQL query 04-test-skeletons.sparql\n\
     // Rendered by gen-ttl directly; this file is documentation.\n\
     package org.yawlfoundation.yawl.erlang.processmining;\n\
     @Tag(\"pm-bridge\")\n\
     class ProcessMiningCapabilityTest {\n\
       @BeforeAll static void checkBeam() { assumeTrue(Boolean.getBoolean(\"beam.available\")); }\n\
     {% for cap in capabilities %}\n\
       @Test void test_{{ cap.erl_function }}() { /* fixture: {{ cap.fixture_path }} */ }\n\
     {% endfor %}\n\
     }\n"
}

fn gen_tera_05() -> &'static str {
    "// Template 05: CapabilityRegistry.java.tera\n\
     // Uses output of SPARQL query 05-capability-registry.sparql\n\
     // Rendered by gen-ttl directly; this file is documentation.\n\
     package org.yawlfoundation.yawl.erlang.processmining;\n\
     public final class CapabilityRegistry {\n\
       public static final Map<String, ProcessMiningCapability> BY_ERL_FUNCTION = ...;\n\
       public static final Map<String, List<ProcessMiningCapability>> BY_GROUP = ...;\n\
     {% for cap in capabilities %}\n\
       // {{ cap.erl_function }} → {{ cap.java_enum_constant }}\n\
     {% endfor %}\n\
     }\n"
}

fn gen_filter_js() -> &'static str {
    "// Custom Tera/Pebble filter: snake_case → lowerCamelCase\n\
     // Used in Java method name generation.\n\
     module.exports = function toCamelCase(str) {\n\
         return str.replace(/_([a-z])/g, (_, c) => c.toUpperCase());\n\
     };\n"
}

fn gen_ggen_toml(caps: &[CapSpec]) -> String {
    format!(
        "# ggen.toml — pm-bridge-ggen project manifest\n\
         # Generated capabilities: {}\n\
         # All artifacts rendered by gen-ttl (Rust); this manifest is documentation.\n\n\
         [project]\n\
         name = \"pm-bridge-ggen\"\n\
         version = \"1.0.0\"\n\n\
         [ontology]\n\
         files = [\"ontology/bridge-core.ttl\", \"ontology/pm-bridge.ttl\"]\n\n\
         [filters]\n\
         to_camel_case = \"filters/to_camel_case.js\"\n\n\
         [[rules]]\n\
         name = \"capability-enum\"\n\
         query = \"queries/01-capability-enum.sparql\"\n\
         template = \"templates/01-ProcessMiningCapability.java.tera\"\n\
         output = \"lib/java/ProcessMiningCapability.java\"\n\n\
         [[rules]]\n\
         name = \"erlang-genserver\"\n\
         query = \"queries/02-erlang-genserver.sparql\"\n\
         template = \"templates/02-process_mining_bridge.erl.tera\"\n\
         output = \"lib/erlang/process_mining_bridge.erl\"\n\n\
         [[rules]]\n\
         name = \"java-bridge\"\n\
         query = \"queries/03-java-bridge.sparql\"\n\
         template = \"templates/03-ProcessMiningBridge.java.tera\"\n\
         output = \"lib/java/ProcessMiningBridge.java\"\n\n\
         [[rules]]\n\
         name = \"test-skeletons\"\n\
         query = \"queries/04-test-skeletons.sparql\"\n\
         template = \"templates/04-ProcessMiningCapabilityTest.java.tera\"\n\
         output = \"lib/test/ProcessMiningCapabilityTest.java\"\n\n\
         [[rules]]\n\
         name = \"capability-registry\"\n\
         query = \"queries/05-capability-registry.sparql\"\n\
         template = \"templates/05-CapabilityRegistry.java.tera\"\n\
         output = \"lib/java/CapabilityRegistry.java\"\n",
        caps.len()
    )
}

fn gen_validate_mjs() -> &'static str {
    r#"#!/usr/bin/env node
// validate.mjs — verify lib/ matches golden/ after gen-ttl runs.
// Exit 0 = all match; Exit 1 = diff found.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = new URL('.', import.meta.url).pathname;
const LIB = join(ROOT, 'lib');
const GOLDEN = join(ROOT, 'golden');

let allOk = true;

function checkDir(rel) {
    const libDir = join(LIB, rel);
    const goldenDir = join(GOLDEN, rel);
    if (!existsSync(libDir)) { console.error(`MISSING lib/${rel}`); allOk = false; return; }
    if (!existsSync(goldenDir)) { console.log(`SKIP golden/${rel} (not yet committed)`); return; }
    for (const f of readdirSync(goldenDir)) {
        const libFile = join(libDir, f);
        const goldenFile = join(goldenDir, f);
        if (!existsSync(libFile)) {
            console.error(`MISSING lib/${rel}/${f}`);
            allOk = false;
            continue;
        }
        const libContent = readFileSync(libFile, 'utf8');
        const goldenContent = readFileSync(goldenFile, 'utf8');
        if (libContent !== goldenContent) {
            console.error(`DIFF lib/${rel}/${f} vs golden/${rel}/${f}`);
            allOk = false;
        } else {
            console.log(`OK   lib/${rel}/${f}`);
        }
    }
}

['java', 'erlang', 'test'].forEach(checkDir);

if (!allOk) { console.error('Validation FAILED'); process.exit(1); }
console.log('All artifacts match golden. Validation PASSED.');
"#
}

// ─── main ─────────────────────────────────────────────────────────────────────

fn write_file(dir: &Path, path: &str, content: &str) {
    let full_path = dir.join(path);
    if let Some(parent) = full_path.parent() {
        fs::create_dir_all(parent)
            .unwrap_or_else(|e| panic!("create_dir_all {parent:?}: {e}"));
    }
    fs::write(&full_path, content)
        .unwrap_or_else(|e| panic!("write {full_path:?}: {e}"));
    eprintln!("  wrote {path}");
}

fn main() {
    // Parse --output-dir <path> (default: current directory)
    let args: Vec<String> = std::env::args().collect();
    let output_dir = args
        .windows(2)
        .find(|w| w[0] == "--output-dir")
        .map(|w| PathBuf::from(&w[1]))
        .unwrap_or_else(|| PathBuf::from("."));

    eprintln!("gen-ttl: output-dir = {output_dir:?}");

    // ── Phase 1: collect capabilities ────────────────────────────────────────
    eprintln!("Phase 1: collecting process_mining bindings...");
    let caps = build_capabilities();
    let count = caps.len();
    assert!(
        count >= 45,
        "Expected ≥45 capabilities from process_mining::bindings::list_functions(), got {count}"
    );
    eprintln!("  found {count} capabilities across {} groups", {
        let g: std::collections::BTreeSet<&str> = caps.iter().map(|c| c.group.as_str()).collect();
        g.len()
    });

    // ── Phase 2: generate ontology ────────────────────────────────────────────
    eprintln!("Phase 2: generating ontology...");
    write_file(&output_dir, "ontology/pm-bridge.ttl", &gen_ttl(&caps));

    // ── Phase 3: generate bridge artifacts ───────────────────────────────────
    eprintln!("Phase 3: generating bridge artifacts...");
    write_file(&output_dir, "lib/java/ProcessMiningCapability.java", &gen_java_enum(&caps));
    write_file(&output_dir, "lib/java/ProcessMiningBridge.java", &gen_java_bridge(&caps));
    write_file(&output_dir, "lib/java/CapabilityRegistry.java", &gen_capability_registry(&caps));
    write_file(&output_dir, "lib/test/ProcessMiningCapabilityTest.java", &gen_junit_test(&caps));
    write_file(&output_dir, "lib/erlang/process_mining_bridge.erl", &gen_erlang_genserver(&caps));

    // ── Phase 4: generate supporting files ───────────────────────────────────
    eprintln!("Phase 4: generating supporting files...");
    write_file(&output_dir, "queries/01-capability-enum.sparql", gen_sparql_01());
    write_file(&output_dir, "queries/02-erlang-genserver.sparql", gen_sparql_02());
    write_file(&output_dir, "queries/03-java-bridge.sparql", gen_sparql_03());
    write_file(&output_dir, "queries/04-test-skeletons.sparql", gen_sparql_04());
    write_file(&output_dir, "queries/05-capability-registry.sparql", gen_sparql_05());
    write_file(&output_dir, "templates/01-ProcessMiningCapability.java.tera", gen_tera_01());
    write_file(&output_dir, "templates/02-process_mining_bridge.erl.tera", gen_tera_02());
    write_file(&output_dir, "templates/03-ProcessMiningBridge.java.tera", gen_tera_03());
    write_file(&output_dir, "templates/04-ProcessMiningCapabilityTest.java.tera", gen_tera_04());
    write_file(&output_dir, "templates/05-CapabilityRegistry.java.tera", gen_tera_05());
    write_file(&output_dir, "filters/to_camel_case.js", gen_filter_js());
    write_file(&output_dir, "ggen.toml", &gen_ggen_toml(&caps));
    write_file(&output_dir, "validate.mjs", gen_validate_mjs());

    // ── Summary ───────────────────────────────────────────────────────────────
    eprintln!("gen-ttl complete: {count} capabilities");
    println!("{count}");  // stdout for scripts to capture
}
