package datazeus.javagroovy.series2._30

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 2 · 30
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * jOOQ — Writing SQL in Java That the Compiler Checks
 *
 *     zeus.bat koans javagroovy series2 _30     (Windows)
 *     ./zeus.sh koans javagroovy series2 _30    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:complete
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: write SQL in Java that the compiler checks, and see the SQL it produces.
 * CONSTRUCTS: DSLContext, generated table/field classes, the code-generation build step,
 * render() to get the SQL as a string.
 * DECISION — jOOQ IS IN SERIES 2, NOT SERIES 4. It is a LIBRARY, not a framework: you call it,
 * it does not call you. And it is the SQL-first position that Series 4 is measured against, so
 * filing it beside JPA and Spring Data would imply it is the same kind of answer. It sits last
 * in this series' database block: the most machinery for the most safety, after the five simpler
 * options. Series 4 · 15's choose-koan puts it back in the comparison, which is where the
 * head-to-head belongs.
 * KOAN ORACLE: render() makes the generated SQL a string, so you can assert on it. Deterministic.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class SqlFirstWithJooqKoans extends JvmKoanBase {

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
