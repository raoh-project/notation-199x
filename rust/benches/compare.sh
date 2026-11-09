#!/bin/sh
# Compares the benchmarks of two commits: `sh rust/benches/compare.sh BASE HEAD`, from the root of a
# checkout where HEAD is checked out, on Linux with valgrind and the gungraun-runner of the version
# Cargo.toml names.
#
# The two are built here, with the one toolchain rust/rust-toolchain.toml names, and measured in
# one job. A count of instructions over LIMIT percent is reported and does not fail the job, so far.
# What fails it is a measure that was not made: a benchmark that did not run, a case that one side
# does not have, or a baseline that is missing.
#
# Where the benchmarks, the toolchain, Cargo.toml or Cargo.lock differ between the two, the two
# were not measured the same way, and it says so and compares nothing, which is not a pass of
# the comparison: the first commit that has the benchmarks has no baseline, and the one after a
# change of them is compared with its own parent from then on.
set -eu

base=$1
head=$2
limit=${LIMIT:-5}
out=${OUT:-"$PWD/perf-report"}
work=$(mktemp -d)
mkdir -p "$out"
summary=${GITHUB_STEP_SUMMARY:-"$out/summary.md"}

changed=$(git diff --name-only "$base" "$head" -- \
  rust/benches rust/Cargo.toml rust/Cargo.lock rust/rust-toolchain.toml)
if [ -n "$changed" ]; then
  {
    echo "### Rust: instructions and time"
    echo
    echo "Benchmark configuration changed between \`$base\` and \`$head\`, so the two were not measured the same way and are not compared:"
    echo
    echo "$changed" | sed 's/^/- `/; s/$/`/'
  } >>"$summary"
  echo "Benchmark configuration changed; nothing compared."
  exit 0
fi

export GUNGRAUN_HOME="$work/gungraun"
git worktree add --detach "$work/base" "$base" >/dev/null

echo "== instructions, $base"
# The base has a target directory of its own: one that two commits share is rebuilt by the
# modification times of the sources, which a checkout sets to now.
(cd "$work/base/rust" && CARGO_TARGET_DIR="$work/base-target" cargo bench --locked --bench unicode -- --save-baseline=base)
echo "== time, $base"
(cd "$work/base/rust" && CARGO_TARGET_DIR="$work/base-target" cargo bench --locked --bench wall >"$out/wall-base.jsonl")

echo "== instructions, $head"
status=0
(cd rust && cargo bench --locked --bench unicode -- \
  --baseline=base --callgrind-limits="ir=${limit}%" --save-summary=json) || status=$?
# 3 is a count over the limit, and anything else is a measure that was not made.
if [ "$status" -ne 0 ] && [ "$status" -ne 3 ]; then
  echo "The benchmarks failed with exit code $status." >&2
  exit 1
fi
echo "== time, $head"
(cd rust && cargo bench --locked --bench wall >"$out/wall-head.jsonl")

python3 rust/benches/report.py --home "$GUNGRAUN_HOME" --limit "$limit" \
  --base "$(printf %.8s "$base")" --head "$(printf %.8s "$head")" \
  --wall-base "$out/wall-base.jsonl" --wall-head "$out/wall-head.jsonl" \
  --out-md "$out/report.md" --out-json "$out/report.json"
cat "$out/report.md" >>"$summary"
cat "$out/report.md"

git worktree remove --force "$work/base"
