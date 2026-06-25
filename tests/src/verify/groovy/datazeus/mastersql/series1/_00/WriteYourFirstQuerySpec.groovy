package datazeus.mastersql.series1._00

import groovy.sql.Sql
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _00 "Write Your First Query".
 * These are the real answers the blog + video show (the hero number is june_orders = 4).
 * Runs against the canonical datasets/northwind/northwind.duckdb — the SAME database the
 * learner queries in the CLI / CloudBeaver and the koans run on. (Opened as a throwaway
 * copy so the gate can't lock or mutate it.)
 * The learner-facing version, with the queries blanked to ___, is WriteYourFirstQueryKoans.
 *
 * Convention: the spec runs the SAME *.sql files the lesson/video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 */
class WriteYourFirstQuerySpec extends Specification {

    @Shared
    Sql db

    @Shared
    File dbCopy

    def setupSpec() {
        File src = new File("../datasets/northwind/northwind.duckdb")
        dbCopy = File.createTempFile("gate-northwind-", ".duckdb")
        Files.copy(src.toPath(), dbCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        db = Sql.newInstance("jdbc:duckdb:" + dbCopy.absolutePath, "org.duckdb.DuckDBDriver")
    }

    def cleanupSpec() {
        db?.close()
        dbCopy?.delete()
    }

    def "the hero query: June shipped exactly four orders"() {
        expect:
        db.firstRow(script("june-orders")).june_orders == 4
    }

    def "SELECT CompanyName, Country FROM Customers LIMIT 5 returns five rows"() {
        expect:
        db.rows(script("customers")).size() == 5
    }

    def "Northwind holds seventy-nine orders in total"() {
        expect:
        db.firstRow('SELECT count(*) AS n FROM "Orders"').n == 79
    }

    def "Northwind holds twenty-five customers"() {
        expect:
        db.firstRow('SELECT count(*) AS n FROM "Customers"').n == 25
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/mastersql/series1-fundamentals/00-write-your-first-query/scripts/${name}.sql").text
    }
}
