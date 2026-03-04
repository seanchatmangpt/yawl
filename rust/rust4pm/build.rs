use std::env;

fn main() {
    // Add the current directory to the cargo metadata
    println!("cargo:rerun-if-changed=build.rs");

    // Configure target for BEAM NIF compilation
    if let Some(beam_path) = env::var_os("ERL_EI_INCLUDE_DIR") {
        // Include Erlang NIF headers
        println!("cargo:include={}", beam_path.to_string_lossy());
    }

    // Handle cross-compilation targets
    if let Ok(target) = env::var("TARGET") {
        if target.contains("linux") {
            println!("cargo:rustc-link-lib=dylib=dl");
            println!("cargo:rustc-link-lib=dylib=rt");
        } else if target.contains("darwin") {
            println!("cargo:rustc-link-lib=dylib=c");
        } else if target.contains("windows") {
            println!("cargo:rustc-link-lib=dylib=ws2_32");
        }
    }
}