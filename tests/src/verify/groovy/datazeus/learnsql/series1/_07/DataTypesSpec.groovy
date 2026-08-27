package datazeus.learnsql.series1._07

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _07 "Data Types".
 * These are the real answers the blog + video show. The lesson runs SIX queries and every
 * one of them is asserted here, on both engines:
 *
 *   1. products-types   — the catalog: ten columns, and the families they fall into
 *   2. orders-dates     — OrderDate is a real date/time, not text
 *   3. price-times-two  — arithmetic works on a number: Chai 18 doubles to 36
 *   4. name-times-two   — and is REFUSED on text (this one is asserted to FAIL)
 *   5. postal-codes     — eight German customers; Leipzig is 04179, leading zero intact
 *   6. text-vs-number   — the trap: '9' > '10' is TRUE, 9 > 10 is FALSE
 *
 * WHY TYPE NAMES ARE NOT ASSERTED LITERALLY. The two engines spell the same family
 * differently — DuckDB says VARCHAR / DECIMAL(19,4) / BOOLEAN, PostgreSQL says
 * "character varying" / numeric / boolean. Asserting one spelling would fail the other
 * and teach the wrong thing besides: the lesson is explicit that families are what
 * matter and spellings vary. So these assert the FAMILY, via the matchers at the foot
 * of this file. The one place a literal spelling IS pinned is koan 2, which runs on
 * DuckDB only — gated separately below.
 *
 * The learner-facing version, with the queries blanked to ___, is DataTypesKoans.
 * Its own data dependencies are gated at the foot of this file: they appear in no
 * script/, so nothing else here would catch a drift and the failure would surface to a
 * student instead of to us.
 *
 * Convention: the spec runs the SAME *.sql files the lesson/video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 */
class DataTypesSpec extends NorthwindGateSpec {

    // --- 1. The catalog: ask the database what it is holding -----------------------------

    @Unroll
    def "[#engine] the catalog reports ten columns for Products"() {
        given:
        def rows = sqlFor(engine).rows(script("products-types"))

        expect: "one row per column of Products"
        rows.size() == 10

        and: "every row names a column and gives its type"
        rows.every { it.column_name && it.data_type }

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] Products covers four of the five families the lesson teaches"() {
        given:
        def byName = sqlFor(engine).rows(script("products-types"))
                .collectEntries { [(it.column_name as String): (it.data_type as String)] }

        expect: "whole numbers — ids and counts"
        isWholeNumber(byName.ProductID)
        isWholeNumber(byName.UnitsInStock)

        and: "a decimal — money"
        isDecimal(byName.UnitPrice)

        and: "text — anything written"
        isText(byName.ProductName)
        isText(byName.QuantityPerUnit)

        and: "true/false — a flag"
        isBoolean(byName.Discontinued)

        where:
        engine << ENGINES
    }

    // --- 2. Dates are their own family, not text -----------------------------------------

    @Unroll
    def "[#engine] OrderDate is a real point in time, and the first three orders are March, April, May 2024"() {
        given:
        def rows = sqlFor(engine).rows(script("orders-dates"))

        expect:
        rows.size() == 3
        rows*.OrderID == [1, 2, 3]

        and: "a date/time value — NOT a String, which is the whole point of the section"
        rows.every { !(it.OrderDate instanceof String) }

        and: "the dates the lesson prints"
        rows*.OrderDate*.toString()*.take(10) == ["2024-03-15", "2024-04-05", "2024-05-20"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the catalog agrees that OrderDate is a date/time"() {
        expect:
        isDateTime(typeOf(engine, "Orders", "OrderDate"))

        where:
        engine << ENGINES
    }

    // --- 3. Arithmetic works on a number -------------------------------------------------

    @Unroll
    def "[#engine] doubling a price: Chai is 18, and twice Chai is 36"() {
        given:
        def rows = sqlFor(engine).rows(script("price-times-two"))

        expect:
        rows.size() == 3
        rows.first().ProductName == "Chai"

        and: "the VALUES are 18 and 36 — how many decimal places get PRINTED is the client's business"
        // Asserted as numbers, deliberately, for the same reason lesson 05 does it: the column
        // is numeric(19,4), the driver hands back "18.0000", and CloudBeaver trims to 18.
        (rows.first().UnitPrice as BigDecimal).compareTo(18 as BigDecimal) == 0
        (rows.first().values().last() as BigDecimal).compareTo(36 as BigDecimal) == 0

        and: "the other two the lesson prints"
        rows*.ProductName == ["Chai", "Chang", "Aniseed Syrup"]
        rows.collect { (it.values().last() as BigDecimal).intValue() } == [36, 38, 20]

        where:
        engine << ENGINES
    }

    // --- 4. ...and is REFUSED on text ----------------------------------------------------

    @Unroll
    def "[#engine] multiplying a product NAME is refused — the type system doing its job"() {
        when: "you ask for arithmetic on text"
        sqlFor(engine).rows(script("name-times-two"))

        then: "the database stops you rather than inventing an answer"
        // The MESSAGE differs per engine ("operator does not exist" on PostgreSQL, "Binder
        // Error" on DuckDB), so only the refusal is asserted. The lesson prints the
        // PostgreSQL wording, because CloudBeaver is what it tells learners to open.
        thrown(Exception)

        where:
        engine << ENGINES
    }

    // --- 5. Text that looks like a number ------------------------------------------------

    @Unroll
    def "[#engine] eight postal codes, and Leipzig keeps its leading zero"() {
        given:
        def rows = sqlFor(engine).rows(script("postal-codes"))

        expect:
        rows.size() == 8

        and: "the seventh row is the one the lesson is built on"
        rows[6].CompanyName == "Morgenstern Gesundkost"
        rows[6].City == "Leipzig"
        rows[6].PostalCode == "04179"

        and: "it is TEXT — as a number the leading zero could not survive"
        rows[6].PostalCode instanceof String

        and: "all eight, in the order the lesson prints them"
        rows*.PostalCode == ["12209", "68306", "52066", "80805",
                             "14776", "60528", "04179", "50739"]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] the catalog agrees that PostalCode is text"() {
        expect:
        isText(typeOf(engine, "Customers", "PostalCode"))

        where:
        engine << ENGINES
    }

    // --- 6. THE TRAP — the wrong answer that looks right ---------------------------------

    @Unroll
    def "[#engine] as TEXT nine beats ten; as NUMBERS it does not"() {
        given:
        def row = sqlFor(engine).rows(script("text-vs-number")).first().values() as List

        expect: "'9' > '10' — true, because text compares like a dictionary"
        row[0] == true

        and: "9 > 10 — false, because numbers compare by value"
        row[1] == false

        where:
        engine << ENGINES
    }

    // --- What the KOANS stand on ---------------------------------------------------------
    // The koans query things this lesson's scripts/ never touch, so nothing above would
    // notice if that data shifted — and the break would land on a student mid-exercise,
    // with a green gate behind it.

    @Unroll
    def "[#engine] koan 1 and 7: Products has 10 columns and Orders has 14"() {
        given:
        def sql = sqlFor(engine)

        expect:
        sql.firstRow("""SELECT count(*) AS n FROM information_schema.columns
                        WHERE table_name = 'Products'""").n == 10
        sql.firstRow("""SELECT count(*) AS n FROM information_schema.columns
                        WHERE table_name = 'Orders'""").n == 14

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] koan 6: the first three products are all still sold"() {
        expect:
        sqlFor(engine).rows('SELECT "Discontinued" FROM "Products" LIMIT 3')
                *.Discontinued == [false, false, false]

        where:
        engine << ENGINES
    }

    def "koan 2 expects DuckDB's own spelling, because the koans run on DuckDB"() {
        // NOT @Unroll'd across ENGINES on purpose: this is the one assertion in the file that
        // pins a literal type name, and it is correct for exactly one engine. PostgreSQL would
        // answer "character varying" and the koan would be wrong to expect it.
        expect:
        typeOf("duckdb", "Products", "ProductName") == "VARCHAR"
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/07-data-types/scripts/${name}.sql").text
    }

    private String typeOf(String engine, String table, String column) {
        sqlFor(engine).firstRow("""SELECT "data_type" AS t FROM information_schema.columns
                                   WHERE table_name = ? AND "column_name" = ?""",
                [table, column]).t as String
    }

    // The five families, spelled the way each engine spells them. Kept deliberately loose:
    // a new engine should widen these, never force a lesson rewrite.
    private static boolean isWholeNumber(String t) { t?.toLowerCase() in ["integer", "int", "int4", "smallint", "int2", "bigint", "int8"] }
    private static boolean isDecimal(String t) { t?.toLowerCase()?.startsWith("decimal") || t?.toLowerCase() in ["numeric", "real", "double precision"] }
    private static boolean isText(String t) { t?.toLowerCase() in ["varchar", "text", "character varying", "char", "character"] }
    private static boolean isBoolean(String t) { t?.toLowerCase() in ["boolean", "bool"] }
    private static boolean isDateTime(String t) { t?.toLowerCase()?.startsWith("timestamp") || t?.toLowerCase() in ["date", "datetime"] }
}
