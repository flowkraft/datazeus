package datazeus.learnsql.series2._00

import datazeus._internal.NorthwindCoKoanBase
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
 * compare orders with the average FREIGHT, suppliers with the average SUPPLIER and sales
 * reps with the average SALES REP — so copying a query across from the video will not work.
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
 *   9    write the whole query: sales reps who beat the average sales rep
 *  10    write the whole query: a value inside a value inside a list
 *
 * These run on DuckDB, on Northwind Company — the same data the Seed Data tab installs
 * into CloudBeaver's PostgreSQL. Every one is written so it returns the SAME answer there,
 * once `SET search_path TO northwind_co_s;` has been run in the editor (this file does
 * that for you). KEEP THE DOUBLE QUOTES on every name, spelled exactly as the schema below
 * spells them: DuckDB forgives a missing quote or a wrong capital letter and PostgreSQL
 * does not, so a koan can go green here on a query that CloudBeaver refuses.
 *
 * ── RELEVANT SCHEMA (Northwind Company, schema northwind_co_s) ───────────────
 *
 * The tables these koans use, with the columns they need, so you can write a query
 * without leaving this file. Five years of trading: 2020-01-01 to 2024-12-31.
 *
 *   "Orders" — 10,000 rows. One row per order.
 *     "OrderID"         INTEGER        "CustomerID"      VARCHAR
 *     "EmployeeID"      INTEGER        "OrderDate"       TIMESTAMP
 *     "RequiredDate"    TIMESTAMP      "ShippedDate"     TIMESTAMP (empty if not shipped)
 *     "ShipVia"         INTEGER        "Freight"         DECIMAL(19,4)
 *     "Status"          VARCHAR ('Shipped', 'Cancelled' or 'Open')
 *     plus the ship-to address, "Channel", "CreatedAt" and "UpdatedAt".
 *
 *   "Customers" — 120 rows. "CustomerID" VARCHAR (five characters, e.g. 'ZENIT'),
 *     "CompanyName" VARCHAR, and address, contact and "Segment" columns.
 *
 *   "Employees" — 12 rows. "EmployeeID" INTEGER, "FirstName" VARCHAR, "LastName" VARCHAR,
 *     "Title" VARCHAR, "ReportsTo" INTEGER. Employees 1 to 3 are the chief executive and
 *     the two sales managers, and take no orders; 4 to 12 are the sales reps.
 *     TWO REPS SHARE A SURNAME (Ravi and Sven Keller) and two share a first name (Lukas):
 *     group by "EmployeeID", never by a name.
 *
 *   "Suppliers" — 20 rows. "SupplierID" INTEGER, "CompanyName" VARCHAR, and address and
 *     contact columns these koans do not need.
 *
 *   "Products" — 80 rows.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "UnitPrice"       DECIMAL(19,4)  "UnitCost"        DECIMAL(19,4)
 *     "Discontinued"    BOOLEAN        plus stock columns.
 *
 *   "Categories" — 8 rows. "CategoryID" INTEGER, "CategoryName" VARCHAR, "Description" VARCHAR.
 *
 *   "Order Details" — 25,233 rows, 5 columns. ONE ROW PER THING BOUGHT.
 *     "OrderID"         INTEGER        "ProductID"       INTEGER
 *     "UnitPrice"       DECIMAL(19,4)  "Quantity"        SMALLINT
 *     "Discount"        DECIMAL(8,4)
 *
 * WHAT ONE LINE IS WORTH: `d."UnitPrice" * d."Quantity" * (1 - d."Discount")`.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class SubqueriesKoans extends NorthwindCoKoanBase {

    // 1) A VALUE IN BRACKETS. How many orders paid more freight than the average order?
    //    The brackets run first and hand back ONE number; the outer query compares every
    //    order with it, exactly as if you had typed the number in. Fill in the function
    //    that averages a column.
    //    (Predict first: more or fewer than half of the 10,000? Checkable fact: the average
    //     freight is 52.74 — select the part in brackets and run it alone to see it.)
    def "a value in brackets: freight above the average"() {
        expect:
        shouldReturn 4065, '''
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
        shouldReturn([[7826, "BRAM2", 294.35]], '''
            SELECT "OrderID", "CustomerID", "Freight"
            FROM "Orders"
            WHERE "Freight" = (___)
        ''')
    }

    // 3) ONE VALUE ONLY. Which orders paid more freight than EVERY order Ines Torres took?
    //    ("EmployeeID" 9.) Written as SELECT "Freight" the brackets hand back 1,125 values —
    //    one per order of hers — and ">" cannot compare with 1,125 numbers: the query stops
    //    with "more than one row returned by a subquery used as an expression". More than
    //    every one of them means more than the BIGGEST of them. Fill in the expression that
    //    turns her 1,125 freights into one.
    //    (Two orders, heaviest first. Neither of them is hers.)
    def "one value only: turn a column of values into one"() {
        expect:
        shouldReturn([[7826, 294.35],
                      [4688, 240.68]], '''
            SELECT "OrderID", "Freight"
            FROM "Orders"
            WHERE "Freight" > (SELECT ___
                               FROM "Orders"
                               WHERE "EmployeeID" = 9)
            ORDER BY "Freight" DESC
        ''')
    }

    // 4) A LIST. Which products has Zenith Fine Foods ever bought? The brackets hand back a
    //    column of product ids — one for every line Zenith ever ordered — and the outer
    //    query keeps each product whose id is IN that list. Fill in the keyword that tests
    //    whether a value is one of a list.
    //    (Six products, by name.)
    def "a list: IN"() {
        expect:
        shouldReturn([["Island Gingerbread"], ["Island Olives"], ["Meadow Cider"],
                      ["Orchard Salami"], ["Rustic Toffee"], ["Stone Marzipan"]], '''
            SELECT "ProductName"
            FROM "Products"
            WHERE "ProductID" ___ (
              SELECT d."ProductID"
              FROM "Order Details" d
              JOIN "Orders" o ON o."OrderID" = d."OrderID"
              WHERE o."CustomerID" = 'ZENIT'
            )
            ORDER BY "ProductName"
        ''')
    }

    // 5) A JOIN REPEATS WHAT IN DOES NOT. The same question as koan 4, written as a join
    //    from the product to its order lines to their orders. Fill in the join condition
    //    that ties each order line to its product.
    //    (Predict first: IN gave six rows. A join returns one row per MATCH, and Zenith
    //     bought some of those products more than once — so how many rows now? IN asks a
    //     yes-or-no question per product, which is why it never repeats one.)
    def "a join repeats what IN does not"() {
        expect:
        shouldReturn 8, '''
            SELECT count(*)
            FROM "Products" p
            JOIN "Order Details" d ON ___
            JOIN "Orders" o ON o."OrderID" = d."OrderID"
            WHERE o."CustomerID" = 'ZENIT'
        '''
    }

    // 6) A TABLE IN FROM. What does the AVERAGE SUPPLIER sell? That needs one total per
    //    supplier first, and then the average of those totals. The brackets are that table
    //    of totals, named t. Fill in the clause that makes the inside return ONE ROW PER
    //    SUPPLIER.
    //    (Without it the inside adds up every line into one total, and the "average" of a
    //     single total is just 15017031.15 — the whole business over five years, not a
    //     supplier. One row per WHAT is the question the brackets answer.)
    def "a table in FROM, and its grain"() {
        expect:
        shouldReturn 750851.56, '''
            SELECT ROUND(AVG(t."Total"), 2)
            FROM (SELECT p."SupplierID",
                         SUM(d."UnitPrice" * d."Quantity"
                           * (1 - d."Discount")) AS "Total"
                  FROM "Products" p
                  JOIN "Order Details" d ON d."ProductID" = p."ProductID"
                  ___) AS t
        '''
    }

    // 7) THE AVERAGE OF WHAT. Which suppliers sell more than the average SUPPLIER?
    //    Each supplier's total is compared with the average of the per-supplier totals —
    //    koan 6's table, now inside the HAVING. Fill in what the average is taken over.
    //    (Nine suppliers of the twenty. Compare them with the average ORDER LINE instead —
    //     595.13 — and all twenty would pass, which is the lesson's trap on different
    //     tables: a supplier's total is thousands of lines added up, so of course it beats
    //     one line.)
    def "the average of what: suppliers above the average supplier"() {
        expect:
        shouldReturn([["Baltic Producers", 1653749.77],
                      ["Saffron Farms", 1527636.00],
                      ["Harbor Brewery", 1459556.71],
                      ["Bramble Dairy", 1317014.75],
                      ["Nordic Fisheries", 1206310.33],
                      ["Valley Brewery", 883794.54],
                      ["Delta Mills", 771196.43],
                      ["Bramble Producers", 770850.02],
                      ["Alpine Kitchens", 763132.73]], '''
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
    //    (Three of the eight categories, dearest first. Checkable fact: the average price
    //     of all 80 products is 24.01.)
    def "a subquery beside HAVING"() {
        expect:
        shouldReturn([["Meat/Poultry", 38.97],
                      ["Confections", 29.29],
                      ["Dairy Products", 26.49]], '''
            SELECT c."CategoryName", ROUND(AVG(p."UnitPrice"), 2) AS "Average price"
            FROM "Products" p
            JOIN "Categories" c ON c."CategoryID" = p."CategoryID"
            GROUP BY c."CategoryName"
            HAVING AVG(p."UnitPrice") > (SELECT ___ FROM "Products")
            ORDER BY "Average price" DESC
        ''')
    }

    // 9) The whole query — no scaffolding.
    //    THE QUESTION: which sales reps sold more than the average sales rep?
    //      · one row per employee who took orders — GROUP BY "EmployeeID" (two reps share a
    //        surname, so a name is not enough), with "FirstName" and "LastName" beside it
    //      · what each sold: the line money, summed, ROUNDed to 2
    //      · kept only if that total beats the average of the per-employee totals
    //      · biggest seller first
    //      · return "FirstName", "LastName", then the money — in that order
    //    GRAIN FIRST: the average must be the average EMPLOYEE, one total per employee,
    //    averaged — not the average order line. Only the nine reps who took orders have a
    //    total, so the average is over those nine.
    //    (Four reps. Checkable fact: the average rep sold 1668559.02 — and the fifth rep
    //     misses it by less than 26,000.)
    def "write the whole query: sales reps who beat the average sales rep"() {
        expect:
        shouldReturn([["Hugo", "Dubois", 2683582.70],
                      ["Ravi", "Keller", 2262633.06],
                      ["Yara", "Schmidt", 2173151.40],
                      ["Umberto", "Jansen", 2027512.41]], '''
            ___
        ''')
    }

    // 10) The whole query again — brackets inside brackets.
    //     THE QUESTION: which suppliers supply the category of our MOST EXPENSIVE product?
    //       · nobody tells you which product that is: find the highest "UnitPrice" (a value),
    //       · then the category of the product with that price (a value),
    //       · then the suppliers of the products in that category (a list)
    //       · return "SupplierID" and "CompanyName", ordered by "SupplierID"
    //     BUILD IT FROM THE INSIDE OUT, running each bracket alone before you wrap the next
    //     one around it. That is how anybody writes a query like this.
    //     (Eight suppliers, from ten products: two suppliers make two of them each, and IN
    //      still lists each supplier once.)
    def "write the whole query: a value inside a value inside a list"() {
        expect:
        shouldReturn([[6, "Harbor Brewery"],
                      [7, "Baltic Orchards"],
                      [9, "Saffron Farms"],
                      [12, "Baltic Mills"],
                      [14, "Meadow Dairy"],
                      [17, "Baltic Producers"],
                      [18, "Elm Fisheries"],
                      [20, "Delta Mills"]], '''
            ___
        ''')
    }
}
