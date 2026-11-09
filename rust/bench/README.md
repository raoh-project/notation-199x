# Benchmarks of the Rust crate

Instruction counts (under Callgrind) and wall time of normalization and case conversion, for a fixed
text in each of ASCII, Latin, Greek, Japanese and text with combining marks. CI compares two
commits with them on every pull request.

This is a crate of its own. The crate it measures is the one in `..`, and nothing here is part of
that crate: not its dependencies, its lock file or the package that is published. That separation is
what lets CI measure the commit a pull request is made on with the benchmarks the pull request has,
whatever it changes in them.

## What there is

- `benches/common/mod.rs` has the functions that are measured, the text, and the one list of cases
  (`cases!`). A case is `function/input`. `benches/unicode.rs` counts the cases and `benches/wall.rs`
  times them, both from that list.
- `fixtures/` is the text, written by `fixtures/generate.py` from a fixed seed. Changing an input is
  changing these files.
- `compare.sh BASE HEAD` measures both commits and writes the report. `report.py` makes it and
  refuses a comparison that cannot be made; `test_report.py` shows that it does.

## How a pull request is compared

The base branch as it is now, against the commit GitHub makes by merging the pull request into it.
Both are built with the benchmarks and the toolchain of the merge commit, so that the only difference
is the crate. A count of instructions over 5% is reported in the summary of the job and does not
fail it. What does fail it is a measure that was not made: a benchmark that did not run, a case that
only one side has, a missing baseline, or a base the benchmarks cannot be built on.

A pull request that needs what its base does not have (the first benchmark of a new function) puts
the line `Benchmark-baseline: none` in the message of a commit. It is then run against itself: the
counts must be the same in every case, which shows that the measuring works, and the report says that
nothing was compared.

Wall time is recorded and not judged: on a shared runner it moves by more than the changes it would
be asked to find.

## Running it

Callgrind runs on Linux only. With Valgrind and `cargo install gungraun-runner --version <the one in
Cargo.toml>`:

    cargo bench --bench unicode      # instruction counts
    cargo bench --bench wall         # wall time
    sh compare.sh <base> <head>      # from a checkout, as CI does

On another system, a Linux container with `--security-opt seccomp=unconfined` runs it.
