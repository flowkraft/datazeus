package datazeus.learnsql.series1._07

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 07 Data Types                        ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _07  (Windows)   ./zeus.sh koans learnsql series1 _07  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * TEN KOANS, IN THE ORDER THE LESSON BUILDS THEM, easiest first:
 *   1-2  ask the catalog what a table holds, and what one column is
 *   3-4  the price column: what its type SAYS, and what the value LOOKS like
 *   5-6  the other two families you will meet — dates, and true/false
 *   7-8  text that looks numeric, and arithmetic that only numbers allow
 *   9    the comparison that catches everyone
 *   10   the whole query, written from scratch
 *
 * These run on DuckDB. Every one returns the same answer in CloudBeaver against
 * PostgreSQL EXCEPT the two that ask for a type NAME (koans 2, 3 and 5): DuckDB says
 * VARCHAR / DECIMAL(19,4) / TIMESTAMP where PostgreSQL says character varying /
 * numeric / timestamp without time zone. Same families, different words — the lesson
 * says so too. Every koan here expects DuckDB's spelling, because that is what runs.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * Every table and column these koans touch, so you can write a query without leaving
 * this file. When a koan asks for "the column holding the postal code", it is asking
 * you to FIND the name below — not to have memorised it.
 *
 *   information_schema.columns — the CATALOG: one row per column, of every table.
 *     "table_name"   VARCHAR  "column_name"  VARCHAR
 *     "data_type"    VARCHAR
 *
 *   "Products" — 20 rows, 10 columns
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 *
 *   "Customers" — 25 rows, 12 columns
 *     "CustomerID"   VARCHAR  "CompanyName"  VARCHAR
 *     "ContactName"  VARCHAR  "ContactTitle" VARCHAR
 *     "Address"      VARCHAR  "City"         VARCHAR
 *     "Region"       VARCHAR  "PostalCode"   VARCHAR
 *     "Country"      VARCHAR  "Phone"        VARCHAR
 *     "Fax"          VARCHAR  "Email"        VARCHAR
 *
 *   "Orders" — 79 rows, 14 columns
 *     "OrderID"        INTEGER        "CustomerID"     VARCHAR
 *     "EmployeeID"     INTEGER        "OrderDate"      TIMESTAMP
 *     "RequiredDate"   TIMESTAMP      "ShippedDate"    TIMESTAMP
 *     "ShipVia"        INTEGER        "Freight"        DECIMAL(19,4)
 *     "ShipName"       VARCHAR        "ShipAddress"    VARCHAR
 *     "ShipCity"       VARCHAR        "ShipRegion"     VARCHAR
 *     "ShipPostalCode" VARCHAR        "ShipCountry"    VARCHAR
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class DataTypesKoans extends KoanBase {

    // 1) Ask the database what it is holding. The catalog is a table like any other, and
    //    every row in it describes one column of one table. Which table do you want to know
    //    about? It is a text VALUE, so it goes in 'single quotes'.
    //    (Predict first: how many columns does "Products" have?)
    def "the catalog knows how many columns Products has"() {
        expect:
        shouldReturn 10, '''
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = ___
        '''
    }

    // 2) Same catalog, a sharper question: not how many, but what KIND. Fill in the name of
    //    the column that holds a product's name — and mind the quotes: it is a text value
    //    being matched here, not a column being named.
    def "what type is the product's name?"() {
        expect:
        shouldReturn "VARCHAR", '''
            SELECT "data_type" FROM information_schema.columns
            WHERE table_name = 'Products' AND "column_name" = ___
        '''
    }

    // 3) Now the price column. The answer is not just "a number" — it comes back as
    //    DECIMAL(19,4), and those two numbers are a promise: up to 19 digits in total, and
    //    exactly 4 of them AFTER the decimal point.
    def "the price column promises four decimal places"() {
        expect:
        shouldReturn "DECIMAL(19,4)", '''
            SELECT "data_type" FROM information_schema.columns
            WHERE table_name = 'Products' AND "column_name" = ___
        '''
    }

    // 4) And here is that promise being kept. Chai costs eighteen — ask for the price and
    //    you get 18.0000, four decimal places, because koan 3 said there would be four.
    //    (CloudBeaver TRIMS those zeros and shows 18. Same value, different client. The
    //    zeros are the column's type, not the number.)
    def "so the price comes back as 18.0000, not 18"() {
        expect:
        shouldReturn 18.0000, '''
            SELECT ___ FROM "Products" LIMIT 1
        '''
    }

    // 5) A third family: dates. Fill in the table that holds the orders, and see what the
    //    catalog calls the column that records WHEN each one was placed. It is not text —
    //    which is what lets the database know March comes before April.
    def "a date is its own kind of thing"() {
        expect:
        shouldReturn "TIMESTAMP", '''
            SELECT "data_type" FROM information_schema.columns
            WHERE table_name = ___ AND "column_name" = 'OrderDate'
        '''
    }

    // 6) The fourth family: a yes-or-no flag. Fill in the column that says whether a product
    //    is still sold. Every row holds either true or false — and only two of the twenty
    //    products are discontinued, so the first three are all false.
    def "read a true/false column"() {
        expect:
        // ONE LIST PER ROW, even for a single column — that is the shape rows() returns, and
        // it is the same shape every other row-set koan in the course uses.
        shouldReturn([[false], [false], [false]], '''
            SELECT ___ FROM "Products" LIMIT 3
        ''')
    }

    // 7) All digits, and still not a number. Fill in the column holding the postal code,
    //    then look at the seventh row: Leipzig is 04179. Store that as a number and the
    //    leading zero is gone — 4179 is a different place. If you will never do arithmetic
    //    on it, it is not a number.
    def "a postal code is text, and the leading zero proves it"() {
        expect:
        shouldReturn([["12209"], ["68306"], ["52066"], ["80805"],
                      ["14776"], ["60528"], ["04179"], ["50739"]], '''
            SELECT ___ FROM "Customers" LIMIT 8
        ''')
    }

    // 8) What being a number BUYS you: arithmetic. Chai costs 18 — double it.
    //    (Try the same thing on "ProductName" in CloudBeaver afterwards. The database
    //    refuses, and that refusal is the type system doing its job.)
    def "numbers do arithmetic"() {
        expect:
        shouldReturn 36.0000, '''
            SELECT "UnitPrice" * ___ FROM "Products" LIMIT 1
        '''
    }

    // 9) THE ONE THAT CATCHES EVERYONE. The same two digits on both sides — one pair quoted,
    //    one pair bare — and the answers disagree. Fill in the bare number so the second
    //    comparison is between NUMBERS. Predict both before you run it.
    //    (Text compares letter by letter, like a dictionary: '9' beats '1' straight away.)
    def "as text 9 beats 10; as numbers it does not"() {
        expect:
        shouldReturn([[true, false]], '''
            SELECT '9' > '10', 9 > ___
        ''')
    }

    // 10) The whole query — no scaffolding. Write the catalog query yourself: how many
    //     columns does the "Orders" table have? (You should get 14.)
    //     This is the one worth remembering — it is the first query to run against any
    //     database you have never seen before.
    def "write it yourself: how many columns does Orders have?"() {
        expect:
        shouldReturn 14, '''
            ___
        '''
    }
}
