package datazeus.learnsql.series1._00

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

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

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/00-write-your-first-query/scripts/${name}.sql").text
    }
}
