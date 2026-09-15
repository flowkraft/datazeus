package datazeus.learnsql.series2._05

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 05   CASE                            ║
 * ║                                          SQL's If/Then for Labels &      ║
 * ║                                          Buckets                         ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series2 _05  (Windows)   ./zeus.sh koans learnsql series2 _05  (macOS/Linux)
 *
 * PREDICT the answer first — if it comes back wrong, the hint shows what your query
 * returned against what it should, so you fix the SQL rather than guess a value.
 *
 * ── THE SHAPE, AND ITS TWO RULES ────────────────────────────────────────────
 *
 *     CASE
 *       WHEN <test> THEN <value>
 *       WHEN <test> THEN <value>
 *       ELSE <value>
 *     END
 *
 *   1. THE FIRST TRUE WHEN WINS. CASE reads top to bottom and stops at the first test
 *      that is true, so a wide test written above a narrow one swallows it.
 *   2. NO ELSE MEANS NULL. A row that no WHEN matches gets NULL — and a NULL fails every
 *      comparison, including "is not equal to". Write the ELSE, unless the NULLs are the
 *      point (count(CASE ... END) counts only what was labelled, on purpose).
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, asked about DIFFERENT TABLES. The lesson labels
 * ORDERS by freight and counts unshipped orders per country. Here you label PRODUCTS by
 * price and stock, ORDER LINES by discount, and suppliers by region.
 *
 * TEN KOANS, EASIEST FIRST:
 *   1    a label for every product: finish the CASE
 *   2    the first true WHEN wins: write the tests in the right order
 *   3    no ELSE means NULL: count only what the CASE labelled
 *   4    a NULL fails "not equal" too: give the rest a label
 *   5    CASE inside SUM: a column for one status
 *   6    CASE inside SUM: money with and without a discount
 *   7    count(CASE ... END): the missing ELSE, on purpose
 *   8    COALESCE is a CASE: write it the long way
 *   9    write the whole query: price bands as columns, per category
 *  10    write the whole query: shipped and waiting, per year
 *
 * These run on DuckDB, and every one returns the SAME answer on the PostgreSQL in
 * CloudBeaver. KEEP THE DOUBLE QUOTES on every name, spelled as the schema spells them:
 * DuckDB forgives a wrong capital letter and PostgreSQL does not.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 *   "Products" — 20 rows, 10 columns.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 *   Two products have "UnitsInStock" = 0. "ReorderLevel" is the stock level at which the
 *   product should be ordered again.
 *
 *   "Categories" — 8 rows. "CategoryID" INTEGER, "CategoryName" VARCHAR,
 *     "Description" VARCHAR, "Picture" BLOB.
 *
 *   "Order Details" — 193 rows, 5 columns. ONE ROW PER THING BOUGHT.
 *     "OrderID"         INTEGER        "ProductID"       INTEGER
 *     "UnitPrice"       DECIMAL(19,4)  "Quantity"        SMALLINT
 *     "Discount"        DECIMAL(8,4)   — 0, 0.05 or 0.1; 0 means full price
 *
 *   "Orders" — 79 rows. The columns these koans use: "OrderID" INTEGER,
 *     "OrderDate" TIMESTAMP, "ShippedDate" TIMESTAMP (27 are empty: not shipped yet).
 *
 *   "Suppliers" — 6 rows. "SupplierID" INTEGER, "CompanyName" VARCHAR, "Region" VARCHAR
 *     (empty for 3 of them), and ten address and contact columns these koans do not need.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class CaseExpressionsKoans extends KoanBase {

    // 1) A LABEL FOR EVERY PRODUCT. Under 15 is budget, under 30 is standard, and every
    //    other product is premium. Fill in the keyword that catches every row the two
    //    WHENs above it did not.
    //    (Three bands. Checkable fact: 5 budget products, 9 standard, 6 premium.)
    def "a label for every product: finish the CASE"() {
        expect:
        shouldReturn([["budget", 5], ["standard", 9], ["premium", 6]], '''
            SELECT CASE
                     WHEN "UnitPrice" < 15 THEN 'budget'
                     WHEN "UnitPrice" < 30 THEN 'standard'
                     ___ 'premium'
                   END AS "Band",
                   count(*) AS "Products"
            FROM "Products"
            GROUP BY "Band"
            ORDER BY min("UnitPrice")
        ''')
    }

    // 2) THE FIRST TRUE WHEN WINS. Label the stock: 'out of stock' when there is none at
    //    all, 'reorder' when the stock is at or below the reorder level, 'ok' otherwise.
    //    Write the two WHEN lines. ORDER MATTERS: a product with no stock is ALSO at or
    //    below its reorder level, so the wrong order labels both empty shelves 'reorder'
    //    and 'out of stock' disappears from the result.
    //    (Three labels: 13 ok, 2 out of stock, 5 reorder. If you get 13 ok and 7 reorder,
    //     the wide test came first.)
    def "the first true WHEN wins: write the tests in the right order"() {
        expect:
        shouldReturn([["ok", 13], ["out of stock", 2], ["reorder", 5]], '''
            SELECT CASE
                     ___
                     ELSE 'ok'
                   END AS "Stock",
                   count(*) AS "Products"
            FROM "Products"
            GROUP BY "Stock"
            ORDER BY "Stock"
        ''')
    }

    // 3) NO ELSE MEANS NULL. This CASE labels discounted order lines and says nothing about
    //    the rest, so every full-price line gets NULL. count(*) counts rows; count of a
    //    column skips the NULLs. Fill in what to count so the answer is the number of lines
    //    the CASE actually labelled.
    //    (Predict first: 193 lines in all. How many carry a discount?)
    def "no ELSE means NULL: count only what the CASE labelled"() {
        expect:
        shouldReturn 113, '''
            SELECT count(___)
            FROM (SELECT CASE
                           WHEN "Discount" > 0 THEN 'discounted'
                         END AS "Label"
                  FROM "Order Details") AS t
        '''
    }

    // 4) A NULL FAILS "NOT EQUAL" TOO. Now count the lines that are NOT discounted. Without an
    //    ELSE their label is NULL, NULL <> 'discounted' is unknown rather than true, and the
    //    WHERE throws every one of them away — the answer would be 0. Fill in the line that
    //    gives them a real label.
    //    (193 lines, 113 discounted, so predict the rest.)
    def "a NULL fails not-equal too: give the rest a label"() {
        expect:
        shouldReturn 80, '''
            SELECT count(*)
            FROM (SELECT CASE
                           WHEN "Discount" > 0 THEN 'discounted'
                           ___
                         END AS "Label"
                  FROM "Order Details") AS t
            WHERE "Label" <> 'discounted'
        '''
    }

    // 5) CASE INSIDE SUM: A COLUMN FOR ONE STATUS. For each category: how many products, and
    //    how many of them are out of stock — on one row. The CASE turns an empty shelf into a
    //    1 and every other product into a 0, and SUM adds them up. Fill in what the CASE
    //    returns in each case.
    //    (Eight categories. Only two of them have a product out of stock.)
    def "CASE inside SUM: a column for one status"() {
        expect:
        shouldReturn([["Beverages", 3, 0], ["Condiments", 3, 0], ["Confections", 2, 0],
                      ["Dairy Products", 3, 1], ["Grains/Cereals", 3, 0], ["Meat/Poultry", 2, 1],
                      ["Produce", 2, 0], ["Seafood", 2, 0]], '''
            SELECT c."CategoryName",
                   count(*) AS "Products",
                   SUM(CASE WHEN p."UnitsInStock" = 0 ___ END) AS "Out of stock"
            FROM "Products" p
            JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
            GROUP BY c."CategoryName"
            ORDER BY c."CategoryName"
        ''')
    }

    // 6) CASE INSIDE SUM, WITH MONEY. Per order year: the money from discounted lines and the
    //    money from full-price lines, side by side. The second column is written for you.
    //    Fill in the test that decides which lines go into the first.
    //    (Three years. 2022 is the month of December only, so its numbers are small.)
    def "CASE inside SUM: money with and without a discount"() {
        expect:
        shouldReturn([[2022, 1624.53, 273.00],
                      [2023, 21443.99, 17187.97],
                      [2024, 9229.76, 8394.06]], '''
            SELECT EXTRACT(YEAR FROM o."OrderDate") AS "Year",
                   ROUND(SUM(CASE WHEN ___
                                  THEN d."UnitPrice" * d."Quantity" * (1 - d."Discount")
                                  ELSE 0 END), 2) AS "Discounted",
                   ROUND(SUM(CASE WHEN d."Discount" = 0
                                  THEN d."UnitPrice" * d."Quantity"
                                  ELSE 0 END), 2) AS "Full price"
            FROM "Orders" o
            JOIN "Order Details" d ON d."OrderID" = o."OrderID"
            GROUP BY EXTRACT(YEAR FROM o."OrderDate")
            ORDER BY "Year"
        ''')
    }

    // 7) count(CASE ... END): THE MISSING ELSE, ON PURPOSE. For the three products sold on the
    //    most order lines: how many lines, and how many of those were discounted. count skips
    //    NULLs, so a CASE with NO ELSE counts exactly the rows its WHEN matched. Fill in the
    //    whole argument to the second count.
    //    (Three products tied on 13 lines, in name order. In koan 4 the missing ELSE was the
    //     bug; here it is the point.)
    def "count(CASE ... END): the missing ELSE, on purpose"() {
        expect:
        shouldReturn([["Boston Crab Meat", 13, 8],
                      ["Chang", 13, 7],
                      ["Chef Antons Cajun Seasoning", 13, 8]], '''
            SELECT p."ProductName",
                   count(*) AS "Lines",
                   count(___) AS "Discounted"
            FROM "Products" p
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            GROUP BY p."ProductName"
            ORDER BY "Lines" DESC, p."ProductName"
            LIMIT 3
        ''')
    }

    // 8) COALESCE IS A CASE. COALESCE("Region", '(none)') means: when the region is NULL use
    //    '(none)', otherwise use the region. Write it the long way — fill in the last line of
    //    the CASE.
    //    (Six suppliers, in "SupplierID" order. Three of them have no region.)
    def "COALESCE is a CASE: write it the long way"() {
        expect:
        shouldReturn([["Exotic Liquids", "(none)"], ["New Orleans Cajun Delights", "LA"],
                      ["Grandma Kellys Homestead", "MI"], ["Tokyo Traders", "(none)"],
                      ["Pavlova Ltd", "Victoria"], ["Pasta Buttini s.r.l.", "(none)"]], '''
            SELECT "CompanyName",
                   CASE
                     WHEN "Region" IS NULL THEN '(none)'
                     ___
                   END AS "Region"
            FROM "Suppliers"
            ORDER BY "SupplierID"
        ''')
    }

    // 9) The whole query — no scaffolding.
    //    THE QUESTION: for each category, how many products are budget, standard and premium?
    //      · one row per category, by name        -> "Categories"."CategoryName"
    //      · then three columns, in this order: budget (under 15), standard (15 up to but not
    //        including 30), premium (30 and above)
    //      · each column a count of that category's products in that band
    //      · ordered by "CategoryName"
    //    ONE SUM(CASE ...) PER COLUMN — the lesson's per-country report, on products.
    //    (Eight rows. Meat/Poultry has no budget or standard products at all.)
    def "write the whole query: price bands as columns, per category"() {
        expect:
        shouldReturn([["Beverages", 1, 2, 0], ["Condiments", 1, 2, 0], ["Confections", 1, 1, 0],
                      ["Dairy Products", 1, 1, 1], ["Grains/Cereals", 1, 1, 1], ["Meat/Poultry", 0, 0, 2],
                      ["Produce", 0, 1, 1], ["Seafood", 0, 1, 1]], '''
            ___
        ''')
    }

    // 10) The whole query again.
    //     THE QUESTION: for each year orders were placed, how many orders, how many have
    //     shipped, and how many are still waiting?
    //       · one row per year of "OrderDate"      -> EXTRACT(YEAR FROM "OrderDate")
    //       · return the year, the orders, the shipped ones, the waiting ones — in that order
    //       · a waiting order has no "ShippedDate"
    //       · oldest year first
    //     CHECK IT: shipped plus waiting has to equal the orders on every row.
    //     (Three rows.)
    def "write the whole query: shipped and waiting, per year"() {
        expect:
        shouldReturn([[2022, 4, 2, 2],
                      [2023, 48, 32, 16],
                      [2024, 27, 18, 9]], '''
            ___
        ''')
    }
}
