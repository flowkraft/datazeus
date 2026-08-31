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
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * Same ten ideas, in the same order, asked about DIFFERENT columns and a different
 * question. The lesson sorted the product catalogue by price; here you mostly sort
 * ORDERS BY WHAT THEY COST US TO SHIP, and then the warehouse by what is left on the
 * shelf. Copying a query across from the lesson will not work — which is the point.
 * You learn the idea by applying it somewhere new, not by retyping an answer.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1-2  put rows in an order, both directions
 *   3-4  keep only the top few — LIMIT, and the standard spelling
 *   5    THE TRAP: the same LIMIT with the ORDER BY taken away
 *   6    break a tie with a second sort key
 *   7    two keys on a report: the first decides, the second only settles ties
 *   8    where "missing" goes, and how to say where you want it
 *   9    skip a page with OFFSET
 *   10   the whole query, written from scratch
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver — including koan 8, once you have solved it. Solve koan 8
 * the lazy way (leave the blank out) and the two engines disagree, which is the koan.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The two tables these koans use, in full, so you can write a query without leaving
 * this file. When a koan asks for "what the delivery cost us", it is asking you to
 * FIND the column name below — not to have memorised it.
 *
 *   "Orders" — 79 rows, 14 columns. "ShippedDate" IS NULL on 27 of them: those
 *   orders were placed and have never shipped. Every other column here is filled in.
 *     "OrderID"        INTEGER        "CustomerID"     VARCHAR
 *     "EmployeeID"     INTEGER        "OrderDate"      TIMESTAMP
 *     "RequiredDate"   TIMESTAMP      "ShippedDate"    TIMESTAMP
 *     "ShipVia"        INTEGER        "Freight"        DECIMAL(19,4)
 *     "ShipName"       VARCHAR        "ShipAddress"    VARCHAR
 *     "ShipCity"       VARCHAR        "ShipRegion"     VARCHAR
 *     "ShipPostalCode" VARCHAR        "ShipCountry"    VARCHAR
 *
 *   "Products" — 20 rows, 10 columns. "UnitsInStock" is how many we have on the
 *   shelf right now; "Discontinued" is true for lines we no longer sell.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class OrderByAndTopNKoans extends KoanBase {

    // 1) Put the rows in an order. Which delivery was the CHEAPEST one we ever paid for?
    //    Fill in the column to sort by — ascending is what ORDER BY does when you don't
    //    say otherwise, and ascending means smallest first.
    //    (Predict first: you are looking for the smallest shipping bill in the table.)
    def "sort first: ascending is the default direction"() {
        expect:
        shouldReturn 8, '''
            SELECT "OrderID" FROM "Orders"
            ORDER BY ___ LIMIT 1
        '''
    }

    // 2) Same column, other end: the delivery that cost us the MOST. One word turns a sort
    //    around — fill in the word that means "biggest first". It is the direction almost
    //    every real request wants, which is why it is worth typing rather than assuming.
    def "DESC turns the sort around"() {
        expect:
        shouldReturn 72, '''
            SELECT "OrderID" FROM "Orders"
            ORDER BY "Freight" ___ LIMIT 1
        '''
    }

    // 3) Now keep more than one. ORDER BY decides WHICH rows; LIMIT decides HOW MANY.
    //    Fill in how many, so this is our five most expensive deliveries.
    def "LIMIT cuts the sorted list to the top few"() {
        expect:
        // ONE LIST PER ROW, even for a single column — that is the shape rows() returns.
        shouldReturn([[72], [59], [46], [33], [20]], '''
            SELECT "OrderID" FROM "Orders"
            ORDER BY "Freight" DESC LIMIT ___
        ''')
    }

    // 4) The same five deliveries, in the SQL standard's own spelling. LIMIT is what you
    //    will write day to day; FETCH FIRST is what Oracle and SQL Server want, and what
    //    you will meet in other people's queries. Fill in the count.
    def "FETCH FIRST is the standard spelling of LIMIT"() {
        expect:
        // A ROW SET, not a single value: every count from 1 upwards puts order 72 in the
        // first row, so checking one cell would leave the blank free to be anything.
        shouldReturn([[72], [59], [46], [33], [20]], '''
            SELECT "OrderID" FROM "Orders"
            ORDER BY "Freight" DESC
            FETCH FIRST ___ ROWS ONLY
        ''')
    }

    // 5) THE ONE THIS LESSON EXISTS FOR. Somebody asks which five deliveries cost us the
    //    most, you are busy, and you write LIMIT 5 with nothing above it. Five rows come
    //    back, no error — and they are not the top five. Fill in the ONE clause that is
    //    missing so the query answers the question that was actually asked.
    //    (Run it WITHOUT your fix first and look at what you get: orders 1 to 5, and order
    //     5 at 11.6100 is the third CHEAPEST freight bill in the whole table.)
    def "LIMIT with no ORDER BY is not a top five"() {
        expect:
        // ALL FIVE ROWS, so you see the corrected list in full — and so the fix has to be
        // the right sort, not merely something that happens to float order 72 to the top.
        shouldReturn([[72], [59], [46], [33], [20]], '''
            SELECT "OrderID" FROM "Orders"
            ___
            LIMIT 5
        ''')
    }

    // 6) A sorted query can still wobble. We are short of stock, so list the five products
    //    we hold the fewest of — and TWO of them are at zero, which leaves the sort with a
    //    tie and nothing to settle it. Add a SECOND sort key so equal-stock rows fall into
    //    alphabetical order, and the same five come back in the same order every run.
    //    (Note where "Thuringer Rostbratwurst" lands: G before T decides it.)
    def "a second sort key breaks the tie"() {
        expect:
        shouldReturn([["Gorgonzola Telino"], ["Thuringer Rostbratwurst"], ["Scottish Longbreads"],
                      ["Aniseed Syrup"], ["Uncle Bobs Organic Dried Pears"]], '''
            SELECT "ProductName" FROM "Products"
            ORDER BY "UnitsInStock", ___
            LIMIT 5
        ''')
    }

    // 7) Two keys doing the job they were made for: a shipping report somebody will READ.
    //    Put every delivery to the same country together, and inside each country show the
    //    dearest first. Fill in the FIRST key. Read it left to right — the first key
    //    decides the whole shape, and the second only gets a say when the first one ties.
    //    (This is NOT grouping in the GROUP BY sense: all 79 rows still come back, rows
    //     that share a country simply end up next to each other.)
    def "the first key decides, the second only breaks ties"() {
        expect:
        shouldReturn([[15], [40], [65], [18], [43], [68]], '''
            SELECT "OrderID" FROM "Orders"
            ORDER BY ___, "Freight" DESC
            LIMIT 6
        ''')
    }

    // 8) WHERE DOES "MISSING" GO? 27 of our 79 orders have never shipped, so "ShippedDate"
    //    is NULL on them. Here we WANT those at the top — a backlog you cannot miss.
    //    Sorting DESC gets you there on PostgreSQL by luck (it treats NULL as larger than
    //    any date) and not at all on DuckDB (which puts missing last). Fill in the two
    //    words that pin it down on BOTH engines, so the answer stops being an accident.
    //    (The ", \"OrderID\"" already there is koan 6's lesson again: all 27 NULLs tie with
    //     each other, so without it there is nothing to decide which three you see.)
    def "NULLS FIRST says where missing values go"() {
        expect:
        shouldReturn([[2], [5], [7]], '''
            SELECT "OrderID" FROM "Orders"
            ORDER BY "ShippedDate" DESC ___, "OrderID"
            LIMIT 3
        ''')
    }

    // 9) Page two. You have shown the five most expensive deliveries; now show the next
    //    five. Fill in how many rows to SKIP. (OFFSET only means anything on top of a
    //    stable order — which is why this one keeps its ORDER BY and koan 5 did not.)
    def "OFFSET skips the rows you already showed"() {
        expect:
        shouldReturn([[71], [58], [45], [32], [19]], '''
            SELECT "OrderID" FROM "Orders"
            ORDER BY "Freight" DESC
            LIMIT 5 OFFSET ___
        ''')
    }

    // 10) The whole query — no scaffolding, and everything above it in one go.
    //     THE QUESTION: which three products should the warehouse reorder first?
    //       · only lines we still sell        -> "Discontinued" is false
    //       · the emptiest shelves first      -> fewest "UnitsInStock", smallest first
    //       · ties settled by name            -> so the list is the same every run
    //       · three of them                   -> and return just "ProductName"
    //     Four clauses, in the only order SQL accepts them: SELECT, FROM, WHERE, ORDER BY,
    //     LIMIT. (Discontinued is a BOOLEAN — compare it to false, don't quote it.)
    def "write the whole query: what to reorder first"() {
        expect:
        shouldReturn([["Gorgonzola Telino"], ["Scottish Longbreads"], ["Aniseed Syrup"]], '''
            ___
        ''')
    }
}
