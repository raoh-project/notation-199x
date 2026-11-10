#!/bin/sh
# Brings the entry of the crate under measurement in DIR/Cargo.lock to the version that crate has,
# with `sh sync-lock.sh DIR`, where DIR is a copy of this directory beside the crate.
#
# The lock file holds the version of a crate that is a dependency by path, and the version of that
# crate is what a release changes, so a lock file written beside one version does not fit the crate
# at another, and `--locked` would refuse it though nothing the benchmarks depend on has changed. The
# commit a pull request is compared with has the version it had, and a commit that bumps the version
# has the lock file of the one before. Nothing else in the lock file is touched, which this checks.
set -eu

cd "$1"
before=$(mktemp)
cp Cargo.lock "$before"
cargo update --quiet -p notation199x
other=$(diff "$before" Cargo.lock | grep '^[<>]' | grep -Ev '^[<>] version = ' || true)
rm -f "$before"
if [ -n "$other" ]; then
  echo "Bringing Cargo.lock to the version of notation199x changed more than that version:" >&2
  echo "$other" >&2
  exit 1
fi
