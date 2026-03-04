//! Simple NIF for benchmarking only

use rustler::{
    atoms,
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
pub fn atom_passthrough<'a>(env: Env<'a>, atom: rustler::types::Atom) -> NifResult<Term<'a>> {
    Ok((ok(), atom).encode(env))
}

#[rustler::nif]
pub fn small_list_passthrough<'a>(env: Env<'a>, list: Vec<rustler::types::Atom>) -> NifResult<Term<'a>> {
    Ok((ok(), list).encode(env))
}

#[rustler::nif]
pub fn tuple_passthrough<'a>(env: Env<'a>, tuple: (rustler::types::Atom, rustler::types::Atom, rustler::types::Atom)) -> NifResult<Term<'a>> {
    Ok((ok(), tuple).encode(env))
}

#[rustler::nif]
pub fn echo_json<'a>(env: Env<'a>, json: String) -> NifResult<Term<'a>> {
    Ok((ok(), json).encode(env))
}

#[rustler::nif]
pub fn echo_term<'a>(env: Env<'a>, term: Term<'a>) -> NifResult<Term<'a>> {
    Ok((ok(), term).encode(env))
}

#[rustler::nif]
pub fn echo_binary<'a>(env: Env<'a>, binary: Vec<u8>) -> NifResult<Term<'a>> {
    Ok((ok(), binary).encode(env))
}

#[rustler::nif]
pub fn echo_ocel_event<'a>(env: Env<'a>, event: String) -> NifResult<Term<'a>> {
    Ok((ok(), event).encode(env))
}

#[rustler::nif]
pub fn large_list_transfer<'a>(env: Env<'a>, list: Vec<i64>) -> NifResult<Term<'a>> {
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
