"""
Shared plumbing for the AI for Data koans.

╔══════════════════════════════════════════════════════════════════════════╗
║  THE DECISIONS BEHIND THIS FILE — 2026-08-24. Read before extending it.  ║
╚══════════════════════════════════════════════════════════════════════════╝

WHY PYTEST HERE WHEN FIVE SIBLING TRACKS USE SPOCK
ETL, dbt, Data Warehousing, Data Ops and BI all assert over JDBC or over a process, which
KoanBase and JvmKoanBase already do — a second harness for them would be a second framework
with no new capability. AI is the exception on two counts: the client libraries live in
Python, and `koan:eval` needs to score generated output where those libraries are.

It reuses the Python koan runner (Docker, `zeus koans ai …`), not a new one. Same image, same
`/datasets` mount, same `___` sentinel semantics as courses/python.

── THE RULE THIS WHOLE TRACK RESTS ON ────────────────────────────────────
NEVER ASSERT THE TEXT OF GENERATED SQL. Two correct queries can differ in every character —
alias names, join order, EXISTS vs IN, whitespace. Asserting the string tests the model's
prose style and fails for reasons a learner cannot act on.

    WRONG   assert generated.strip() == "SELECT ... FROM Customers WHERE Country = 'Germany'"
    RIGHT   run it, and assert it returns the same 11 rows the known-good query returns

That is what `equivalent_to` below is for, and it is the single thing that makes an AI track
gradeable at all rather than vibes.

── AND THE SECOND RULE: A MODEL IS NOT DETERMINISTIC, SO SCORE IT ────────
For retrieval and multi-step questions even a correct system is right *most* of the time.
`score_against` asserts a THRESHOLD over a known-good set. This is the ONLY place in the
academy where a koan may be statistical — everywhere else that would be a bug. It is honest
here because the subject genuinely is.

── NO NETWORK IN A KOAN ──────────────────────────────────────────────────
A koan that calls a paid API is a koan that fails on a train, costs money to run, and gives a
different answer every time. `recorded()` replays fixtures captured once and committed. An
episode that genuinely needs a live model is an episode for the lesson prose, not the koans.
"""

import json
import os
import pathlib

import pytest

# Reuse the Python track's blank and Northwind fixtures — one harness, not two.
import sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from conftest import ___, northwind, nw_df, CountingConnection, counting  # noqa: F401,E402

FIXTURES = pathlib.Path("/datasets/ai-fixtures")


def equivalent_to(con, generated_sql: str, known_good_sql: str) -> bool:
    """True when two queries return the SAME rows, whatever they look like.

    Order-insensitive on purpose: a generated query is under no obligation to ORDER BY, and a
    koan that failed for that would be teaching the wrong thing. When order IS the subject,
    compare the lists directly instead and say so in the koan.
    """
    def rows(q):
        return sorted(tuple(r) for r in con.sql(q).fetchall())
    return rows(generated_sql) == rows(known_good_sql)


def score_against(answers: dict, known_good: dict) -> float:
    """Fraction of questions answered correctly. Assert a threshold, never equality.

    A koan asserting 1.0 on a model's output is a koan that goes red the week the model is
    updated, for a reason the learner cannot act on — which is how a suite gets deleted.
    """
    if not known_good:
        return 0.0
    hit = sum(1 for k, v in known_good.items() if answers.get(k) == v)
    return hit / len(known_good)


def recorded(name: str):
    """Replay a model response captured once and committed, so koans need no network.

    Capture with the lesson's own script and commit the JSON; never let a koan call a live
    endpoint. See datasets/README.md for where these sit.
    """
    p = FIXTURES / f"{name}.json"
    if not p.exists():
        pytest.skip(f"no recorded fixture {name} — run these through `zeus koans ai`")
    return json.loads(p.read_text(encoding="utf-8"))
