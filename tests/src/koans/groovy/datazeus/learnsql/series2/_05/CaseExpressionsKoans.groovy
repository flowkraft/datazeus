package datazeus.learnsql.series2._05

import datazeus._internal.NorthwindCoKoanBase
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
 * ── THE SHAPE, AND ITS RULES ────────────────────────────────────────────────
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
 *   3. A SHARE IS 100.0 * SUM(CASE ...) / count(*). The decimal point, and the multiplying
 *      before the dividing, are what stop PostgreSQL from throwing the fraction away.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, asked DIFFERENT QUESTIONS. The lesson labels ORDERS by
 * freight, counts them per channel by status, and per courier. Here you label PRODUCTS by
 * price, stock and whether they are still sold, ORDER LINES by discount, the ship-to line of
 * the year's last orders, and the returns in the stock ledger.
 *
 * ELEVEN KOANS, EASIEST FIRST:
 *   1    a label for every product: finish the CASE
 *   2    the first true WHEN wins: write the tests in the right order
 *   3    no ELSE means NULL: count only what the CASE labelled
 *   4    a NULL fails not-equal too: give the rest a label
 *   5    COALESCE is a CASE: write it the long way
 *   6    CASE inside SUM: a column for one status
 *   7    CASE inside SUM: money with and without a discount
 *   8    count(CASE ... END): the missing ELSE, on purpose
 *   9    a share of the lines: 100.0 before you divide
 *  10    write the whole query: price bands as columns, per category
 *  11    write the whole query: the return rate of each category
 *
 * These run on DuckDB, on Northwind Company — the same rows the Seed Data tab installs into
 * the PostgreSQL in CloudBeaver — and every one returns the SAME answer there, as long as you
 * write the shares with 100.0, which is the one place the two engines part company (koan 9
 * says how). KEEP THE DOUBLE QUOTES on every name, spelled as the schema spells them: DuckDB
 * forgives a wrong capital letter and PostgreSQL does not.
 *
 * ── RELEVANT SCHEMA (Northwind Company, schema northwind_co_s) ──────────────
 *
 *   "Products" — 80 rows, 11 columns, ten products in each category.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitCost"        DECIMAL(19,4)  "UnitsInStock"    SMALLINT
 *     "UnitsOnOrder"    SMALLINT       "ReorderLevel"    SMALLINT
 *     "Discontinued"    BOOLEAN
 *   "ReorderLevel" is the stock level at which the product should be ordered again; six
 *   products are discontinued (no longer sold, so never reordered). No product has a price of
 *   exactly 15 or 30.
 *
 *   "Categories" — 8 rows. "CategoryID" INTEGER, "CategoryName" VARCHAR, "Description" VARCHAR.
 *
 *   "Order Details" — 25233 rows, 5 columns. ONE ROW PER THING BOUGHT.
 *     "OrderID"         INTEGER        "ProductID"       INTEGER
 *     "UnitPrice"       DECIMAL(19,4)  "Quantity"        SMALLINT
 *     "Discount"        DECIMAL(8,4)   — 0, 0.05, 0.1 or 0.15; 0 means full price
 *
 *   "Orders" — 10000 rows, 2020-01-01 to 2024-12-31. The columns these koans use:
 *     "OrderID" INTEGER, "OrderDate" TIMESTAMP, "ShipCity" VARCHAR,
 *     "ShipRegion" VARCHAR (empty for most countries), "ShipCountry" VARCHAR.
 *
 *   "StockMovements" — 29264 rows: every time stock goes in or out.
 *     "MovementID" INTEGER, "ProductID" INTEGER, "MovementDate" DATE,
 *     "MovementType" VARCHAR — 'Receipt', 'Sale', 'Return' or 'Adjustment',
 *     "Quantity" INTEGER, "OrderID" INTEGER (set for sales and returns).
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class CaseExpressionsKoans extends NorthwindCoKoanBase {

    // 1) A LABEL FOR EVERY PRODUCT. Under 15 is budget, under 30 is standard, and every
    //    other product is premium. Fill in the keyword that catches every row the two
    //    WHENs above it did not.
    //    (Three bands. Checkable fact: 28 budget products, 27 standard, 25 premium.)
    def "a label for every product: finish the CASE"() {
        expect:
        shouldReturn([["budget", 28], ["standard", 27], ["premium", 25]], '''
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

    // 2) THE FIRST TRUE WHEN WINS. Label the stock: 'discontinued' when the product is no
    //    longer sold, 'reorder' when its stock is at or below the reorder level, 'ok' otherwise.
    //    Write the two WHEN lines. ORDER MATTERS: a discontinued product can ALSO be low on
    //    stock, and nobody reorders a product they have stopped selling — so the wrong order
    //    labels it 'reorder' and puts it on the purchase list.
    //    (Three labels: 6 discontinued, 71 ok, 3 reorder. If you get 5 discontinued and
    //     4 reorder, the stock test came first.)
    def "the first true WHEN wins: write the tests in the right order"() {
        expect:
        shouldReturn([["discontinued", 6], ["ok", 71], ["reorder", 3]], '''
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
    //    (Predict first: 25233 lines in all. How many carry a discount?)
    def "no ELSE means NULL: count only what the CASE labelled"() {
        expect:
        shouldReturn 12559, '''
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
    //    (25233 lines, 12559 discounted, so predict the rest.)
    def "a NULL fails not-equal too: give the rest a label"() {
        expect:
        shouldReturn 12674, '''
            SELECT count(*)
            FROM (SELECT CASE
                           WHEN "Discount" > 0 THEN 'discounted'
                           ___
                         END AS "Label"
                  FROM "Order Details") AS t
            WHERE "Label" <> 'discounted'
        '''
    }

    // 5) COALESCE IS A CASE. The ship-to line of the orders placed on the last day of 2024
    //    shows the region where the address has one, and the country where it does not:
    //    COALESCE("ShipRegion", "ShipCountry"). Write it the long way — fill in the last line
    //    of the CASE.
    //    (Eleven orders, in "OrderID" order. Three of them go to Portland, in the region OR.)
    def "COALESCE is a CASE: write it the long way"() {
        expect:
        shouldReturn([[9990, "Madrid", "Spain"], [9991, "Portland", "OR"], [9992, "Portland", "OR"],
                      [9993, "Oslo", "Norway"], [9994, "Brussels", "Belgium"], [9995, "Guadalajara", "Mexico"],
                      [9996, "Oslo", "Norway"], [9997, "Tampere", "Finland"], [9998, "Brussels", "Belgium"],
                      [9999, "Portland", "OR"], [10000, "Paris", "France"]], '''
            SELECT "OrderID", "ShipCity",
                   CASE
                     WHEN "ShipRegion" IS NULL THEN "ShipCountry"
                     ___
                   END AS "Region or country"
            FROM "Orders"
            WHERE "OrderDate" = DATE '2024-12-31'
            ORDER BY "OrderID"
        ''')
    }

    // 6) CASE INSIDE SUM: A COLUMN FOR ONE STATUS. For each category: how many products, and
    //    how many of them are discontinued — on one row. The CASE turns a discontinued product
    //    into a 1 and every other product into a 0, and SUM adds them up. Fill in what the CASE
    //    returns in each case.
    //    (Eight categories of ten products each. Two of them have nothing discontinued.)
    def "CASE inside SUM: a column for one status"() {
        expect:
        shouldReturn([["Beverages", 10, 1], ["Condiments", 10, 1], ["Confections", 10, 0],
                      ["Dairy Products", 10, 1], ["Grains/Cereals", 10, 1], ["Meat/Poultry", 10, 1],
                      ["Produce", 10, 1], ["Seafood", 10, 0]], '''
            SELECT c."CategoryName",
                   count(*) AS "Products",
                   SUM(CASE WHEN p."Discontinued" = TRUE ___ END) AS "Discontinued"
            FROM "Products" p
            JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
            GROUP BY c."CategoryName"
            ORDER BY c."CategoryName"
        ''')
    }

    // 7) CASE INSIDE SUM, WITH MONEY. Per order year: the money from discounted lines (after
    //    the discount) and the money from full-price lines, side by side. The second column is
    //    written for you. Fill in the test that decides which lines go into the first.
    //    (Five years, 2020 to 2024. Every year about half of each.)
    def "CASE inside SUM: money with and without a discount"() {
        expect:
        shouldReturn([[2020, 1205673.17, 1268466.04],
                      [2021, 1294494.28, 1434204.86],
                      [2022, 1361895.85, 1541464.49],
                      [2023, 1533790.73, 1748469.18],
                      [2024, 1704648.13, 1923924.42]], '''
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

    // 8) count(CASE ... END): THE MISSING ELSE, ON PURPOSE. For the three products sold on the
    //    most order lines: how many lines, and how many of those were discounted. count skips
    //    NULLs, so a CASE with NO ELSE counts exactly the rows its WHEN matched. Fill in the
    //    whole argument to the second count.
    //    (Rustic Granola leads on 1252 lines. In koan 4 the missing ELSE was the bug; here it
    //     is the point.)
    def "count(CASE ... END): the missing ELSE, on purpose"() {
        expect:
        shouldReturn([["Rustic Granola", 1252, 635],
                      ["Island Shrimp", 1160, 595],
                      ["Orchard Salami", 1156, 563]], '''
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

    // 9) A SHARE OF THE LINES. For each category: how many order lines, how many of them were
    //    discounted, and what percentage of the category's lines that is, to one decimal
    //    place. The two counts are written for you. Fill in what goes inside the ROUND.
    //    MULTIPLY BY 100.0 — WITH THE DECIMAL POINT — AND DO IT BEFORE YOU DIVIDE. SUM(...)
    //    and count(*) are both whole numbers, and on PostgreSQL a whole number divided by a
    //    whole number throws the fraction away: every category would come back 0. DuckDB,
    //    which runs these koans, keeps the fraction and would forgive you. CloudBeaver would
    //    not. Write it the way both engines agree on.
    //    (Eight categories. Beverages: 1319 of its 2686 lines were discounted, so 49.1.)
    def "a share of the lines: 100.0 before you divide"() {
        expect:
        shouldReturn([["Beverages", 2686, 1319, 49.1], ["Condiments", 1211, 626, 51.7],
                      ["Confections", 4267, 2102, 49.3], ["Dairy Products", 2426, 1209, 49.8],
                      ["Grains/Cereals", 2592, 1294, 49.9], ["Meat/Poultry", 4787, 2405, 50.2],
                      ["Produce", 3841, 1900, 49.5], ["Seafood", 3423, 1704, 49.8]], '''
            SELECT c."CategoryName",
                   count(*) AS "Lines",
                   SUM(CASE WHEN d."Discount" > 0 THEN 1 ELSE 0 END) AS "Discounted",
                   ROUND(___, 1) AS "Discounted %"
            FROM "Order Details" d
            JOIN "Products" p ON p."ProductID" = d."ProductID"
            JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
            GROUP BY c."CategoryName"
            ORDER BY c."CategoryName"
        ''')
    }

    // 10) The whole query — no scaffolding.
    //     THE QUESTION: for each category, how many products are budget, standard and premium?
    //       · one row per category, by name        -> "Categories"."CategoryName"
    //       · then three columns, in this order: budget (under 15), standard (15 up to but not
    //         including 30), premium (30 and above)
    //       · each column a count of that category's products in that band
    //       · ordered by "CategoryName"
    //     ONE SUM(CASE ...) PER COLUMN — the lesson's column-per-status report, on products.
    //     (Eight rows. Grains/Cereals has no premium product and Meat/Poultry no budget one.)
    def "write the whole query: price bands as columns, per category"() {
        expect:
        shouldReturn([["Beverages", 4, 3, 3], ["Condiments", 4, 3, 3], ["Confections", 2, 3, 5],
                      ["Dairy Products", 4, 1, 5], ["Grains/Cereals", 6, 4, 0], ["Meat/Poultry", 0, 5, 5],
                      ["Produce", 6, 3, 1], ["Seafood", 2, 5, 3]], '''
            ___
        ''')
    }

    // 11) The whole query again.
    //     THE QUESTION: for each category, how many sales and how many returns does the stock
    //     ledger record, and what is the return rate?
    //       · one row per category, by name        -> "Categories"."CategoryName"
    //       · return the name, the sales, the returns, the return rate — in that order
    //       · sales are "StockMovements" rows with "MovementType" 'Sale', returns those with
    //         'Return'; the ledger holds receipts and adjustments too, which are neither
    //       · the return rate is the returns as a percentage OF THE SALES, to one decimal place,
    //         written so CloudBeaver agrees
    //       · ordered by "CategoryName"
    //     WATCH THE BOTTOM OF THE DIVISION: count(*) would count every movement, receipts and
    //     adjustments included. The rate needs the sales column there.
    //     (Eight rows. Confections: 54 returns on 4141 sales, 1.3.)
    def "write the whole query: the return rate of each category"() {
        expect:
        shouldReturn([["Beverages", 2619, 37, 1.4], ["Condiments", 1184, 18, 1.5],
                      ["Confections", 4141, 54, 1.3], ["Dairy Products", 2349, 35, 1.5],
                      ["Grains/Cereals", 2522, 22, 0.9], ["Meat/Poultry", 4651, 54, 1.2],
                      ["Produce", 3731, 52, 1.4], ["Seafood", 3339, 34, 1.0]], '''
            ___
        ''')
    }
}
