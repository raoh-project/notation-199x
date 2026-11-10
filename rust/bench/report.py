#!/usr/bin/env python3
"""Makes the report of a comparison of two commits from what `compare.sh` left.

Reads the summaries gungraun saved for the commit under test, each of which holds the count of
instructions (Ir) of the commit and of the baseline it was compared with, and the lines `wall.rs`
printed for each of the two. Writes a Markdown table, for the summary of the CI job, and a JSON
file of the same numbers.

It exits with 1 where the comparison cannot be made: a case without a baseline, or a case that one
of the counts and the times has and another has not, or a summary of a version it does not know.
A case over the limit is in the report and is not a failure, so far.

With --self-check the two are the same commit, which is what is compared where there is no commit
before to compare with. The count of instructions of a program and an input does not vary, so a
difference is a failure: the comparison did not measure what it says it does.
"""

import argparse
import json
import pathlib
import sys

SUMMARY_VERSION = "7"


class Incomparable(Exception):
    pass


def read_counts(home):
    """The Ir of the commit and of the baseline, for each `function/id` that was run."""
    counts = {}
    for path in sorted(pathlib.Path(home).rglob("summary.json")):
        summary = json.loads(path.read_text())
        if summary.get("version") != SUMMARY_VERSION:
            raise Incomparable(
                f"{path} is of version {summary.get('version')!r} of the summary, "
                f"and this reads version {SUMMARY_VERSION}"
            )
        case = f"{summary['function_name']}/{summary['id']}"
        callgrind = [p for p in summary["profiles"] if p["tool"] == "Callgrind"]
        if len(callgrind) != 1:
            raise Incomparable(f"{case} has no Callgrind profile")
        values = callgrind[0]["data"]["total"]["metrics"]["Ir"]["values"]
        if "old" not in values or "new" not in values:
            raise Incomparable(f"{case} was not compared with a baseline")
        if case in counts:
            raise Incomparable(f"{case} is in two summaries")
        counts[case] = (values["old"], values["new"])
    return counts


def read_wall(path):
    cases = {}
    for line in pathlib.Path(path).read_text().splitlines():
        if not line.startswith("{"):
            continue
        row = json.loads(line)
        if row["case"] in cases:
            raise Incomparable(f"{row['case']} is twice in {path}")
        cases[row["case"]] = row
    return cases


def same_cases(**named):
    reference_name, reference = next(iter(named.items()))
    for name, cases in named.items():
        if not cases:
            raise Incomparable(f"{name} holds no case")
        if set(cases) != set(reference):
            only = sorted(set(cases) ^ set(reference))
            raise Incomparable(f"{name} and {reference_name} are not of the same cases: {only}")


def change(old, new):
    return (new - old) / old * 100 if old else float("inf") if new else 0.0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--home", required=True, help="GUNGRAUN_HOME the comparison ran in")
    parser.add_argument("--wall-base", required=True)
    parser.add_argument("--wall-head", required=True)
    parser.add_argument("--limit", type=float, required=True, help="percent of Ir that is flagged")
    parser.add_argument("--base", default="base")
    parser.add_argument("--head", default="head")
    parser.add_argument("--self-check", action="store_true", help="the two are one commit")
    parser.add_argument("--out-md", required=True)
    parser.add_argument("--out-json", required=True)
    args = parser.parse_args()

    counts = read_counts(args.home)
    wall_base, wall_head = read_wall(args.wall_base), read_wall(args.wall_head)
    same_cases(instructions=counts, wall_base=wall_base, wall_head=wall_head)
    for case, row in wall_head.items():
        if (row["scalars"], row["bytes"]) != (wall_base[case]["scalars"], wall_base[case]["bytes"]):
            raise Incomparable(f"{case} was timed on a different input in the two commits")

    if args.self_check:
        differing = sorted(case for case, (old, new) in counts.items() if old != new)
        if differing:
            raise Incomparable(f"one commit gave two counts of instructions for {differing}")

    rows, flagged = [], []
    for case in sorted(counts):
        old, new = counts[case]
        w_old, w_new = wall_base[case], wall_head[case]
        scalars, size = w_new["scalars"], w_new["bytes"]
        ir_change = change(old, new)
        row = {
            "case": case,
            "scalars": scalars,
            "bytes": size,
            "ir_base": old,
            "ir_head": new,
            "ir_change_pct": ir_change,
            "ir_per_scalar": new / scalars,
            "ir_per_byte": new / size,
            "ns_per_scalar_base": w_old["median_ns"] / scalars,
            "ns_per_scalar_head": w_new["median_ns"] / scalars,
            "ns_per_scalar_change_pct": change(w_old["median_ns"], w_new["median_ns"]),
            "ns_per_byte_head": w_new["median_ns"] / size,
        }
        rows.append(row)
        if ir_change > args.limit:
            flagged.append(row)

    lines = [
        f"### Rust: instructions and time, `{args.base}` to `{args.head}`",
        "",
    ]
    if args.self_check:
        lines += [
            "There is no commit before this one that the benchmarks can be built on, which a commit "
            "that says so allows, so this commit is compared with itself: the counts are those of "
            "two runs of one program, and they are the same in every case. That shows the "
            "measuring works; it shows nothing of a change of speed.",
            "",
        ]
    lines += [
        f"Ir is the count of instructions under Callgrind, the same for the same program and the same input. "
        f"The time is the median of a run on this runner, which a shared machine and the layout of the "
        f"program move, and it is not judged. A case is flagged where Ir grows by more than {args.limit:g}%.",
        "",
        "| Case | Scalars | Bytes | Ir base | Ir head | Ir change | Ir/scalar | Ir/byte "
        "| ns/scalar base | ns/scalar head | time change |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        flag = " ⚠" if r in flagged else ""
        lines.append(
            f"| `{r['case']}` | {r['scalars']} | {r['bytes']} | {r['ir_base']} | {r['ir_head']} "
            f"| {r['ir_change_pct']:+.2f}%{flag} | {r['ir_per_scalar']:.1f} | {r['ir_per_byte']:.1f} "
            f"| {r['ns_per_scalar_base']:.2f} | {r['ns_per_scalar_head']:.2f} "
            f"| {r['ns_per_scalar_change_pct']:+.1f}% |"
        )
    lines.append("")
    if flagged:
        names = ", ".join(f"`{r['case']}`" for r in flagged)
        lines.append(f"{len(flagged)} of {len(rows)} cases are over the limit: {names}. "
                     "This is reported and does not fail the job.")
    else:
        lines.append(f"No case of {len(rows)} is over the limit.")
    pathlib.Path(args.out_md).write_text("\n".join(lines) + "\n")
    pathlib.Path(args.out_json).write_text(
        json.dumps({"limit_pct": args.limit, "cases": rows}, indent=2) + "\n"
    )


if __name__ == "__main__":
    try:
        main()
    except Incomparable as refusal:
        print(f"Cannot compare: {refusal}", file=sys.stderr)
        sys.exit(1)
