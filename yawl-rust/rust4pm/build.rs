use std::env;
use std::path::PathBuf;
use std::fs::File;
use std::io::Write;

fn main() {
    let out_dir = env::var("OUT_DIR").unwrap();
    let mut path = PathBuf::from(out_dir);
    path.push("rust4pm_nif.h");

    let mut file = File::create(path).unwrap();

    // Write NIF header
    write!(file, "#include <erl_nif.h>\n\n").unwrap();
}