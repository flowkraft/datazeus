package datazeus._internal

import groovy.sql.Sql
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Shared plumbing for every SQL koan — so a lesson file shows ONLY the questions
 * and the blanks, never the wiring.
 *
 * A koan lesson extends this, adds @Stepwise, and writes `expect:` blocks. The
 * blank (`___`) goes INSIDE the SQL — you write the query, the koan checks the
 * RESULT. (You learn SQL by writing queries, not by typing in a number.)
 *
 *     import datazeus._internal.KoanBase
 *     import spock.lang.Stepwise
 *
 *     @Stepwise   // koans run in order; once one fails, the rest wait
 *     class MyKoans extends KoanBase {
 *
 *         // fragment koan: the lesson is the one blanked token
 *         def "keep only June 2024 orders"() {
 *             expect:
 *             shouldReturn 4, '''
 *                 SELECT count(*) FROM "Orders"
 *                 WHERE "OrderDate" >= DATE '2024-06-01'
 *                   AND "OrderDate" ___  DATE '2024-07-01'
 *             '''
 *         }
 *
 *         // whole-query capstone: write all of it; a row-set is fake-resistant
 *         def "first five customers (company, country)"() {
 *             expect:
 *             shouldReturn([ ["Alfreds Futterkiste", "Germany"], ...four more rows... ], '''
 *                 ___
 *             ''')
 *         }
 *     }
 *
 * What this base provides:
 *   - `shouldReturn(expected, sql)` — run the learner's SQL and compare its result
 *     to `expected` (a scalar, or a List of rows). RED with a goal-aware hint until
 *     the query is written and correct; never a stacktrace.
 *   - `db`   an embedded DuckDB, seeded once from the Northwind dataset, for the
 *     occasional koan that needs raw access (e.g. predict-the-value on visible data).
 *   - `___`  the bare blank, for that predict-the-value style (`actual == ___`).
 *
 * NOTE: @Stepwise must go on the lesson class itself — Spock does not inherit it
 * from a base class. It is what makes the path sequential (walk one koan at a time).
 *
 * A lesson that needs a different dataset just overrides `dataset()`.
 */
abstract class KoanBase extends Specification {

    /** The bare blank, for predict-the-value koans (`actual == ___`). */
    protected static final Object ___ = new Object()

    @Shared
    protected Sql db

    @Shared
    private File dbCopy

    /** The canonical Northwind database (relative to the tests/ module dir). Override per lesson if needed. */
    protected String dataset() { "../datasets/northwind/northwind.duckdb" }

    def setupSpec() {
        // Open a throwaway COPY of the shipped database, so a koan run can never lock
        // or mutate the same northwind.duckdb the learner is exploring in the CLI /
        // CloudBeaver. Same data, every engine — by construction.
        File src = new File(dataset())
        dbCopy = File.createTempFile("koan-northwind-", ".duckdb")
        Files.copy(src.toPath(), dbCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        db = Sql.newInstance("jdbc:duckdb:" + dbCopy.absolutePath, "org.duckdb.DuckDBDriver")
    }

    def cleanupSpec() {
        db?.close()
        dbCopy?.delete()
    }

    /**
     * The heart of a query koan: run the SQL the learner wrote and check its result.
     *  - `expected` a List  -> compare the full row set (each row as a List of cells)
     *  - otherwise          -> compare the single value in the first cell
     * Returns true on a match (so `expect: shouldReturn(...)` is a green condition);
     * otherwise throws a KoanHint whose message is the learner-facing "compare" line.
     */
    protected boolean shouldReturn(Object expected, String sql) {
        if (sql == null || sql.trim().isEmpty() || sql.contains("___")) {
            throw new KoanHint("you haven't written the query yet - replace the ___ with SQL.\n" +
                    "it should return ${show(expected)}")
        }
        Object actual
        try {
            actual = (expected instanceof List) ? rows(sql) : firstCell(sql)
        } catch (Exception e) {
            throw new KoanHint("your query didn't run: ${firstLine(e.message)}\n" +
                    "(it should return ${show(expected)})")
        }
        if (actual != expected) {
            throw new KoanHint("your query returned ${show(actual)},\n" +
                    "but it should return ${show(expected)}")
        }
        return true
    }

    /** The single value in the first cell of the first row (null if no rows). */
    protected Object firstCell(String sql) {
        def row = db.firstRow(sql)
        return row == null ? null : row[0]
    }

    /** Every row as a plain List of cell values, in column order. */
    protected List rows(String sql) {
        return db.rows(sql).collect { r -> r.values().toList() }
    }

    /** Compact, learner-friendly rendering of an expected/actual result for the hint. */
    private static String show(Object v) {
        if (v instanceof List) {
            if (v.isEmpty()) return "no rows"
            return "${v.size()} row${v.size() == 1 ? '' : 's'} (first: ${v[0]})"
        }
        return String.valueOf(v)
    }

    private static String firstLine(String msg) {
        if (msg == null) return "syntax error"
        String first = msg.readLines().find { it?.trim() } ?: msg
        return first.length() > 90 ? first.substring(0, 90) + " ..." : first
    }
}
