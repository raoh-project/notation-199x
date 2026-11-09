//! What both benchmarks read and call, so that the instruction counts and the wall time are of the
//! same text and the same functions under the same names.
//!
//! The text is in `fixtures/`, written by `fixtures/generate.py`. `include_str!` puts it in the
//! program, so that reading a file is in neither measure.

use notation199x::{Nfc, Nfd, Nfkc, lowercase, lowercase_within, uppercase};

pub const ASCII_LOWER: &str = include_str!("../../fixtures/ascii_lower.txt");
pub const ASCII_MIXED: &str = include_str!("../../fixtures/ascii_mixed.txt");
pub const LATIN_LOWER: &str = include_str!("../../fixtures/latin_lower.txt");
pub const LATIN_MIXED: &str = include_str!("../../fixtures/latin_mixed.txt");
pub const LATIN_DECOMPOSED: &str = include_str!("../../fixtures/latin_decomposed.txt");
pub const GREEK_LOWER: &str = include_str!("../../fixtures/greek_lower.txt");
pub const GREEK_UPPER: &str = include_str!("../../fixtures/greek_upper.txt");
pub const JAPANESE: &str = include_str!("../../fixtures/japanese.txt");
pub const JAPANESE_COMPAT: &str = include_str!("../../fixtures/japanese_compat.txt");
pub const COMBINING: &str = include_str!("../../fixtures/combining.txt");

/// A bound far above any answer here, so that the bounded functions run to the end and what they
/// cost beyond the unbounded ones is the bound's check.
pub const LONGEST: usize = 1 << 20;

pub fn nfc(text: &str) -> String {
    Nfc::normalize(text)
}

pub fn nfd(text: &str) -> String {
    Nfd::normalize(text)
}

pub fn nfkc(text: &str) -> String {
    Nfkc::normalize(text)
}

pub fn nfc_within(text: &str) -> Option<String> {
    Nfc::normalize_within(text, LONGEST)
}

pub fn lower(text: &str) -> String {
    lowercase(text)
}

pub fn upper(text: &str) -> String {
    uppercase(text)
}

pub fn lower_within(text: &str) -> Option<String> {
    lowercase_within(text, LONGEST)
}

/// The one list of the cases, which `unicode.rs` counts and `wall.rs` times: each function of a
/// group (`nfc_case`), the operation it calls, what that returns, and the cases of it, named by the
/// input they are given. `cases!(m)` calls the macro `m` with the list, so that what a case is
/// called, what it calls and what it reads are said once, and the count and the time of a case
/// cannot be of different inputs.
///
/// An input a function does not change (`lower_case/ascii_lower` is text already in lower case) is the
/// path that text that maps to itself takes.
macro_rules! cases {
    ($callback:ident) => {
        $callback! {
            nfc_case, nfc -> String:
                ascii_lower = ASCII_LOWER, latin_lower = LATIN_LOWER,
                latin_decomposed = LATIN_DECOMPOSED, greek_lower = GREEK_LOWER,
                japanese = JAPANESE, combining = COMBINING;
            nfd_case, nfd -> String:
                ascii_lower = ASCII_LOWER, latin_lower = LATIN_LOWER, greek_lower = GREEK_LOWER,
                japanese = JAPANESE, combining = COMBINING;
            nfkc_case, nfkc -> String:
                ascii_lower = ASCII_LOWER, latin_lower = LATIN_LOWER, japanese = JAPANESE,
                japanese_compat = JAPANESE_COMPAT, combining = COMBINING;
            nfc_within_case, nfc_within -> Option<String>:
                latin_decomposed = LATIN_DECOMPOSED;
            lower_case, lower -> String:
                ascii_lower = ASCII_LOWER, ascii_mixed = ASCII_MIXED, latin_lower = LATIN_LOWER,
                latin_mixed = LATIN_MIXED, greek_lower = GREEK_LOWER, greek_upper = GREEK_UPPER,
                japanese = JAPANESE;
            upper_case, upper -> String:
                ascii_lower = ASCII_LOWER, latin_lower = LATIN_LOWER, greek_lower = GREEK_LOWER,
                greek_upper = GREEK_UPPER, japanese = JAPANESE;
            lower_within_case, lower_within -> Option<String>:
                greek_upper = GREEK_UPPER;
        }
    };
}
