package datazeus.learnsql.series2._00

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 00   Subqueries                      ║
 * ║                                          Using One Query's Result        ║
 * ║                                          Inside Another                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series2 _00  (Windows)   ./zeus.sh koans learnsql series2 _00  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * ── ONE IDEA, THREE SHAPES ──────────────────────────────────────────────────
 *
 * A subquery is a query in brackets, and the database runs it FIRST. What it hands
 * back decides where it may go:
 *
 *   a VALUE   one row, one column   compare with it:   WHERE x > (SELECT AVG(...) ...)
 *   a LIST    one column, any rows  test membership:   WHERE x IN (SELECT ... )
 *   a TABLE   rows and columns      query it:          FROM (SELECT ... ) AS t
 *
 * Put a list where a value belongs and the query stops with "more than one row returned
 * by a subquery used as an expression" (koan 3 is that mistake, fixed). Every subquery
 * here runs on its own: select the part in brackets and run it alone before you trust
 * the whole.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, asked about DIFFERENT TABLES. The lesson compares
 * products with the average PRICE and customers with the average CUSTOMER. Here you
 * compare orders with the average FREIGHT, and suppliers with the average SUPPLIER —
 * so copying a query across from the video will not work.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1    a value in brackets: freight above the average
 *   2    a value in brackets can be the whole comparison
 *   3    one value only: turn a column of values into ONE
 *   4    a list: IN
 *   5    a join repeats what IN does not
 *   6    a table in FROM, and its grain
 *   7    the average of WHAT: suppliers above the average supplier
 *   8    a subquery beside HAVING
 *   9    write the whole query: products that beat the average product
 *  10    write the whole query: a value inside a list inside a query
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver. KEEP THE DOUBLE QUOTES on every name, spelled exactly as
 * the schema below spells them: DuckDB forgives a missing quote or a wrong capital
 * letter and PostgreSQL does not, so a koan can go green here on a query that
 * CloudBeaver refuses.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The tables these koans use, in full, so you can write a query without leaving this
 * file.
 *
 *   "Orders" — 79 rows, 14 columns. One row per order.
 *     "OrderID"         INTEGER        "CustomerID"      VARCHAR
 *     "EmployeeID"      INTEGER        "OrderDate"       TIMESTAMP
 *     "RequiredDate"    TIMESTAMP      "ShippedDate"     TIMESTAMP (27 are empty)
 *     "ShipVia"         INTEGER        "Freight"         DECIMAL(19,4)
 *     "ShipName"        VARCHAR        "ShipAddress"     VARCHAR
 *     "ShipCity"        VARCHAR        "ShipRegion"      VARCHAR
 *     "ShipPostalCode"  VARCHAR        "ShipCountry"     VARCHAR
 *
 *   "Customers" — 25 rows. The two columns these koans use:
 *     "CustomerID"      VARCHAR (five capital letters, e.g. 'ALFKI')
 *     "CompanyName"     VARCHAR
 *
 *   "Employees" — 3 rows. "EmployeeID" 1 is Nancy, 2 is Andrew, 3 is Janet.
 *
 *   "Suppliers" — 6 rows. "SupplierID" INTEGER, "CompanyName" VARCHAR, and eleven
 *     address and contact columns these koans do not need.
 *
 *   "Products" — 20 rows, 10 columns.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 *
 *   "Categories" — 8 rows. "CategoryID" INTEGER, "CategoryName" VARCHAR,
 *     "Description" VARCHAR, "Picture" BLOB.
 *
 *   "Order Details" — 193 rows, 5 columns. ONE ROW PER THING BOUGHT.
 *     "OrderID"         INTEGER        "ProductID"       INTEGER
 *     "UnitPrice"       DECIMAL(19,4)  "Quantity"        SMALLINT
 *     "Discount"        DECIMAL(8,4)
 *
 * WHAT ONE LINE IS WORTH: `d."UnitPrice" * d."Quantity" * (1 - d."Discount")`.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class SubqueriesKoans extends KoanBase {

    // 1) A VALUE IN BRACKETS. How many orders paid more freight than the average order?
    //    The brackets run first and hand back ONE number; the outer query compares every
    //    order with it, exactly as if you had typed the number in. Fill in the function
    //    that averages a column.
    //    (Predict first: more or fewer than half of the 79? Checkable fact: the average
    //     freight is 50.49 — select the part in brackets and run it alone to see it.)
    def "a value in brackets: freight above the average"() {
        expect:
        shouldReturn 36, '''
            SELECT count(*)
            FROM "Orders"
            WHERE "Freight" > (SELECT ___("Freight") FROM "Orders")
        '''
    }

    // 2) THE BRACKETS CAN BE THE WHOLE RIGHT-HAND SIDE. Which order paid the most freight?
    //    Nobody told you the number, so ask for it inside the comparison. Write the whole
    //    query that goes in the brackets: it must hand back exactly one value.
    //    (One row comes back. If your subquery hands back a list, the database refuses to
    //     compare "=" with it — see koan 3.)
    def "a value in brackets can be the whole comparison"() {
        expect:
        shouldReturn([[72, "HILAA", 98.92]], '''
            SELECT "OrderID", "CustomerID", "Freight"
            FROM "Orders"
            WHERE "Freight" = (___)
        ''')
    }

    // 3) ONE VALUE ONLY. Which orders paid more freight than EVERY order Janet took?
    //    ("EmployeeID" 3.) Written as SELECT "Freight" the brackets hand back twenty-seven
    //    values — one per order of hers — and ">" cannot compare with twenty-seven numbers:
    //    the query stops with "more than one row returned by a subquery used as an
    //    expression". More than every one of them means more than the BIGGEST of them.
    //    Fill in the expression that turns her twenty-seven freights into one.
    //    (Two orders, heaviest first. Neither of them is Janet's.)
    def "one value only: turn a column of values into one"() {
        expect:
        shouldReturn([[72, 98.92],
                      [59, 97.53]], '''
            SELECT "OrderID", "Freight"
            FROM "Orders"
            WHERE "Freight" > (SELECT ___
                               FROM "Orders"
                               WHERE "EmployeeID" = 3)
            ORDER BY "Freight" DESC
        ''')
    }

    // 4) A LIST. Which products has Alfreds Futterkiste ever bought? The brackets hand back
    //    a column of product ids — one for every line Alfreds ever ordered — and the outer
    //    query keeps each product whose id is IN that list. Fill in the keyword that tests
    //    whether a value is one of a list.
    //    (Eight products, by name.)
    def "a list: IN"() {
        expect:
        shouldReturn([["Boston Crab Meat"], ["Chai"], ["Chang"], ["Genen Shouyu"],
                      ["Ikura"], ["Mishi Kobe Niku"], ["Pavlova"], ["Thuringer Rostbratwurst"]], '''
            SELECT "ProductName"
            FROM "Products"
            WHERE "ProductID" ___ (
              SELECT d."ProductID"
              FROM "Order Details" d
              JOIN "Orders" o ON o."OrderID" = d."OrderID"
              WHERE o."CustomerID" = 'ALFKI'
            )
            ORDER BY "ProductName"
        ''')
    }

    // 5) A JOIN REPEATS WHAT IN DOES NOT. The same question as koan 4, written as a join
    //    from the product to its order lines to their orders. Fill in the join condition
    //    that ties each order line to its product.
    //    (Predict first: IN gave eight rows. A join returns one row per MATCH, and Alfreds
    //     bought some of those products more than once — so how many rows now? IN asks a
    //     yes-or-no question per product, which is why it never repeats one.)
    def "a join repeats what IN does not"() {
        expect:
        shouldReturn 11, '''
            SELECT count(*)
            FROM "Products" p
            JOIN "Order Details" d ON ___
            JOIN "Orders" o ON o."OrderID" = d."OrderID"
            WHERE o."CustomerID" = 'ALFKI'
        '''
    }

    // 6) A TABLE IN FROM. What does the AVERAGE SUPPLIER earn us? That needs one total per
    //    supplier first, and then the average of those totals. The brackets are that table
    //    of totals, named t. Fill in the clause that makes the inside return ONE ROW PER
    //    SUPPLIER.
    //    (Without it the inside adds up every line into one total, and the "average" of a
    //     single total is just 58153.31 — the whole business, not a supplier. One row per
    //     WHAT is the question the brackets answer.)
    def "a table in FROM, and its grain"() {
        expect:
        shouldReturn 9692.22, '''
            SELECT ROUND(AVG(t."Total"), 2)
            FROM (SELECT p."SupplierID",
                         SUM(d."UnitPrice" * d."Quantity"
                           * (1 - d."Discount")) AS "Total"
                  FROM "Products" p
                  JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                  ___) AS t
        '''
    }

    // 7) THE AVERAGE OF WHAT. Which suppliers earn us more than the average SUPPLIER?
    //    Each supplier's total is compared with the average of the per-supplier totals —
    //    koan 6's table, now inside the HAVING. Fill in what the average is taken over.
    //    (Two suppliers. Compare them with the average ORDER LINE instead — 301.31 — and
    //     all six would pass, which is the lesson's trap on different tables: a supplier's
    //     total is hundreds of lines added up, so of course it beats one line.)
    def "the average of what: suppliers above the average supplier"() {
        expect:
        shouldReturn([["Pavlova Ltd", 18519.15],
                      ["Tokyo Traders", 17535.04]], '''
            SELECT s."CompanyName",
                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                     * (1 - d."Discount")), 2) AS "Total sales"
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            GROUP BY s."CompanyName"
            HAVING SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount"))
                 > (SELECT AVG(___)
                    FROM (SELECT p2."SupplierID",
                                 SUM(d2."UnitPrice" * d2."Quantity"
                                   * (1 - d2."Discount")) AS "Total"
                          FROM "Products" p2
                          JOIN "Order Details" d2 ON d2."ProductID" = p2."ProductID"
                          GROUP BY p2."SupplierID") AS t)
            ORDER BY "Total sales" DESC
        ''')
    }

    // 8) A SUBQUERY BESIDE HAVING. Which categories have an average product price above the
    //    average price of ALL products? HAVING filters the groups; the brackets supply the
    //    one number every group is measured against. Fill in the brackets' select list.
    //    (One category, and by a long way: the two most expensive products in the
    //     catalogue are both in it.)
    def "a subquery beside HAVING"() {
        expect:
        shouldReturn([["Meat/Poultry", 110.4]], '''
            SELECT c."CategoryName", ROUND(AVG(p."UnitPrice"), 2) AS "Average price"
            FROM "Products" p
            JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
            GROUP BY c."CategoryName"
            HAVING AVG(p."UnitPrice") > (SELECT ___ FROM "Products")
        ''')
    }

    // 9) The whole query — no scaffolding.
    //    THE QUESTION: which products earned us more than the average product did?
    //      · one row per product, by name       -> "Products"."ProductName"
    //      · what it earned: the line money, summed, ROUNDed to 2
    //      · kept only if that total beats the average of the per-product totals
    //      · biggest earner first
    //      · return "ProductName", then the money — in that order
    //    GRAIN FIRST: the average must be the average PRODUCT, one total per product,
    //    averaged — not the average order line.
    //    (Five products. Checkable fact: the average product earned 2907.67.)
    def "write the whole query: products that beat the average product"() {
        expect:
        shouldReturn([["Thuringer Rostbratwurst", 16464.07],
                      ["Mishi Kobe Niku", 6867.60],
                      ["Ikura", 4338.45],
                      ["Gnocchi di nonna Alice", 3784.80],
                      ["Tofu", 3218.96]], '''
            ___
        ''')
    }

    // 10) The whole query again — brackets inside brackets.
    //     THE QUESTION: which customers have ever bought our CHEAPEST product?
    //       · nobody tells you which product that is: find the lowest "UnitPrice" (a value),
    //       · then the product with that price (a value),
    //       · then the customers with an order line for it (a list)
    //       · return "CustomerID" and "CompanyName", ordered by "CustomerID"
    //     BUILD IT FROM THE INSIDE OUT, running each bracket alone before you wrap the next
    //     one around it. That is how anybody writes a query like this.
    //     (Seven customers.)
    def "write the whole query: a value inside a list inside a query"() {
        expect:
        shouldReturn([["ANATR", "Ana Trujillo Emparedados y helados"],
                      ["BONAP", "Bon app'"],
                      ["DRACD", "Drachenblut Delikatessen"],
                      ["FOLKO", "Folk och fä HB"],
                      ["GREAL", "Great Lakes Food Market"],
                      ["LILAS", "LILA-Supermercado"],
                      ["OTTIK", "Ottilies Käseladen"]], '''
            ___
        ''')
    }
}
