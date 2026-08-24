package datazeus.javagroovy.series2._60

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 2 · 60
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Virtual Threads and Parallel Streams — Running Slow Calls in Parallel
 *
 *     zeus.bat koans javagroovy series2 _60     (Windows)
 *     ./zeus.sh koans javagroovy series2 _60    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:predict, koan:complete
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: do many slow things at once without breaking anything.
 * CONSTRUCTS: Executors.newVirtualThreadPerTaskExecutor, structured concurrency, parallelStream,
 * and why a parallel stream over blocking JDBC is the mistake this episode prevents.
 * THE LESSON IS WHICH KIND OF WORK YOU HAVE: virtual threads for I/O-bound waiting (all of
 * Series 2), parallel streams for CPU-bound work. Using either on the other's problem is worse
 * than doing nothing.
 * This is also the Java 21 feature the track assumed and never used — the header calls out
 * records, sealed types and text blocks, then left the headline out.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class ConcurrencyAndVirtualThreadsKoans extends JvmKoanBase {

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
