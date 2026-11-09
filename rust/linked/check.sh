#!/bin/sh
# Holds what a statically linked program carries of the crate's tables to what it asks of the crate:
# the program of one feature carries the tables of the groups that feature needs, and of no other.
# A program of NFC or NFD does not carry the compatibility mappings, one of NFD or NFKD does not
# carry the composition tables, one that lowercases does not carry the uppercase tables, and one that
# asks nothing carries no table.
#
# Each table is read off the linked program by its symbol, so that a table that is carried is found
# whichever way it came to be, and a table that is needed is found to be there, which keeps the
# check from passing on a program whose symbols it cannot read.
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

# check FEATURE GROUP...: the program built with FEATURE carries the tables of each GROUP, and of
# no group but those.
check() {
    feature=$1
    shift
    wanted=" $* "
    before=$failed
    if [ "$feature" = nothing ]; then
        cargo build --release --quiet --target-dir "$target"
    else
        cargo build --release --quiet --features "$feature" --target-dir "$target"
    fi
    for group in $groups; do
        eval "names=\$$group"
        for name in $names; do
            case $wanted in *" $group "*) want=present ;; *) want=absent ;; esac
            if carries "$name"; then have=present; else have=absent; fi
            if [ "$have" != "$want" ]; then
                echo "FAIL: the program of '$feature' has $name $have, and should have it $want" >&2
                failed=1
            fi
        done
    done
    if [ "$feature" = nothing ] && nm "$target/release/linked" | grep -q "_tables"; then
        echo "FAIL: the program that asks nothing of the crate carries a table" >&2
        failed=1
    fi
    if [ "$failed" = "$before" ]; then echo "ok: $feature"; fi
}

check nfc canonical composition
check nfd canonical
check nfkc canonical compatibility composition
check nfkd canonical compatibility
check lowercase lower
check uppercase upper
check form canonical compatibility composition
check nothing

exit "$failed"
