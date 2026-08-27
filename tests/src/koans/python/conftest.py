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

WHERE THE NON-FIXTURE PIECES LIVE: koanlib.py, not here. `___`, CountingConnection, RowSink
and run_tool are plain Python objects that other conftests and the koans themselves import,
and "conftest" is not a unique module name — see the note at the top of koanlib.py for what
that cost us. They are re-exported below so `from conftest import ___` keeps working in koans
that already say it.
"""

import os
import re
import shutil
import tempfile

import duckdb
import pytest

# Re-exported on purpose: the koans say `from conftest import ___`, and a learner who has
# already edited one keeps that line through `zeus update`. koanlib is where they now live.
from koanlib import ___, CountingConnection, RowSink, run_tool  # noqa: F401


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


@pytest.fixture
def counting(northwind):
    """`counting()` -> a Northwind connection that counts every query you send it."""
    return lambda: CountingConnection(northwind)


@pytest.fixture
def sink():
    return RowSink()


# ══════════════════════════════════════════════════════════════════════════
#  THE PATH TO ENLIGHTENMENT — the same screen the JVM koans print
# ══════════════════════════════════════════════════════════════════════════
#
#  A learner does not think of themselves as "on the pytest track" or "on the Spock track".
#  They are doing the koans. So walking the path has to LOOK the same wherever they are, and
#  this is the Python half of PathToEnlightenment.groovy — same sections, same wording, same
#  progress bar, same zen line. Keep the two in step.
#
#  Two things this has to solve that the JVM side does not:
#
#  1. THE PATHS ARE CONTAINER PATHS. These koans run in Docker so nobody has to install
#     Python, and the learner neither knows nor should care. Every path pytest reports is
#     therefore a location inside that container, which is not where their file lives and
#     cannot be pasted into an editor. `zeus koans python` passes the real host directory in
#     as DATAZEUS_KOANS_HOST_DIR and we translate back to it.
#
#  2. PYTEST'S OWN OUTPUT IS THE WRONG OUTPUT. Tracebacks, F characters and "4 failed in
#     0.9s" are a test-runner's report to an engineer; the koans are a lesson. The launchers
#     run pytest with `-p no:terminalreporter`, so pytest prints nothing at all and this
#     writes the whole screen. That also means collection errors would be SILENT unless we
#     print them ourselves — see _screen(), which is what a learner's syntax error hits.

MOUNT = "/koans"

_ESC = "\033"
_GREEN = _ESC + "[32m"
_RED = _ESC + "[31m"
_DIM = _ESC + "[90m"
_BOLD = _ESC + "[1m"
_CYAN = _ESC + "[36m"
_RESET = _ESC + "[0m"

_ZEN = [
    "Real data, real questions. Become the Data Zeus.",
    "Don't go hunting for the answer — write a query and ask for it.",
    "A query you typed is worth a thousand you watched.",
    "You became a legendary Data Zeus!",
    "Small data, fits in your head. Real enough to ask anything.",
    "The rows you keep tell the truth. WHERE is your discipline.",
]

_results = []          # [rel, koan_name, passed, hint_lines, def_line]
_seen = set()
_collect_errors = []   # [where, text]


def pytest_collectreport(report):
    if report.failed:
        _collect_errors.append((report.nodeid or "?", report.longreprtext))


def pytest_runtest_logreport(report):
    # "call" is the koan itself; a "setup" failure never reaches call, and losing it would
    # silently drop a koan from the roster.
    if report.when not in ("call", "setup"):
        return
    if report.when == "setup" and not report.failed:
        return
    if report.nodeid in _seen:
        return
    _seen.add(report.nodeid)
    rel = report.nodeid.split("::")[0]
    name = report.nodeid.split("::")[-1]
    def_line = (report.location[1] or 0) + 1
    _results.append([rel, name, report.passed, _hint(report), def_line])


def _hint(report):
    """The koan's own message, without the traceback around it.

    pytest puts the raised message on the `E ` lines. `___` raises through pytest.fail with
    exactly the two lines a learner needs ("you haven't filled in the blank yet" / "the koan
    expected it to equal: 12"), so lifting those gives the JVM track's hint section for free.
    """
    text = getattr(report, "longreprtext", "") or ""
    out = []
    for line in text.splitlines():
        if line.startswith("E "):
            cleaned = line[1:].strip()
            if not out and cleaned.startswith("Failed: "):
                cleaned = cleaned[len("Failed: "):]
            if cleaned:
                out.append(cleaned)
    return out[:6]


def _koan_name(test_name):
    """test_utf8_is_not_a_safe_default -> "utf8 is not a safe default"."""
    base = test_name[5:] if test_name.startswith("test_") else test_name
    return base.replace("_", " ")


def _lesson_title(rel):
    """series1/_15/test_files_and_encodings.py -> "series1 _15 Files And Encodings"."""
    parts = rel.split("/")
    stem = parts[-1]
    if stem.startswith("test_"):
        stem = stem[5:]
    if stem.endswith(".py"):
        stem = stem[:-3]
    pretty = " ".join(w.capitalize() for w in stem.split("_") if w)
    tag = " ".join(p for p in parts[:-1] if p)
    tag = tag.replace("/", " ")
    return (tag + " " + pretty).strip()


def _find_blank(rel, start_line):
    """From the koan's def line, the line actually holding the ___ — the blank to fill.

    Bounded to this koan (stop at the next `def test_`) so a filled-but-wrong koan never
    points at a later koan's blank. Same rule as blankLine() on the JVM side.
    """
    try:
        with open(os.path.join(MOUNT, rel), encoding="utf-8") as fh:
            lines = fh.read().splitlines()
    except OSError:
        return None
    for i in range(max(1, start_line), min(len(lines), start_line + 60) + 1):
        text = lines[i - 1]
        if "___" in text:
            return i, text.strip()
        if i > start_line and text.lstrip().startswith("def test_"):
            break
    return None


def _host_path(rel, host_root):
    """A container-relative koan path -> that same file on the learner's own machine."""
    if not host_root:
        return os.path.join(MOUNT, rel)
    windows = "\\" in host_root or (len(host_root) > 1 and host_root[1] == ":")
    sep = "\\" if windows else "/"
    return host_root.rstrip("/\\") + sep + rel.replace("/", sep)


_MOUNT_RE = re.compile(re.escape(MOUNT) + r"/([^\s\"']+)")


def _to_host(text, host_root):
    """Rewrite any /koans/... path INSIDE a message to the learner's own path.

    Python's own errors quote the file they choked on — `File "/koans/series1/_15/x.py",
    line 49` — and that path is inside a container the learner does not know exists.
    """
    if not host_root or not text:
        return text
    return _MOUNT_RE.sub(lambda m: _host_path(m.group(1), host_root), text)


def _error_lines(text):
    """The part of a collection failure a learner can act on.

    pytest's report is a full import traceback: a dozen frames through importlib and pytest's
    own assertion rewriter, then — on the `E ` lines at the very end — the actual SyntaxError,
    the offending line and a caret under it. Only that last part is about their edit.
    """
    lines = (text or "").splitlines()
    flagged = [ln[1:].rstrip() for ln in lines if ln.startswith("E ")]
    return flagged or lines[-12:]


def _bar(done, total):
    width = min(total, 50) if total else 0
    fill = 0 if not total else round(done / total * width)
    if done > 0 and fill == 0:
        fill = 1
    if done == total:
        fill = width
    return "[" + _GREEN + "#" * fill + _RESET + _DIM + "." * (width - fill) + _RESET + "]"


def _screen(host_root):
    o = ["", ]

    if _collect_errors:
        # The Python twin of "YOUR KOANS DID NOT COMPILE": the lesson is there, the learner's
        # own edit will not import. Never say "update" here — nothing is missing.
        o.append("=" * 74)
        o.append("  " + _BOLD + "YOUR KOANS DID NOT LOAD." + _RESET)
        o.append("")
        o.append("  The lesson is present - this is an error in the code you edited,")
        o.append("  usually a typo where the ___ used to be (a missing quote, comma")
        o.append("  or bracket). You do NOT need to update; fix the edit and re-run.")
        o.append("")
        o.append("  Python said:")
        o.append("=" * 74)
        for where, text in _collect_errors:
            rel = where.split("::")[0]
            o.append("")
            if rel.endswith(".py"):
                o.append("      Your koans file is  " + _BOLD + _CYAN +
                         os.path.basename(rel) + _RESET)
                o.append("      Its location is     " + _CYAN +
                         _host_path(rel, host_root) + _RESET)
                o.append("")
            for line in _error_lines(text):
                o.append("  " + _to_host(line, host_root))
        o.append("")
        return "\n".join(o)

    if not _results:
        return None

    total = len(_results)
    done = sum(1 for r in _results if r[2])

    # One "Forging" group per lesson file, in the order the koans ran.
    by_file = []
    for rel, name, passed, hint, line in _results:
        if not by_file or by_file[-1][0] != rel:
            by_file.append((rel, []))
        by_file[-1][1].append((name, passed, hint, line))

    for rel, koans in by_file:
        o.append("  " + _CYAN + _BOLD + "Forging '" + _lesson_title(rel) + "'" + _RESET)
        o.append("")
        for name, passed, _hints, _line in koans:
            if passed:
                o.append("      " + _GREEN + "You mastered '" + _koan_name(name) +
                         "' which expanded +1 your awareness." + _RESET)
            else:
                o.append("      " + _RED + "'" + _koan_name(name) +
                         "' has damaged your karma." + _RESET)
                break   # the rest of this lesson waits — one koan at a time
        o.append("")

    if done == total:
        o.append("  " + _GREEN + _BOLD + "You have reached enlightenment." + _RESET)
        o.append("  " + _GREEN + "Every koan is green - " + str(total) + " of " +
                 str(total) + ". Well done." + _RESET)
        o.append("")
        o.append("  " + _CYAN + _ZEN[done % len(_ZEN)] + _RESET)
        o.append("")
        return "\n".join(o)

    current = next((r for r in _results if not r[2]), None)
    o.append("  " + _BOLD + "You have not yet reached enlightenment ..." + _RESET)
    if current:
        rel, name, _passed, hint, def_line = current
        for line in (hint or ["The koan \"" + _koan_name(name) + "\" is not yet true."]):
            o.append("      " + _to_host(line, host_root))
        o.append("")
        o.append("  " + _BOLD + "Please meditate on the following code:" + _RESET)
        o.append("      Your koans file is  " + _BOLD + _CYAN + os.path.basename(rel) + _RESET)
        o.append("      Its location is     " + _CYAN + _host_path(rel, host_root) + _RESET)
        blank = _find_blank(rel, def_line)
        if blank:
            o.append("")
            o.append("      Fix line " + _BOLD + str(blank[0]) + _RESET + ":")
            o.append("      " + str(blank[0]) + ":   " + blank[1])
    o.append("")
    o.append("      your path thus far  " + _bar(done, total) + "  " +
             _BOLD + str(done) + _RESET + " of " + _BOLD + str(total) + _RESET + " koans")
    o.append("")
    o.append("  " + _CYAN + _ZEN[done % len(_ZEN)] + _RESET)
    o.append("")
    return "\n".join(o)


def pytest_unconfigure(config):
    # unconfigure, not pytest_terminal_summary: with the terminal reporter switched off there
    # is no terminalreporter to write through, and this runs after the session is finished
    # either way.
    try:
        screen = _screen(os.environ.get("DATAZEUS_KOANS_HOST_DIR"))
    except Exception:                                    # never let the report kill the run
        return
    if screen:
        print(screen)
