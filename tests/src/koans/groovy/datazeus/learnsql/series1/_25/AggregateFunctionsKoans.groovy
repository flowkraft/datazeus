package datazeus.learnsql.series1._25

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 25 COUNT, SUM, AVG, MIN, MAX         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _25  (Windows)   ./zeus.sh koans learnsql series1 _25  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * Same ten ideas, in the same order, asked about DIFFERENT columns and a different
 * question. The lesson counts orders, totals the freight bill and works over the price
 * list; here you work the WAREHOUSE ("UnitsInStock"), the ORDER LINES ("Quantity") and
 * the CUSTOMER LIST. Copying a query across from the lesson will not work — which is the
 * point. You learn the idea by applying it somewhere new, not by retyping an answer.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1    many rows in, one number out
 *   2-4  the four that do arithmetic — MIN and MAX, then SUM, then AVG
 *   5    WHERE runs first, so the aggregate only ever sees what survived it
 *   6    THE TRAP: count(*) counts ROWS, count(column) counts VALUES
 *   7    an aggregate over no rows at all — and what SUM and MAX hand back
 *   8    counting the DIFFERENT values instead of the rows
 *   9    naming the row an aggregate cannot name for you
 *   10   the whole query, written from scratch
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver — including koan 4, whose average comes out exactly 29.25 on
 * both because 585 units divide evenly by twenty lines.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The four tables these koans use, in full, so you can write a query without leaving
 * this file. When a koan asks for "what is left on the shelf", it is asking you to FIND
 * the column name below — not to have memorised it.
 *
 *   "Products" — 20 rows, 10 columns. "UnitsInStock" is how many we have on the shelf
 *   right now; "Discontinued" is true for the two lines we no longer sell. No column
 *   here is ever empty.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 *
 *   "Order Details" — 193 rows, 5 columns. ONE ROW PER PRODUCT ON AN ORDER, so an order
 *   with three products has three rows here. "Quantity" is how many units of that
 *   product the customer asked for. Note the SPACE in the table name — it still goes in
 *   double quotes, exactly like every other identifier.
 *     "OrderID"    INTEGER        "ProductID"  INTEGER
 *     "UnitPrice"  DECIMAL(19,4)  "Quantity"   SMALLINT
 *     "Discount"   DECIMAL(8,4)
 *
 *   "Customers" — 25 rows, 12 columns. "Fax" IS EMPTY on 7 of them, which is the whole
 *   of koan 6; "Country" holds ten different countries across the twenty-five rows.
 *     "CustomerID"   VARCHAR        "CompanyName"  VARCHAR
 *     "ContactName"  VARCHAR        "ContactTitle" VARCHAR
 *     "Address"      VARCHAR        "City"         VARCHAR
 *     "Region"       VARCHAR        "PostalCode"   VARCHAR
 *     "Country"      VARCHAR        "Phone"        VARCHAR
 *     "Fax"          VARCHAR        "Email"        VARCHAR
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class AggregateFunctionsKoans extends KoanBase {

    // 1) MANY ROWS IN, ONE NUMBER OUT. Everything you have written so far handed back rows;
    //    this hands back a single value. Fill in the whole aggregate — the one that answers
    //    "how many rows are there", whatever is in them.
    //    (Predict first: the customer list is small. It is not 79 — that is the order table.)
    def "many rows in, one number out"() {
        expect:
        shouldReturn 25, '''
            SELECT ___ AS "Customers"
            FROM "Customers"
        '''
    }

    // 2) MIN and MAX bracket a column: the smallest value in it and the largest. Here that is
    //    the emptiest shelf in the warehouse and the fullest. Fill in the function that goes
    //    with "Fullest".
    //    (Predict first: two products are at zero stock, so you already know the left-hand
    //     answer. One line is stacked far deeper than any other — that is the right-hand one.)
    def "MIN and MAX bracket a column"() {
        expect:
        // A ROW SET, not a single value: the koan is about the SECOND column, and a scalar
        // check only ever looks at the first cell — so `min` in the blank would pass.
        shouldReturn([[0, 123]], '''
            SELECT min("UnitsInStock") AS "Emptiest",
                   ___("UnitsInStock") AS "Fullest"
            FROM "Products"
        ''')
    }

    // 3) SUM adds a column up. "Order Details" holds one row per product on an order, so this
    //    is every unit of everything we have ever been asked for. Fill in the column.
    //    (Predict first: 193 order lines, and the average line is about ten units.)
    def "SUM adds one column up, over every row"() {
        expect:
        shouldReturn 2070, '''
            SELECT sum(___) AS "Units sold"
            FROM "Order Details"
        '''
    }

    // 4) AVG is the total divided by how many values it found. Fill in the function, then
    //    CHECK ITS ARITHMETIC against the number beside it — that habit is the reason this
    //    koan shows both columns instead of just the average.
    //    (Predict first: twenty products, and the total is in the left-hand column.)
    def "AVG is the total over the number of values"() {
        expect:
        // 585 units over 20 product lines is exactly 29.25 — no rounding, on either engine.
        shouldReturn([[585, 29.25]], '''
            SELECT sum("UnitsInStock") AS "Units held",
                   ___("UnitsInStock") AS "Average shelf"
            FROM "Products"
        ''')
    }

    // 5) WHERE RUNS FIRST. The aggregate never sees the whole table — it sees whatever WHERE
    //    left behind, and then collapses that. Fill in the condition so this counts only our
    //    German customers. (Column name and value are both in the schema block above; the
    //    value is text, so it needs single quotes.)
    //    (Predict first: Germany is comfortably our biggest market, but this is 25 customers
    //     in total — so the answer is under half of them.)
    def "WHERE runs first, so the count is of what survived it"() {
        expect:
        shouldReturn 11, '''
            SELECT count(*) AS "German customers"
            FROM "Customers"
            WHERE ___
        '''
    }

    // 6) THE ONE THIS LESSON EXISTS FOR. count(*) counts ROWS. count(column) counts VALUES —
    //    and an empty cell is not a value, so it is not counted. Fill in the column whose
    //    empties make the two numbers differ.
    //    (Predict first: the schema note above tells you how many customers have no fax. Work
    //     out the second number before you run it, and you have understood the whole slide.)
    def "count(*) counts rows, count(column) counts values"() {
        expect:
        shouldReturn([[25, 18]], '''
            SELECT count(*) AS "Customers",
                   count(___) AS "With a fax"
            FROM "Customers"
        ''')
    }

    // 7) AN AGGREGATE OVER NO ROWS AT ALL. Nothing we sell costs more than 200, so WHERE
    //    throws every row away — and the query STILL returns one row. Fill in the function
    //    that asks for the dearest, and look hard at what it hands back.
    //    (Predict first: count says 0. The other one does NOT say 0.)
    def "an aggregate over no rows still returns one row"() {
        expect:
        // null, not 0, and not "no rows". That difference is the koan: a report that expected
        // a number gets a blank, and nothing errored to tell anybody.
        shouldReturn([[0, null]], '''
            SELECT count(*) AS "Products",
                   ___("UnitPrice") AS "Dearest"
            FROM "Products"
            WHERE "UnitPrice" > 200
        ''')
    }

    // 8) "HOW MANY COUNTRIES ARE OUR CUSTOMERS IN?" is not "how many customers are there" —
    //    the same country turns up on row after row and you want to count it once. Fill in
    //    the one word that goes INSIDE the brackets, in front of the column.
    //    (Predict first: 25 customers, and this number is well under half of that.)
    def "count the different values, not the rows"() {
        expect:
        shouldReturn 10, '''
            SELECT count(___ "Country") AS "Countries"
            FROM "Customers"
        '''
    }

    // 9) AN AGGREGATE CANNOT NAME THE ROW IT CAME FROM. max("UnitsInStock") tells you 123 and
    //    nothing else — ask it for "ProductName" alongside and the database refuses, because
    //    one row cannot hold twenty names. To NAME the row you sort and take one, which you
    //    already know how to do. Fill in what to sort by, and in which direction.
    //    (Predict first: it is the fullest shelf, so you want the biggest first.)
    def "to name the row, sort it and take one"() {
        expect:
        shouldReturn([["Boston Crab Meat", 123]], '''
            SELECT "ProductName", "UnitsInStock"
            FROM "Products"
            ORDER BY ___
            LIMIT 1
        ''')
    }

    // 10) The whole query — no scaffolding, and both halves of the lesson in one go.
    //     THE QUESTION: of our GERMAN customers, how many are there, and how many of them
    //     gave us a fax number?
    //       · Germans only              -> WHERE, on "Country"
    //       · how many of them          -> count(*), aliased "Customers"
    //       · how many gave us a fax    -> count of the "Fax" column, aliased "With a fax"
    //     Two aggregates in one SELECT, both describing the same filtered set of rows.
    //     Three clauses, in the only order SQL accepts them: SELECT, FROM, WHERE.
    def "write the whole query: German customers, and how many faxed"() {
        expect:
        shouldReturn([[11, 8]], '''
            ___
        ''')
    }
}
