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
 * Seven koans, one per idea in the lesson and in the same order: ask the catalog how
 * many columns a table has, ask what type one column is, do arithmetic on a number,
 * see that a postal code is text, meet the comparison that catches everyone, read a
 * boolean, then write a catalog query from scratch.
 *
 * These run on DuckDB. Every one of them returns the same rows in CloudBeaver against
 * PostgreSQL — EXCEPT the spelling of the type names in koan 2. DuckDB says VARCHAR
 * where PostgreSQL says "character varying". Same family, different word; the lesson
 * says so too. Every koan here expects DuckDB's spelling.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * Every table and column these koans touch, so you can write a query without leaving
 * this file. When a koan asks for "the column holding the postal code", it is asking
 * you to FIND the name below — not to have memorised it.
 *
 * Types are DuckDB's, because that is what the koans run on.
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
    //    every row in it describes one column of one table. Which table do you want to
    //    know about? It is a text VALUE, so it goes in 'single quotes'.
    //    (Predict first: how many columns does Products have?)
    def "the catalog knows how many columns Products has"() {
        expect:
        shouldReturn 10, '''
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = ___
        '''
    }

    // 2) Same catalog, a sharper question: not how many, but what KIND. Fill in the name
    //    of the column that holds a product's name — and mind the quotes: 'ProductName'
    //    here is a text value being matched, not a column being named.
    def "what type is the product's name?"() {
        expect:
        shouldReturn "VARCHAR", '''
            SELECT "data_type" FROM information_schema.columns
            WHERE table_name = 'Products' AND "column_name" = ___
        '''
    }

    // 3) A number is a number: you can do arithmetic on it. Chai costs 18 — double it.
    //    (Try the same thing on "ProductName" in CloudBeaver afterwards. The database
    //    refuses, and that refusal is the type system doing its job.)
    def "numbers do arithmetic"() {
        expect:
        shouldReturn 36.0000, '''
            SELECT "UnitPrice" * ___ FROM "Products" LIMIT 1
        '''
    }

    // 4) All digits, and still not a number. Fill in the column holding the postal code,
    //    then look at the seventh row: Leipzig is 04179. Store that as a number and the
    //    leading zero is gone — 4179 is a different place. If you will never do arithmetic
    //    on it, it is not a number.
    def "a postal code is text, and the leading zero proves it"() {
        expect:
        shouldReturn(["12209", "68306", "52066", "80805",
                      "14776", "60528", "04179", "50739"], '''
            SELECT ___ FROM "Customers" LIMIT 8
        ''')
    }

    // 5) THE ONE THAT CATCHES EVERYONE. Same two digits on both sides — one pair quoted,
    //    one pair bare — and the answers disagree. Fill in the bare number so the second
    //    comparison is between NUMBERS. Predict both before you run it.
    //    (Text compares letter by letter, like a dictionary: '9' beats '1' straight away.)
    def "as text 9 beats 10; as numbers it does not"() {
        expect:
        shouldReturn([[true, false]], '''
            SELECT '9' > '10', 9 > ___
        ''')
    }

    // 6) A true/false column. Fill in the one that says whether a product is still sold —
    //    and note you write true and false bare, like numbers, never as 'true' in quotes.
    def "read a boolean column"() {
        expect:
        shouldReturn([false, false, false], '''
            SELECT ___ FROM "Products" LIMIT 3
        ''')
    }

    // 7) The whole query — no scaffolding. Write the catalog query yourself: how many
    //    columns does the "Orders" table have? (You should get 14.)
    //    This is the query worth remembering — it is the first one to run against any
    //    database you have never seen before.
    def "write it yourself: how many columns does Orders have?"() {
        expect:
        shouldReturn 14, '''
            ___
        '''
    }
}
