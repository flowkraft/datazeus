package datazeus.javagroovy.series4._15

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 4 · 15
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Spring Data Repositories — Your Method Name Becomes the Query
 *
 *     zeus.bat koans javagroovy series4 _15     (Windows)
 *     ./zeus.sh koans javagroovy series4 _15    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:choose, koan:diagnose
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: know what Spring Data does for you, and choose between its two flavours.
 * CONSTRUCTS: Repository interfaces, derived query methods (the method NAME becomes the query),
 * @Query for the rest, Spring Data JDBC vs Spring Data JPA, Pageable.
 * THE CHOOSE-KOAN, and the one place the whole argument meets: ONE requirement, FOUR
 * implementations — JdbcTemplate, Spring Data JDBC, Spring Data JPA, and jOOQ from Series 2 · 30.
 * jOOQ belongs in the comparison even though it lives in another series; leaving it out would rig
 * the answer. The winner genuinely changes with the requirement, and a single right answer would
 * be a lie.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class SpringDataRepositoriesKoans extends JvmKoanBase {

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
