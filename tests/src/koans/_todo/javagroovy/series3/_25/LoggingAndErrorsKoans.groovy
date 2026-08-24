package datazeus.javagroovy.series3._25

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 3 · 25
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Logging and Failure — What Someone Needs to See When the Job Breaks
 *
 *     zeus.bat koans javagroovy series3 _25     (Windows)
 *     ./zeus.sh koans javagroovy series3 _25    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:capture
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: make a failure readable by someone who was not there when it happened.
 * CONSTRUCTS: SLF4J, structured context (which record, which stage), logging an exception ONCE,
 * exit codes, and never logging a secret.
 * KOAN ORACLE (capture): inject a bad record mid-run and assert on what was printed —
 *   MUST contain: the failing record's id, the stage name, the stack trace exactly once
 *   MUST NOT contain: the secret, a second copy of the same trace
 * Captures the STREAMS rather than hooking a logging backend on purpose: the koans must not care
 * whether the learner used SLF4J or println, and a course that mandates a logging framework to
 * pass an exercise is teaching the wrong thing.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class LoggingAndErrorsKoans extends JvmKoanBase {

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
