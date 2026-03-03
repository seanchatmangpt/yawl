use std::fs;
use std::path::Path;
use regex::Regex;
use serde::Serialize;

// Function signature structure
#[derive(Debug, Clone, Serialize)]
struct FunctionSignature {
    fn_name: String,
    return_type: String,
    parameters: Vec<Parameter>,
    is_unsafe: bool,
}

#[derive(Debug, Clone, Serialize)]
struct Parameter {
    name: String,
    type_: String,
}

// NativeCall triple structure
#[derive(Debug, Clone, Serialize)]
struct NativeCallTriple {
    subject: String,
    predicate: String,
    object: String,
}

// Extract function signatures from rust4pm lib.rs
// Handles multi-line signatures with #[no_mangle] attributes
fn extract_function_signatures(lib_rs_path: &Path) -> Vec<FunctionSignature> {
    let content = fs::read_to_string(lib_rs_path)
        .expect("Failed to read rust4pm lib.rs");

    let mut signatures = Vec::new();
    let mut current_sig = String::new();
    let mut in_function = false;
    let mut brace_count: i32 = 0;
    let mut found_no_mangle = false;

    for line in content.lines() {
        // Detect #[no_mangle] attribute
        if line.trim().starts_with("#[no_mangle]") {
            found_no_mangle = true;
            in_function = true;
            current_sig.clear();
            current_sig.push_str(line.trim());
            current_sig.push('\n');
            continue;
        }

        if in_function {
            current_sig.push_str(line.trim());
            current_sig.push('\n');

            // Count braces to find function body end
            brace_count += line.matches('{').count() as i32;
            brace_count -= line.matches('}').count() as i32;

            // When braces balance back to 0 (or we hit the opening brace on a body-less signature)
            if brace_count == 0 && (line.contains('}') || (current_sig.contains("->") && line.trim().ends_with(';'))) {
                // Parse the complete signature
                if let Some(sig) = parse_multi_line_signature(&current_sig, found_no_mangle) {
                    println!("Found function: {} -> {}", sig.fn_name, sig.return_type);
                    signatures.push(sig);
                }
                in_function = false;
                found_no_mangle = false;
                current_sig.clear();
            }
        }
    }

    signatures
}

/// Parse a multi-line function signature into a FunctionSignature
fn parse_multi_line_signature(signature_text: &str, is_no_mangle: bool) -> Option<FunctionSignature> {
    // Normalize whitespace: collapse all whitespace to single spaces
    let normalized: String = signature_text
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");

    // Extract function name
    let fn_match = Regex::new(r#"fn\s+(\w+)"#).unwrap();
    let fn_name = fn_match.captures(&normalized)?.get(1)?.as_str().to_string();

    // Extract parameters between parentheses
    let params_match = Regex::new(r#"\(([^)]*)\)"#).unwrap();
    let params_str = params_match
        .captures(&normalized)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().to_string())
        .unwrap_or_default();

    // Extract return type (after -> and before { or ;)
    let return_match = Regex::new(r#"->\s*([^{;]+)"#).unwrap();
    let return_type = return_match
        .captures(&normalized)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().trim().to_string())
        .unwrap_or_else(|| "void".to_string());

    // Parse parameters
    let parameters = parse_parameters(&params_str);

    // Check if unsafe
    let is_unsafe = signature_text.contains("unsafe");

    Some(FunctionSignature {
        fn_name,
        return_type,
        parameters,
        is_unsafe: is_unsafe || is_no_mangle && is_unsafe,
    })
}

// Parse function parameters
fn parse_parameters(params_str: &str) -> Vec<Parameter> {
    let mut params = Vec::new();

    if params_str.trim().is_empty() {
        return params;
    }

    let param_parts: Vec<&str> = params_str.split(',').collect();

    for part in param_parts {
        let part = part.trim();
        if part.is_empty() {
            continue;
        }

        // Split into type and name (simple assumption: last word is name)
        let parts: Vec<&str> = part.split_whitespace().collect();
        if parts.len() >= 2 {
            let type_ = parts[..parts.len()-1].join(" ");
            let name = parts.last().unwrap();

            // Handle pointer types like *const c_char
            let (type_, name) = if name.starts_with('*') {
                // This is a pointer type, the name is actually part of the type
                (part.to_string(), "".to_string())
            } else {
                (type_.to_string(), name.to_string())
            };

            params.push(Parameter {
                name,
                type_,
            });
        }
    }

    params
}

// Generate NativeCall triples in pm-bridge.ttl format
fn generate_native_call_triples(functions: &[FunctionSignature]) -> Vec<NativeCallTriple> {
    let mut triples = Vec::new();

    for func in functions {
        // Create capability triple
        let cap_name = format!("Cap_{}", func.fn_name.to_uppercase()
            .replace("RUST4PM_", "")
            .replace("PARSE_", "")
            .replace("LOG_", "")
            .replace("DISCOVER_", "")
            .replace("CHECK_", "")
            .replace("SIZEOF_", "")
            .replace("OFFSETOF_", "")
            .replace("_", "_"));

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", cap_name),
            predicate: "a yawl-bridge:BridgeCapability".to_string(),
            object: "".to_string(),
        });

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", cap_name),
            predicate: "yawl-bridge:capabilityName".to_string(),
            object: format!("\"{}\"", func.fn_name.to_uppercase()),
        });

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", cap_name),
            predicate: "yawl-bridge:capabilityDescription".to_string(),
            object: format!("\"Native function: {}\"", func.fn_name),
        });

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", cap_name),
            predicate: "yawl-bridge:nativeTarget".to_string(),
            object: format!("\"{}\"", func.fn_name),
        });

        // Create function triple
        let fn_name = format!("Fn_{}", func.fn_name.to_case(Case::Camel));
        let handle_name = format!("mh${}", fn_name);

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", fn_name),
            predicate: "a yawl-bridge:NativeFunction".to_string(),
            object: "".to_string(),
        });

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", fn_name),
            predicate: "yawl-bridge:fnHandleName".to_string(),
            object: format!("\"{}\"", handle_name),
        });

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", fn_name),
            predicate: "yawl-bridge:fnJavaName".to_string(),
            object: format!("\"{}\"", fn_name.to_case(Case::Camel)),
        });

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", fn_name),
            predicate: "yawl-bridge:fnNativeSymbol".to_string(),
            object: format!("\"{}\"", func.fn_name),
        });

        // Determine return type for function descriptor
        let return_type = if func.return_type.contains("void") {
            "void".to_string()
        } else if func.return_type.contains("usize") || func.return_type.contains("size_t") {
            "JAVA_LONG".to_string()
        } else {
            "ADDRESS".to_string()
        };

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", fn_name),
            predicate: "yawl-bridge:fnReturnType".to_string(),
            object: format!("\"{}\"", return_type),
        });

        // Generate function descriptor
        let mut descriptor_parts = Vec::new();

        // Add return type
        if return_type == "void" {
            descriptor_parts.push("FunctionDescriptor.ofVoid".to_string());
        } else {
            descriptor_parts.push("FunctionDescriptor.of".to_string());
        }

        // Add parameter layouts
        let mut param_layouts = Vec::new();
        for param in &func.parameters {
            if param.type_.contains("OcelLogHandle") {
                param_layouts.push("ValueLayout.ADDRESS".to_string());
            } else if param.type_.contains("*const c_char") || param.type_.contains("*mut c_char") {
                param_layouts.push("ValueLayout.ADDRESS".to_string());
            } else if param.type_.contains("usize") || param.type_.contains("size_t") || param.type_.contains("long") {
                param_layouts.push("ValueLayout.JAVA_LONG".to_string());
            } else if param.type_.contains("i64") {
                param_layouts.push("ValueLayout.JAVA_LONG".to_string());
            } else if param.type_.contains("c_double") || param.type_.contains("f64") {
                param_layouts.push("ValueLayout.JAVA_DOUBLE".to_string());
            } else {
                param_layouts.push("ValueLayout.ADDRESS".to_string()); // Default
            }
        }

        descriptor_parts.push(format!("({})", param_layouts.join(", ")));

        triples.push(NativeCallTriple {
            subject: format!("pm:{}", fn_name),
            predicate: "yawl-bridge:fnDescriptor".to_string(),
            object: format!("\"{}\"", descriptor_parts.join("")),
        });

        // Link capability to function
        triples.push(NativeCallTriple {
            subject: format!("pm:{}", fn_name),
            predicate: "yawl-bridge:forCapability".to_string(),
            object: format!("pm:{}", cap_name),
        });
    }

    triples
}

// Convert to camel case
trait ToCase {
    fn to_case(&self, case: Case) -> String;
}

#[derive(Debug, Clone, Copy)]
enum Case {
    Camel,
}

impl ToCase for str {
    fn to_case(&self, case: Case) -> String {
        match case {
            Case::Camel => {
                let mut result = String::new();
                let mut capitalize_next = false;

                for c in self.chars() {
                    if c == '_' {
                        capitalize_next = true;
                    } else if capitalize_next {
                        result.push(c.to_ascii_uppercase());
                        capitalize_next = false;
                    } else {
                        result.push(c.to_ascii_lowercase());
                    }
                }

                // Capitalize first letter
                if let Some(first) = result.chars().next() {
                    result.replace_range(0..1, &first.to_ascii_uppercase().to_string());
                }

                result
            }
        }
    }
}

// Generate complete TTL content
fn generate_ttl_content(triples: &[NativeCallTriple]) -> String {
    let mut output = String::new();

    // Add prefix declarations
    output.push_str(
        "@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:        <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd:         <http://www.w3.org/2001/XMLSchema#> .
@prefix yawl-bridge: <http://yawlfoundation.org/yawl/bridge#> .
@prefix pm:          <http://yawlfoundation.org/yawl/processmining/bridge#> .

"
    );

    // Group triples by subject
    let mut grouped: std::collections::HashMap<String, Vec<String>> = std::collections::HashMap::new();

    for triple in triples {
        let predicate_object = format!("    {} {} .", triple.predicate, triple.object);
        grouped.entry(triple.subject.clone()).or_insert_with(Vec::new).push(predicate_object);
    }

    // Generate triples
    for (subject, predicates) in grouped {
        output.push_str(&format!("{}\n", subject));
        for pred in predicates {
            output.push_str(&format!("{}\n", pred));
        }
        output.push_str("\n");
    }

    output
}

fn main() {
    let args: Vec<String> = std::env::args().collect();

    if args.len() < 2 {
        eprintln!("Usage: gen-ttl <rust4pm-lib.rs> [output-file]");
        eprintln!("  If output-file is not specified, writes to stdout");
        std::process::exit(1);
    }

    let lib_rs_path = Path::new(&args[1]);
    let output_path = args.get(2);

    println!("Extracting function signatures from: {}", lib_rs_path.display());

    // Extract function signatures
    let functions = extract_function_signatures(lib_rs_path);

    println!("Found {} functions", functions.len());

    // Generate triples
    let triples = generate_native_call_triples(&functions);

    // Generate TTL content
    let ttl_content = generate_ttl_content(&triples);

    // Write output
    if let Some(output_path) = output_path {
        let path = Path::new(output_path);
        fs::write(path, ttl_content)
            .expect("Failed to write output file");
        println!("Generated TTL file: {}", path.display());
    } else {
        print!("{}", ttl_content);
    }
}