package datazeus.learnsql.series2._20

import datazeus._internal.NorthwindCoKoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 20   CTEs (WITH)                     ║
 * ║                                          Breaking a Long Query Into      ║
 * ║                                          Named Steps                     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series2 _20  (Windows)   ./zeus.sh koans learnsql series2 _20  (macOS/Linux)
 *
 * SOME OF THESE ARE EQUIVALENCE KOANS. They run a nested query first — one you are
 * given, already correct — and then check that YOUR rewrite with WITH returns exactly
 * the same rows. A rewrite is only a rewrite if the answer did not move.
 *
 * ── THE SHAPE ───────────────────────────────────────────────────────────────
 *
 *     WITH first_step AS (
 *       SELECT …
 *     ),
 *     second_step AS (
 *       SELECT … FROM first_step …      <- a step can read the steps above it
 *     )
 *     SELECT … FROM second_step …;      <- the query that uses them
 *
 *   1. NAME IT, THEN USE THE NAME. A CTE is a subquery with a name, written before the
 *      query that uses it. Use it as often as you like — no copy-pasted brackets.
 *   2. COUNT THE ROWS BEFORE AND AFTER A REWRITE. Tidying a filter out of a step
 *      and into the final WHERE can change the answer.
 *   3. RUN ONE STEP ON ITS OWN: SELECT * FROM that_step.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, on DIFFERENT QUESTIONS. The lesson names the steps
 * of the customers above the average customer, the Dairy products' May units and the
 * sales and freight per customer for one month. Here you name steps over categories,
 * sales reps, order channels, products, couriers, countries and customer segments.
 *
 * TEN KOANS, EASIEST FIRST:
 *   1    name it, then use the name: the three biggest categories
 *   2    use a step twice: categories above the average category
 *   3    one step reads another: sales per order, per sales rep
 *   4    a total of totals: each channel's share of all sales
 *   5    equivalent: rewrite the nested product query with WITH
 *   6    diagnose: the tidy-up that dropped two categories
 *   7    run one step on its own
 *   8    equivalent: rewrite the courier report with WITH
 *   9    write the whole query: countries above the average country
 *  10    write the whole query: customers and August 2023 units per segment
 *
 * These run on DuckDB, on schema northwind_co_s, and every one returns the SAME answer on
 * the PostgreSQL in CloudBeaver once `SET search_path TO northwind_co_s;` has been run. KEEP
 * THE DOUBLE QUOTES on every name, spelled as the schema spells them: DuckDB forgives a wrong
 * capital letter and PostgreSQL does not.
 *
 * ── RELEVANT SCHEMA (Northwind Company) ─────────────────────────────────────
 *
 *   "Orders" — 10000 rows, one per order, 2020-01-01 to 2024-12-31. "OrderID",
 *     "CustomerID", "EmployeeID", "OrderDate" TIMESTAMP, "ShipVia" (-> "Shippers"."ShipperID"),
 *     "Freight", "Channel" ('Sales rep', 'Web', 'Phone' or 'EDI').
 *   "Order Details" — 25233 rows, one per order line. "OrderID", "ProductID",
 *     "UnitPrice", "Quantity", "Discount". A line's sale is
 *     "UnitPrice" * "Quantity" * (1 - "Discount").
 *   "Products" — 80 rows. "ProductID", "ProductName", "SupplierID", "CategoryID".
 *   "Categories" — 8 rows. "CategoryID", "CategoryName".
 *   "Shippers" — 4 rows. "ShipperID", "CompanyName".
 *   "Employees" — 12 rows. "EmployeeID", "FirstName", "LastName". Two sales reps share a
 *     first name and two share a surname: group by "EmployeeID", never by a name.
 *   "Customers" — 120 rows. "CustomerID", "CompanyName", "Country" (21 countries),
 *     "Segment" ('Restaurant', 'Retail' or 'Wholesale').
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class CtesKoans extends NorthwindCoKoanBase {

    // 1) NAME IT, THEN USE THE NAME. The WITH clause builds a step called category_totals: one
    //    row per category, with its total sales. Fill in where the final SELECT reads from.
    //    (Three rows, biggest first.)
    def "name it, then use the name: the three biggest categories"() {
        expect:
        shouldReturn([["Meat/Poultry", 3077564.58], ["Confections", 2593131.62], ["Produce", 2232360.64]], '''
            WITH category_totals AS (
              SELECT c."CategoryName",
                     ROUND(SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")), 2) AS "Total"
              FROM "Categories" c
              JOIN "Products" p ON p."CategoryID" = c."CategoryID"
              JOIN "Order Details" d ON d."ProductID" = p."ProductID"
              GROUP BY c."CategoryName"
            )
            SELECT "CategoryName", "Total"
            FROM ___
            ORDER BY "Total" DESC
            LIMIT 3
        ''')
    }

    // 2) USE A STEP TWICE. The same step feeds the list AND the average it is compared with —
    //    no second copy of the brackets. Fill in the comparison's right-hand side: the average
    //    "Total" of the step.
    //    (Predict: the average category is 1877128.89. Koan 1 showed you the top three.)
    def "use a step twice: categories above the average category"() {
        expect:
        shouldReturn([["Meat/Poultry", 3077564.58], ["Confections", 2593131.62], ["Produce", 2232360.64],
                      ["Seafood", 2027789.62]], '''
            WITH category_totals AS (
              SELECT c."CategoryName",
                     SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
              FROM "Categories" c
              JOIN "Products" p ON p."CategoryID" = c."CategoryID"
              JOIN "Order Details" d ON d."ProductID" = p."ProductID"
              GROUP BY c."CategoryName"
            )
            SELECT "CategoryName", ROUND("Total", 2) AS "Total"
            FROM category_totals
            WHERE "Total" > ___
            ORDER BY "Total" DESC
        ''')
    }

    // 3) ONE STEP READS ANOTHER. rep_totals has one row per sales rep, with their orders and
    //    their sales. The second step, per_order, works out the sales per order FROM THE FIRST
    //    STEP. Fill in what per_order reads from.
    //    (The three best averages. Nine reps take orders, and they are close.)
    def "one step reads another: sales per order, per sales rep"() {
        expect:
        shouldReturn([["Umberto", "Jansen", 1523.30], ["Yara", "Schmidt", 1518.62], ["Lukas", "Young", 1508.97]], '''
            WITH rep_totals AS (
              SELECT o."EmployeeID",
                     count(DISTINCT o."OrderID") AS "Orders",
                     SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Sales"
              FROM "Orders" o
              JOIN "Order Details" d ON d."OrderID" = o."OrderID"
              GROUP BY o."EmployeeID"
            ),
            per_order AS (
              SELECT "EmployeeID", ROUND(CAST("Sales" / "Orders" AS DECIMAL(12,4)), 2) AS "Per order"
              FROM ___
            )
            SELECT e."FirstName", e."LastName", po."Per order"
            FROM per_order po
            JOIN "Employees" e ON e."EmployeeID" = po."EmployeeID"
            ORDER BY po."Per order" DESC
            LIMIT 3
        ''')
    }

    // 4) A TOTAL OF TOTALS. channel_sales has one row per order channel. all_sales adds those
    //    rows up into one number. Fill in what all_sales reads from, so the final SELECT can show
    //    each channel's share of everything sold, as a percentage.
    //    (Four rows. The sales reps bring in more than a third.)
    def "a total of totals: each channel's share of all sales"() {
        expect:
        shouldReturn([["Sales rep", 37.5], ["Web", 27.5], ["Phone", 25.0], ["EDI", 10.0]], '''
            WITH channel_sales AS (
              SELECT o."Channel",
                     SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Sales"
              FROM "Orders" o
              JOIN "Order Details" d ON d."OrderID" = o."OrderID"
              GROUP BY o."Channel"
            ),
            all_sales AS (
              SELECT SUM("Sales") AS "All"
              FROM ___
            )
            SELECT "Channel",
                   ROUND(CAST(100 * "Sales" / (SELECT "All" FROM all_sales) AS DECIMAL(12,4)), 1) AS "Share"
            FROM channel_sales
            ORDER BY "Share" DESC
        ''')
    }

    // 5) EQUIVALENT: REWRITE THE NESTED QUERY WITH WITH. The first query below is the nested
    //    version, and it is right: the products that sold more units in 2024 than the average
    //    product. Rewrite it — fill in the body of product_units so that it returns one row per
    //    "ProductID" with its 2024 units as "Units". The koan checks that your version returns
    //    EXACTLY the rows the nested one does.
    //    (Look inside the nested version's brackets: the step is already there, written twice.)
    def "equivalent: rewrite the nested product query with WITH"() {
        given: "the nested version, which is correct"
        def nested = rows('''
            SELECT p."ProductName", t."Units"
            FROM (SELECT d."ProductID", sum(d."Quantity") AS "Units"
                  FROM "Order Details" d
                  JOIN "Orders" o ON o."OrderID" = d."OrderID"
                  WHERE o."OrderDate" >= DATE '2024-01-01' AND o."OrderDate" < DATE '2025-01-01'
                  GROUP BY d."ProductID") AS t
            JOIN "Products" p ON p."ProductID" = t."ProductID"
            WHERE t."Units" > (SELECT AVG(t2."Units")
                               FROM (SELECT d."ProductID", sum(d."Quantity") AS "Units"
                                     FROM "Order Details" d
                                     JOIN "Orders" o ON o."OrderID" = d."OrderID"
                                     WHERE o."OrderDate" >= DATE '2024-01-01' AND o."OrderDate" < DATE '2025-01-01'
                                     GROUP BY d."ProductID") AS t2)
            ORDER BY t."Units" DESC, p."ProductName"
        ''')

        expect: "your rewrite returns the same rows"
        shouldReturn(nested, '''
            WITH product_units AS (
              ___
            )
            SELECT p."ProductName", u."Units"
            FROM product_units u
            JOIN "Products" p ON p."ProductID" = u."ProductID"
            WHERE u."Units" > (SELECT AVG("Units") FROM product_units)
            ORDER BY u."Units" DESC, p."ProductName"
        ''')
    }

    // 6) DIAGNOSE: THE TIDY-UP THAT DROPPED TWO CATEGORIES. "Every category and its units on
    //    18 February 2023." Somebody tidied the query: one step with every order line and its
    //    date, and the day's test moved to the final WHERE. It returned 6 rows out of 8 — a WHERE
    //    on a LEFT JOINed column throws away the categories with no line that day, and the unit
    //    totals all still looked right. Put the day's test back INSIDE the step, where it narrows
    //    the lines before the join: fill in the step's WHERE, half-open ("OrderDate" is a
    //    TIMESTAMP), with DATE '…' literals.
    //    (Eight rows. Two categories sold nothing that day.)
    def "diagnose: the tidy-up that dropped two categories"() {
        expect:
        shouldReturn([["Beverages", 123], ["Condiments", null], ["Confections", 29], ["Dairy Products", 46],
                      ["Grains/Cereals", null], ["Meat/Poultry", 6], ["Produce", 103], ["Seafood", 24]], '''
            WITH day_lines AS (
              SELECT p."CategoryID", d."Quantity"
              FROM "Products" p
              JOIN "Order Details" d ON d."ProductID" = p."ProductID"
              JOIN "Orders" o ON o."OrderID" = d."OrderID"
              WHERE ___
            ),
            day_units AS (
              SELECT "CategoryID", sum("Quantity") AS "Units"
              FROM day_lines
              GROUP BY "CategoryID"
            )
            SELECT c."CategoryName", u."Units"
            FROM "Categories" c
            LEFT JOIN day_units u ON u."CategoryID" = c."CategoryID"
            ORDER BY c."CategoryName"
        ''')
    }

    // 7) RUN ONE STEP ON ITS OWN. When a query with steps looks wrong, read the steps one at a
    //    time. Keep a step like koan 6's and look straight into it: how many order lines on
    //    18 February 2023, and how many units? Fill in what the final SELECT reads from.
    //    (One row: lines, then units.)
    def "run one step on its own"() {
        expect:
        shouldReturn([[7, 331]], '''
            WITH day_lines AS (
              SELECT d."OrderID", d."Quantity"
              FROM "Order Details" d
              JOIN "Orders" o ON o."OrderID" = d."OrderID"
              WHERE o."OrderDate" >= DATE '2023-02-18' AND o."OrderDate" < DATE '2023-02-19'
            )
            SELECT count(*) AS "Lines", sum("Quantity") AS "Units"
            FROM ___
        ''')
    }

    // 8) EQUIVALENT: REWRITE THE COURIER REPORT WITH WITH. The nested version below sums the
    //    freight where one row is one order and counts the lines over the join, each in its own
    //    brackets. Rewrite it: fill in the body of the lines step — one row per "ShipVia", with
    //    the number of order lines as "Lines".
    //    (Four rows, and the rewrite must match them exactly.)
    def "equivalent: rewrite the courier report with WITH"() {
        given: "the nested version, which is correct"
        def nested = rows('''
            SELECT s."CompanyName", f."Freight", l."Lines"
            FROM "Shippers" s
            JOIN (SELECT "ShipVia", SUM("Freight") AS "Freight"
                  FROM "Orders" GROUP BY "ShipVia") AS f
              ON f."ShipVia" = s."ShipperID"
            JOIN (SELECT o."ShipVia", count(*) AS "Lines"
                  FROM "Orders" o
                  JOIN "Order Details" d ON d."OrderID" = o."OrderID"
                  GROUP BY o."ShipVia") AS l
              ON l."ShipVia" = s."ShipperID"
            ORDER BY s."CompanyName"
        ''')

        expect: "your rewrite returns the same rows"
        shouldReturn(nested, '''
            WITH freight AS (
              SELECT "ShipVia", SUM("Freight") AS "Freight"
              FROM "Orders"
              GROUP BY "ShipVia"
            ),
            lines AS (
              ___
            )
            SELECT s."CompanyName", f."Freight", l."Lines"
            FROM "Shippers" s
            JOIN freight f ON f."ShipVia" = s."ShipperID"
            JOIN lines l ON l."ShipVia" = s."ShipperID"
            ORDER BY s."CompanyName"
        ''')
    }

    // 9) The whole query — no scaffolding.
    //    THE QUESTION: which customer countries buy more than the average country?
    //      · a step with one row per "Customers"."Country" and its total sales
    //        (UnitPrice × Quantity × (1 − Discount), summed over the order lines)
    //      · return the country and its total ROUNDed to 2 decimals, biggest first
    //      · keep only the countries above the AVERAGE of that step's totals
    //    NAME THE STEP ONCE AND USE IT TWICE, as in koan 2.
    //    (Twenty-one countries in all. How many beat their own average?)
    def "write the whole query: countries above the average country"() {
        expect:
        shouldReturn([["Spain", 1888866.87], ["Germany", 1843311.18], ["USA", 1782708.72], ["Finland", 1658906.50],
                      ["Brazil", 1419735.92], ["Belgium", 1336736.04], ["Norway", 1019667.00], ["Mexico", 765645.83]], '''
            ___
        ''')
    }

    // 10) The whole query again.
    //     THE QUESTION: for each customer segment, how many customers are in it, and how many
    //     units did its customers buy in August 2023?
    //       · one row per "Customers"."Segment", ordered by "Segment"
    //       · three columns: the segment, the customers, the August 2023 units
    //       · August 2023, half-open: on or after DATE '2023-08-01', before DATE '2023-09-01'
    //     TWO STEPS, EACH AT ITS OWN GRAIN: customers counted where one row is one customer,
    //     units summed over the August lines — then join the two. Keep the August test inside
    //     its step (koan 6). Count the customers after joining the lines and you count lines.
    //     (Three rows. Every segment bought something in August 2023.)
    def "write the whole query: customers and August 2023 units per segment"() {
        expect:
        shouldReturn([["Restaurant", 35, 3039], ["Retail", 46, 3906], ["Wholesale", 39, 4893]], '''
            ___
        ''')
    }
}
