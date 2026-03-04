//! Simple NIF for benchmarking only

use rustler::{
    atoms,
    resource::ResourceArc,
    Encoder, Env, NifResult, Term,
};
use std::sync::Mutex;

atoms! {
    ok,
    error,
}

// Simple benchmark functions
#[rustler::nif]
pub fn nop(env: Env<'_>) -> NifResult<Term<'_>> {
    Ok(ok().encode(env))
}

#[rustler::nif]
pub fn int_passthrough(env: Env<'_>, n: i64) -> NifResult<Term<'_>> {
    Ok((ok(), n).encode(env))
}

#[rustler::nif]
pub fn atom_passthrough(env: Env<'_>, atom: rustler::types::Atom) -> NifResult<Term<'_>> {
    Ok((ok(), atom).encode(env))
}

#[rustler::nif]
pub fn small_list_passthrough(env: Env<'_>, list: Vec<rustler::types::Atom>) -> NifResult<Term<'_>> {
    Ok((ok(), list).encode(env))
}

#[rustler::nif]
pub fn tuple_passthrough(env: Env<'_>, tuple: (rustler::types::Atom, rustler::types::Atom, rustler::types::Atom)) -> NifResult<Term<'_>> {
    Ok((ok(), tuple).encode(env))
}

#[rustler::nif]
pub fn echo_json(env: Env<'_>, json: String) -> NifResult<Term<'_>> {
    Ok((ok(), json).encode(env))
}

#[rustler::nif]
pub fn echo_term(env: Env<'_>, term: Term<'_>) -> NifResult<Term<'_>> {
    Ok((ok(), term).encode(env))
}

#[rustler::nif]
pub fn echo_binary(env: Env<'_>, binary: Vec<u8>) -> NifResult<Term<'_>> {
    Ok((ok(), binary).encode(env))
}

#[rustler::nif]
pub fn echo_ocel_event(env: Env<'_>, event: String) -> NifResult<Term<'_>> {
    Ok((ok(), event).encode(env))
}

#[rustler::nif]
pub fn large_list_transfer(env: Env<'_>, list: Vec<i64>) -> NifResult<Term<'_>> {
    Ok((ok(), list).encode(env))
}

fn load(env: Env<'_>, _term: Term<'_>) -> bool {
    true
}

rustler::init!("process_mining_bridge", [
    nop,
    int_passthrough,
    atom_passthrough,
    small_list_passthrough,
    tuple_passthrough,
    echo_json,
    echo_term,
    echo_binary,
    echo_ocel_event,
    large_list_transfer,
], load = load);
