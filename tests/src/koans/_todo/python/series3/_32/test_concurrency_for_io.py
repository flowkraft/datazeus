"""
╔══════════════════════════════════════════════════════════════════════════╗
║  PYTHON KOANS — Python for Data · Series 3 · 32
╚══════════════════════════════════════════════════════════════════════════╝

Threads for Slow API Calls — and Why They Do Not Speed Up pandas

TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which the runner does not mount and zeus
does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
src/koans/python/series3/_32/ when it is real.

    zeus.bat koans python series3 _32     (Windows)
    ./zeus.sh koans python series3 _32    (macOS/Linux)

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

THIS LESSON'S RUNGS: koan:count:queries, koan:predict
DATASET: api-stub

── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
GOAL: fetch 4,000 API pages in under a minute instead of twenty, and know when that
technique will do nothing at all.
LIBRARIES: concurrent.futures.ThreadPoolExecutor (as_completed) and a requests/httpx Session.
asyncio is NAMED in one paragraph and not taught — it colours every function it touches and
turns a five-line change into a rewrite.

RULE 2 IS AT ITS MOST TEMPTING HERE AND STILL HOLDS: never assert wall-clock time. It does not
need to, because the countable proxies are the lesson:

  1. CALLS ISSUED (the `counting` fixture). Threading a paginated fetch must not change how
     many pages you asked for. A pool that re-requests page 1 four times is a real bug and a
     stopwatch cannot see it.
  2. COMPLETENESS AND ORDER. as_completed returns results in finishing order, which is the most
     common threading bug in data code: rows arriving shuffled and quietly attributed to the
     wrong page. Assert the collected set is complete AND correctly keyed.
  3. THE NEGATIVE RESULT, and make it the LAST koan. The same DataFrame work, threaded, and
     nothing improves — the reader proves the GIL to themselves instead of being told. It is
     the best koan in the episode and the reason the episode exists rather than a paragraph
     bolted onto Series 2 · 20.

The rule to leave behind: threads help you WAIT in parallel, never COMPUTE in parallel. Slow
DataFrame work goes to vectorizing (3 · 30), then polars (3 · 35), then back to SQL (2 · 05).

DATASET: api-stub — the same local stdlib HTTP server Series 2 · 20 uses, with a deliberate
per-request delay so the serial loop is genuinely slow. Add a per-page counter to that server
so koan 1 asserts on what it actually RECEIVED, not on what the client believes it sent.
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
