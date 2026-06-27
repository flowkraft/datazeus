package datazeus.learnsql.series1._00

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

import java.sql.SQLException

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _00 "Write Your First Query".
 * These are the real answers the blog + video show (the hero number is june_orders = 4).
 *
 * Each feature runs on BOTH engines (see NorthwindGateSpec): the bundled DuckDB and a real
 * Postgres — so we prove the learner gets the SAME result whether they use the DuckDB CLI or
 * the Northwind PostgreSQL in CloudBeaver, not just that the data matches.
 *
 * The learner-facing version, with the queries blanked to ___, is WriteYourFirstQueryKoans.
 *
 * Convention: the spec runs the SAME *.sql files the lesson/video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 */
class WriteYourFirstQuerySpec extends NorthwindGateSpec {

    @Unroll
    def "[#engine] the hero query: June shipped exactly four orders"() {
        expect:
        sqlFor(engine).firstRow(script("june-orders")).june_orders == 4

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] SELECT CompanyName, Country FROM Customers LIMIT 5 returns five rows"() {
        expect:
        sqlFor(engine).rows(script("customers")).size() == 5

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
        new File("../courses/learnsql/series1-fundamentals/00-write-your-first-query/scripts/${name}.sql").text
    }
}
