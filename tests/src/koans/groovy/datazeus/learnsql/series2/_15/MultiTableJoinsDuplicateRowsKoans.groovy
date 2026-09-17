package datazeus.learnsql.series2._15

import datazeus._internal.NorthwindCoKoanBase
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
 *      VALUES, not repeated orders — it is wrong the moment two orders share a freight.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, on DIFFERENT QUESTIONS. The lesson reports the Dairy
 * products in May 2024 and freight per customer, and its case file the shipped orders with
 * no invoice. Here: an employee's orders, every category on one day, freight per customer
 * country, orders per year, a few orders you write into the query yourself, and every
 * employee in 2024.
 *
 * TEN KOANS, EASIEST FIRST:
 *   1    count the rows after the join: Jonas's orders become lines
 *   2    diagnose: an INNER JOIN after a LEFT JOIN drops categories
 *   3    predict: LEFT all the way looks repaired
 *   4    predict: what one more join does to freight per country
 *   5    count what you mean: orders, not lines, per year
 *   6    SUM(DISTINCT) is wrong: two orders with the same freight
 *   7    aggregate first: freight and lines per country
 *   8    through the bridge: lines and customers for Smoked Fudge
 *   9    write the whole query: orders, freight and sales per year
 *  10    write the whole query: every employee's orders and units in 2024
 *
 * These run on DuckDB, on schema northwind_co_s, and every one returns the SAME answer on
 * the PostgreSQL in CloudBeaver once `SET search_path TO northwind_co_s;` has been run. KEEP
 * THE DOUBLE QUOTES on every name, spelled as the schema spells them: DuckDB forgives a wrong
 * capital letter and PostgreSQL does not.
 *
 * ── RELEVANT SCHEMA (Northwind Company) ─────────────────────────────────────
 *
 *   "Orders" — 10000 rows, ONE PER ORDER. "OrderID" INTEGER, "CustomerID" VARCHAR,
 *     "EmployeeID" INTEGER, "OrderDate" TIMESTAMP (2020-01-01 to 2024-12-31),
 *     "ShipVia" INTEGER, "Freight" DECIMAL, "Status" VARCHAR, "Channel" VARCHAR.
 *     Many orders share the same freight value.
 *
 *   "Order Details" — 25233 rows, ONE PER ORDER LINE. "OrderID" INTEGER,
 *     "ProductID" INTEGER, "UnitPrice" DECIMAL, "Quantity" SMALLINT, "Discount" DECIMAL.
 *     Every order has between 1 and 6 lines.
 *
 *   "Products" — 80 rows. "ProductID" INTEGER, "ProductName" VARCHAR, "CategoryID" INTEGER.
 *   "Categories" — 8 rows. "CategoryID" INTEGER, "CategoryName" VARCHAR.
 *   "Customers" — 120 rows. "CustomerID" VARCHAR, "Country" VARCHAR (21 countries).
 *   "Employees" — 12 rows. "EmployeeID" INTEGER, "FirstName" VARCHAR, "LastName" VARCHAR.
 *     The first three (the chief executive and two sales managers) take no orders.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class MultiTableJoinsDuplicateRowsKoans extends NorthwindCoKoanBase {

    // 1) COUNT THE ROWS AFTER THE JOIN. Jonas Novak (employee 10) took 530 orders. Join each
    //    order to its order lines and count the rows. Fill in the condition that matches a line
    //    to its order.
    //    (Predict before you run it: an order has between 1 and 6 lines. Is it still 530?)
    def "count the rows after the join: Jonas's orders become lines"() {
        expect:
        shouldReturn 1321, '''
            SELECT count(*)
            FROM "Orders" o
            JOIN "Order Details" d ON ___
            WHERE o."EmployeeID" = 10
        '''
    }

    // 2) DIAGNOSE: AN INNER JOIN AFTER A LEFT JOIN. "Every category, and the units it sold on
    //    30 December 2024." Somebody wrote
    //        FROM "Categories" c
    //        LEFT JOIN "Products" p ...  LEFT JOIN "Order Details" d ...
    //        JOIN "Orders" o ON o."OrderID" = d."OrderID" AND <30 December 2024>
    //    and got 6 rows out of 8 categories: the plain JOIN threw away the categories with no
    //    order that day. Narrow FIRST instead: the brackets add up that day's lines per category,
    //    and every category is LEFT JOINed to that. Fill in the WHERE that keeps 30 December 2024,
    //    half-open ("OrderDate" is a TIMESTAMP).
    //    (Eight rows. Two categories sold nothing that day, so they come back empty.)
    def "diagnose: an INNER JOIN after a LEFT JOIN drops categories"() {
        expect:
        shouldReturn([["Beverages", 70], ["Condiments", null], ["Confections", 20], ["Dairy Products", 112],
                      ["Grains/Cereals", null], ["Meat/Poultry", 55], ["Produce", 84], ["Seafood", 118]], '''
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
    //    30 December 2024's real total is 459 units.
    //    (The date test sits in the ON of a LEFT JOIN: it decides whether an ORDER is
    //     attached, and the order lines are kept either way.)
    def "predict: LEFT all the way looks repaired"() {
        expect:
        shouldReturn 955168, '''
            SELECT sum(d."Quantity")
            FROM "Categories" c
            LEFT JOIN "Products" p ON p."CategoryID" = c."CategoryID"
            LEFT JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            ___ JOIN "Orders" o
              ON o."OrderID" = d."OrderID"
             AND o."OrderDate" >= DATE '2024-12-30'
             AND o."OrderDate" <  DATE '2024-12-31'
        '''
    }

    // 4) PREDICT: WHAT ONE MORE JOIN DOES TO FREIGHT. Over "Orders" alone, the five countries
    //    whose customers paid the most freight are Spain 66716.89, Germany 65131.99, USA
    //    61982.13, Finland 58056.81 and Brazil 49784.87. Add the order lines to this query —
    //    fill in the join — and predict before you run it: do the numbers only grow, or does
    //    the order of the five change too?
    //    (An order has between 1 and 6 lines, and "Freight" is stored once per order.)
    def "predict: what one more join does to freight per country"() {
        expect:
        shouldReturn([["Germany", 214339.19], ["Spain", 212931.72], ["USA", 200187.00],
                      ["Finland", 189394.18], ["Brazil", 161104.48]], '''
            SELECT c."Country", SUM(o."Freight") AS "Freight"
            FROM "Customers" c
            JOIN "Orders" o ON o."CustomerID" = c."CustomerID"
            ___
            GROUP BY c."Country"
            ORDER BY "Freight" DESC
            LIMIT 5
        ''')
    }

    // 5) COUNT WHAT YOU MEAN. After joining the order lines, count(*) counts LINES: 4228, 4564,
    //    4910, 5516 and 6015 in the five years of orders. Fill in the count that gives each
    //    year's number of ORDERS.
    //    (The lesson on GROUP BY counted them over "Orders" alone. Same answer, one more join.)
    def "count what you mean: orders, not lines, per year"() {
        expect:
        shouldReturn([[2020, 1638], [2021, 1802], [2022, 1982], [2023, 2180], [2024, 2398]], '''
            SELECT EXTRACT(YEAR FROM o."OrderDate") AS "Year",
                   ___ AS "Orders"
            FROM "Orders" o
            JOIN "Order Details" d ON d."OrderID" = o."OrderID"
            GROUP BY EXTRACT(YEAR FROM o."OrderDate")
            ORDER BY "Year"
        ''')
    }

    // 6) SUM(DISTINCT) IS WRONG. Three orders written into the query — 101 and 102 both cost
    //    32.00, 103 costs 18.40 — after a join that gave each of them two lines.
    //    SUM(DISTINCT "Freight") returns 50.40: it keeps one 32.00 and throws the other ORDER's
    //    freight away. Make ONE ROW PER ORDER first, then sum: fill in the keyword that removes
    //    the repeated lines.
    //    (Predict: 32 + 32 + 18.40.)
    def "SUM(DISTINCT) is wrong: two orders with the same freight"() {
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
    //    (Koan 4's freight was wrong. Predict who is first now, and look at the lines column.)
    def "aggregate first: freight and lines per country"() {
        expect:
        shouldReturn([["Spain", 66716.89, 3157], ["Germany", 65131.99, 3039], ["USA", 61982.13, 3003],
                      ["Finland", 58056.81, 2790], ["Brazil", 49784.87, 2424]], '''
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
            LIMIT 5
        ''')
    }

    // 8) THROUGH THE BRIDGE. "Order Details" joins orders to products, many to many. For Smoked
    //    Fudge (product 67): how many order lines, and how many different customers? Fill in
    //    what the second count counts.
    //    (Predict: can a customer buy Smoked Fudge on more than one order?)
    def "through the bridge: lines and customers for Smoked Fudge"() {
        expect:
        shouldReturn([[46, 22]], '''
            SELECT count(*) AS "Lines",
                   count(DISTINCT ___) AS "Customers"
            FROM "Order Details" d
            JOIN "Orders" o ON o."OrderID" = d."OrderID"
            WHERE d."ProductID" = 67
        ''')
    }

    // 9) The whole query — no scaffolding.
    //    THE QUESTION: for each year, how many orders, how much freight, and how much in sales?
    //      · one row per year of "OrderDate" — EXTRACT(YEAR FROM …) — ordered by the year
    //      · four columns, in this order: year, orders, freight, sales
    //      · freight is summed where one row is one ORDER            -> "Orders" alone
    //      · sales are UnitPrice × Quantity × (1 − Discount), summed where one row is one
    //        ORDER LINE and ROUNDed to 2 decimals once, on the SUM   -> "Orders" JOIN "Order Details"
    //    AGGREGATE EACH IN ITS OWN BRACKETS, THEN JOIN THE TWO ON THE YEAR. If 2024's freight
    //    comes back as 414844.39, you summed it after joining the lines.
    //    (Five rows.)
    def "write the whole query: orders, freight and sales per year"() {
        expect:
        shouldReturn([[2020, 1638, 86898.68, 2474139.21],
                      [2021, 1802, 96556.70, 2728699.14],
                      [2022, 1982, 100954.79, 2903360.34],
                      [2023, 2180, 114985.30, 3282259.91],
                      [2024, 2398, 128033.99, 3628572.55]], '''
            ___
        ''')
    }

    // 10) The whole query again.
    //     THE QUESTION: for each employee, how many orders did they take in 2024, and how many
    //     units were on those orders?
    //       · one row per "Employees" row — ALL TWELVE — ordered by "EmployeeID"
    //       · four columns: "EmployeeID", "FirstName", the 2024 orders, the 2024 units
    //       · an employee with no 2024 orders shows two empty cells
    //       · 2024, half-open: on or after DATE '2024-01-01', before DATE '2025-01-01'
    //     TWO GRAINS AGAIN: orders are counted where one row is one order; units are summed over
    //     order lines. Count the orders after joining the lines and Hugo Dubois (12) has 990.
    //     (Twelve rows, three of them empty: the managers take no orders.)
    def "write the whole query: every employee's orders and units in 2024"() {
        expect:
        shouldReturn([[1, "Maya", null, null], [2, "Anna", null, null], [3, "Hugo", null, null],
                      [4, "Ravi", 362, 34218], [5, "Sven", 60, 5849], [6, "Lukas", 191, 17571],
                      [7, "Umberto", 344, 30853], [8, "Lukas", 241, 22109], [9, "Ines", 266, 24593],
                      [10, "Jonas", 189, 16660], [11, "Yara", 366, 32474], [12, "Hugo", 379, 35982]], '''
            ___
        ''')
    }
}
