package datazeus.javagroovy.series3._30

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 3 · 30
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Testing Data Code — JUnit and Spock, and What Is Worth Testing
 *
 *     zeus.bat koans javagroovy series3 _30     (Windows)
 *     ./zeus.sh koans javagroovy series3 _30    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:author
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: know what is worth testing in a data job, and write those tests.
 * CONSTRUCTS: JUnit 5 basics, Spock given/when/then and data tables, testing a transform as a
 * pure function, using DuckDB as a real database in a test.
 * The honest scope: transforms and parsing are worth testing; a thin JDBC wrapper mostly is not.
 * Say which is which — that judgement is the episode.
 * DROPPED: a "mutation" koan that ran the learner's spec against a seeded-buggy implementation to
 * prove the test catches something. It is the only honest way to grade a test, and it is too
 * clever — running a spec from inside a spec is exactly the kind of test infrastructure a course
 * acquires and then has to maintain forever.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class TestingWithSpockKoans extends JvmKoanBase {

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
