package datazeus.javagroovy.series2._40

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 2 · 40
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Streaming Large Results — Reading Millions of Rows Without Filling Memory
 *
 *     zeus.bat koans javagroovy series2 _40     (Windows)
 *     ./zeus.sh koans javagroovy series2 _40    (macOS/Linux)
 *
 * TODO — NOT A KOAN YET. Lives under src/koans/_todo/, which maven does not compile
 * and zeus does not see, so it cannot mislead anyone into thinking the exercise exists.
 * MOVE IT into src/koans/groovy/datazeus/javagroovy/ (same series/episode path) when it is real.
 *
 * Brief below; delete it as you write the koans.
 *
 * ── READ THESE TWO FIRST ────────────────────────────────────────────────
 *   JvmKoanBase                        the rules, and why they are the rules
 *   series2/_35/BatchingAndTransactionsKoans   the worked example
 *
 * ── THE THREE RULES THAT DECIDE EVERY KOAN HERE ─────────────────────────
 *  1. THE BLANK IS A DATA DECISION, NEVER A LANGUAGE FACT. This reader already knows Java.
 *     A koan that teaches map() insults them; one that hands them a wrong number does not.
 *  2. NEVER ASSERT WALL-CLOCK TIME. Every performance lesson here has a countable proxy that
 *     is also the actual lesson. If you think you need a stopwatch, you need a counter.
 *  3. ONE MECHANISM, ONLY THE ASSERTION TARGET CHANGES — a value, a counter, captured output,
 *     or a process result. There is no second framework and there should never be one.
 *
 * THIS LESSON'S RUNGS: koan:count:rows-held
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: read more rows than fit in memory.
 * CONSTRUCTS: setFetchSize, TYPE_FORWARD_ONLY, autoCommit(false) for the Postgres cursor,
 * consuming row-by-row instead of collecting.
 * KOAN ORACLE: RowSink counts the most rows held AT ONCE. Streaming gives a peak of 1;
 * materialising gives the row count. Verified: 1 vs 1000.
 * REJECTED: forking a JVM with -Xmx32m so the naive version OOMs. It works and it is dramatic,
 * but it costs ~30s per run to prove what the counter proves instantly — and an OOM only tells
 * you that you ran out of memory, where the counter tells you what you did wrong.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class StreamingLargeResultsKoans extends JvmKoanBase {

    // TODO: koans, in the same order as the lesson's beats.
    //
    //   value      expect: someTransform(ROWS) == ___
    //   counter    def c = counting(); ...; c.executions == ___
    //              def s = sink();     ...; s.peakHeld  == ___
    //   captured   def log = capturing { ... };  log.contains(...) && !log.contains(SECRET)
    //   process    def r = runTool(Tool, "--in", "x.csv");  r.exit == ___
    //
    // Northwind is already open as `db` (inherited from KoanBase) — use the SAME numbers the
    // SQL koans use wherever the topic allows it. Two tools, one skill, and it costs nothing.
}
