//! Instruction counts, which a change of the layout of the program does not move the way it moves
//! the time. `cargo bench --bench unicode` runs them under Callgrind, on Linux, with the
//! `gungraun-runner` of the version `gungraun` in Cargo.toml is. `wall.rs` takes the time.
//!
//! Each case is a function and an input, named `function/input`, and a case whose input does not
//! change what the function does (`lower_case/ascii_lower` is text already in lower case) is the
//! path a text that maps to itself takes. `report.py` divides the count by the scalar values and
//! the bytes of the input, which are not counted here.

use gungraun::{library_benchmark, library_benchmark_group, main};

mod common;
use common::*;

#[library_benchmark]
#[bench::ascii_lower(ASCII_LOWER)]
#[bench::latin_lower(LATIN_LOWER)]
#[bench::latin_decomposed(LATIN_DECOMPOSED)]
#[bench::greek_lower(GREEK_LOWER)]
#[bench::japanese(JAPANESE)]
#[bench::combining(COMBINING)]
fn nfc_case(text: &str) -> String {
    nfc(text)
}

#[library_benchmark]
#[bench::ascii_lower(ASCII_LOWER)]
#[bench::latin_lower(LATIN_LOWER)]
#[bench::greek_lower(GREEK_LOWER)]
#[bench::japanese(JAPANESE)]
#[bench::combining(COMBINING)]
fn nfd_case(text: &str) -> String {
    nfd(text)
}

#[library_benchmark]
#[bench::japanese_compat(JAPANESE_COMPAT)]
#[bench::combining(COMBINING)]
fn nfkc_case(text: &str) -> String {
    nfkc(text)
}

#[library_benchmark]
#[bench::latin_decomposed(LATIN_DECOMPOSED)]
fn nfc_within_case(text: &str) -> Option<String> {
    nfc_within(text)
}

#[library_benchmark]
#[bench::ascii_lower(ASCII_LOWER)]
#[bench::ascii_mixed(ASCII_MIXED)]
#[bench::latin_lower(LATIN_LOWER)]
#[bench::latin_mixed(LATIN_MIXED)]
#[bench::greek_lower(GREEK_LOWER)]
#[bench::greek_upper(GREEK_UPPER)]
#[bench::japanese(JAPANESE)]
fn lower_case(text: &str) -> String {
    lower(text)
}

#[library_benchmark]
#[bench::ascii_lower(ASCII_LOWER)]
#[bench::latin_lower(LATIN_LOWER)]
#[bench::greek_lower(GREEK_LOWER)]
#[bench::greek_upper(GREEK_UPPER)]
#[bench::japanese(JAPANESE)]
fn upper_case(text: &str) -> String {
    upper(text)
}

#[library_benchmark]
#[bench::greek_upper(GREEK_UPPER)]
fn lower_within_case(text: &str) -> Option<String> {
    lower_within(text)
}

library_benchmark_group!(
    name = unicode;
    benchmarks = nfc_case, nfd_case, nfkc_case, nfc_within_case, lower_case, upper_case, lower_within_case
);

main!(library_benchmark_groups = unicode);
