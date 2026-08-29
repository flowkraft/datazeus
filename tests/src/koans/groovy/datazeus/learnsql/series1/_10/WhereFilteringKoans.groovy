package datazeus.learnsql.series1._10

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 10 WHERE, AND/OR/NOT, IN, BETWEEN     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _10  (Windows)   ./zeus.sh koans learnsql series1 _10  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * THIRTEEN KOANS, IN THE ORDER THE LESSON BUILDS THEM, easiest first:
 *   1-3    one test: keep the rows that pass it, flip it, then compare a number
 *   4      two tests at once, each one narrowing
 *   5-6    the trap: write what SQL REALLY ran, then write what you meant
 *   7      turn a whole group inside out
 *   8      one test against a list
 *   9-10   a range with its ends in, then the same range with its ends out
 *   11     the date habit that never miscounts a month
 *   12     patterns
 *   13     the whole query, written from scratch
 *
 * KOANS 1-3 ARE THE LESSON'S OWN QUERIES, and they are the only three that are. The video
 * opens this file on screen and shows them by name, so they have to be here, spelled exactly
 * this way — a viewer who scrolls to koan 1 and finds something else has been lied to. They
 * also make a gentle start: you have just watched all three answered.
 *
 * KOANS 4-13 ASK QUESTIONS THE LESSON NEVER ASKS. Where it tests "Country" and "UnitPrice",
 * these mostly test "UnitsInStock" and "CategoryID". That is deliberate: past the first three,
 * retyping a query you just watched proves only that you can copy it. You will recognise every
 * IDEA and none of the answers, so the SQL has to come from you. Predict the number first.
 *
 * TWO OPERATORS ARE NOT DRILLED, on purpose: NOT IN and LIKE's lowercase trap. NOT IN is one
 * token from koan 8 and hides nothing, so it is left as an aside there rather than spending a
 * koan. The thirteen slots go to the ideas that bite: exact spelling, precedence, negating a
 * group, and the two ends of a range.
 *
 * They run on DuckDB — a single file, no server. Every one returns the same answer in
 * CloudBeaver against PostgreSQL.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * Every table and column these koans touch, so you can write a query without leaving
 * this file. When the last koan asks for "name and price of every product", it is
 * asking you to FIND those two columns below — not to have memorised them.
 *
 * Types are DuckDB's, because that is what the koans run on; CloudBeaver against
 * PostgreSQL reports the same tables and the same columns.
 *
 *   "Customers" — 25 rows, 12 columns
 *     "CustomerID"   VARCHAR  "CompanyName"  VARCHAR
 *     "ContactName"  VARCHAR  "ContactTitle" VARCHAR
 *     "Address"      VARCHAR  "City"         VARCHAR
 *     "Region"       VARCHAR  "PostalCode"   VARCHAR
 *     "Country"      VARCHAR  "Phone"        VARCHAR
 *     "Fax"          VARCHAR  "Email"        VARCHAR
 *
 *   "Products" — 20 rows, 10 columns
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
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
class WhereFilteringKoans extends KoanBase {

    // 1) WHERE keeps only the rows that pass the test. Text values go in
    //    'single quotes', and the comparison is EXACT — 'germany' matches nothing.
    def "keep only the German customers"() {
        expect:
        shouldReturn 11, '''
            SELECT count(*) FROM "Customers"
            WHERE "Country" = ___
        '''
    }

    // 2) The opposite test: NOT equal. One operator keeps everyone else.
    //    (SQL spells it <> — and accepts != as the same thing.)
    def "keep everyone who is NOT in Germany"() {
        expect:
        shouldReturn 14, '''
            SELECT count(*) FROM "Customers"
            WHERE "Country" ___ 'Germany'
        '''
    }

    // 3) Numbers are not text: they compare WITHOUT quotes. Below which price are
    //    there exactly two products? (Predict which two before you run it.)
    def "products cheaper than ten"() {
        expect:
        shouldReturn([["Filo Mix", 7.0000], ["Guarana Fantastica", 4.5000]], '''
            SELECT "ProductName", "UnitPrice" FROM "Products"
            WHERE "UnitPrice" < ___
        ''')
    }

    // ── from here on the questions are new: same ideas, ground you have not walked ──

    // 4) Two conditions, BOTH must pass — each one narrows the result further. Well stocked
    //    AND cheap: more than 30 units on the shelf, and under 20 to buy. One word joins them.
    //    (Predict it first: more than 30 in stock is eight products. How many survive the price?)
    def "well stocked AND under twenty"() {
        expect:
        shouldReturn 5, '''
            SELECT count(*) FROM "Products"
            WHERE "UnitsInStock" > 30 ___ "UnitPrice" < 20
        '''
    }

    // 5) THE TRAP, and this koan makes you write it out. AND binds tighter than OR, so
    //
    //        WHERE "CategoryID" = 4 OR "CategoryID" = 5 AND "UnitPrice" < 20
    //
    //    is NOT "categories 4 or 5, under 20". SQL groups the AND first and runs something
    //    else entirely. Add the brackets that make its REAL meaning explicit — same five
    //    rows, because you are not changing the query, only writing down what it already
    //    does. Every dairy product (category 4) gets in at ANY price.
    def "write the brackets SQL already put there"() {
        expect:
        shouldReturn 5, '''
            SELECT count(*) FROM "Products"
            WHERE "CategoryID" = 4 OR ___
        '''
    }

    // 6) Now the query you MEANT: dairy or grains (categories 4 and 5), and under 20 — the
    //    price cap applying to both, not just to one. Write the whole WHERE clause.
    //    Five rows became three, and the two it dropped were over the cap all along.
    def "parenthesize the OR — categories 4 or 5, under 20"() {
        expect:
        shouldReturn 3, '''
            SELECT count(*) FROM "Products"
            WHERE ___
        '''
    }

    // 7) NOT turns a test inside out. It applies to whatever FOLLOWS it, so to flip a whole
    //    group you must wrap the group first — without the brackets you would negate only the
    //    first comparison and quietly get a different answer. Keep every product that is
    //    neither meat (category 6) nor produce (category 7).
    def "everything except meat and produce"() {
        expect:
        shouldReturn 16, '''
            SELECT count(*) FROM "Products"
            WHERE ___ ("CategoryID" = 6 OR "CategoryID" = 7)
        '''
    }

    // 8) One test against a whole list: IN. The same as three = tests glued with OR, but
    //    readable. Keep the products in categories 3, 6 and 8 — two in each.
    //    (Put NOT in front of the IN and you get the other fourteen. Try that too.)
    def "products in any of three categories"() {
        expect:
        shouldReturn 6, '''
            SELECT count(*) FROM "Products"
            WHERE "CategoryID" IN (___)
        '''
    }

    // 9) A range in one word — and BOTH ends are included. Stock levels from 20 to 40.
    //    Guarana Fantastica has exactly 20 units: does it make the cut? Predict, then run.
    def "stock from twenty to forty, ends included"() {
        expect:
        shouldReturn 11, '''
            SELECT count(*) FROM "Products"
            WHERE "UnitsInStock" ___ 20 AND 40
        '''
    }

    // 10) The same range with the ends EXCLUDED — no BETWEEN this time, two strict
    //     comparisons. Guarana Fantastica at exactly 20 drops out: eleven become ten.
    def "the same range, ends excluded"() {
        expect:
        shouldReturn 10, '''
            SELECT count(*) FROM "Products"
            WHERE "UnitsInStock" ___ 20 AND "UnitsInStock" ___ 40
        '''
    }

    // 11) The date habit: greater-or-equal the FIRST day of the month, and strictly before
    //     the first day of the NEXT one. Never the 31st, never BETWEEN — this shape is right
    //     whether the column holds a date or a timestamp, and for a month of any length.
    //     Finish the March 2024 query: which date closes it?
    def "orders placed in March 2024"() {
        expect:
        shouldReturn 5, '''
            SELECT count(*) FROM "Orders"
            WHERE "OrderDate" >= DATE '2024-03-01'
              AND "OrderDate" <  DATE ___
        '''
    }

    // 12) LIKE matches text patterns; % stands for "anything from here". Write the pattern
    //     (in 'single quotes') that keeps the four products STARTING WITH G. Remember LIKE is
    //     case-exact like every text comparison — a lowercase g finds nothing.
    def "products whose name starts with G"() {
        expect:
        shouldReturn([["Gorgonzola Telino"], ["Gnocchi di nonna Alice"],
                      ["Guarana Fantastica"], ["Genen Shouyu"]], '''
            SELECT "ProductName" FROM "Products"
            WHERE "ProductName" LIKE ___
        ''')
    }

    // 13) The whole query — no scaffolding. Write ALL of it: name and price of every product
    //     with nothing left in stock. (Two of them, and one is the most expensive thing the
    //     company sells — which is exactly why somebody would want this list.)
    def "write it yourself: the empty shelf"() {
        expect:
        shouldReturn([["Gorgonzola Telino", 12.5000],
                      ["Thuringer Rostbratwurst", 123.7900]], '''
            ___
        ''')
    }
}
