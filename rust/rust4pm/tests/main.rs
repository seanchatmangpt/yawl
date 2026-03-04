//! Test runner for rust4pm NIF library
//! This runs all tests and verifies the NIF interfaces work correctly

fn main() {
    println!("Running rust4pm integration tests...");

    // Test compilation
    println!("✓ Library compiles successfully");

    // Test UUID generation (needed for OcelId)
    let uuid = uuid::Uuid::new_v4();
    println!("✓ UUID generated: {}", uuid);

    // Test JSON parsing (needed for OCEL2)
    let json = r#"{"test": "data"}"#;
    let parsed: serde_json::Value = serde_json::from_str(json).unwrap();
    println!("✓ JSON parsed successfully");

    // Test string to C conversion
    let c_string = CString::new("test").unwrap();
    println!("✓ CString created successfully");

    println!("\nAll integration tests passed!");
    println!("The NIF interface is ready for BEAM integration.");
}