#!/bin/sh
# Compares the benchmarks of two commits: `sh rust/bench/compare.sh BASE HEAD`, in a checkout of the
# repository, on Linux with valgrind and the gungraun-runner of the version Cargo.toml names here.
#
# This directory is the instrument and rust/src is what it measures, and this is what makes the two
# commits comparable whatever a commit changes: both are measured with the instrument of HEAD. The
# instrument of HEAD is put beside the crate of BASE, and both are built with the toolchain
# rust/rust-toolchain.toml of HEAD names, so that the only difference between the two is the crate.
# A pull request that changes the benchmarks, their inputs or the toolchain is compared the same way,
# with its own benchmarks run on both.
#
# A count of instructions over LIMIT percent is reported and does not fail the job, so far. What
# fails it is a measure that was not made: a benchmark that did not run, a case that one side does
# not have, a baseline that is missing, or a BASE the instrument cannot be built on.
#
# The one case that has no BASE to compare with is a pull request that needs what BASE does not
# have, as the first benchmark of a function that is new. It is allowed where a commit of the branch
# has the line `Benchmark-baseline: none` in its message, which is in the history and says why, and
# what is then run is HEAD against itself: the counts must be the same in every case, which shows
# that the measuring works, and the report says that nothing was compared.
set -eu

base=$1
head=$2
limit=${LIMIT:-5}
root=$(git rev-parse --show-toplevel)
cd "$root"
out=${OUT:-"$root/perf-report"}
work=$(mktemp -d)
mkdir -p "$out"
summary=${GITHUB_STEP_SUMMARY:-"$out/summary.md"}

cleanup() {
  git worktree remove --force "$work/base" >/dev/null 2>&1 || true
  git worktree remove --force "$work/head" >/dev/null 2>&1 || true
}
trap cleanup EXIT

channel=$(sed -n 's/^channel = "\(.*\)"/\1/p' rust/rust-toolchain.toml)
if [ -z "$channel" ]; then
  echo "rust/rust-toolchain.toml names no channel." >&2
  exit 1
fi
export RUSTUP_TOOLCHAIN=$channel
export GUNGRAUN_HOME="$work/gungraun"

git worktree add --detach "$work/base" "$base" >/dev/null
git worktree add --detach "$work/head" "$head" >/dev/null
# The instrument of HEAD, as it is committed, is what measures BASE.
rm -rf "${work:?}/base/rust/bench"
git archive "$head" rust/bench | tar -x -C "$work/base"

mode=compared
baseline_tree=$work/base
baseline_target=$work/base-target
if ! (cd "$work/base/rust/bench" &&
  CARGO_TARGET_DIR=$baseline_target cargo bench --locked --no-run >"$work/base-build.log" 2>&1); then
  tail -30 "$work/base-build.log" >&2
  if git log --format=%B "$base..$head" | grep -Eiq '^Benchmark-baseline: none( |$)'; then
    mode=self
    baseline_tree=$work/head
    baseline_target=$work/head-target
  else
    {
      echo "### Rust: instructions and time"
      echo
      echo "The benchmarks of this commit cannot be built on \`$base\`, so there is nothing to compare with, and the job fails."
      echo "A pull request that adds what the benchmarks need and \`$base\` does not have says so with the line \`Benchmark-baseline: none\` in the message of a commit, and is then compared with itself."
    } >>"$summary"
    echo "The benchmarks of $head cannot be built on $base." >&2
    exit 1
  fi
fi

echo "== instructions, ${mode} baseline"
(cd "$baseline_tree/rust/bench" &&
  CARGO_TARGET_DIR=$baseline_target cargo bench --locked --bench unicode -- --save-baseline=base)
echo "== time, baseline"
(cd "$baseline_tree/rust/bench" &&
  CARGO_TARGET_DIR=$baseline_target cargo bench --locked --bench wall >"$out/wall-base.jsonl")

echo "== instructions, $head"
status=0
(cd "$work/head/rust/bench" &&
  CARGO_TARGET_DIR=$work/head-target cargo bench --locked --bench unicode -- \
    --baseline=base --callgrind-limits="ir=${limit}%" --save-summary=json) || status=$?
# 3 is a count over the limit, and anything else is a measure that was not made.
if [ "$status" -ne 0 ] && [ "$status" -ne 3 ]; then
  echo "The benchmarks failed with exit code $status." >&2
  exit 1
fi
echo "== time, $head"
(cd "$work/head/rust/bench" &&
  CARGO_TARGET_DIR=$work/head-target cargo bench --locked --bench wall >"$out/wall-head.jsonl")

if [ "$mode" = self ]; then
  base_label=$(printf %.8s "$head")
  check=--self-check
else
  base_label=$(printf %.8s "$base")
  check=
fi
# shellcheck disable=SC2086
python3 rust/bench/report.py --home "$GUNGRAUN_HOME" --limit "$limit" $check \
  --base "$base_label" --head "$(printf %.8s "$head")" \
  --wall-base "$out/wall-base.jsonl" --wall-head "$out/wall-head.jsonl" \
  --out-md "$out/report.md" --out-json "$out/report.json"
cat "$out/report.md" >>"$summary"
cat "$out/report.md"
