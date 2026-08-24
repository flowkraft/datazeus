package datazeus.javagroovy.series3._15

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 3 · 15
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Configuration and Secrets — Keeping Passwords Out of Your Code
 *
 *     zeus.bat koans javagroovy series3 _15     (Windows)
 *     ./zeus.sh koans javagroovy series3 _15    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:process:jar, koan:capture
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: configure a job without putting a password anywhere it can leak.
 * CONSTRUCTS: environment variables, profiles, a config record parsed once at startup,
 * failing loudly when a required value is missing.
 * KOAN ORACLE — a real pincer, and the reason this episode is testable at all:
 *   with the env var set    -> runs, and the secret appears in NEITHER stdout NOR the jar bytes
 *   with it absent          -> non-zero exit, and the message NAMES what is missing
 * A tool that silently falls back to a default is the bug this episode exists to prevent.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class ConfigAndSecretsKoans extends JvmKoanBase {

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
