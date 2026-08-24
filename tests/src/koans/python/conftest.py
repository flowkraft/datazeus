"""
Shared plumbing for every Python koan — the Python for Data counterpart to KoanBase.

╔══════════════════════════════════════════════════════════════════════════╗
║  THE DECISIONS BEHIND THIS FILE — 2026-08-24. Read before extending it.  ║
╚══════════════════════════════════════════════════════════════════════════╝

WHY A SECOND KOAN HARNESS AT ALL
Spock is Groovy and cannot run Python, so there was no reusing the JVM one. But "new
harness" means a sentinel, a fixture and two helpers — NOT a framework. That is the same
conclusion the JVM track reached after an earlier design invented four named mechanisms and
had to be cut back to one.

ONE MECHANISM: THE KOAN. Only the assertion target changes, exactly as on the JVM side:

    a value      the default — most of Series 1 and 2
    a counter    queries issued, or rows held at once     (see counting_conn, RowSink)
    captured     stdout/stderr after a failure            (pytest's capsys, no helper needed)
    a process    exit code and output of the CLI          (see run_tool)

NEVER ASSERT WALL-CLOCK TIME. Same rule, same reason: machine-dependent, flaky in CI, and
eventually deleted by whoever the flake wakes up. Every performance lesson in this track has
a countable proxy that IS the lesson. If you think you need a stopwatch, you need a counter.

THE BLANK IS A DATA DECISION, NEVER A LANGUAGE FACT. This matters more here than anywhere,
because Python Koans (gregmalcolm/python_koans) is a famous and excellent LANGUAGE tutorial,
and a reader who has met it will expect `assert 1 + 1 == ___`. That is not what these are.

    WRONG   assert type(df.groupby("c")) == ___
    RIGHT   12,000 order lines merged onto 4,000 orders — what is total revenue?

WHO IS READING, stated exactly, because this file used to say "someone who can already write
Python" while the curriculum said Series 1 was "from zero" and spent two episodes on types,
loops and functions. Both cannot be true. The true one:

    Someone who can already PROGRAM — in SQL, in Excel formulas, in a bit of Java — and is
    learning Python as a tool rather than as a first language.

So Series 1 · 05 and · 10 DO teach the language, fast and entirely on data, and they are the
only two episodes that do. The koan rule does not bend for them: even there the blank is what
the DATA did, never what the syntax means. "Which type did you get back" is a lookup; "your
loop produced 89 rows from 91 orders — where did two go" is the same construct doing the work
it was taught for. If a blank can be answered by reading the docs instead of the data, it is
the wrong blank, in Series 1 as much as in Series 3.

SAME NORTHWIND AS EVERY OTHER TRACK. The `northwind` fixture opens a throwaway COPY of the
same database the SQL and JVM koans use. A learner who found 11 German customers with WHERE
should find 11 with a DataFrame filter. Two tools, one skill, same numbers.
"""

import os
import shutil
import subprocess
import sys
import tempfile

import duckdb
import pytest


class _Blank:
    """The unfilled blank. Any comparison against it fails with a readable nudge.

    Mirrors KoanBase's `___`: a koan you have not answered should say so, not fail with
    something that looks like a bug in your code.
    """

    def __repr__(self):
        return "___ (you have not filled this in yet)"

    def __eq__(self, other):
        pytest.fail(
            f"you haven't filled in the blank yet — replace ___ with your answer.\n"
            f"the koan expected it to equal: {other!r}"
        )

    __ne__ = __eq__


___ = _Blank()


# ══════════════════════════════════════════════════════════════════════════
#  The dataset
# ══════════════════════════════════════════════════════════════════════════

DATASET = "/datasets/northwind/northwind.duckdb"


@pytest.fixture
def northwind():
    """A throwaway COPY of the shipped Northwind, as a DuckDB connection.

    A copy rather than the file itself, for the same reason KoanBase copies it: a koan run
    must never lock or mutate the database the learner is exploring in CloudBeaver or Jupyter.
    The copy dies with the test.
    """
    if not os.path.exists(DATASET):
        pytest.skip(f"Northwind not mounted at {DATASET} — run these through `zeus koans python`")
    tmp = tempfile.NamedTemporaryFile(suffix=".duckdb", delete=False)
    tmp.close()
    shutil.copy(DATASET, tmp.name)
    con = duckdb.connect(tmp.name)
    try:
        yield con
    finally:
        con.close()
        os.unlink(tmp.name)


@pytest.fixture
def nw_df(northwind):
    """Northwind tables as DataFrames, by name: `nw_df("Customers")`.

    Most of Series 1 is DataFrame work, and loading a table should not be three lines of
    ceremony at the top of every koan.
    """
    def load(table: str):
        return northwind.sql(f'SELECT * FROM "{table}"').df()
    return load


# ══════════════════════════════════════════════════════════════════════════
#  ASSERT ON A COUNTER — "the answer is right either way; the lesson is how you got it"
# ══════════════════════════════════════════════════════════════════════════

class CountingConnection:
    """Wraps a DuckDB connection and counts how many times you went to the database.

    Series 2 and 3 have episodes where the naive and the sensible version both produce the
    right answer — chunking, pushing work back to SQL, a query inside a loop. Correctness
    cannot separate them; the count can, and the count IS the lesson.

    `queries` is what a koan asserts on: "your version asked 1,001 times; one join asks once."
    """

    def __init__(self, con):
        self._con = con
        self.queries = 0

    def sql(self, q, *a, **k):
        self.queries += 1
        return self._con.sql(q, *a, **k)

    def execute(self, q, *a, **k):
        self.queries += 1
        return self._con.execute(q, *a, **k)

    def __getattr__(self, name):
        return getattr(self._con, name)

    def reset(self):
        self.queries = 0

    def __repr__(self):
        return f"{self.queries} queries"


@pytest.fixture
def counting(northwind):
    """`counting()` -> a Northwind connection that counts every query you send it."""
    return lambda: CountingConnection(northwind)


class RowSink:
    """Counts the most rows held in memory AT ONCE.

    Series 3 · 30 is chunking. Reading a large file whole and reading it in chunks produce the
    identical, correct total, so the koan asserts on the high-water mark instead: streaming
    gives a small peak, `read_csv` of the whole thing gives the row count.

    Rejected alternative, same as on the JVM side: running the container under a memory cap so
    the naive version is OOM-killed. It works, it is dramatic, and it proves nothing this
    counter does not — while turning a two-second koan into a slow one. An OOM tells you that
    you ran out of memory; this tells you what you did wrong.

    `seen` is the other jaw of the pincer: hold few rows AND still see all of them, so a small
    peak cannot be bought by quietly dropping data.
    """

    def __init__(self):
        self.held = 0
        self.peak_held = 0
        self.seen = 0

    def accept(self, n=1):
        self.seen += n
        self.held += n
        self.peak_held = max(self.peak_held, self.held)

    def release(self, n=1):
        self.held = max(0, self.held - n)

    def consume(self, chunk):
        """Take a chunk, count it, let it go. Chunked code falls into this naturally."""
        n = len(chunk)
        self.accept(n)
        try:
            return chunk
        finally:
            self.release(n)

    def __repr__(self):
        return f"saw {self.seen} rows, held at most {self.peak_held} at once"


@pytest.fixture
def sink():
    return RowSink()


# ══════════════════════════════════════════════════════════════════════════
#  ASSERT ON A PROCESS — Series 3 · 10 and the project
# ══════════════════════════════════════════════════════════════════════════

def run_tool(module: str, *args):
    """Run a learner's CLI script and collect its exit code and output.

    Invoked as `python -m <module>` in a subprocess rather than imported, because the thing
    being taught is that it behaves correctly as a COMMAND — argparse parsing, a non-zero exit
    when something is wrong, and a message that names what is missing.

    Returns (exit_code, stdout+stderr).
    """
    proc = subprocess.run(
        [sys.executable, "-m", module, *args],
        capture_output=True, text=True,
    )
    return proc.returncode, proc.stdout + proc.stderr
