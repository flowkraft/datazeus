package datazeus.javagroovy.series2._35

import datazeus._internal.JvmKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  JVM KOANS — Java & Groovy for Data · Series 2 · 35                       ║
 * ║  Batches and Transactions — 100,000 Inserts That Finish                   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 *     zeus.bat koans javagroovy series2 _35     (Windows)
 *     ./zeus.sh koans javagroovy series2 _35    (macOS/Linux)
 *
 * THE WORKED EXAMPLE for the counter-assertion shape. If you are writing koans elsewhere in
 * this track, read JvmKoanBase's header and then this file — between them they carry every
 * decision the track made about exercises.
 *
 * ── WHY THIS EPISODE NEEDS A DIFFERENT ASSERTION ─────────────────────────
 * Series 1 koans assert on a VALUE, because shaping data in memory is a pure function with
 * one right answer. Not here: the slow version and the fast version both end up with the
 * same correct rows. Asserting the row count passes for both, so it teaches nothing.
 *
 * The obvious next move is to time them. DO NOT. Timing assertions are machine-dependent,
 * flaky in CI, and get deleted by whoever the flake wakes up. The round-trip COUNT is
 * deterministic on every machine, and it is not a stand-in for the lesson — it IS the lesson.
 *
 * ── WHY THE NUMBERS HERE ARE NOT 100,000 ─────────────────────────────────
 * Measured on DuckDB, 2026-08-24, before choosing them:
 *     20,000 rows one-at-a-time  ->  27.0 s
 *     20,000 rows batched        ->   3.5 s   (21 round trips)
 * So the episode's headline 100,000 naive inserts is about 135 seconds, and a koan nobody
 * waits for is a koan nobody runs. The naive koan therefore uses 2,000 rows — the counter
 * still says "one round trip per row", which is the entire point, and it says it in three
 * seconds. The batched koan uses 50,000, because showing that batching makes real volume
 * tractable is worth the eight seconds.
 *
 * Keep that trade in mind before raising these numbers: the lesson lives in the COUNTER, not
 * in how long the learner sat there.
 *
 * ── WHAT IS ACTUALLY BEING DECIDED ───────────────────────────────────────
 * Not "how do I write addBatch" — this track assumes you know Java and you can look that up.
 * The decisions are: how much work should cross the wire at once, and what happens to a
 * half-finished load when row 40,000 turns out to be bad. Those are data-engineering
 * judgements, and they are what the blanks sit on.
 */
@Stepwise // walk them in order — once one fails, the rest wait
class BatchingAndTransactionsKoans extends JvmKoanBase {

    static final int CHATTY = 2_000
    static final int BULK = 50_000

    def setup() {
        db.execute('DROP TABLE IF EXISTS staging_orders')
        db.execute('CREATE TABLE staging_orders (id INTEGER, customer VARCHAR, total DECIMAL(19,4))')
    }

    // 1) See the problem instead of being told about it. This code is CORRECT. Predict how
    //    many times it talks to the database before you run it — the answer is the lesson.
    def "the slow way is not wrong, it is chatty"() {
        given:
        def counter = counting()
        def conn = counter.connection

        when: "one row, one round trip, over and over"
        def ps = conn.prepareStatement('INSERT INTO staging_orders VALUES (?, ?, ?)')
        (1..CHATTY).each { i ->
            ps.setInt(1, i); ps.setString(2, "cust-${i % 40}"); ps.setBigDecimal(3, 10.0g)
            ps.executeUpdate()
        }

        then: "the data is perfectly fine — correctness cannot tell you anything here"
        firstCell('SELECT count(*) FROM staging_orders') == CHATTY

        and: "this is the number that should bother you"
        counter.executions == ___
    }

    // 2) Twenty-five times the rows. One blank: how many should cross the wire at a time?
    //    Too small and you have barely helped; too large and you are holding the whole load
    //    in memory at both ends. There is a broad right answer, not a magic number — so the
    //    koan checks the CONSEQUENCE rather than your arithmetic.
    def "batching: far more rows, far fewer conversations"() {
        given:
        def counter = counting()
        def conn = counter.connection
        conn.autoCommit = false
        int batchSize = ___

        when:
        def ps = conn.prepareStatement('INSERT INTO staging_orders VALUES (?, ?, ?)')
        (1..BULK).each { i ->
            ps.setInt(1, i); ps.setString(2, "cust-${i % 40}"); ps.setBigDecimal(3, 10.0g)
            ps.addBatch()
            if (i % batchSize == 0) ps.executeBatch()
        }
        ps.executeBatch()
        conn.commit()

        then: "every row still arrived — a faster wrong answer is not the goal"
        firstCell('SELECT count(*) FROM staging_orders') == BULK
        counter.rowsBatched == BULK

        and: "and you asked a few dozen times instead of fifty thousand"
        counter.executions < 120

        cleanup:
        conn.autoCommit = true
    }

    // 3) The half-written load — the lesson people usually buy in production, expensively.
    //    With autocommit ON, a failure at row 40,000 leaves 39,999 rows behind and no error
    //    anyone finds until the totals are wrong next month. Predict what survives here.
    def "a transaction is what makes a failed load leave nothing behind"() {
        given:
        def conn = counting().connection
        conn.autoCommit = false

        when: "row 40,000 is bad, and we never commit"
        try {
            def ps = conn.prepareStatement('INSERT INTO staging_orders VALUES (?, ?, ?)')
            (1..50_000).each { i ->
                if (i == 40_000) throw new IllegalStateException("bad record at ${i}")
                ps.setInt(1, i); ps.setString(2, 'c'); ps.setBigDecimal(3, 10.0g)
                ps.addBatch()
                if (i % 1000 == 0) ps.executeBatch()
            }
        } catch (IllegalStateException ignored) {
            conn.rollback()
        }

        then:
        firstCell('SELECT count(*) FROM staging_orders') == ___

        cleanup:
        conn.autoCommit = true
    }

    // ── A KOAN THAT IS DELIBERATELY NOT HERE ─────────────────────────────
    // A fourth koan was drafted: "what does ANOTHER connection see while your transaction is
    // still open?" It does not work, and the reason is worth recording so nobody re-adds it.
    // KoanBase hands out ONE connection to a single-file DuckDB, and counting() wraps that
    // same connection — so "another connection" is the same connection and sees the same
    // uncommitted rows. Making it real would mean a second database and a lot of scaffolding
    // for one koan.
    //
    // It also belongs somewhere else: isolation levels, locking and concurrency are Data Ops,
    // Series 2, and Learn SQL's excludes already hands them over. COMMIT and ROLLBACK basics
    // are the part that lives here, and koan 3 covers them.
}
