package datazeus.javagroovy.series4._05

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 4 · 05
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Hibernate — The Library That Turns Your JPA Code Into SQL
 *
 *     zeus.bat koans javagroovy series4 _05     (Windows)
 *     ./zeus.sh koans javagroovy series4 _05    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:count:rows-held, koan:count:roundtrips
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: know what actually runs your JPA code, and what it costs on a data job.
 * CONSTRUCTS: SessionFactory/Session, dirty checking, the first-level cache, flush timing,
 * StatelessSession, hibernate.jdbc.batch_size, @BatchSize.
 * GET THE RELATIONSHIP RIGHT IN THE FIRST SENTENCE: Hibernate is NOT an alternative to JPA — it
 * IMPLEMENTS it, and it is Spring Boot's default provider. It also predates JPA and keeps its own
 * native API (Session, StatelessSession, HQL) that is not in the spec. "JPA, plus more", never
 * "instead of". That extra surface is why this episode exists: StatelessSession is the single
 * most useful thing here for a data job and a reader thinking in pure JPA will never find it.
 * TEACH WHAT IT DOES FIRST, then what it costs. The title used to name only the cost, which read
 * as though memory management were Hibernate's job.
 * This is where the track's central claim stops being an assertion and becomes a demonstration:
 * the session is built to track a graph of objects you are EDITING; a data job is MOVING ROWS, so
 * every service the session provides is overhead you did not ask for.
 * KOAN: the same load with and without StatelessSession — round trips, and a rows-held sink
 * showing what the session was holding on your behalf.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class HibernateKoans extends JvmKoanBase {

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
