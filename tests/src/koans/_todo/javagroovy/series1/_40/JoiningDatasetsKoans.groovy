package datazeus.javagroovy.series1._40

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 1 · 40
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Joining Two Datasets in Memory — Combining Rows Without Doubling Totals
 *
 *     zeus.bat koans javagroovy series1 _40     (Windows)
 *     ./zeus.sh koans javagroovy series1 _40    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:predict, koan:diagnose
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: combine two sources on a key, and get the totals right.
 * CONSTRUCTS: index one side into a Map, stream the other, flatMap for one-to-many.
 * THE BEST KOAN IN SERIES 1. This is the SAME join fan-out bug as Learn SQL Series 2 · 06, which
 * the reader has already met — except in memory NOTHING warns you: no database, no constraint,
 * no row count. Join orders to order-lines, sum the order total, get 3x revenue.
 * Say the cross-reference out loud; it is the strongest argument for why SQL came first.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class JoiningDatasetsKoans extends JvmKoanBase {

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
