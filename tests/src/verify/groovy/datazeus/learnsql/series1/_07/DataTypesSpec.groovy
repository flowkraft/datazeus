package datazeus.learnsql.series1._07

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _07 "Data Types".
 *
 * Every figure the video, the article and the koans put in front of a learner is asserted
 * here, on BOTH engines. The lesson's eight scripts:
 *
 *   1. products-types        — the catalog: ten columns, and the families they fall into
 *   2. unit-price            — Chai's price, and the scale its type promises.
 *                              NOT SHOWN IN THE ARTICLE ANY MORE, and not unused either:
 *                              the "why 18 comes back as 18.0000" section was cut on
 *                              2026-08-28 because a reader following the lesson in
 *                              CloudBeaver sees 18 and was being talked out of something
 *                              they had never seen. KOAN 4 is where a learner actually
 *                              meets 18.0000 (the koans run on DuckDB, which prints the
 *                              zeros), so that is where it is taught now — and this script
 *                              is what still gates the claim on both engines.
 *   3. orders-dates          — OrderDate is a real point in time, not text
 *   4. price-times-two       — arithmetic works on a number: 18 doubles to 36
 *   5. name-times-two        — and is REFUSED on text (asserted to FAIL)
 *   6. postal-codes          — eight German customers; Leipzig is 04179, leading zero intact
 *   7. text-vs-number        — the trap: '9' > '10' is TRUE, 9 > 10 is FALSE
 *   8. products-discontinued — a boolean column, and what the first four products say
 *
 * WHY TYPE NAMES ARE NOT ASSERTED LITERALLY ACROSS ENGINES. DuckDB says VARCHAR /
 * DECIMAL(19,4) / TIMESTAMP / BOOLEAN where PostgreSQL says character varying / numeric /
 * timestamp without time zone / boolean. Pinning one spelling would fail the other and
 * teach the wrong thing besides — the lesson is explicit that families are what matter.
 * So these assert the FAMILY, via the matchers at the foot of this file.
 *
 * The DuckDB spellings ARE pinned, separately, at the end: the koans run on DuckDB only and
 * expect those exact strings, so if DuckDB ever renames one the koans break for students
 * and nothing else here would notice.
 *
 * Convention: the spec runs the SAME *.sql files the lesson and the video show, so the SQL
 * is authored in exactly one place (the lesson's scripts/) and verified here — no drift.
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

        and: "the ten the lesson prints, whatever order they arrive in"
        rows*.column_name.toSet() == ["ProductID", "ProductName", "SupplierID", "CategoryID",
                                      "QuantityPerUnit", "UnitPrice", "UnitsInStock",
                                      "UnitsOnOrder", "ReorderLevel", "Discontinued"].toSet()

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

    // --- 2. The price, and the scale its type promises -----------------------------------

    @Unroll
    def "[#engine] Chai costs 18, and the column promises four decimal places"() {
        given:
        def row = sqlFor(engine).rows(script("unit-price")).first()

        expect:
        row.ProductName == "Chai"

        and: "the VALUE is 18 — how many decimals get PRINTED is the client's business"
        // The article says this explicitly: CloudBeaver trims the zeros and shows 18, while
        // the DuckDB CLI and psql print 18.0000. Asserting the rendered string would gate a
        // detail that changes with the tool; asserting the SCALE gates the claim itself.
        (row.UnitPrice as BigDecimal).compareTo(18 as BigDecimal) == 0

        and: "and the type carries a scale of 4, which is WHY 18.0000 is what it really is"
        (row.UnitPrice as BigDecimal).scale() == 4

        where:
        engine << ENGINES
    }

    // --- 3. Dates are their own family, not text -----------------------------------------

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

    // --- 4. Arithmetic works on a number -------------------------------------------------

    @Unroll
    def "[#engine] doubling a price: Chai is 18, and twice Chai is 36"() {
        given:
        def rows = sqlFor(engine).rows(script("price-times-two"))

        expect:
        rows.size() == 3
        rows*.ProductName == ["Chai", "Chang", "Aniseed Syrup"]

        and: "the doubled column is the LAST one, whatever the engine auto-names it"
        rows.collect { (it.values().last() as BigDecimal).intValue() } == [36, 38, 20]

        and: "and it really is the price doubled, row by row"
        rows.every { (it.values().last() as BigDecimal) == (it.UnitPrice as BigDecimal) * 2 }

        where:
        engine << ENGINES
    }

    // --- 5. ...and is REFUSED on text ----------------------------------------------------

    @Unroll
    def "[#engine] multiplying a product NAME is refused — the type system doing its job"() {
        when: "you ask for arithmetic on text"
        sqlFor(engine).rows(script("name-times-two"))

        then: "the database stops you rather than inventing an answer"
        // The MESSAGE differs per engine ("operator does not exist" on PostgreSQL, "Binder
        // Error" on DuckDB), so only the refusal is asserted. The article prints the
        // PostgreSQL wording, because CloudBeaver is what it tells learners to open.
        thrown(Exception)

        where:
        engine << ENGINES
    }

    // --- 6. Text that looks like a number ------------------------------------------------

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

    // --- 7. THE TRAP — the wrong answer that looks right ---------------------------------

    @Unroll
    def "[#engine] as TEXT nine beats ten; as NUMBERS it does not"() {
        given:
        // READ POSITIONALLY, not by column name. Neither comparison is aliased — the lesson
        // shows them exactly as a learner types them — so BOTH columns come back named
        // "?column?" on PostgreSQL, and Groovy's map-backed row keeps only the last of two
        // identically-named columns. The first version of this test asserted `row[0] == true`
        // and got false, because row[0] WAS the second comparison. A raw ResultSet has
        // positions, not names, so it cannot collapse.
        def vals = []
        sqlFor(engine).query(script("text-vs-number")) { rs ->
            rs.next()
            vals = [rs.getBoolean(1), rs.getBoolean(2)]
        }

        expect: "'9' > '10' — true, because text compares like a dictionary"
        vals[0] == true

        and: "9 > 10 — false, because numbers compare by value"
        vals[1] == false

        where:
        engine << ENGINES
    }

    // --- 8. A boolean column, and the counts around it -----------------------------------

    @Unroll
    def "[#engine] the first four products are all still sold"() {
        given:
        def rows = sqlFor(engine).rows(script("products-discontinued"))

        expect:
        rows.size() == 4
        rows*.ProductName == ["Chai", "Chang", "Aniseed Syrup", "Chef Antons Cajun Seasoning"]
        rows*.UnitsInStock == [39, 17, 13, 53]
        rows*.Discontinued == [false, false, false, false]

        where:
        engine << ENGINES
    }

    @Unroll
    def "[#engine] exactly two of the twenty products are discontinued — the caption says so"() {
        given:
        def sql = sqlFor(engine)

        expect:
        sql.firstRow('SELECT count(*) AS n FROM "Products"').n == 20
        sql.firstRow('SELECT count(*) AS n FROM "Products" WHERE "Discontinued" = true').n == 2

        where:
        engine << ENGINES
    }

    // --- What the KOANS stand on ---------------------------------------------------------
    // (koan 8's eight postal codes are already pinned by the postal-codes test above, and
    //  koans 2/3/5's type spellings by the DuckDB block at the foot of this section.)
    // The koans ask things the scripts above never touch, so nothing here would notice if
    // that data shifted — and the break would land on a student mid-exercise, with a green
    // gate behind it. One assertion per koan that has its own dependency.

    @Unroll
    def "[#engine] koans 1 and 10: Products has 10 columns and Orders has 14"() {
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

    @Unroll
    def "[#engine] koan 7: twice Chai's price is 36"() {
        expect:
        (sqlFor(engine).firstRow('SELECT "UnitPrice" * 2 AS n FROM "Products" LIMIT 1').n as BigDecimal)
                .compareTo(36 as BigDecimal) == 0

        where:
        engine << ENGINES
    }

    def "the koans pin DuckDB's own spelling, because that is the engine they run on"() {
        // NOT @Unroll'd across ENGINES on purpose: koans 2, 3 and 5 expect these exact
        // strings, and they are correct for exactly one engine. PostgreSQL would answer
        // "character varying", "numeric" and "timestamp without time zone" — all right, all
        // wrong for a koan. If DuckDB ever renames one of these, students see a red koan and
        // nothing else in this file would have caught it.
        expect:
        typeOf("duckdb", "Products", "ProductName") == "VARCHAR"
        typeOf("duckdb", "Products", "UnitPrice") == "DECIMAL(19,4)"
        typeOf("duckdb", "Orders", "OrderDate") == "TIMESTAMP"
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
