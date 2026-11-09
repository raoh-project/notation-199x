#!/bin/sh
# Holds what a statically linked program carries of the crate to what it asks of the crate.
#
# The program of one feature carries the tables of the groups that feature needs, and of no other.
# A program of NFC or NFD does not carry the compatibility mappings, one of NFD or NFKD does not
# carry the composition tables, one that lowercases does not carry the uppercase tables, and one that
# asks nothing carries no table. Each program makes every call of the crate its feature stands for,
# `normalize_within` and `append` of a form as well as `normalize`.
#
# Each table is read off the linked program by its symbol, so that a table that is carried is found
# whichever way it came to be, and a table that is needed is found to be there, which keeps the
# check from passing on a program whose symbols it cannot read.
#
# And each program is built as a WebAssembly module, as it is sent to a browser, and held to a
# size. A table that grows, or code that comes to carry what it did not, is a rise in bytes that
# the groups do not show. Each limit is about 10% over the module the crate gave when the limit was
# set, so that what a release of Rust changes passes and what the crate changes does not; a limit
# is raised on purpose, with the change that needs it.
#
# Run from rust/: sh linked/check.sh
set -eu

cd "$(dirname "$0")"
target=${CARGO_TARGET_DIR:-target}
failed=0

# The tables of each group, by the name the crate gives them: one that ends in _ is the start of
# several names. FINAL_SIGMA has one entry, which the compiler holds in the code and not as a table.
canonical='CANONICAL DECOMPOSITION_POSITION_ COMBINING_CLASS_ STABLE_'
compatibility='COMPATIBILITY'
composition='COMPOSITION_FIRST_ COMPOSITION_SECOND_ COMPOSITION_ROWS COMPOSITION_PAIRS'
lower='LOWER LOWER_ASCII LOWER_POSITION_ CASE_CONTEXT_'
upper='UPPER UPPER_ASCII UPPER_POSITION_'
groups='canonical compatibility composition lower upper'

# carries NAME: whether the program has a table of that name. A name in a symbol follows the
# number of its characters, and ends the symbol or is followed by the hash of a legacy symbol.
carries() {
    case $1 in
        *_) pattern="_tables[0-9]+$1" ;;
        *) pattern="_tables[0-9]+$1(17h|\$)" ;;
    esac
    nm "$target/release/linked" | grep -Eq "$pattern"
}

# check FEATURE LIMIT GROUP...: the program built with FEATURE carries the tables of each GROUP, and
# of no group but those, and its WebAssembly module is no more than LIMIT bytes.
check() {
    feature=$1
    limit=$2
    shift 2
    wanted=" $* "
    bad=0
    if [ "$feature" = nothing ]; then
        cargo build --release --quiet --target-dir "$target"
    else
        cargo build --release --quiet --features "$feature" --target-dir "$target"
    fi
    # The symbols of the program are read, which a table that is expected absent does not show: the
    # program has symbols of its own, and a program whose symbols nm cannot read has none.
    if ! nm "$target/release/linked" | grep -q "linked"; then
        echo "FAIL: nm reads no symbol of the program of '$feature'" >&2
        failed=1
        bad=1
    fi
    for group in $groups; do
        eval "names=\$$group"
        for name in $names; do
            case $wanted in *" $group "*) want=present ;; *) want=absent ;; esac
            if carries "$name"; then have=present; else have=absent; fi
            if [ "$have" != "$want" ]; then
                echo "FAIL: the program of '$feature' has $name $have, and should have it $want" >&2
                failed=1
                bad=1
            fi
        done
    done
    if [ "$feature" = nothing ] && nm "$target/release/linked" | grep -q "_tables"; then
        echo "FAIL: the program that asks nothing of the crate carries a table" >&2
        failed=1
        bad=1
    fi
    if [ "$feature" = nothing ]; then
        CARGO_PROFILE_RELEASE_STRIP=symbols CARGO_PROFILE_RELEASE_PANIC=abort \
            cargo build --release --quiet --lib --target wasm32-unknown-unknown --target-dir "$target"
    else
        CARGO_PROFILE_RELEASE_STRIP=symbols CARGO_PROFILE_RELEASE_PANIC=abort \
            cargo build --release --quiet --lib --features "$feature" --target wasm32-unknown-unknown \
            --target-dir "$target"
    fi
    size=$(wc -c < "$target/wasm32-unknown-unknown/release/linked.wasm" | tr -d ' ')
    if [ "$size" -gt "$limit" ]; then
        echo "FAIL: the module of '$feature' is $size bytes, and should be no more than $limit" >&2
        failed=1
        bad=1
    fi
    if [ "$bad" = 0 ]; then echo "ok: $feature ($size bytes)"; fi
}

check nfc 135000 canonical composition
check nfd 120000 canonical
check nfkc 203000 canonical compatibility composition
check nfkd 188000 canonical compatibility
check lowercase 91000 lower
check uppercase 84000 upper
check form 222000 canonical compatibility composition
check nothing 18000

exit "$failed"
