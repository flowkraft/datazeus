package datazeus.learnsql.series1._05

import datazeus.support.NorthwindGateSpec
import spock.lang.Unroll

/**
 * VERIFIED spec = the PUBLISH GATE for Series 1 · lesson _05 "Meet Your Data With SELECT".
 * These are the real answers the blog + video show. The lesson runs FIVE queries and every
 * one of them is asserted here, on both engines:
 *
 *   1. categories-all          — SELECT * on a small table: the whole thing, 8 rows x 3 cols
 *   2. employees-all           — SELECT * on a WIDE table: 3 rows x 18 cols (why you name columns)
 *   3. employees-named-columns — the select list: 3 columns, in the order YOU asked for
 *   4. products-name-and-price — LIMIT trims a 20-row table to 5; the price shows as 18.0000
 *   5. products-columns-and-types — the catalog explains that 18.0000: DECIMAL(10,4)
 *
 * The learner-facing version, with the queries blanked to ___, is MeetYourDataWithSelectKoans.
 *
 * Convention: the spec runs the SAME *.sql files the lesson/video show, so the SQL is
 * authored in exactly one place (the lesson's scripts/) and verified here — no drift.
 */
class MeetYourDataWithSelectSpec extends NorthwindGateSpec {

    // --- 1. SELECT * on a small table: you can see all of it -----------------------------

    @Unroll
    def "[#engine] SELECT * on Categories returns the whole table — eight rows, three columns"() {
        given:
        def rows = sqlFor(engine).rows(script("categories-all"))

        expect:
        rows.size() == 8
        rows.first().size() == 3

        and: "the first row is Beverages, and the last is Seafood"
        rows.first().CategoryName == "Beverages"
        rows.last().CategoryName == "Seafood"

        where:
        engine << ENGINES
    }

    // --- 2. SELECT * on a WIDE table: the motivation for naming columns ------------------

    @Unroll
    def "[#engine] SELECT * on Employees returns only three people — but eighteen columns each"() {
        given:
        def rows = sqlFor(engine).rows(script("employees-all"))

        expect: "three employees is the whole staff"
        rows.size() == 3

        and: "eighteen columns is more than anyone can read at a glance"
        rows.first().size() == 18

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

        and: "even though the TABLE stores LastName before FirstName"
        columnPosition(engine, "Employees", "LastName") < columnPosition(engine, "Employees", "FirstName")

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
    def "[#engine] the first product is Chai, and its price prints as 18.0000"() {
        given:
        def first = sqlFor(engine).rows(script("products-name-and-price")).first()

        expect:
        first.ProductName == "Chai"

        and: "four decimal places — the type talking, not the data"
        first.UnitPrice.toString() == "18.0000"

        where:
        engine << ENGINES
    }

    // --- 4b. ROUND changes the DISPLAY, not what is stored --------------------------------

    @Unroll
    def "[#engine] ROUND(UnitPrice, 2) shows Chai at 18.00 — two decimals, same price"() {
        given:
        def rows = sqlFor(engine).rows(script("products-rounded-price"))

        expect:
        rows.size() == 5
        rows.first().ProductName == "Chai"
        rows.first().values().last().toString() == "18.00"

        and: "the stored column is untouched — still four decimals"
        sqlFor(engine).firstRow('SELECT "UnitPrice" FROM "Products" LIMIT 1')
                      .UnitPrice.toString() == "18.0000"

        where:
        engine << ENGINES
    }

    // Without an alias each engine invents its own name for the computed column. The lesson
    // shows this on purpose — it is what motivates aliases, two lessons later.
    def "[duckdb] names the computed column round(UnitPrice, 2)"() {
        expect:
        sqlFor("duckdb").rows(script("products-rounded-price"))
                        .first().keySet().last() == "round(UnitPrice, 2)"
    }

    def "[postgres] names the very same computed column just round"() {
        expect:
        sqlFor("postgres").rows(script("products-rounded-price"))
                          .first().keySet().last() == "round"
    }

    // --- 5. The catalog explains the trailing zeros --------------------------------------

    @Unroll
    def "[#engine] the database describes itself — Products has ten columns"() {
        expect:
        sqlFor(engine).rows(script("products-columns-and-types")).size() == 10

        where:
        engine << ENGINES
    }

    def "[duckdb] and the catalog names UnitPrice DECIMAL(10,4) — there are the four decimals"() {
        expect:
        typeOf("duckdb", "Products", "UnitPrice") == "DECIMAL(10,4)"
    }

    // --- Type NAMES are not portable; the concepts are -----------------------------------
    // The lesson publishes this three-row comparison, so all six cells are asserted.

    def "[duckdb] type names: DECIMAL(10,4) / VARCHAR / SMALLINT"() {
        expect:
        typeOf("duckdb", "Products", "UnitPrice") == "DECIMAL(10,4)"
        typeOf("duckdb", "Products", "ProductName") == "VARCHAR"
        typeOf("duckdb", "Products", "UnitsInStock") == "SMALLINT"
    }

    def "[postgres] the same three columns: numeric / text / integer"() {
        expect:
        typeOf("postgres", "Products", "UnitPrice") == "numeric"
        typeOf("postgres", "Products", "ProductName") == "text"
        typeOf("postgres", "Products", "UnitsInStock") == "integer"
    }

    // --- helpers ---------------------------------------------------------------
    // Paths are relative to the tests/ module dir (where `mvn` runs).

    private String typeOf(String engine, String table, String column) {
        catalogRow(engine, table, column).data_type
    }

    private int columnPosition(String engine, String table, String column) {
        catalogRow(engine, table, column).ordinal_position as int
    }

    private catalogRow(String engine, String table, String column) {
        sqlFor(engine).firstRow("SELECT data_type, ordinal_position FROM information_schema.columns" +
                                " WHERE table_name = '" + table + "'" +
                                " AND column_name = '" + column + "'")
    }

    private static String script(String name) {
        new File("../courses/learnsql/series1-fundamentals/05-meet-your-data-with-select/scripts/${name}.sql").text
    }
}
