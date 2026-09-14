package datazeus.learnsql.series1._50

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 50   SELECT + JOIN + GROUP BY        ║
 * ║                                          Building Your First Real Report ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _50  (Windows)   ./zeus.sh koans learnsql series1 _50  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * ── NOTHING NEW HERE, AND THAT IS THE POINT ─────────────────────────────────
 *
 * Every keyword below has already had a lesson of its own. What is new is that you
 * assemble them into ONE report and CHECK IT BEFORE YOU SEND IT. That habit is the
 * difference between somebody who writes SQL and somebody you would trust with a
 * number. The last two koans are whole queries, written from scratch.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, asked about DIFFERENT TABLES. The lesson builds
 * the CUSTOMER sales summary — who spends the most with us — out of "Customers",
 * "Orders" and "Order Details". Here you build the SUPPLIER side of the same
 * business: whose goods actually earn us the money, out of "Suppliers", "Products"
 * and "Order Details". Copying a query across from the video will not work, which is
 * the point: you learn the idea by applying it somewhere new.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1    count the rows BEFORE you group — the habit the whole lesson is about
 *   2    follow the pointer one more time, and watch the row count move
 *   3    the money: an aggregate runs once per group
 *   4    count the THINGS, not the rows — the trap the moved row count sets
 *   5    the second check: get koan 4's count a second way, without the joins
 *   6    an average: divide the money by the products, not by the order lines
 *   7    rank the report by the money, and keep the top six
 *   8    know what the cut left out
 *   9    write the whole query: the supplier sales summary
 *  10    write the whole query: the top three suppliers, orders and all
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver. KEEP THE DOUBLE QUOTES on every name, spelled exactly as
 * the schema below spells them: DuckDB forgives a missing quote or a wrong capital
 * letter — "Total Sales" for "Total sales" — and PostgreSQL does not. So a koan can go
 * green here on a query that CloudBeaver refuses.
 *
 * ── THE TWO CHECKS, BECAUSE KOANS 1, 2 AND 5 ARE THEM ───────────────────────
 *
 *   1. COUNT THE ROWS BEFORE THE JOIN AND AFTER IT. Six suppliers. Join them to
 *      their products and you get 20 rows — one per product, because a supplier has
 *      several. Join THAT to the order lines and you get 193 — one per line, because
 *      a product was sold many times. THE NUMBER MOVING IS NOT A BUG: it is the
 *      answer to "one row per WHAT?" changing under you, twice. It is only a bug if
 *      you did not notice, because from then on every count counts order lines —
 *      even a count that names the product column (koan 4).
 *
 *   2. GET THE NUMBER A SECOND WAY, BY A ROUTE THAT DOES NOT LOOK LIKE THE FIRST. The
 *      lesson does it with the money, and the money checks out here too: koan 3's six
 *      totals add up to 58153.31, every penny on every order line. Koan 5 does it with
 *      a COUNT: koan 4 says our six suppliers sell us 20 different products between
 *      them, and "Order Details" on its own — no join, no grouping — has to say 20 too.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The three tables these koans use, in full, so you can write a query without leaving
 * this file.
 *
 *   "Suppliers" — 6 rows, 13 columns. The companies we buy from.
 *     "SupplierID"      INTEGER        "CompanyName"     VARCHAR
 *     "ContactName"     VARCHAR        "ContactTitle"    VARCHAR
 *     "Address"         VARCHAR        "City"            VARCHAR
 *     "Region"          VARCHAR        "PostalCode"      VARCHAR
 *     "Country"         VARCHAR        "Phone"           VARCHAR
 *     "Fax"             VARCHAR        "HomePage"        VARCHAR
 *     "Email"           VARCHAR
 *
 *   "Products" — 20 rows, 10 columns. "Discontinued" is true on exactly two of them.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 *
 *   "Order Details" — 193 rows, 5 columns. ONE ROW PER THING BOUGHT, which is where
 *   the money is. "UnitPrice" here is what that line SOLD for on the day; the one in
 *   "Products" is what the catalogue charges now.
 *     "OrderID"         INTEGER        "ProductID"       INTEGER
 *     "UnitPrice"       DECIMAL(19,4)  "Quantity"        SMALLINT
 *     "Discount"        DECIMAL(8,4)
 *
 * The links: "Products"."SupplierID" points at "Suppliers"."SupplierID", and
 * "Order Details"."ProductID" points at "Products"."ProductID". Two hops, so two
 * joins — the same join you already know, written twice.
 *
 * WHAT ONE LINE IS WORTH: "UnitPrice" times "Quantity", less the discount —
 * `d."UnitPrice" * d."Quantity" * (1 - d."Discount")`. Wrap the WHOLE calculation in
 * ROUND(..., 2) when you want money rather than eight decimal places.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class YourFirstRealReportKoans extends KoanBase {

    // 1) BEFORE THE REPORT, THE HABIT. You are about to build a report on top of this
    //    join, so first ask what the join gives you: how many rows come out? Fill in
    //    the function that counts rows.
    //    (Predict first. There are 6 suppliers and 20 products, and every product
    //     names exactly one supplier — so which of those two numbers comes back?)
    def "count the rows before you group"() {
        expect:
        shouldReturn 20, '''
            SELECT ___(*)
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
        '''
    }

    // 2) THE MONEY IS ONE MORE HOP AWAY. A product row does not know what it sold for
    //    — that is on the order line. So follow the pointer again: fill in the table
    //    that holds one row per thing bought.
    //    (THE NUMBER MOVES: 20 becomes 193, because each product was sold many times.
    //     That is not a bug, it is the answer to "one row per what?" changing — you
    //     now have one row per ORDER LINE. Note it. Koan 4 is what it costs you.)
    def "follow the pointer one more time, and watch the row count move"() {
        expect:
        shouldReturn 193, '''
            SELECT count(*)
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
            JOIN ___ d ON d."ProductID" = p."ProductID"
        '''
    }

    // 3) Now the report. One row per supplier, and in it the money their goods earned
    //    us. Fill in the function that ADDS UP a column inside each group.
    //    (Six rows. Checkable fact: the six totals add up to 58153.31 — every penny on
    //     every order line, the same total the lesson gets from "Order Details" alone.
    //     The rows multiplied on the way through the joins; the money did not.)
    def "the money: an aggregate runs once per group"() {
        expect:
        shouldReturn([["Exotic Liquids", 4465.75],
                      ["Grandma Kellys Homestead", 5522.57],
                      ["New Orleans Cajun Delights", 2862.30],
                      ["Pasta Buttini s.r.l.", 9248.50],
                      ["Pavlova Ltd", 18519.15],
                      ["Tokyo Traders", 17535.04]], '''
            SELECT s."CompanyName",
                   ROUND(___(d."UnitPrice" * d."Quantity"
                     * (1 - d."Discount")), 2) AS "Total sales"
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            GROUP BY s."CompanyName"
            ORDER BY s."CompanyName"
        ''')
    }

    // 4) THE TRAP THE MOVED ROW COUNT SETS, AND THE ONE THIS LESSON EXISTS FOR. Add a
    //    column saying how many PRODUCTS each supplier sells us. Naming the product
    //    column looks like enough — count(p."ProductID") — but naming a column does not
    //    change what count counts. It counts how many VALUES are sitting in that column,
    //    and since the second join there is one value per ORDER LINE: Tokyo Traders
    //    would come back as 51, when only 5 of those values are different. Fill in the
    //    word that makes count ask for the DIFFERENT ones.
    //    (Six small numbers, none above 5. If yours run from 20 to 51, you are counting
    //     order lines instead of products — which is exactly what the moved row count
    //     in koan 2 warned you about.)
    def "count the THINGS, not the rows"() {
        expect:
        shouldReturn([["Exotic Liquids", 3],
                      ["Grandma Kellys Homestead", 3],
                      ["New Orleans Cajun Delights", 2],
                      ["Pasta Buttini s.r.l.", 4],
                      ["Pavlova Ltd", 3],
                      ["Tokyo Traders", 5]], '''
            SELECT s."CompanyName",
                   count(___ p."ProductID") AS "Products"
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            GROUP BY s."CompanyName"
            ORDER BY s."CompanyName"
        ''')
    }

    // 5) THE SECOND CHECK, AND THE POINT OF IT IS THAT IT DOES NOT GO THROUGH THE
    //    JOINS. Add up koan 4's six counts — 3 + 3 + 2 + 4 + 3 + 5 — and you get 20.
    //    Every product has exactly ONE supplier, so no product was counted under two of
    //    them: 20 has to be how many different products were ever sold. "Order Details"
    //    can tell you that on its own, with no join and no grouping. Fill in the column
    //    that says which product a line is for.
    //    (Two routes, one number. If they disagree, one of the two queries is wrong —
    //     and the report on its own would never have told you.)
    def "check the count a second way, without the joins"() {
        expect:
        shouldReturn 20, '''
            SELECT count(DISTINCT ___) AS "Products sold"
            FROM "Order Details"
        '''
    }

    // 6) WHAT THE TOTAL DOESN'T SHOW. Pavlova Ltd and Tokyo Traders earn us nearly the
    //    same money — 18519.15 and 17535.04 — but Pavlova Ltd does it with 3 products
    //    and Tokyo Traders with 5. So what does ONE product earn each of them? Divide
    //    the money by how many different products there are. Fill in the divisor; koan 4
    //    already showed you how to count them.
    //    (Predict first: which of the two wins, product for product? Divide by count(*)
    //     instead and you divide by ORDER LINES — 25 and 51 of them — and both averages
    //     come out far too small: 740.77 and 343.82. It is the same damage the lesson
    //     shows, where the wrong count drops Alfreds' average from 767.69 to 348.95.)
    def "an average: divide the money by the products, not the order lines"() {
        expect:
        shouldReturn([["Pavlova Ltd", 6173.05],
                      ["Tokyo Traders", 3507.01]], '''
            SELECT s."CompanyName",
                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                     * (1 - d."Discount")) / ___, 2) AS "Avg per product"
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            WHERE s."CompanyName" IN ('Pavlova Ltd', 'Tokyo Traders')
            GROUP BY s."CompanyName"
            ORDER BY s."CompanyName"
        ''')
    }

    // 7) A report is read from the top, so put the answer there — and the report you
    //    send is short. Rank the products by what they earned us, biggest first, and keep
    //    the top six. Write that whole last line: what to sort by, which way, and where
    //    to stop. The money column already has a name a few lines up, so use it.
    //    (Predict first: is the top product close? It is not — it earns more than twice
    //     what the second one does.)
    def "rank by the money, and keep the top six"() {
        expect:
        shouldReturn([["Thuringer Rostbratwurst", 11, 16464.07],
                      ["Mishi Kobe Niku", 8, 6867.60],
                      ["Ikura", 10, 4338.45],
                      ["Gnocchi di nonna Alice", 7, 3784.80],
                      ["Tofu", 11, 3218.96],
                      ["Uncle Bobs Organic Dried Pears", 7, 2430.00]], '''
            SELECT p."ProductName",
                   count(DISTINCT d."OrderID") AS "Orders",
                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                     * (1 - d."Discount")), 2) AS "Total sales"
            FROM "Products" p
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            GROUP BY p."ProductName"
            ___
        ''')
    }

    // 8) KNOW WHAT THE CUT LEFT OUT. The top six is what you send; the seventh product
    //    is the one somebody asks you about. Same query, same order — now skip the six
    //    you have already shown and return only the next one. Fill in the keyword that
    //    skips rows (lesson 15 used it to show page two of a price list).
    //    (Chef Antons Cajun Seasoning is on 13 orders — more than any product in your
    //     top six — and it still misses the list, 77.10 short of sixth place. Nothing is
    //     wrong with that. It fell below the line, and now you know it before anybody
    //     asks.)
    def "know what the cut left out"() {
        expect:
        shouldReturn([["Chef Antons Cajun Seasoning", 13, 2352.90]], '''
            SELECT p."ProductName",
                   count(DISTINCT d."OrderID") AS "Orders",
                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                     * (1 - d."Discount")), 2) AS "Total sales"
            FROM "Products" p
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            GROUP BY p."ProductName"
            ORDER BY "Total sales" DESC
            LIMIT 1 ___ 6
        ''')
    }

    // 9) The whole query — no scaffolding. This is the report itself, and it is the
    //     supplier twin of the one the lesson built.
    //     THE QUESTION: which suppliers earn us the most, and on how many products?
    //       · one row per supplier, by name      -> "Suppliers"."CompanyName"
    //       · how many DIFFERENT products        -> count the products, not the rows
    //       · what those products earned us      -> the line money, summed, ROUNDed to 2
    //       · biggest earner first
    //       · return "CompanyName", the product count, then the money — in that order
    //     RUN BOTH CHECKS BEFORE YOU BELIEVE IT: 193 rows out of the two joins, the six
    //     product counts adding up to 20, and the six totals adding up to 58153.31.
    def "write the whole query: the supplier sales summary"() {
        expect:
        shouldReturn([["Pavlova Ltd", 3, 18519.15],
                      ["Tokyo Traders", 5, 17535.04],
                      ["Pasta Buttini s.r.l.", 4, 9248.50],
                      ["Grandma Kellys Homestead", 3, 5522.57],
                      ["Exotic Liquids", 3, 4465.75],
                      ["New Orleans Cajun Delights", 2, 2862.30]], '''
            ___
        ''')
    }

    // 10) The whole query again — the report you would actually send, cut to the top.
    //     THE QUESTION: who are our three biggest suppliers, and how many products and how
    //     many orders is their money spread over?
    //       · one row per supplier, by name      -> "Suppliers"."CompanyName"
    //       · how many DIFFERENT products        -> as in koan 9
    //       · how many DIFFERENT orders bought their goods -> "Order Details"."OrderID"
    //       · what those products earned us      -> the line money, summed, ROUNDed to 2
    //       · biggest earner first, and only the top three
    //       · return "CompanyName", the product count, the order count, then the money
    //     THE ORDERS COLUMN IS WHERE THE LESSON'S BUG COMES BACK. Every row here is an
    //     order line, so a plain count of "OrderID" counts lines: Tokyo Traders' 51 lines
    //     are only 39 different orders.
    //     (Three rows, biggest earner first.)
    def "write the whole query: the top three suppliers, orders and all"() {
        expect:
        shouldReturn([["Pavlova Ltd", 3, 25, 18519.15],
                      ["Tokyo Traders", 5, 39, 17535.04],
                      ["Pasta Buttini s.r.l.", 4, 24, 9248.50]], '''
            ___
        ''')
    }
}
