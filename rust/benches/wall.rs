//! Wall time, taken on the machine that runs it, for the cases `unicode.rs` counts. It is noisy on
//! a shared runner and moves with the layout of the program, so it is recorded and not judged.
//!
//! Prints one JSON object a line: the case, the scalar values and bytes of the input, and the median
//! and the spread (the smallest and the largest) of the time of a run in nanoseconds, over ROUNDS
//! rounds of a run that is repeated so that it lasts long enough to be read.

use std::hint::black_box;
use std::time::Instant;

mod common;
use common::*;

const ROUNDS: usize = 31;
const AT_LEAST_NANOS: u128 = 2_000_000;

fn time(mut run: impl FnMut()) -> (f64, f64, f64) {
    // How many runs make a round last long enough, after a first run that warms the caches.
    run();
    let mut per_round = 1usize;
    loop {
        let start = Instant::now();
        for _ in 0..per_round {
            run();
        }
        if start.elapsed().as_nanos() >= AT_LEAST_NANOS || per_round >= 1 << 20 {
            break;
        }
        per_round *= 2;
    }
    let mut rounds: Vec<f64> = (0..ROUNDS)
        .map(|_| {
            let start = Instant::now();
            for _ in 0..per_round {
                run();
            }
            start.elapsed().as_nanos() as f64 / per_round as f64
        })
        .collect();
    rounds.sort_by(|a, b| a.total_cmp(b));
    (rounds[ROUNDS / 2], rounds[0], rounds[ROUNDS - 1])
}

fn case(name: &str, text: &str, mut run: impl FnMut(&str)) {
    let (median, min, max) = time(|| run(black_box(text)));
    println!(
        "{{\"case\":\"{name}\",\"scalars\":{},\"bytes\":{},\"median_ns\":{median:.1},\"min_ns\":{min:.1},\"max_ns\":{max:.1}}}",
        text.chars().count(),
        text.len()
    );
}

fn main() {
    macro_rules! cases {
        ($function:ident, $name:literal: $($id:literal => $text:expr),+ $(,)?) => {
            $( case(concat!($name, "/", $id), $text, |t| { black_box($function(t)); }); )+
        };
    }
    cases!(nfc, "nfc_case": "ascii_lower" => ASCII_LOWER, "latin_lower" => LATIN_LOWER,
        "latin_decomposed" => LATIN_DECOMPOSED, "greek_lower" => GREEK_LOWER,
        "japanese" => JAPANESE, "combining" => COMBINING);
    cases!(nfd, "nfd_case": "ascii_lower" => ASCII_LOWER, "latin_lower" => LATIN_LOWER,
        "greek_lower" => GREEK_LOWER, "japanese" => JAPANESE, "combining" => COMBINING);
    cases!(nfkc, "nfkc_case": "japanese_compat" => JAPANESE_COMPAT, "combining" => COMBINING);
    cases!(nfc_within, "nfc_within_case": "latin_decomposed" => LATIN_DECOMPOSED);
    cases!(lower, "lower_case": "ascii_lower" => ASCII_LOWER, "ascii_mixed" => ASCII_MIXED,
        "latin_lower" => LATIN_LOWER, "latin_mixed" => LATIN_MIXED, "greek_lower" => GREEK_LOWER,
        "greek_upper" => GREEK_UPPER, "japanese" => JAPANESE);
    cases!(upper, "upper_case": "ascii_lower" => ASCII_LOWER, "latin_lower" => LATIN_LOWER,
        "greek_lower" => GREEK_LOWER, "greek_upper" => GREEK_UPPER, "japanese" => JAPANESE);
    cases!(lower_within, "lower_within_case": "greek_upper" => GREEK_UPPER);
}
