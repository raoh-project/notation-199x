//! Wall time, taken on the machine that runs it, for the cases `unicode.rs` counts, from the same
//! list in `common`. It is noisy on a shared runner and moves with the layout of the program, so it
//! is recorded and not judged.
//!
//! Prints one JSON object a line: the case, the scalar values and bytes of the input, and the median
//! and the spread (the smallest and the largest) of the time of a run in nanoseconds, over ROUNDS
//! rounds of a run that is repeated so that it lasts long enough to be read.

use std::hint::black_box;
use std::time::Instant;

#[macro_use]
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

macro_rules! walls {
    ($($function:ident, $operation:ident -> $returns:ty: $($input:ident = $text:ident),+;)+) => {
        fn main() {
            $( $(
                case(
                    concat!(stringify!($function), "/", stringify!($input)),
                    $text,
                    |text| {
                        black_box($operation(text));
                    },
                );
            )+ )+
        }
    };
}

cases!(walls);
