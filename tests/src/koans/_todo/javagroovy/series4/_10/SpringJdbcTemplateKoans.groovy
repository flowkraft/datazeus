package datazeus.javagroovy.series4._10

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 4 · 10
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Spring JdbcTemplate — Running Your Own SQL From a Spring Application
 *
 *     zeus.bat koans javagroovy series4 _10     (Windows)
 *     ./zeus.sh koans javagroovy series4 _10    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:complete, koan:count:roundtrips
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ──────────────────────────────────────
 * GOAL: get Spring's plumbing without giving up your SQL.
 * CONSTRUCTS: JdbcTemplate, NamedParameterJdbcTemplate, RowMapper, @Transactional,
 * DataSource configuration, and HikariCP, which Spring Boot auto-configures.
 * CLOSES THE POOLING GAP: Series 2 · 10 teaches Connection -> Statement -> ResultSet -> close, and
 * every real job uses a pool. Opening a connection per query is the classic production bug.
 * It lands here for CONVENIENCE, not because pooling belongs to Spring — if Series 2 ever grows a
 * pooling episode, move it there and leave a pointer.
 * POSITION: third, not first. An earlier draft opened the series here because it depends on
 * neither JPA nor Hibernate. That made the series zigzag between vendors. Foundations first, then
 * one vendor at a time.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class SpringJdbcTemplateKoans extends JvmKoanBase {

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
