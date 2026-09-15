package datazeus.learnsql.series2._15

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 15   Multi-Table JOINs & Grain       ║
 * ║                                          When a Join Silently Drops or   ║
 * ║                                          Multiplies Your Rows            ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series2 _15  (Windows)   ./zeus.sh koans learnsql series2 _15  (macOS/Linux)
 *
 * PREDICT the answer first — if it comes back wrong, the hint shows what your query
 * returned against what it should, so you fix the SQL rather than guess a value.
 *
 * SOME OF THESE ARE DIAGNOSES: the comment shows a query somebody wrote and what it
 * returned. Say why it is wrong before you write the fix.
 *
 * ── ONE ROW IS ONE WHAT? ────────────────────────────────────────────────────
 *
 *   "Orders"          one row = one order          "Freight" lives here
 *   "Order Details"   one row = one order line     "Quantity", "UnitPrice" live here
 *
 *   1. COUNT THE ROWS BEFORE AND AFTER EVERY JOIN. Joining the lines turns one order
 *      into as many rows as it has lines.
 *   2. AN INNER JOIN AFTER A LEFT JOIN DROPS WHAT THE LEFT JOIN KEPT.
 *   3. NARROW OR AGGREGATE BEFORE THE JOIN, in a table in brackets, not after it.
 *   4. SUM A COLUMN ONLY WHERE ONE ROW IS ONE OF ITS OWN. After joining the lines,
 *      an order's freight is copied onto every line. SUM(DISTINCT …) removes repeated
 *      VALUES, not repeated orders — it is right only by luck.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, on DIFFERENT QUESTIONS. The lesson reports products in
 * June 2024 and freight per customer, and its case file the orders with no ship date per rep
 * and courier. Here: categories in other months, freight per customer country, orders per
 * year, and a few orders you write into the query yourself.
 *
 * TEN KOANS, EASIEST FIRST:
 *   1    count the rows after the join: Janet's orders become lines
 *   2    diagnose: an INNER JOIN after a LEFT JOIN drops categories
 *   3    predict: LEFT all the way looks repaired
 *   4    predict: what one more join does to freight per country
 *   5    count what you mean: orders, not lines, per year
 *   6    SUM(DISTINCT) is luck: two orders with the same freight
 *   7    aggregate first: freight and lines per country
 *   8    through the bridge: lines and customers for Chang
 *   9    write the whole query: orders, freight and sales per year
 *  10    write the whole query: products and December 2022 units per category
 *
 * These run on DuckDB, and every one returns the SAME answer on the PostgreSQL in
 * CloudBeaver. KEEP THE DOUBLE QUOTES on every name, spelled as the schema spells them:
 * DuckDB forgives a wrong capital letter and PostgreSQL does not.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 *   "Orders" — 79 rows, ONE PER ORDER. "OrderID" INTEGER, "CustomerID" VARCHAR,
 *     "EmployeeID" INTEGER, "OrderDate" TIMESTAMP (December 2022 to June 2024),
 *     "Freight" DECIMAL(19,4). No two orders have the same freight.
 *
 *   "Order Details" — 193 rows, ONE PER ORDER LINE. "OrderID" INTEGER,
 *     "ProductID" INTEGER, "UnitPrice" DECIMAL(19,4), "Quantity" SMALLINT,
 *     "Discount" DECIMAL(8,4). Every order has 1, 2 or 3 lines.
 *
 *   "Products" — 20 rows. "ProductID" INTEGER, "ProductName" VARCHAR, "CategoryID" INTEGER.
 *   "Categories" — 8 rows. "CategoryID" INTEGER, "CategoryName" VARCHAR.
 *   "Customers" — 25 rows. "CustomerID" VARCHAR, "Country" VARCHAR (10 countries).
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class MultiTableJoinsDuplicateRowsKoans extends KoanBase {

    // 1) COUNT THE ROWS AFTER THE JOIN. Janet (employee 3) took 27 orders. Join each order to
    //    its order lines and count the rows. Fill in the condition that matches a line to its
    //    order.
    //    (Predict before you run it: every order has 1, 2 or 3 lines. Is it still 27?)
    def "count the rows after the join: Janet's orders become lines"() {
        expect:
        shouldReturn 65, '''
            SELECT count(*)
            FROM "Orders" o
            JOIN "Order Details" d ON ___
            WHERE o."EmployeeID" = 3
        '''
    }

    // 2) DIAGNOSE: AN INNER JOIN AFTER A LEFT JOIN. "Every category, and the units it sold in
    //    January 2024." Somebody wrote
    //        FROM "Categories" c
    //        LEFT JOIN "Products" p ...  LEFT JOIN "Order Details" d ...
    //        JOIN "Orders" o ON o."OrderID" = d."OrderID" AND <January 2024>
    //    and got 6 rows out of 8 categories: the plain JOIN threw away the categories with no
    //    January order. Narrow FIRST instead: the brackets add up January's lines per category,
    //    and every category is LEFT JOINed to that. Fill in the WHERE that keeps January 2024,
    //    half-open.
    //    (Eight rows. Two categories sold nothing in January, so they come back empty.)
    def "diagnose: an INNER JOIN after a LEFT JOIN drops categories"() {
        expect:
        shouldReturn([["Beverages", 33], ["Condiments", 33], ["Confections", 14], ["Dairy Products", 37],
                      ["Grains/Cereals", 15], ["Meat/Poultry", null], ["Produce", null], ["Seafood", 32]], '''
            SELECT c."CategoryName", m."Units"
            FROM "Categories" c
            LEFT JOIN (SELECT p."CategoryID", sum(d."Quantity") AS "Units"
                       FROM "Products" p
                       JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                       JOIN "Orders" o ON o."OrderID" = d."OrderID"
                       WHERE ___
                       GROUP BY p."CategoryID") AS m
              ON m."CategoryID" = c."CategoryID"
            ORDER BY c."CategoryName"
        ''')
    }

    // 3) PREDICT: LEFT ALL THE WAY LOOKS REPAIRED. The other "fix" for koan 2 is to make the
    //    last join LEFT too. Fill in the join keyword, and predict the total before you run it.
    //    January 2024's real total is 164 units.
    //    (The January test sits in the ON of a LEFT JOIN: it decides whether an ORDER is
    //     attached, and the order lines are kept either way.)
    def "predict: LEFT all the way looks repaired"() {
        expect:
        shouldReturn 2070, '''
            SELECT sum(d."Quantity")
            FROM "Categories" c
            LEFT JOIN "Products" p ON p."CategoryID" = c."CategoryID"
            LEFT JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            ___ JOIN "Orders" o
              ON o."OrderID" = d."OrderID"
             AND o."OrderDate" >= DATE '2024-01-01'
             AND o."OrderDate" <  DATE '2024-02-01'
        '''
    }

    // 4) PREDICT: WHAT ONE MORE JOIN DOES TO FREIGHT. Over "Orders" alone, the six countries
    //    whose customers paid the most freight are Germany 1841.78, Sweden 410.60, France 347.85,
    //    Venezuela 254.38, Austria 226.15 and Mexico 212.88. Add the order lines to this query —
    //    fill in the join — and predict before you run it: do the numbers only grow, or does
    //    the order of the six change too?
    //    (Every order has 1, 2 or 3 lines, and "Freight" is stored once per order.)
    def "predict: what one more join does to freight per country"() {
        expect:
        shouldReturn([["Germany", 4521.64], ["Sweden", 1037.48], ["France", 880.67],
                      ["Venezuela", 563.22], ["Mexico", 548.52], ["Austria", 527.35]], '''
            SELECT c."Country", SUM(o."Freight") AS "Freight"
            FROM "Customers" c
            JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
            ___
            GROUP BY c."Country"
            ORDER BY "Freight" DESC
            LIMIT 6
        ''')
    }

    // 5) COUNT WHAT YOU MEAN. After joining the order lines, count(*) counts LINES: 10, 120 and
    //    63 in the three years of orders. Fill in the count that gives each year's number of
    //    ORDERS.
    //    (The lesson on GROUP BY counted them over "Orders" alone. Same answer, one more join.)
    def "count what you mean: orders, not lines, per year"() {
        expect:
        shouldReturn([[2022, 4], [2023, 48], [2024, 27]], '''
            SELECT EXTRACT(YEAR FROM o."OrderDate") AS "Year",
                   ___ AS "Orders"
            FROM "Orders" o
            JOIN "Order Details" d ON d."OrderID" = o."OrderID"
            GROUP BY EXTRACT(YEAR FROM o."OrderDate")
            ORDER BY "Year"
        ''')
    }

    // 6) SUM(DISTINCT) IS LUCK. Three orders written into the query — 101 and 102 both cost
    //    32.00, 103 costs 18.40 — after a join that gave each of them two lines.
    //    SUM(DISTINCT "Freight") returns 50.40: it keeps one 32.00 and throws the other ORDER's
    //    freight away. Make ONE ROW PER ORDER first, then sum: fill in the keyword that removes
    //    the repeated lines.
    //    (Predict: 32 + 32 + 18.40.)
    def "SUM(DISTINCT) is luck: two orders with the same freight"() {
        expect:
        shouldReturn 82.40, '''
            SELECT SUM(u."Freight")
            FROM (SELECT ___ "OrderID", "Freight"
                  FROM (VALUES (101, 32.00), (101, 32.00),
                               (102, 32.00), (102, 32.00),
                               (103, 18.40), (103, 18.40)) AS t("OrderID", "Freight")) AS u
        '''
    }

    // 7) AGGREGATE FIRST. Freight and order lines per country, both right, on one row: the
    //    freight is summed over customers and "Orders" alone, the lines are counted over the join,
    //    each in its own brackets, and only then are the two joined on the country. Fill in the
    //    condition that joins the lines inside the second brackets.
    //    (Koan 4's freight was wrong. Predict where Mexico lands now, and look at its lines.)
    def "aggregate first: freight and lines per country"() {
        expect:
        shouldReturn([["Germany", 1841.78, 78], ["Sweden", 410.60, 19], ["France", 347.85, 15],
                      ["Venezuela", 254.38, 14], ["Austria", 226.15, 7], ["Mexico", 212.88, 18]], '''
            SELECT f."Country", f."Freight", l."Lines"
            FROM (SELECT c."Country", SUM(o."Freight") AS "Freight"
                  FROM "Customers" c
                  JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                  GROUP BY c."Country") AS f
            JOIN (SELECT c."Country", count(*) AS "Lines"
                  FROM "Customers" c
                  JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
                  JOIN "Order Details" d ON ___
                  GROUP BY c."Country") AS l
              ON l."Country" = f."Country"
            ORDER BY f."Freight" DESC
            LIMIT 6
        ''')
    }

    // 8) THROUGH THE BRIDGE. "Order Details" joins orders to products, many to many. For Chang
    //    (product 2): how many order lines, and how many different customers? Fill in what the
    //    second count counts.
    //    (Predict: can a customer buy Chang on more than one order?)
    def "through the bridge: lines and customers for Chang"() {
        expect:
        shouldReturn([[13, 12]], '''
            SELECT count(*) AS "Lines",
                   count(DISTINCT ___) AS "Customers"
            FROM "Order Details" d
            JOIN "Orders" o ON o."OrderID" = d."OrderID"
            WHERE d."ProductID" = 2
        ''')
    }

    // 9) The whole query — no scaffolding.
    //    THE QUESTION: for each year, how many orders, how much freight, and how much in sales?
    //      · one row per year of "OrderDate" — EXTRACT(YEAR FROM …) — ordered by the year
    //      · four columns, in this order: year, orders, freight, sales
    //      · freight is summed where one row is one ORDER            -> "Orders" alone
    //      · sales are UnitPrice × Quantity × (1 − Discount), ROUNDed to 2 decimals, summed
    //        where one row is one ORDER LINE                         -> "Orders" JOIN "Order Details"
    //    AGGREGATE EACH IN ITS OWN BRACKETS, THEN JOIN THE TWO ON THE YEAR. If 2023's freight
    //    comes back as 6798.36, you summed it after joining the lines.
    //    (Three rows.)
    def "write the whole query: orders, freight and sales per year"() {
        expect:
        shouldReturn([[2022, 4, 82.18, 1897.53],
                      [2023, 48, 2721.60, 38631.96],
                      [2024, 27, 1184.74, 17623.82]], '''
            ___
        ''')
    }

    // 10) The whole query again.
    //     THE QUESTION: for each category, how many products does it have, and how many units
    //     did it sell in December 2022?
    //       · one row per "Categories"."CategoryName" — ALL EIGHT — ordered by "CategoryName"
    //       · three columns: the name, the products, the December 2022 units
    //       · a category that sold nothing in December 2022 shows an empty units cell
    //       · December 2022, half-open: on or after DATE '2022-12-01', before DATE '2023-01-01'
    //     TWO GRAINS AGAIN: products are counted where one row is one product; units are summed
    //     over order lines. Count the products after joining the lines and Beverages has 30.
    //     (Eight rows, one of them with no December units.)
    def "write the whole query: products and December 2022 units per category"() {
        expect:
        shouldReturn([["Beverages", 3, 5], ["Condiments", 3, 3], ["Confections", 2, 4], ["Dairy Products", 3, 9],
                      ["Grains/Cereals", 3, 5], ["Meat/Poultry", 2, 13], ["Produce", 2, null], ["Seafood", 2, 5]], '''
            ___
        ''')
    }
}
