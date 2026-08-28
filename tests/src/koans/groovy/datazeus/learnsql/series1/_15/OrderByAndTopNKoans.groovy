package datazeus.learnsql.series1._15

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 15 ORDER BY, LIMIT & FETCH FIRST     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _15  (Windows)   ./zeus.sh koans learnsql series1 _15  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * TEN KOANS, IN THE ORDER THE LESSON BUILDS THEM, easiest first:
 *   1-2  put rows in an order, both directions
 *   3-4  keep only the top few — LIMIT, and the standard spelling
 *   5    THE TRAP: the same LIMIT with the ORDER BY taken away
 *   6    break a tie with a second sort key
 *   7    sort text, and sort by two keys the way a report does
 *   8    where "missing" goes, and how to say where you want it
 *   9    skip a page with OFFSET
 *   10   the whole query, written from scratch
 *
 * These run on DuckDB. Every one returns the same answer in CloudBeaver against
 * PostgreSQL — with ONE exception, and it is koan 8, which is the point of koan 8:
 * the two engines put NULLs in different places unless you say where you want them.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * Every table and column these koans touch, so you can write a query without leaving
 * this file. When a koan asks for "the column holding the price", it is asking you to
 * FIND the name below — not to have memorised it.
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
 *   "Orders" — 79 rows, 14 columns. "ShippedDate" IS NULL on 27 of them: those
 *   orders were placed and have never shipped.
 *     "OrderID"        INTEGER        "CustomerID"     VARCHAR
 *     "EmployeeID"     INTEGER        "OrderDate"      TIMESTAMP
 *     "RequiredDate"   TIMESTAMP      "ShippedDate"    TIMESTAMP
 *     "ShipVia"        INTEGER        "Freight"        DECIMAL(19,4)
 *     "ShipName"       VARCHAR        "ShipAddress"    VARCHAR
 *     "ShipCity"       VARCHAR        "ShipRegion"     VARCHAR
 *     "ShipPostalCode" VARCHAR        "ShipCountry"    VARCHAR
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class OrderByAndTopNKoans extends KoanBase {

    // 1) Put the rows in an order. Fill in the column to sort by — you want the CHEAPEST
    //    product, and ascending is what ORDER BY does when you don't say otherwise.
    //    (Predict first: what is the cheapest thing this company sells?)
    def "cheapest first, and ascending is the default"() {
        expect:
        shouldReturn "Guarana Fantastica", '''
            SELECT "ProductName" FROM "Products"
            ORDER BY ___ LIMIT 1
        '''
    }

    // 2) Same column, other end. One word turns a sort around — fill in the word that
    //    means "biggest first". It is the direction almost every real request wants.
    def "one word turns the sort around"() {
        expect:
        shouldReturn "Thuringer Rostbratwurst", '''
            SELECT "ProductName" FROM "Products"
            ORDER BY "UnitPrice" ___ LIMIT 1
        '''
    }

    // 3) Now keep more than one. ORDER BY decides WHICH rows; LIMIT decides HOW MANY.
    //    Fill in how many you want, so that this is the top FIVE by price.
    def "sort, then cut: the five most expensive"() {
        expect:
        // ONE LIST PER ROW, even for a single column — that is the shape rows() returns.
        shouldReturn([["Thuringer Rostbratwurst"], ["Mishi Kobe Niku"], ["Gnocchi di nonna Alice"],
                      ["Camembert Pierrot"], ["Ikura"]], '''
            SELECT "ProductName" FROM "Products"
            ORDER BY "UnitPrice" DESC LIMIT ___
        ''')
    }

    // 4) The same five rows, in the SQL standard's own spelling. LIMIT is what you will
    //    write day to day; FETCH FIRST is what Oracle, Db2 and SQL Server want, and what
    //    you will meet in other people's queries. Fill in the count.
    def "FETCH FIRST is the standard spelling of the same idea"() {
        expect:
        // A ROW SET, not a single name: every count from 1 upwards puts Thuringer in the
        // first row, so checking one cell would leave the blank free to be anything.
        shouldReturn([["Thuringer Rostbratwurst"], ["Mishi Kobe Niku"], ["Gnocchi di nonna Alice"],
                      ["Camembert Pierrot"], ["Ikura"]], '''
            SELECT "ProductName" FROM "Products"
            ORDER BY "UnitPrice" DESC
            FETCH FIRST ___ ROWS ONLY
        ''')
    }

    // 5) THE ONE THIS LESSON EXISTS FOR. Somebody asks for "the five most expensive
    //    products" and you write LIMIT 5 without an ORDER BY. You get five rows, no
    //    error — and they are not the top five. Fill in the ONE thing that is missing so
    //    the query answers the question that was actually asked.
    //    (Run it WITHOUT your fix first, and look at what comes back. Aniseed Syrup at
    //     10.0000 is the third CHEAPEST product in the catalogue.)
    def "LIMIT without ORDER BY is five rows, not a top five"() {
        expect:
        // ALL FIVE ROWS, so you see the corrected list in full — and so the fix has to be
        // the right sort, not merely something that happens to float Thuringer to the top.
        shouldReturn([["Thuringer Rostbratwurst"], ["Mishi Kobe Niku"], ["Gnocchi di nonna Alice"],
                      ["Camembert Pierrot"], ["Ikura"]], '''
            SELECT "ProductName" FROM "Products"
            ___
            LIMIT 5
        ''')
    }

    // 6) A sorted query can still wobble. Scottish Longbreads and Gorgonzola Telino BOTH
    //    cost 12.5000, so "the five cheapest" has two candidates for fifth place and
    //    nothing to choose between them. Add a SECOND sort key so the tie is broken by
    //    name — and the same five come back, in the same order, every single run.
    def "break the tie with a second sort key"() {
        expect:
        shouldReturn([["Guarana Fantastica"], ["Filo Mix"], ["Aniseed Syrup"],
                      ["Gorgonzola Telino"], ["Scottish Longbreads"]], '''
            SELECT "ProductName" FROM "Products"
            ORDER BY "UnitPrice", ___
            LIMIT 5
        ''')
    }

    // 7) Two keys doing the job they were made for: a list somebody is going to READ.
    //    Group it by country, and inside each country order the companies by name.
    //    Fill in the first key. Read it left to right — the first key decides, and the
    //    second only gets a say when the first one ties.
    def "country groups the list, name orders each country"() {
        expect:
        shouldReturn([["Cactus Comidas para llevar"], ["Ernst Handel"],
                      ["Bon app'"], ["Du monde entier"]], '''
            SELECT "CompanyName" FROM "Customers"
            ORDER BY ___, "CompanyName"
            LIMIT 4
        ''')
    }

    // 8) WHERE DOES "MISSING" GO? 27 of our 79 orders have no "ShippedDate" — they have
    //    never shipped. Ask for the five most recently shipped and DuckDB puts the missing
    //    ones last, while PostgreSQL puts them FIRST and hands you five orders that never
    //    shipped at all. Same query, two answers, neither engine wrong.
    //    Fill in the two words that settle it on every engine.
    def "say where missing values belong, and the engines stop disagreeing"() {
        expect:
        shouldReturn([[6], [4], [79], [3], [78]], '''
            SELECT "OrderID" FROM "Orders"
            ORDER BY "ShippedDate" DESC ___
            LIMIT 5
        ''')
    }

    // 9) Page two. You have shown the top five; now show the next five. Fill in how many
    //    rows to SKIP. (OFFSET only means anything on top of a stable order — which is
    //    why this one has an ORDER BY and koan 5 did not.)
    def "OFFSET skips the rows you have already shown"() {
        expect:
        shouldReturn "Uncle Bobs Organic Dried Pears", '''
            SELECT "ProductName" FROM "Products"
            ORDER BY "UnitPrice" DESC
            LIMIT 5 OFFSET ___
        '''
    }

    // 10) The whole query — no scaffolding. Which single order cost us the most to ship?
    //     Return just its "OrderID". You want the "Freight" column, biggest first, one row.
    //     (You should get 72 — a delivery to San Cristóbal, Venezuela, at 98.9200.)
    def "write it yourself: which order cost the most to ship?"() {
        expect:
        shouldReturn 72, '''
            ___
        '''
    }
}
