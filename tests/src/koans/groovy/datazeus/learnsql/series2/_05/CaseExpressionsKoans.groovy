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
 * If it comes back wrong, the hint shows what your query returned next to what it should.
 * Read the difference — it usually names the mistake — and fix the SQL, not the numbers.
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
 *   3. NULL NEEDS IS NULL. The short form, CASE x WHEN v, tests x = v — and x = NULL is never
 *      true, not even when x is NULL. To catch a NULL, write the long form: WHEN x IS NULL.
 *   4. A WHOLE NUMBER DIVIDED BY A WHOLE NUMBER STAYS WHOLE. SUM(CASE ...) and count(*) are
 *      both whole numbers, so on PostgreSQL 1319 / 2686 is 0: the fraction is thrown away the
 *      moment you divide. These koans divide the same way, so a % that CloudBeaver would show
 *      wrong comes back wrong here too.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, asked DIFFERENT QUESTIONS. The lesson labels ORDERS by freight, sorts the
 * bands heaviest first, counts the orders per channel by status, and per courier. Here you
 * label PRODUCTS by price, stock and whether they are still sold, put the ones to reorder at
 * the top, label ORDER LINES by discount, the ship-to line of the year's last orders, and the
 * returns in the stock ledger.
 *
 * TWELVE KOANS, EASIEST FIRST:
 *   1    a label for every product: finish the CASE
 *   2    the first true WHEN wins: write the tests in the right order
 *   3    no ELSE means NULL: count only what the CASE labelled
 *   4    a NULL fails not-equal too: give the rest a label
 *   5    COALESCE is a CASE: write it the long way
 *   6    the short form never finds NULL: write the WHENs the long way
 *   7    CASE inside SUM: the discontinued beside the total
 *   8    CASE in ORDER BY: the products to reorder first
 *   9    count(CASE ... END): the missing ELSE, on purpose
 *  10    the discounted % of each category
 *  11    write the whole query: price bands as columns, per category
 *  12    write the whole query: the return rate of each category
 *
 * These run on DuckDB, on Northwind Company — the same data DataPallas installs by itself
 * into CloudBeaver's PostgreSQL, in the database northwind_co. Every one returns the SAME
 * answer there, once `SET search_path TO northwind_co_s;` has been run in the editor (this
 * file does that for you), and the koans set DuckDB to divide whole numbers the way
 * PostgreSQL does (rule 4). What DuckDB still forgives and PostgreSQL does not:
 *   · a column's new name (its AS alias) used again in the same SELECT, in WHERE or inside an
 *     ORDER BY CASE — CloudBeaver answers that the column does not exist;
 *   · a name without its double quotes, or with a wrong capital letter. KEEP THE DOUBLE QUOTES
 *     on every name, spelled as the schema below spells them.
 * Either one can turn a koan green here on a query that CloudBeaver refuses.
 *
 * ── RELEVANT SCHEMA (Northwind Company, schema northwind_co_s) ──────────────
 *
 *   "Products" — 80 rows, 12 columns, ten products in each category.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitCost"        DECIMAL(19,4)  "UnitsInStock"    SMALLINT
 *     "UnitsOnOrder"    SMALLINT       "ReorderLevel"    SMALLINT
 *     "Discontinued"    BOOLEAN        "UpdatedAt"       TIMESTAMP
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
 *     "ShipRegion" VARCHAR (NULL for most countries), "ShipCountry" VARCHAR.
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

    // 2) THE FIRST TRUE WHEN WINS. Label the stock: 'reorder' when the stock is at or below the
    //    reorder level, 'discontinued' when the product is no longer sold, 'ok' otherwise.
    //    Write the WHEN lines. One fact decides their order: nobody reorders a product they
    //    have stopped selling, even when its stock is low.
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
    //    the rest, so every full-price line gets NULL. count(*) would say 25233: every line,
    //    labelled or not. Fill in what to count so the answer is only the lines the CASE
    //    actually labelled.
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

    // 4) A NULL FAILS "NOT EQUAL" TOO. Now count the lines that are NOT discounted. Delete the
    //    ___ and run the koans first: the hint shows what this query returns as it stands,
    //    though about half the lines are full price. Then fill in the line that gives those
    //    lines a real label.
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
    //    COALESCE("ShipRegion", "ShipCountry"). Write the same thing as a CASE — fill in its
    //    inside.
    //    (Eleven orders, in "OrderID" order. Three of them go to Portland, in the region OR.
    //     One city comes back SHOUTED: nobody tidied it when it was typed in. Print it as it is
    //     stored — cleaning text is its own lesson, later in the series.)
    def "COALESCE is a CASE: write it the long way"() {
        expect:
        shouldReturn([[9990, "Madrid", "Spain"], [9991, "Portland", "OR"], [9992, "Portland", "OR"],
                      [9993, "Oslo", "Norway"], [9994, "Brussels", "Belgium"], [9995, "Guadalajara", "Mexico"],
                      [9996, "Oslo", "Norway"], [9997, "TAMPERE", "Finland"], [9998, "Brussels", "Belgium"],
                      [9999, "Portland", "OR"], [10000, "Paris", "France"]], '''
            SELECT "OrderID", "ShipCity",
                   CASE
                     ___
                   END AS "Region or country"
            FROM "Orders"
            WHERE "OrderDate" = DATE '2024-12-31'
            ORDER BY "OrderID"
        ''')
    }

    // 6) THE SHORT FORM NEVER FINDS NULL. Someone labelled the ship-to region of the same eleven
    //    orders with the short form:
    //
    //        CASE "ShipRegion" WHEN 'OR' THEN 'Oregon' WHEN NULL THEN '(no region)' END
    //
    //    It runs without an error. Paste everything between its CASE and its END into the blank
    //    and run the koans: the hint shows what it really returns. Then write the WHENs so that
    //    both labels land.
    def "the short form never finds NULL: write the WHENs the long way"() {
        expect:
        shouldReturn([["(no region)", 8], ["Oregon", 3]], '''
            SELECT CASE
                     ___
                   END AS "Region",
                   count(*) AS "Orders"
            FROM "Orders"
            WHERE "OrderDate" = DATE '2024-12-31'
            GROUP BY "Region"
            ORDER BY "Orders" DESC
        ''')
    }

    // 7) CASE INSIDE SUM: THE DISCONTINUED BESIDE THE TOTAL. For each category: how many
    //    products, and how many of them are discontinued — on one row. Fill in the CASE that
    //    SUM adds up.
    //    (Eight categories of ten products each. Two of them have nothing discontinued.)
    def "CASE inside SUM: the discontinued beside the total"() {
        expect:
        shouldReturn([["Beverages", 10, 1], ["Condiments", 10, 1], ["Confections", 10, 0],
                      ["Dairy Products", 10, 1], ["Grains/Cereals", 10, 1], ["Meat/Poultry", 10, 1],
                      ["Produce", 10, 1], ["Seafood", 10, 0]], '''
            SELECT c."CategoryName",
                   count(*) AS "Products",
                   SUM(___) AS "Discontinued"
            FROM "Products" p
            JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
            GROUP BY c."CategoryName"
            ORDER BY c."CategoryName"
        ''')
    }

    // 8) CASE IN ORDER BY: THE PRODUCTS TO REORDER FIRST. The buyer's list of the products still
    //    sold: the ones at or below their reorder level at the top, then all the others — each
    //    group in "ProductName" order. Write the ORDER BY.
    //    (Three products need reordering, so the five rows are those three, then the first two
    //     of the others.)
    def "CASE in ORDER BY: the products to reorder first"() {
        expect:
        shouldReturn([["Coastal Parmesan", 8, 20], ["Orchard Nougat", 3, 15], ["Stone Mussels", 7, 10],
                      ["Alpine Ale", 88, 10], ["Alpine Butter", 23, 20]], '''
            SELECT "ProductName", "UnitsInStock", "ReorderLevel"
            FROM "Products"
            WHERE "Discontinued" = FALSE
            ORDER BY ___
            LIMIT 5
        ''')
    }

    // 9) count(CASE ... END): THE MISSING ELSE, ON PURPOSE. For the three products sold on the
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

    // 10) THE DISCOUNTED % OF EACH CATEGORY. For each category: how many order lines, how many
    //    of them were discounted, and what percentage of the category's lines that is, to one
    //    decimal place. The two counts are written for you. Fill in what goes inside the ROUND.
    //    Rule 4 applies. And "Lines" and "Discounted" are new names made in this same SELECT:
    //    DuckDB would let you use them here, CloudBeaver would say they do not exist.
    //    (Eight categories. Beverages: 1319 of its 2686 lines were discounted.)
    def "the discounted % of each category"() {
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

    // 11) The whole query — no scaffolding.
    //     THE QUESTION: for each category, how many products are budget, standard and premium?
    //       · one row per category, by name        -> "Categories"."CategoryName"
    //       · then three columns, in this order: budget (under 15), standard (15 up to but not
    //         including 30), premium (30 and above)
    //       · each column a count of that category's products in that band
    //       · ordered by "CategoryName"
    //     THE LESSON'S THREE COUNTS SIDE BY SIDE, on products instead of orders.
    //     (Eight rows. Grains/Cereals has no premium product and Meat/Poultry no budget one.)
    def "write the whole query: price bands as columns, per category"() {
        expect:
        shouldReturn([["Beverages", 4, 3, 3], ["Condiments", 4, 3, 3], ["Confections", 2, 3, 5],
                      ["Dairy Products", 4, 1, 5], ["Grains/Cereals", 6, 4, 0], ["Meat/Poultry", 0, 5, 5],
                      ["Produce", 6, 3, 1], ["Seafood", 2, 5, 3]], '''
            ___
        ''')
    }

    // 12) The whole query again.
    //     THE QUESTION: for each category, how many sales and how many returns does the stock
    //     ledger record, and what is the return rate?
    //       · one row per category, by name        -> "Categories"."CategoryName"
    //       · return the name, the sales, the returns, the return rate — in that order
    //       · sales are "StockMovements" rows with "MovementType" 'Sale', returns those with
    //         'Return'; the ledger holds receipts and adjustments too, which are neither
    //       · the return rate is the returns as a percentage OF THE SALES, to one decimal place
    //         (rule 4, and koan 10's note on new names, apply)
    //       · ordered by "CategoryName"
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
