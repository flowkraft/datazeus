"""
╔══════════════════════════════════════════════════════════════════════════╗
║  KOANS — AI for Data · Series 3 · 10
╚══════════════════════════════════════════════════════════════════════════╝

Read-Only Contracts & Least Privilege — What an Agent Must Never Touch

TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which the runner does not mount and zeus
does not see. MOVE IT into src/koans/python/ai/series3/_10/ when it is real.

    zeus.bat koans ai series3 _10     (Windows)
    ./zeus.sh koans ai series3 _10    (macOS/Linux)

── WHY PYTEST AND NOT SPOCK FOR THIS TRACK ─────────────────────────────
The other five roadmap tracks assert over JDBC or over a process, which KoanBase already does.
AI is the exception: the client libraries live in Python, and `koan:eval` — score generated
output against known-good and assert a threshold — has to live where they are.

── READ THESE FIRST ────────────────────────────────────────────────────
  ../../conftest.py                the ___ blank, fixtures and helpers
  courses/ai/curriculum.yaml       the track's decisions, incl. the vector-store one

── THE RULES ───────────────────────────────────────────────────────────
 1. THE BLANK IS A DATA DECISION, NEVER A SYNTAX FACT.
 2. NEVER ASSERT WALL-CLOCK TIME — a countable proxy, always.
 3. NEVER ASSERT THE TEXT OF GENERATED SQL. Two correct queries can differ in every
    character. Assert the ROWS it returns. That is what makes this track gradeable.
 4. Same Northwind numbers as every other track.

── WHAT THIS EPISODE'S RUNGS OBLIGE YOU TO WRITE ───────────────────────
  PREDICT — state the answer BEFORE running it. The blank is a number or a row
     count the reader should be able to reason out; being wrong is the lesson.
  DIAGNOSE — hand them something already broken and ask WHY. No fix required.
     The habit this builds is noticing, which is the part nobody teaches.

── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
GOAL: TODO — one sentence.
THE BLANK TURNS ON: TODO — the specific value that is either right or quietly, plausibly
wrong. The only part of this file a machine could not write for you.
"""

from conftest import ___  # noqa: F401


# TODO: koans, one per idea in the lesson, in the lesson's order.
