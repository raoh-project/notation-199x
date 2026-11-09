//! What both benchmarks read and call, so that the instruction counts and the wall time are of the
//! same text and the same functions under the same names.
//!
//! The text is in `fixtures/`, written by `fixtures/generate.py`. `include_str!` puts it in the
//! program, so that reading a file is in neither measure.

use notation199x::{Nfc, Nfd, Nfkc, lowercase, lowercase_within, uppercase};

pub const ASCII_LOWER: &str = include_str!("../fixtures/ascii_lower.txt");
pub const ASCII_MIXED: &str = include_str!("../fixtures/ascii_mixed.txt");
pub const LATIN_LOWER: &str = include_str!("../fixtures/latin_lower.txt");
pub const LATIN_MIXED: &str = include_str!("../fixtures/latin_mixed.txt");
pub const LATIN_DECOMPOSED: &str = include_str!("../fixtures/latin_decomposed.txt");
pub const GREEK_LOWER: &str = include_str!("../fixtures/greek_lower.txt");
pub const GREEK_UPPER: &str = include_str!("../fixtures/greek_upper.txt");
pub const JAPANESE: &str = include_str!("../fixtures/japanese.txt");
pub const JAPANESE_COMPAT: &str = include_str!("../fixtures/japanese_compat.txt");
pub const COMBINING: &str = include_str!("../fixtures/combining.txt");

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
