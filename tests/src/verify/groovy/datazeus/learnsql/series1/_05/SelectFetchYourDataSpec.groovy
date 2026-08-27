package datazeus.learnsql.series1._05

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _05 "Meet Your Data With SELECT".
 * These are the real answers the blog + video show. The lesson runs FOUR queries and every
 * one of them is asserted here, on both engines:
 *
 *   1. categories-all          — SELECT * on a small table: the whole thing, 8 rows x 4 cols
 *   2. employees-all           — SELECT * on a WIDE table: 3 rows x 20 cols (why you name columns)
 *   3. employees-named-columns — the select list: 3 columns, in the order YOU asked for
 *   4. products-name-and-price — LIMIT trims a 20-row table to 5; Chai is the first, at 18
 *
 * The learner-facing version, with the queries blanked to ___, is SelectFetchYourDataKoans.
 * Its own data dependencies — Shippers, the eight category names, Chai/Chang — are gated at
 * the foot of this file: they appear in no script/, so nothing else here would catch a drift
 * and the failure would surface to a student instead of to us.
 *
 * Convention: the spec runs the SAME *.sql files the lesson/video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 */
class SelectFetchYourDataSpec extends NorthwindGateSpec {

    // --- 1. SELECT * on a small table: you can see all of it -----------------------------

    @Unroll
    def "[#engine] SELECT * on Categories returns the whole table — eight rows, four columns"() {
        given:
        def rows = sqlFor(engine).rows(script("categories-all"))

        expect:
        rows.size() == 8

        and: "FOUR columns — Picture is real, and empty for all eight rows"
        // Four, not three. Picture is in the Category entity and so exists on every engine, but
        // was missing from the hand-written DuckDB DDL until 2026-08-26 — and because PostgreSQL
        // is seeded FROM the DuckDB file, both engines agreed on the wrong answer and this spec
        // passed. DuckDBSchemaVerifier now compares the generated file against the entities on
        // every run, which is what would have caught it.
        rows.first().size() == 4
        rows.every { it.Picture == null }

        and: "the first row is Beverages, and the last is Seafood"
        rows.first().CategoryName == "Beverages"
        rows.last().CategoryName == "Seafood"

        where:
        engine << ENGINES
    }

    // --- 2. SELECT * on a WIDE table: the motivation for naming columns ------------------

    @Unroll
    def "[#engine] SELECT * on Employees returns only three people — but twenty columns each"() {
        given:
        def rows = sqlFor(engine).rows(script("employees-all"))

        expect: "three employees is the whole staff"
        rows.size() == 3

        and: "twenty columns is more than anyone can read at a glance"
        // TWENTY, not eighteen. Mobile and Email were added to the JPA entities and the DuckDB
        // sample only caught up on 2026-08-26, so this asserted 18 while PostgreSQL returned 20
        // and nothing failed — both engines were seeded from the same stale DuckDB. The creator
        // now verifies itself against the entities on every run; see DuckDBSchemaVerifier.
        rows.first().size() == 20

        where:
        engine << ENGINES
    }

    // --- 3. The select list: which columns, and in what order ----------------------------

    @Unroll
    def "[#engine] naming three columns returns exactly those three"() {
        given:
        def rows = sqlFor(engine).rows(script("employees-named-columns"))

        expect:
        rows.size() == 3
        rows.first().size() == 3

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the select list sets the column ORDER — the lesson's whole point"() {
        given:
        def rows = sqlFor(engine).rows(script("employees-named-columns"))

        expect: "the query asked for FirstName first, so FirstName comes first"
        rows.first().keySet()*.toString()*.toLowerCase() == ["firstname", "lastname", "title"]

        // The physical-order assertion that used to live here is GONE, with the lesson claim
        // it backed: "Employees stores LastName before FirstName" is true of the DuckDB
        // dataset and FALSE on PostgreSQL (FirstName is column 6, LastName is 11). JPA does
        // not guarantee column order, so no schema fix can settle it. What survives is the
        // part that is true everywhere: you asked for this order, so you got it.

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the three employees are Nancy Davolio, Andrew Fuller and Janet Leverling"() {
        given:
        def rows = sqlFor(engine).rows(script("employees-named-columns"))

        expect:
        rows*.FirstName == ["Nancy", "Andrew", "Janet"]
        rows*.LastName == ["Davolio", "Fuller", "Leverling"]
        rows[1].Title == "Vice President, Sales"

        where:
        engine << ENGINES
    }

    // --- 4. LIMIT trims the ROWS (the other dial) ----------------------------------------

    @Unroll
    def "[#engine] Products holds twenty rows, and LIMIT 5 hands back five"() {
        given:
        def sql = sqlFor(engine)

        expect: "the table itself is twenty products"
        sql.firstRow('SELECT count(*) AS n FROM "Products"').n == 20

        and: "but the query asked for five"
        sql.rows(script("products-name-and-price")).size() == 5

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the first product is Chai, at a unit price of 18"() {
        given:
        def first = sqlFor(engine).rows(script("products-name-and-price")).first()

        expect:
        first.ProductName == "Chai"

        and: "the VALUE is 18 — how many decimal places get PRINTED is the client's business"
        // Asserted as a number, deliberately. The column is numeric(19,4) and the JDBC driver
        // hands back "18.0000", but CloudBeaver — the client this course tells learners to use —
        // trims the trailing zeros and shows 18, which is what the video and the article now
        // print. Asserting the rendered string would gate a detail the learner never sees and
        // would contradict the lesson on any client that formats differently.
        (first.UnitPrice as BigDecimal).compareTo(18 as BigDecimal) == 0

        where:
        engine << ENGINES
    }

    // --- What the KOANS stand on ---------------------------------------------------------
    // The koans query things this lesson's scripts/ never touch, so nothing above would
    // notice if that data shifted — and the break would land on a student mid-exercise,
    // with a green gate behind it. These three are the koans' own dependencies.

    @Unroll
    def "[#engine] koan 1: Shippers is three rows of three columns"() {
        given:
        def rows = sqlFor(engine).rows('SELECT * FROM "Shippers"')

        expect:
        rows.size() == 3
        rows.first().size() == 3

        and: "the koan's expected companies, in order"
        rows*.CompanyName == ["Speedy Express", "United Package", "Federal Shipping"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 2: all eight category names, in the order the koan expects"() {
        expect:
        sqlFor(engine).rows('SELECT "CategoryName" FROM "Categories"')*.CategoryName ==
                ["Beverages", "Condiments", "Confections", "Dairy Products",
                 "Grains/Cereals", "Meat/Poultry", "Produce", "Seafood"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 5: the first two products are Chai and Chang"() {
        expect:
        sqlFor(engine).rows('SELECT "ProductName" FROM "Products" LIMIT 2')*.ProductName ==
                ["Chai", "Chang"]

        where:
        engine << ENGINES
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/05-select-fetch-your-data/scripts/${name}.sql").text
    }
}
