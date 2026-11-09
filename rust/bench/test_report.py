"""Tests of report.py: that a comparison that cannot be made is refused, whichever way it cannot.

Run with `python3 -m unittest discover -s rust/bench`, which CI does before it compares anything, so
that the refusals are shown to work in the environment that relies on them.
"""

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

REPORT = pathlib.Path(__file__).with_name("report.py")


def summary(function, case, old, new, version="7", tool="Callgrind"):
    values = {"new": new}
    if old is not None:
        values["old"] = old
    return {
        "version": version,
        "function_name": function,
        "id": case,
        "profiles": [{"tool": tool, "data": {"total": {"metrics": {"Ir": {"values": values}}}}}],
    }


def wall(case, scalars=100, size=120, median=1000.0):
    return {"case": case, "scalars": scalars, "bytes": size, "median_ns": median,
            "min_ns": median, "max_ns": median}


class Case(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.dir.name)

    def tearDown(self):
        self.dir.cleanup()

    def write_summaries(self, *summaries):
        for n, s in enumerate(summaries):
            folder = self.root / "home" / f"{n}"
            folder.mkdir(parents=True)
            (folder / "summary.json").write_text(json.dumps(s))

    def write_wall(self, name, *rows):
        path = self.root / name
        path.write_text("\n".join(json.dumps(r) for r in rows) + "\n")

    def run_report(self, *extra):
        return subprocess.run(
            [sys.executable, str(REPORT), "--home", str(self.root / "home"), "--limit", "5",
             "--wall-base", str(self.root / "wall-base"), "--wall-head", str(self.root / "wall-head"),
             "--out-md", str(self.root / "r.md"), "--out-json", str(self.root / "r.json"), *extra],
            capture_output=True, text=True,
        )

    def both_walls(self, *rows):
        self.write_wall("wall-base", *rows)
        self.write_wall("wall-head", *rows)


class Compared(Case):
    def test_a_comparison_is_made_and_a_count_over_the_limit_is_flagged_and_not_a_failure(self):
        self.write_summaries(summary("f", "a", 1000, 1000), summary("f", "b", 1000, 1100))
        self.both_walls(wall("f/a"), wall("f/b"))
        result = self.run_report()
        self.assertEqual(result.returncode, 0, result.stderr)
        report = (self.root / "r.md").read_text()
        self.assertIn("`f/b` | 100 | 120 | 1000 | 1100 | +10.00% ⚠", report)
        self.assertIn("1 of 2 cases are over the limit", report)

    def test_a_case_without_a_baseline_is_refused(self):
        self.write_summaries(summary("f", "a", None, 1000))
        self.both_walls(wall("f/a"))
        result = self.run_report()
        self.assertEqual(result.returncode, 1)
        self.assertIn("was not compared with a baseline", result.stderr)

    def test_a_summary_of_a_version_it_does_not_read_is_refused(self):
        self.write_summaries(summary("f", "a", 1000, 1000, version="8"))
        self.both_walls(wall("f/a"))
        result = self.run_report()
        self.assertEqual(result.returncode, 1)
        self.assertIn("version", result.stderr)

    def test_a_summary_without_callgrind_is_refused(self):
        self.write_summaries(summary("f", "a", 1000, 1000, tool="DHAT"))
        self.both_walls(wall("f/a"))
        self.assertEqual(self.run_report().returncode, 1)

    def test_no_summary_at_all_is_refused(self):
        (self.root / "home").mkdir()
        self.both_walls(wall("f/a"))
        result = self.run_report()
        self.assertEqual(result.returncode, 1)
        self.assertIn("holds no case", result.stderr)

    def test_a_case_that_is_counted_and_not_timed_is_refused(self):
        self.write_summaries(summary("f", "a", 1000, 1000), summary("f", "b", 1000, 1000))
        self.both_walls(wall("f/a"))
        result = self.run_report()
        self.assertEqual(result.returncode, 1)
        self.assertIn("not of the same cases", result.stderr)

    def test_a_case_that_one_commit_is_timed_on_and_the_other_is_not_is_refused(self):
        self.write_summaries(summary("f", "a", 1000, 1000))
        self.write_wall("wall-base", wall("f/a"))
        self.write_wall("wall-head", wall("f/a"), wall("f/b"))
        self.assertEqual(self.run_report().returncode, 1)

    def test_a_case_timed_on_another_input_in_the_two_commits_is_refused(self):
        self.write_summaries(summary("f", "a", 1000, 1000))
        self.write_wall("wall-base", wall("f/a", scalars=100))
        self.write_wall("wall-head", wall("f/a", scalars=101))
        result = self.run_report()
        self.assertEqual(result.returncode, 1)
        self.assertIn("different input", result.stderr)

    def test_a_case_twice_in_the_times_is_refused(self):
        self.write_summaries(summary("f", "a", 1000, 1000))
        self.write_wall("wall-base", wall("f/a"), wall("f/a"))
        self.write_wall("wall-head", wall("f/a"))
        self.assertEqual(self.run_report().returncode, 1)


class SelfCheck(Case):
    def test_the_same_counts_pass(self):
        self.write_summaries(summary("f", "a", 1000, 1000))
        self.both_walls(wall("f/a"))
        result = self.run_report("--self-check")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("compared with itself", (self.root / "r.md").read_text())

    def test_a_difference_between_two_runs_of_one_commit_is_refused(self):
        self.write_summaries(summary("f", "a", 1000, 1001))
        self.both_walls(wall("f/a"))
        result = self.run_report("--self-check")
        self.assertEqual(result.returncode, 1)
        self.assertIn("two counts", result.stderr)


if __name__ == "__main__":
    unittest.main()
