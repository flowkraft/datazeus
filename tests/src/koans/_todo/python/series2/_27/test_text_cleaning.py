"""
╔══════════════════════════════════════════════════════════════════════════╗
║  PYTHON KOANS — Python for Data · Series 2 · 27
╚══════════════════════════════════════════════════════════════════════════╝

Text Cleaning — Trimming, Splitting and Matching Messy Strings

TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which the runner does not mount and zeus
does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
src/koans/python/series2/_27/ when it is real.

    zeus.bat koans python series2 _27     (Windows)
    ./zeus.sh koans python series2 _27    (macOS/Linux)

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

THIS LESSON'S RUNGS: koan:complete, koan:diagnose
DATASET: messy

── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
GOAL: make two spellings of the same thing join to each other, and turn a text column
into a number.
LIBRARIES: the .str accessor — strip, lower, replace, split(expand=True), contains, extract —
and `re` only where a pattern genuinely needs it.

NOT THE SAME EPISODE AS 30, and keeping them apart is the point. Two questions share the word
"cleaning": here the value is REPAIRABLE (" ALFKI " and "alfki" are one customer); at 30 it is
NOT (a quantity of -5, a date in 1899), and the question becomes reject, quarantine or fail.
Parse (25) -> repair (27) -> decide (30) is the order the work happens in.

THE KOAN THAT CARRIES THIS EPISODE is trailing whitespace, because it is invisible in a printed
frame and in a Jupyter cell, and merge on it silently drops rows. Structure it as the diagnose
rung: give them a merge that matched 89 of 91 and ask why — then .nunique() before and after
.str.strip() is how they find it without having been told where to look. That is a habit, not
a fact, which is what this rung is for.

The other four, in order: case/accent normalisation for MATCHING only (normalise a copy, never
the value you display); one column holding "Lastname, Firstname" and what split(expand=True)
does to the row with no comma; .str.extract with one capture group to pull an invoice number
out of free text; and "1.234,56" -> 1234.56, which is the only repair on the list that changes
a number rather than a label, and which points straight back to 15 — same export, same locale,
a second symptom.

DATASET: messy/orders-export.csv, which already carries the decimal commas and the quoted
field. If a defect on that list is not in the file yet, ADD IT THERE and record it in
datasets/messy/README.md against this episode. Do not invent a second file — the whole value
of that CSV is that one small file carries every text defect the track teaches.
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
