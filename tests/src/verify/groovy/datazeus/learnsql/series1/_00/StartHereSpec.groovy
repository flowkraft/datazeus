package datazeus.learnsql.series1._00

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _00, "Start Here: The SQL Thinking
 * Loop". These are the real answers the blog + video show (the hero number is june_orders = 4).
 *
 * GET, NOT SHIP. The hero question is "how many orders did we GET in June", and the query
 * filters "OrderDate". Northwind's "Orders" also carries a "ShippedDate", so "shipped in June"
 * is a DIFFERENT question with a different answer — and this lesson's whole subject is
 * translating a question into the right query. The article and this spec both said "ship"
 * while the video said "get", and the SQL answered the video. Fixed 2026-08-25; if any of the
 * three ever drifts again, they are wrong, not the data.
 *
 * Each feature runs on BOTH engines (see NorthwindGateSpec): the bundled DuckDB and a real
 * Postgres — so we prove the learner gets the SAME result whether they use the DuckDB CLI or
 * the Northwind PostgreSQL in CloudBeaver, not just that the data matches.
 *
 * The learner-facing version, with the queries blanked to ___, is StartHereKoans.
 *
 * Convention: the spec runs the SAME *.sql files the lesson/video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 */
class StartHereSpec extends NorthwindGateSpec {

    @Unroll
    def "[#engine] the hero query: June received exactly four orders"() {
        expect:
        sqlFor(engine).firstRow(script("june-orders")).values().first() == 4

        where:
        engine << ENGINES
    }

    // Asserts the ROWS, not just how many there are. The lesson prints this exact table, so
    // a count-only check let the two drift: the script once had no ORDER BY, which makes the
    // five rows whatever the engine feels like handing back — and the lesson's table and the
    // koan's answer key disagreed for months without failing anything.
    @Unroll
    def "[#engine] the five customers the lesson prints, in the order it prints them"() {
        expect:
        sqlFor(engine).rows(script("customers")).collect { it.values().toList() } == [
                ["Alfreds Futterkiste", "Germany"],
                ["Ana Trujillo Emparedados y helados", "Mexico"],
                ["Antonio Moreno Taquería", "Mexico"],
                ["Around the Horn", "UK"],
                ["Berglunds snabbköp", "Sweden"],
        ]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] Northwind holds seventy-nine orders in total"() {
        expect:
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Orders"').n == 79

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] Northwind holds twenty-five customers"() {
        expect:
        sqlFor(engine).firstRow('SELECT count(*) AS n FROM "Customers"').n == 25

        where:
        engine << ENGINES
    }

    // --- "Why the double quotes?" — prove the case-folding claims the lesson makes -------
    // Unlike the tests above, these assert DIFFERENT results per engine — that's the whole
    // point of the section. (The quoted "Orders" form is already proven to work on BOTH
    // engines by the hero-query tests above.)

    def "[duckdb] ignores identifier case — unquoted table & column names resolve"() {
        expect: "lowercase 'orders' and capitalised 'Orders' both find the \"Orders\" table"
        sqlFor("duckdb").firstRow('SELECT count(*) AS n FROM orders').n == 79
        sqlFor("duckdb").firstRow('SELECT count(*) AS n FROM Orders').n == 79

        and: "an unquoted column resolves too (OrderDate -> the \"OrderDate\" column)"
        sqlFor("duckdb").firstRow('SELECT OrderDate FROM "Orders" LIMIT 1') != null
    }

    // The two assertions above prove the MECHANISMS separately — a folded table name, a
    // folded column name. The lesson makes a stronger, composed claim about the block it
    // prints: "In DuckDB … this works fine". A reader takes that as "and I still get 4",
    // which is the whole point — the ONLY difference between the quoted and unquoted forms
    // is the folding, not the answer. That is what this pins, on the article's query as
    // printed (no alias, so the value is read positionally).
    def "[duckdb] and the whole unquoted query still answers 4 — only the folding differs"() {
        expect:
        sqlFor("duckdb").firstRow('''
            SELECT count(*)
            FROM orders
            WHERE OrderDate >= DATE '2024-06-01'
              AND OrderDate <  DATE '2024-07-01'
        ''')[0] == 4
    }

    def "[postgres] folds unquoted 'orders' to lowercase — the table is \"Orders\", so it fails"() {
        when:
        sqlFor("postgres").firstRow('SELECT count(*) FROM orders')

        then:
        thrown(SQLException)
    }

    def "[postgres] keeping the capital doesn't help — unquoted 'Orders' fails the same way"() {
        when:
        sqlFor("postgres").firstRow('SELECT count(*) FROM Orders')

        then:
        thrown(SQLException)
    }

    def "[postgres] an unquoted column is folded too — OrderDate -> orderdate, not found"() {
        when:
        sqlFor("postgres").firstRow('SELECT OrderDate FROM "Orders" LIMIT 1')

        then:
        thrown(SQLException)
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/00-start-here/scripts/${name}.sql").text
    }
}
