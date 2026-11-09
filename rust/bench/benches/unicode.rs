//! Instruction counts, which a change of the layout of the program does not move the way it moves
//! the time. `cargo bench --bench unicode` runs them under Callgrind, on Linux, with the
//! `gungraun-runner` of the version `gungraun` in Cargo.toml is. `wall.rs` takes the time of the same
//! cases, from the same list in `common`.
//!
//! A case is named `function/input`. `report.py` divides the count by the scalar values and the
//! bytes of the input, which are not counted here.

use gungraun::{library_benchmark, library_benchmark_group, main};

#[macro_use]
mod common;
use common::*;

// The braces round the input are for the attribute macro, which names a variable with the span of the
// argument it is given: an argument that comes from the list in `common` is of another macro's, and
// without a block of this macro's round it the variable is not found.
macro_rules! benchmarks {
    ($($function:ident, $operation:ident -> $returns:ty: $($input:ident = $text:ident),+;)+) => {
        $(
            #[library_benchmark]
            $( #[bench::$input({ $text })] )+
            fn $function(text: &str) -> $returns {
                $operation(text)
            }
        )+

        library_benchmark_group!(name = unicode; benchmarks = $($function),+);
    };
}

cases!(benchmarks);

main!(library_benchmark_groups = unicode);
