package datazeus.learnsql.series1._30

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 30 GROUP BY                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _30  (Windows)   ./zeus.sh koans learnsql series1 _30  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * Same ten ideas, in the same order, asked about a DIFFERENT table and a different
 * question. The lesson counts ORDERS PER COUNTRY; here you mostly count PRODUCTS PER
 * CATEGORY and add up what is on the shelf. Copying a query across from the lesson
 * will not work — which is the point. You learn the idea by applying it somewhere
 * new, not by retyping an answer.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1    say what the groups are — one row per distinct value, in no promised order
 *   2    the aggregate runs once per group, not once per table
 *   3    rank the groups by the number you just made — and why LIMIT needs ORDER BY
 *   4    more than one number per group — a report, not a count
 *   5    THE TRAP: a GROUP BY list one column too long, and a report that lies
 *   6    the same two keys, now answering the question that wants them
 *   7    group by an EXPRESSION, not just a column
 *   8    WHERE runs FIRST — and a whole group can disappear
 *   9    grouped rows tie like any other rows, so break the tie
 *   10   the whole query, written from scratch
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver — no koan here depends on an engine's private choices.
 *
 * ONE THING THE LESSON SHOWS AND THESE KOANS DO NOT: the two ERRORS. A koan checks a
 * result, so a query that refuses to run has nothing to compare. Koans 4 and 5 are the
 * same rule from the other side — the side where the query runs and the answer is wrong.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The three tables these koans use, in full, so you can write a query without leaving
 * this file. When a koan asks for "what is on the shelf", it is asking you to FIND the
 * column name below — not to have memorised it.
 *
 *   "Products" — 20 rows, 10 columns. "UnitsInStock" is how many we have on the shelf
 *   right now; "Discontinued" is true for lines we no longer sell (2 of the 20).
 *   Products fall into 8 categories and come from 6 suppliers, and the two groupings
 *   CUT ACROSS EACH OTHER — every one of the eight categories is supplied by more than
 *   one supplier, which is what koans 5 and 6 are about.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 *
 *   "Customers" — 25 rows, 12 columns. They live in 10 countries; 11 of the 25 are
 *   German, and every other country has one or two.
 *     "CustomerID"   VARCHAR        "CompanyName"  VARCHAR
 *     "ContactName"  VARCHAR        "ContactTitle" VARCHAR
 *     "Address"      VARCHAR        "City"         VARCHAR
 *     "Region"       VARCHAR        "PostalCode"   VARCHAR
 *     "Country"      VARCHAR        "Phone"        VARCHAR
 *     "Fax"          VARCHAR        "Email"        VARCHAR
 *
 *   "Orders" — 79 rows, 14 columns. Used by koan 7 only, because it is the one table
 *   here with a DATE in it and koan 7 is about grouping by an expression.
 *   "OrderDate" is filled in on all 79 and runs from December 2022 to June 2024.
 *     "OrderID"        INTEGER        "CustomerID"     VARCHAR
 *     "EmployeeID"     INTEGER        "OrderDate"      TIMESTAMP
 *     "RequiredDate"   TIMESTAMP      "ShippedDate"    TIMESTAMP
 *     "ShipVia"        INTEGER        "Freight"        DECIMAL(19,4)
 *     "ShipName"       VARCHAR        "ShipAddress"    VARCHAR
 *     "ShipCity"       VARCHAR        "ShipRegion"     VARCHAR
 *     "ShipPostalCode" VARCHAR        "ShipCountry"    VARCHAR
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class GroupByKoans extends KoanBase {

    // 1) One number per WHAT? That is the only question GROUP BY asks you.
    //    How many products do we sell in each category? Fill in the column that says
    //    what a group IS — the database then makes one group per distinct value in it,
    //    and runs count(*) once inside each.
    //    (Predict first: 20 products, 8 categories, so expect 8 rows — not 20, and not 1.)
    //    (AND THE "ORDER BY" BELOW IS NOT DECORATION — it is here for the same reason it is
    //     on every query in this lesson. GROUP BY guarantees you one row per group. It does
    //     NOT guarantee what order those rows come back in. They arrive 1..8 here only
    //     because that last line asks for it; take it away and the engine may hand you the
    //     same eight rows in any order it likes — one order today, another tomorrow, with
    //     nothing changed and nothing to tell you why.)
    def "say what the groups are: one row per distinct value"() {
        expect:
        shouldReturn([[1, 3], [2, 3], [3, 2], [4, 3], [5, 3], [6, 2], [7, 2], [8, 2]], '''
            SELECT "CategoryID", count(*) FROM "Products"
            GROUP BY ___
            ORDER BY "CategoryID"
        ''')
    }

    // 2) The aggregate runs once PER GROUP, not once per table. How many units are on
    //    the shelf in each category? Fill in the function that adds a column up.
    //    (Note how this differs from count: count(*) asks how MANY rows are in the pile,
    //     this asks what the pile ADDS UP TO. Category 8 has only 2 products and the
    //     most stock of all.)
    def "the aggregate runs once per group"() {
        expect:
        shouldReturn([[1, 76], [2, 105], [3, 35], [4, 41], [5, 95], [6, 29], [7, 50], [8, 154]], '''
            SELECT "CategoryID", ___("UnitsInStock") FROM "Products"
            GROUP BY "CategoryID"
            ORDER BY "CategoryID"
        ''')
    }

    // 3) The rows GROUP BY hands back are rows like any other, so everything you learned
    //    about sorting still works on them — including sorting by the number you just
    //    computed. Which three categories hold the most stock? Fill in what to sort by.
    //    (You gave the total a name on the line above. Use it.)
    //    (WHY THIS ONE CANNOT BE ANSWERED WITHOUT THE SORT, and koan 1 said why: GROUP BY
    //     promises no order at all. So "LIMIT 3" on its own does not mean "the top three" —
    //     it means "three of them, whichever three the engine reached first". The ORDER BY
    //     is what turns a limit into a ranking. A LIMIT without one is a coin toss wearing
    //     a report's clothes.)
    def "rank the groups by the number you just made"() {
        expect:
        shouldReturn([[8, 154], [2, 105], [5, 95]], '''
            SELECT "CategoryID", sum("UnitsInStock") AS "TotalStock" FROM "Products"
            GROUP BY "CategoryID"
            ORDER BY ___ DESC
            LIMIT 3
        ''')
    }

    // 4) A group can give you as many numbers as you like — that is what turns a count
    //    into a REPORT. Here: the cheapest and the dearest thing in each category.
    //    "UnitPrice" is not in the GROUP BY, so it cannot be shown raw — every column
    //    you SELECT is either grouped or wrapped in an aggregate. Fill in the aggregate
    //    that returns the DEAREST price in each group.
    //    (Checkable fact: category 6 runs from 97.0000 to 123.7900 — our two premium lines.)
    def "more than one number per group"() {
        expect:
        shouldReturn([[1, 4.5000, 19.0000], [2, 10.0000, 22.0000], [3, 12.5000, 17.4500],
                      [4, 12.5000, 34.0000], [5, 7.0000, 38.0000], [6, 97.0000, 123.7900],
                      [7, 23.2500, 30.0000], [8, 18.4000, 31.0000]], '''
            SELECT "CategoryID", min("UnitPrice"), ___("UnitPrice") FROM "Products"
            GROUP BY "CategoryID"
            ORDER BY "CategoryID"
        ''')
    }

    // 5) THE ONE THIS LESSON EXISTS FOR. Somebody asked for the number of products in
    //    each category. A colleague wanted the supplier visible too, hit the "must appear
    //    in the GROUP BY clause" error, added "SupplierID" to the GROUP BY to make the
    //    error go away — and the report has been wrong ever since.
    //    Categories 1 and 2 hold three products each. With "SupplierID" still in the
    //    GROUP BY you get FIVE rows reading 2, 1, 1, 1, 1, because those six products come
    //    from three different suppliers. Fill in the GROUP BY so the report is per category
    //    again, and says 3 and 3.
    //    (THE RULE: the GROUP BY list IS the grain of your report. Every column you add
    //     splits the piles finer. Only put a column there if you want the report split by it.)
    def "the GROUP BY list is the grain of the report"() {
        expect:
        shouldReturn([[1, 3], [2, 3]], '''
            SELECT "CategoryID", count(*) FROM "Products"
            WHERE "CategoryID" IN (1, 2)
            GROUP BY ___
            ORDER BY "CategoryID"
        ''')
    }

    // 6) The same two keys are not a bug — they are the right answer to a DIFFERENT
    //    question. Now we really do want it split: how many products does each supplier
    //    give us within each category? Fill in the second key.
    //    (Same six products as koan 5, same WHERE — five rows this time, and that is
    //     correct here. The query did not change its mind; the question did.)
    def "the same two keys, on purpose"() {
        expect:
        shouldReturn([[1, 1, 2], [1, 2, 1], [2, 1, 1], [2, 2, 1], [2, 4, 1]], '''
            SELECT "CategoryID", "SupplierID", count(*) FROM "Products"
            WHERE "CategoryID" IN (1, 2)
            GROUP BY "CategoryID", ___
            ORDER BY "CategoryID", "SupplierID"
        ''')
    }

    // 7) You can group by anything you can COMPUTE, not just by a column that already
    //    exists — which is how "per month" and "per year" reports are written, since no
    //    table has a "Year" column in it. How many orders did we take in each year?
    //    Fill in the expression that pulls the year out of a date.
    //    (Checkable fact: the orders run from December 2022 to June 2024, so expect three
    //     rows — and 2022 is a stub of a year with only four orders in it.)
    def "group by an expression, not just a column"() {
        expect:
        shouldReturn([[2022, 4], [2023, 48], [2024, 27]], '''
            SELECT EXTRACT(YEAR FROM "OrderDate") AS "Year", count(*) FROM "Orders"
            GROUP BY ___
            ORDER BY "Year"
        ''')
    }

    // 8) WHERE RUNS BEFORE GROUP BY. It throws rows away one at a time, before any pile
    //    exists — so a group whose every row is filtered out does not come back as zero.
    //    It does not come back at all.
    //    Count only the lines we still sell. Fill in the WHERE.
    //    (PREDICT WHAT HAPPENS, this is the whole koan: koan 1 returned eight rows, one
    //     per category. This returns SEVEN. Both of our discontinued products are in
    //     category 6, so category 6 leaves the report entirely — and nothing says so.)
    def "WHERE runs first, and a whole group can disappear"() {
        expect:
        shouldReturn([[1, 3], [2, 3], [3, 2], [4, 3], [5, 3], [7, 2], [8, 2]], '''
            SELECT "CategoryID", count(*) FROM "Products"
            WHERE ___
            GROUP BY "CategoryID"
            ORDER BY "CategoryID"
        ''')
    }

    // 9) Grouped rows tie like any other rows. How many customers do we have in each
    //    country? Germany runs away with it at eleven — and then FIVE countries are level
    //    on two: France, Mexico, Sweden, the UK and Venezuela. A top-four has room for
    //    three of them. Add the second sort key that settles which three, so this list is
    //    the same every run.
    //    (Straight from the sorting lesson, and it applies here unchanged. THE SIZE OF THE
    //     TIE IS THE POINT: five countries level on two, three places left, so ORDER BY on
    //     the count alone leaves the engine free to hand back any three of them in any
    //     order — a different three on a different day, with nothing to tell you why.)
    def "grouped rows tie too, so break the tie"() {
        expect:
        shouldReturn([["Germany", 11], ["France", 2], ["Mexico", 2], ["Sweden", 2]], '''
            SELECT "Country", count(*) AS "CustomerCount" FROM "Customers"
            GROUP BY "Country"
            ORDER BY "CustomerCount" DESC, ___
            LIMIT 4
        ''')
    }

    // 10) The whole query — no scaffolding, and everything above it in one go.
    //     THE QUESTION: which three suppliers do we hold the most stock from?
    //       · only lines we still sell     -> "Discontinued" is false
    //       · one row per supplier         -> group by "SupplierID"
    //       · add up what is on the shelf  -> sum("UnitsInStock"), named "TotalStock"
    //       · most stock first             -> sort by that total, biggest first
    //       · three of them                -> and return "SupplierID" and the total
    //     Five clauses, in the only order SQL accepts them: SELECT, FROM, WHERE,
    //     GROUP BY, ORDER BY, LIMIT. ("Discontinued" is a BOOLEAN — compare it to false,
    //     don't quote it.)
    //     (THE FILTER DECIDES THE ANSWER, so do not skip it: leave it out and supplier 4
    //      tops the list with 172. Its discontinued line alone is holding 29 units.)
    def "write the whole query: where our stock is sitting"() {
        expect:
        shouldReturn([[3, 144], [4, 143], [6, 76]], '''
            ___
        ''')
    }
}
