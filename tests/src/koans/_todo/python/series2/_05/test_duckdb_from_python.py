"""
╔══════════════════════════════════════════════════════════════════════════╗
║  PYTHON KOANS — Python for Data · Series 2 · 05
╚══════════════════════════════════════════════════════════════════════════╝

DuckDB from Python — Run SQL on Files and DataFrames, No Server

TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which the runner does not mount and zeus
does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
src/koans/python/series2/_05/ when it is real.

    zeus.bat koans python series2 _05     (Windows)
    ./zeus.sh koans python series2 _05    (macOS/Linux)

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

THIS LESSON'S RUNGS: koan:complete
DATASET: northwind

── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
GOAL: run SQL against files and DataFrames without a server.
LIBRARIES: duckdb.sql, .df(), read_csv_auto, read_parquet, querying a DataFrame by name.
The reader is already using DuckDB — it is what the koans run on. Say so.
The point worth making: this is where "push it back to SQL" (Series 3 · 30) becomes possible
without installing anything.
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
