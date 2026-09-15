package datazeus.learnsql.series2._20

import datazeus._internal.KoanBase
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
 * The same ideas, in the same order, on DIFFERENT TABLES. The lesson names the steps of
 * the customer report, the June product report and the days since each customer's last
 * order. Here you name steps over categories, employees, suppliers, shippers and
 * countries.
 *
 * TEN KOANS, EASIEST FIRST:
 *   1    name it, then use the name: the three biggest categories
 *   2    use a step twice: categories above the average category
 *   3    one step reads another: sales per order, per employee
 *   4    a total of totals: each supplier's share of all sales
 *   5    equivalent: rewrite the nested supplier query with WITH
 *   6    diagnose: the tidy-up that dropped two categories
 *   7    run one step on its own
 *   8    equivalent: rewrite the shipper report with WITH
 *   9    write the whole query: countries above the average country
 *  10    write the whole query: products and August 2023 units per supplier
 *
 * These run on DuckDB, and every one returns the SAME answer on the PostgreSQL in
 * CloudBeaver. KEEP THE DOUBLE QUOTES on every name, spelled as the schema spells them:
 * DuckDB forgives a wrong capital letter and PostgreSQL does not.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 *   "Orders" — 79 rows, one per order. "OrderID", "CustomerID", "EmployeeID",
 *     "OrderDate" TIMESTAMP, "ShipVia" (-> "Shippers"."ShipperID"), "Freight".
 *   "Order Details" — 193 rows, one per order line. "OrderID", "ProductID",
 *     "UnitPrice", "Quantity", "Discount". A line's sale is
 *     "UnitPrice" * "Quantity" * (1 - "Discount").
 *   "Products" — 20 rows. "ProductID", "ProductName", "SupplierID", "CategoryID".
 *   "Categories" — 8 rows. "CategoryID", "CategoryName".
 *   "Suppliers" — 6 rows. "SupplierID", "CompanyName".
 *   "Shippers" — 3 rows. "ShipperID", "CompanyName".
 *   "Employees" — 3 rows. "EmployeeID", "FirstName".
 *   "Customers" — 25 rows. "CustomerID", "CompanyName", "Country".
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class CtesKoans extends KoanBase {

    // 1) NAME IT, THEN USE THE NAME. The WITH clause builds a step called category_totals: one
    //    row per category, with its total sales. Fill in where the final SELECT reads from.
    //    (Three rows. One category sells more than three times the next.)
    def "name it, then use the name: the three biggest categories"() {
        expect:
        shouldReturn([["Meat/Poultry", 23331.67], ["Seafood", 6634.77], ["Grains/Cereals", 6360.78]], '''
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
    //    (Predict: the average category is 7269.16. Koan 1 showed you the top three.)
    def "use a step twice: categories above the average category"() {
        expect:
        shouldReturn([["Meat/Poultry", 23331.67]], '''
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

    // 3) ONE STEP READS ANOTHER. employee_totals has one row per employee, with their orders
    //    and their sales. The second step, per_order, works out the sales per order FROM THE
    //    FIRST STEP. Fill in what per_order reads from.
    //    (Three rows, best average order first.)
    def "one step reads another: sales per order, per employee"() {
        expect:
        shouldReturn([["Andrew", 836.64], ["Nancy", 693.53], ["Janet", 690.93]], '''
            WITH employee_totals AS (
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
            SELECT e."FirstName", po."Per order"
            FROM per_order po
            JOIN "Employees" e ON e."EmployeeID" = po."EmployeeID"
            ORDER BY po."Per order" DESC
        ''')
    }

    // 4) A TOTAL OF TOTALS. supplier_sales has one row per supplier. all_sales adds those rows
    //    up into one number. Fill in what all_sales reads from, so the final SELECT can show each
    //    supplier's share of everything sold, as a percentage.
    //    (Six rows. Two suppliers account for more than 60% between them.)
    def "a total of totals: each supplier's share of all sales"() {
        expect:
        shouldReturn([["Pavlova Ltd", 31.8], ["Tokyo Traders", 30.2], ["Pasta Buttini s.r.l.", 15.9],
                      ["Grandma Kellys Homestead", 9.5], ["Exotic Liquids", 7.7], ["New Orleans Cajun Delights", 4.9]], '''
            WITH supplier_sales AS (
              SELECT s."CompanyName",
                     SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Sales"
              FROM "Suppliers" s
              JOIN "Products" p ON p."SupplierID" = s."SupplierID"
              JOIN "Order Details" d ON d."ProductID" = p."ProductID"
              GROUP BY s."CompanyName"
            ),
            all_sales AS (
              SELECT SUM("Sales") AS "All"
              FROM ___
            )
            SELECT "CompanyName",
                   ROUND(CAST(100 * "Sales" / (SELECT "All" FROM all_sales) AS DECIMAL(12,4)), 1) AS "Share"
            FROM supplier_sales
            ORDER BY "Share" DESC
        ''')
    }

    // 5) EQUIVALENT: REWRITE THE NESTED QUERY WITH WITH. The first query below is the nested
    //    version, and it is right: the suppliers whose sales beat the average supplier. Rewrite
    //    it — fill in the body of supplier_totals so that it returns one row per "SupplierID"
    //    with its sales as "Total". The koan checks that your version returns EXACTLY the rows
    //    the nested one does.
    //    (Two rows. Look inside the nested version's brackets: the step is already there.)
    def "equivalent: rewrite the nested supplier query with WITH"() {
        given: "the nested version, which is correct"
        def nested = rows('''
            SELECT s."CompanyName", ROUND(t."Total", 2) AS "Total"
            FROM (SELECT p."SupplierID",
                         SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                  FROM "Products" p
                  JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                  GROUP BY p."SupplierID") AS t
            JOIN "Suppliers" s ON s."SupplierID" = t."SupplierID"
            WHERE t."Total" > (SELECT AVG(t2."Total")
                               FROM (SELECT p."SupplierID",
                                            SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS "Total"
                                     FROM "Products" p
                                     JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                                     GROUP BY p."SupplierID") AS t2)
            ORDER BY "Total" DESC
        ''')

        expect: "your rewrite returns the same rows"
        shouldReturn(nested, '''
            WITH supplier_totals AS (
              ___
            )
            SELECT s."CompanyName", ROUND(t."Total", 2) AS "Total"
            FROM supplier_totals t
            JOIN "Suppliers" s ON s."SupplierID" = t."SupplierID"
            WHERE t."Total" > (SELECT AVG("Total") FROM supplier_totals)
            ORDER BY "Total" DESC
        ''')
    }

    // 6) DIAGNOSE: THE TIDY-UP THAT DROPPED TWO CATEGORIES. "Every category and its units in
    //    August 2023." Somebody tidied the query: one step with every order line and its date,
    //    and the August test moved to the final WHERE. It returned 6 rows out of 8 — a WHERE on a
    //    LEFT JOINed column throws away the categories with no August line, and the unit totals
    //    all still looked right. Put the August test back INSIDE the step, where it narrows the
    //    lines before the join: fill in the step's WHERE, half-open.
    //    (Eight rows. Two categories sold nothing in August 2023.)
    def "diagnose: the tidy-up that dropped two categories"() {
        expect:
        shouldReturn([["Beverages", 33], ["Condiments", 33], ["Confections", 14], ["Dairy Products", 37],
                      ["Grains/Cereals", 15], ["Meat/Poultry", null], ["Produce", null], ["Seafood", 32]], '''
            WITH august_lines AS (
              SELECT p."CategoryID", d."Quantity"
              FROM "Products" p
              JOIN "Order Details" d ON d."ProductID" = p."ProductID"
              JOIN "Orders" o ON o."OrderID" = d."OrderID"
              WHERE ___
            ),
            august_units AS (
              SELECT "CategoryID", sum("Quantity") AS "Units"
              FROM august_lines
              GROUP BY "CategoryID"
            )
            SELECT c."CategoryName", u."Units"
            FROM "Categories" c
            LEFT JOIN august_units u ON u."CategoryID" = c."CategoryID"
            ORDER BY c."CategoryName"
        ''')
    }

    // 7) RUN ONE STEP ON ITS OWN. When a query with steps looks wrong, read the steps one at a
    //    time. Keep the step from koan 6 and look straight into it: how many August 2023 lines,
    //    and how many units? Fill in what the final SELECT reads from.
    //    (One row: lines, then units.)
    def "run one step on its own"() {
        expect:
        shouldReturn([[10, 164]], '''
            WITH august_lines AS (
              SELECT d."OrderID", d."Quantity"
              FROM "Order Details" d
              JOIN "Orders" o ON o."OrderID" = d."OrderID"
              WHERE o."OrderDate" >= '2023-08-01' AND o."OrderDate" < '2023-09-01'
            )
            SELECT count(*) AS "Lines", sum("Quantity") AS "Units"
            FROM ___
        ''')
    }

    // 8) EQUIVALENT: REWRITE THE SHIPPER REPORT WITH WITH. The nested version below sums the
    //    freight where one row is one order and counts the lines over the join, each in its own
    //    brackets. Rewrite it: fill in the body of the lines step — one row per "ShipVia", with
    //    the number of order lines as "Lines".
    //    (Three rows, and the rewrite must match them exactly.)
    def "equivalent: rewrite the shipper report with WITH"() {
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
    //    (Ten countries in all. How many beat their own average?)
    def "write the whole query: countries above the average country"() {
        expect:
        shouldReturn([["Germany", 27256.20]], '''
            ___
        ''')
    }

    // 10) The whole query again.
    //     THE QUESTION: for each supplier, how many products do they supply, and how many units
    //     of them sold in August 2023?
    //       · one row per "Suppliers"."CompanyName", ordered by "CompanyName"
    //       · three columns: the name, the products, the August 2023 units
    //       · August 2023, half-open: on or after 2023-08-01, before 2023-09-01
    //     TWO STEPS, EACH AT ITS OWN GRAIN: products counted where one row is one product, units
    //     summed over the August lines — then join both to the suppliers. Keep the August test
    //     inside its step (koan 6).
    //     (Six rows. Every supplier sold something in August 2023.)
    def "write the whole query: products and August 2023 units per supplier"() {
        expect:
        shouldReturn([["Exotic Liquids", 3, 49], ["Grandma Kellys Homestead", 3, 17], ["New Orleans Cajun Delights", 2, 17],
                      ["Pasta Buttini s.r.l.", 4, 52], ["Pavlova Ltd", 3, 14], ["Tokyo Traders", 5, 15]], '''
            ___
        ''')
    }
}
