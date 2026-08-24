package datazeus.dataops.series2._00

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  KOANS — Data Ops · Series 2 · 00
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Transactions & ACID — BEGIN, COMMIT, ROLLBACK and What They Guarantee
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile and zeus
 * does not see, so it cannot mislead anyone into thinking the exercise exists. MOVE IT into
 * src/koans/groovy/datazeus/dataops/series2/_00/ when it is real.
 *
 *     zeus.bat koans dataops series2 _00     (Windows)
 *     ./zeus.sh koans dataops series2 _00    (macOS/Linux)
 *
 * ── READ THESE FIRST ────────────────────────────────────────────────────
 *   _internal/KoanBase.groovy       the ___ blank and the assertion helpers
 *   _internal/JvmKoanBase.groovy    process and counter helpers
 *   courses/dataops/curriculum.yaml   the track's decisions
 *
 * ── THE RULES ───────────────────────────────────────────────────────────
 *  1. THE BLANK IS A DATA DECISION, NEVER A SYNTAX FACT. If a blank can be answered by
 *     reading the docs instead of the data, it is the wrong blank.
 *  2. NEVER ASSERT WALL-CLOCK TIME. Every performance lesson has a countable proxy that is
 *     also the lesson. If you think you need a stopwatch, you need a counter.
 *  3. ONE MECHANISM, only the assertion target changes.
 *  4. Use the SAME Northwind numbers every other track uses wherever the topic allows it.
 *
 * ── WHAT THIS EPISODE'S RUNGS OBLIGE YOU TO WRITE ───────────────────────
 *   CONCURRENT — two sessions. Assert what session B sees while A holds an open
 *     transaction or a lock. A single session literally cannot observe this.
 *   PREDICT — state the answer BEFORE running it. The blank is a number or a row
 *     count the reader should be able to reason out; being wrong is the lesson.
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: TODO — one sentence.
 * THE BLANK TURNS ON: TODO — the specific number/value that is either right or quietly,
 * plausibly wrong. This is the only part of this file a machine could not write for you.
 */
@Stepwise
class TransactionsAndAcidKoans extends KoanBase {

    // TODO: koans, one per idea in the lesson, in the lesson's order.
}
