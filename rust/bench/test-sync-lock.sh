#!/bin/sh
# Shows that sync-lock.sh follows a bump of the version of the crate and nothing else: a copy of
# the crate with its version bumped gets a lock file that differs from the committed one in the line
# of that version, and builds with --locked. Run from anywhere; CI runs it. The bumped version is
# the version with -bump after it, which is a version whether or not the crate is at a release.
set -eu

here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "${tmp:?}"' EXIT
mkdir "$tmp/rust"
(cd "$here/.." && git ls-files -z . | xargs -0 -I{} cp --parents {} "$tmp/rust/")

version=$(sed -n 's/^version = "\(.*\)"/\1/p' "$tmp/rust/Cargo.toml" | head -1)
sed -i.bak "0,/^version = \"$version\"/s//version = \"$version-bump\"/" "$tmp/rust/Cargo.toml"
rm -f "$tmp/rust/Cargo.toml.bak"
sh "$here/sync-lock.sh" "$tmp/rust/bench"
grep -A1 '^name = "notation199x"$' "$tmp/rust/bench/Cargo.lock" | grep -q "version = \"$version-bump\""
changed=$(diff "$here/Cargo.lock" "$tmp/rust/bench/Cargo.lock" | grep -c '^[<>]')
test "$changed" -eq 2
(cd "$tmp/rust/bench" && cargo metadata --locked --format-version 1 >/dev/null)
echo "ok: the lock file follows the version of the crate and nothing else"
