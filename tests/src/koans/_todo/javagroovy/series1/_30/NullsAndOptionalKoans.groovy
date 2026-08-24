package datazeus.javagroovy.series1._30

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 1 · 30
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Nulls and Optional — Handling Data That Is Missing
 *
 *     zeus.bat koans javagroovy series1 _30     (Windows)
 *     ./zeus.sh koans javagroovy series1 _30    (macOS/Linux)
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
 * GOAL: decide what to do with values that are not there, before they become a wrong total.
 * CONSTRUCTS: Optional, orElse/orElseGet, OptionalDouble from average(), null in summingDouble.
 * KOAN (predict): average() of an empty stream, and a null slipping into a sum. Both produce a
 * wrong number rather than an exception, which is why they belong in a data track.
 * BOUNDARY: Learn SQL Series 1 owns SQL's NULL semantics (three-valued logic). This is the Java
 * side. Data Modeling Series 1 owns nullable-or-not as a DESIGN decision. Three tracks, three
 * different questions that share a word — name the other two.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class NullsAndOptionalKoans extends JvmKoanBase {

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
