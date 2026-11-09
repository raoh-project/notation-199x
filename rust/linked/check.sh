#!/bin/sh
# Holds what a statically linked program carries of the crate's normalization tables to what it asks
# of the crate: a program that asks for NFC or NFD never carries the compatibility mappings, and one
# that asks for NFD or NFKD never carries the composition tables.
#
# Each table is read off the linked program by its symbol, so that a table that is carried is found
# whichever way it came to be, and a table that is expected is found to be there, which keeps the
# check from passing on a program whose symbols it cannot read.
#
# Run from rust/: sh linked/check.sh
set -eu

cd "$(dirname "$0")"
target=${CARGO_TARGET_DIR:-target}
failed=0

# The tables of each group, by the start of the name the crate gives their symbols.
decomposition='CANONICAL DECOMPOSITION_POSITION_ COMBINING_CLASS_ STABLE_'
compatibility='COMPATIBILITY'
composition='COMPOSITION_FIRST_ COMPOSITION_SECOND_ COMPOSITION_ROWS COMPOSITION_PAIRS'

carries() {
    # A name in a symbol follows the number of its characters.
    nm "$target/release/linked" | grep -Eq "normalization_tables[0-9]+$1"
}

# expect FEATURE GROUP present|absent...: what the program built with FEATURE carries of each group.
check() {
    feature=$1
    shift
    before=$failed
    cargo build --release --quiet --features "$feature" --target-dir "$target"
    while [ $# -gt 0 ]; do
        group=$1
        want=$2
        shift 2
        eval "names=\$$group"
        for name in $names; do
            if carries "$name"; then have=present; else have=absent; fi
            if [ "$have" != "$want" ]; then
                echo "FAIL: the program of '$feature' has $name $have, and should have it $want" >&2
                failed=1
            fi
        done
    done
    if [ "$failed" = "$before" ]; then echo "ok: $feature"; fi
}

check nfc decomposition present compatibility absent composition present
check nfd decomposition present compatibility absent composition absent
check nfkc decomposition present compatibility present composition present
check nfkd decomposition present compatibility present composition absent
check form decomposition present compatibility present composition present

# A program that asks for no normalization carries none of it.
cargo build --release --quiet --target-dir "$target"
if nm "$target/release/linked" | grep -q "normalization_tables"; then
    echo "FAIL: the program that asks nothing of the crate carries a normalization table" >&2
    failed=1
else
    echo "ok: nothing"
fi

exit "$failed"
