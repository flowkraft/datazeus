"""
╔══════════════════════════════════════════════════════════════════════════╗
║  PYTHON KOANS — Python for Data · Series 2 · 02
╚══════════════════════════════════════════════════════════════════════════╝

Excel Files — Reading the Spreadsheets Your Business Sends You

TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which the runner does not mount and zeus
does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
src/koans/python/series2/_02/ when it is real.

    zeus.bat koans python series2 _02     (Windows)
    ./zeus.sh koans python series2 _02    (macOS/Linux)

── READ THESE FIRST ────────────────────────────────────────────────────
  conftest.py                              the ___ blank, fixtures, and the rules
  series1/_15/test_files_and_encodings.py  worked example: the messy-CSV dataset
  series1/_35/test_merge_and_join.py       worked example: the join fan-out counter

── THE RULES ───────────────────────────────────────────────────────────
 1. THE BLANK IS A DATA DECISION, NEVER A LANGUAGE FACT. This matters more here than on any
    other track, because Python Koans (gregmalcolm/python_koans) is a famous LANGUAGE tutorial
    and a reader who has met it will expect `assert 1 + 1 == ___`. That is not what these
    are. The reader here can already PROGRAM — in SQL, in Excel formulas, in a bit of Java —
    and is learning Python as a tool rather than as a first language. Series 1 · 05 and · 10
    do teach the language, fast and on data; even there the blank is what the DATA did, never
    what the syntax means. If a blank can be answered from the docs instead of from the data,
    it is the wrong blank.
 2. NEVER ASSERT WALL-CLOCK TIME. Every performance lesson has a countable proxy that is also
    the lesson. If you think you need a stopwatch, you need a counter.
 3. ONE MECHANISM, only the assertion target changes: a value, a counter, captured output, or
    a process result. There is no second framework and there should never be one.
 4. Use the SAME Northwind numbers the SQL and JVM koans use wherever the topic allows it.

THIS LESSON'S RUNGS: koan:predict, koan:diagnose
DATASET: generated

── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
GOAL: open a real .xlsx and get a frame whose numbers and dates are numbers and dates.
LIBRARIES: pd.read_excel via openpyxl — sheet_name, header, skiprows, usecols, dtype.

WHY THIS EPISODE EXISTS: Java & Groovy has carried "Excel with POI" from the start and Python
— where this is one line — had nothing until 2026-08-24. Nobody defended that; it was an
oversight, and it is the episode most likely to be somebody's first real day of work.

THE FOUR DEFECTS, none of which can exist in a CSV, so the reader has met none of them:
  1. a title row above the real header, giving you columns called `Unnamed: 1`
  2. merged cells — the value once, then NaN for the rest of the span
  3. a date that arrives as 45123, because Excel counts days from 1899-12-30, and which is
     a perfectly plausible order ID
  4. a number stored as text (the invisible leading apostrophe), so .sum() concatenates

KOANS: 4 is the one that matters and it belongs last — it is the only defect here that
produces an ANSWER rather than an error. Assert on the total: the text column "sums" to
something that is either visibly absurd or, worse, quietly wrong. 3 is the predict rung:
give them the integer and ask for the date. 1 and 2 are diagnose — hand them the mangled
frame and ask which read_excel argument was wrong.

DATASET: generated, and the reasoning is the interesting part. An .xlsx is a zip of XML, so
committing one would break tier 2's entire rationale — that a reviewer can see the defect in
a diff. The defects are AUTHORED in datasets/generated/make_messy_excel.py, which is
reviewable text, and the binary is built from it under the same fixed seed as every other
generator, so the expected answers are stable for every learner.
"""

from conftest import ___  # noqa: F401


# TODO: koans, one per idea in the lesson, in the lesson's order.
#
#   value      def test_revenue_per_customer(nw_df):
#                  assert totals(nw_df("Orders"))["ALFKI"] == ___
#
#   counter    def test_one_query_not_a_thousand(counting):
#                  con = counting(); ...; assert con.queries == ___
#
#   rows held  def test_you_never_held_the_whole_file(sink):
#                  ...; assert sink.peak_held == ___
#
#   captured   def test_the_log_says_which_row_died(capsys):
#                  ...; out = capsys.readouterr().out; assert "4712" in out
#
#   process    def test_it_fails_loudly_without_config():
#                  code, out = run_tool("mytool"); assert code == ___
