"""
The Python track's shared harness — everything that is NOT a pytest fixture.

WHY THIS FILE EXISTS, AND WHY IT IS NOT NAMED conftest.py — 2026-08-26.

pytest's default `prepend` import mode names a module after its basename when the directory
holds no `__init__.py`. Every conftest.py in the tree therefore wants the SAME module name,
`conftest`. That is fine while only one of them is ever loaded, and it stops being fine the
moment a run spans more than one — `zeus koans python` with no lesson, say, which collects
both this directory and ai/.

At that point `from conftest import ___` inside ai/conftest.py does not reach up to the root
conftest at all: `import` consults sys.modules before sys.path, and sys.modules["conftest"] is
by then ai/conftest.py ITSELF, still half-executed. The import fails as a circular import, and
because it happens during collection pytest aborts the whole session — so a single unreachable
name stopped every koan in the track from running, including the healthy ones.

No amount of sys.path juggling can fix that, because sys.path is not what is consulted. The
fix is a name that cannot collide. `koanlib` exists once, at the track root, so `import
koanlib` resolves the same way from anywhere below it.

WHAT BELONGS HERE: the blank, and the assert targets that are plain Python objects. WHAT DOES
NOT: fixtures. Fixtures declared in conftest.py are inherited by every directory beneath it
automatically — importing them was never necessary, and doing so is what caused the crash.
"""

import subprocess
import sys

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
