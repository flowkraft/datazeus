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
 * Same ten ideas, in the same order, asked about DIFFERENT TABLES. The lesson builds
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
 *   5    the second check: get the total a second way, without the joins
 *   6    rank the report by the number you just made
 *   7    HAVING filters the totals, not the rows
 *   8    WHERE runs first, and a filter can collapse the whole ranking
 *   9    write the whole query: the supplier sales summary
 *  10    write the whole query: the same report, on what we can still sell
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver.
 *
 * ── THE TWO CHECKS, BECAUSE KOANS 1, 2 AND 5 ARE THEM ───────────────────────
 *
 *   1. COUNT THE ROWS BEFORE THE JOIN AND AFTER IT. Six suppliers. Join them to
 *      their products and you get 20 rows — one per product, because a supplier has
 *      several. Join THAT to the order lines and you get 193 — one per line, because
 *      a product was sold many times. THE NUMBER MOVING IS NOT A BUG: it is the
 *      answer to "one row per WHAT?" changing under you, twice. It is only a bug if
 *      you did not notice, because from then on `count(*)` no longer counts products.
 *
 *   2. GET THE TOTAL A SECOND WAY. The six supplier subtotals have to add up to the
 *      money on every order line there is — a number you can get from "Order Details"
 *      alone, with no join and no grouping. Two routes, one number. The rows
 *      multiplied; the money must not have.
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
    //    (Six rows. Checkable fact: the six totals add up to 58153.31, which is every
    //     penny on every order line — koan 5 gets that number the other way.)
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
    //    column saying how many PRODUCTS each supplier sells us. A plain count(*) here
    //    counts ORDER LINES, not products — Tokyo Traders would come back as 51. Fill
    //    in the word that makes the count ignore repeats.
    //    (Six small numbers, none above 5. If any of yours is in the twenties or
    //     fifties, you are counting rows instead of things — which is exactly the bug
    //     the moved row count in koan 2 warned you about.)
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
    //    JOINS. Add koan 3's six totals up by hand and you get 58153.31. Now get the
    //    same number the short way — every penny on every order line, from ONE table,
    //    no join and no grouping. Fill in which table that is.
    //    (Two routes, one number. The rows multiplied on the way through the joins;
    //     the money must not have. If these two disagree, the report is wrong before
    //     anybody reads it, and no amount of formatting will save it.)
    def "get the total a second way, without the joins"() {
        expect:
        shouldReturn 58153.31, '''
            SELECT ROUND(SUM("UnitPrice" * "Quantity"
              * (1 - "Discount")), 2) AS "Total"
            FROM ___
        '''
    }

    // 6) A report is read from the top, so put the answer there. Fill in what to sort
    //    by — you gave that number a name a few lines up, so use it.
    //    (Biggest earner first. Predict the top two before you run it: one supplier
    //     sells us five different lines, another sells three. Does that decide it?)
    def "rank the report by the number you just made"() {
        expect:
        shouldReturn([["Pavlova Ltd", 18519.15],
                      ["Tokyo Traders", 17535.04],
                      ["Pasta Buttini s.r.l.", 9248.50],
                      ["Grandma Kellys Homestead", 5522.57],
                      ["Exotic Liquids", 4465.75],
                      ["New Orleans Cajun Delights", 2862.30]], '''
            SELECT s."CompanyName",
                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                     * (1 - d."Discount")), 2) AS "Total sales"
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            GROUP BY s."CompanyName"
            ORDER BY ___ DESC
        ''')
    }

    // 7) Now filter the TOTALS instead of the rows: which suppliers earned us more
    //    than five thousand? That test is about a number which does not exist until
    //    the grouping has happened, so it cannot go in a WHERE. Fill in the keyword
    //    that filters groups.
    //    (Four of the six survive. Put SUM in a WHERE instead and the database refuses
    //     the query outright — that refusal is the whole of lesson 35.)
    def "HAVING filters the totals, not the rows"() {
        expect:
        shouldReturn([["Pavlova Ltd", 18519.15],
                      ["Tokyo Traders", 17535.04],
                      ["Pasta Buttini s.r.l.", 9248.50],
                      ["Grandma Kellys Homestead", 5522.57]], '''
            SELECT s."CompanyName",
                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                     * (1 - d."Discount")), 2) AS "Total sales"
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            GROUP BY s."CompanyName"
            ___ SUM(d."UnitPrice" * d."Quantity"
                  * (1 - d."Discount")) > 5000
            ORDER BY "Total sales" DESC
        ''')
    }

    // 8) WHERE RUNS FIRST, so it decides which rows ever reach a pile — and that can
    //    rewrite the whole ranking. Ask the question the buyer actually cares about:
    //    the same report, counting only lines we can STILL SELL. Fill in the filter
    //    that drops the discontinued products. It is a BOOLEAN, so NOT in front of it
    //    is the whole condition — no `= false` needed.
    //    (PAVLOVA LTD GOES FROM FIRST TO LAST: 18519.15 becomes 2055.08. Almost all of
    //     their money was one discontinued sausage. The first report was not wrong —
    //     it answered "what did we earn". This one answers "what can we earn next
    //     year", and they are different questions with different winners.)
    def "WHERE runs first, and a filter can collapse the ranking"() {
        expect:
        shouldReturn([["Tokyo Traders", 10667.44],
                      ["Pasta Buttini s.r.l.", 9248.50],
                      ["Grandma Kellys Homestead", 5522.57],
                      ["Exotic Liquids", 4465.75],
                      ["New Orleans Cajun Delights", 2862.30],
                      ["Pavlova Ltd", 2055.08]], '''
            SELECT s."CompanyName",
                   ROUND(SUM(d."UnitPrice" * d."Quantity"
                     * (1 - d."Discount")), 2) AS "Total sales"
            FROM "Suppliers" s
            JOIN "Products" p ON p."SupplierID" = s."SupplierID"
            JOIN "Order Details" d ON d."ProductID" = p."ProductID"
            WHERE ___
            GROUP BY s."CompanyName"
            ORDER BY "Total sales" DESC
        ''')
    }

    // 9) The whole query — no scaffolding. This is the report itself, and it is the
    //     supplier twin of the one the lesson built.
    //     THE QUESTION: which suppliers earn us the most, and on how many lines?
    //       · one row per supplier, by name      -> "Suppliers"."CompanyName"
    //       · how many DIFFERENT products        -> count the products, not the rows
    //       · what those products earned us      -> the line money, summed, ROUNDed to 2
    //       · biggest earner first
    //       · return "CompanyName", the product count, then the money — in that order
    //     RUN BOTH CHECKS BEFORE YOU BELIEVE IT: 193 rows out of the two joins, and
    //     the six totals adding to 58153.31.
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

    // 10) The whole query again, with both filters — the hardest one, and the exam.
    //     THE QUESTION: of the products we can still sell, which suppliers earned us
    //     more than four thousand?
    //       · only lines that are not discontinued   -> filters ROWS, before grouping
    //       · only suppliers whose total is over 4000 -> filters GROUPS, after it
    //       · one row per supplier, biggest first
    //       · return "CompanyName" and the money, rounded to 2
    //     TWO FILTERS, AND THEY GO IN DIFFERENT PLACES. Whether a product is
    //     discontinued is a property of a ROW and is known before any grouping — so it
    //     is a WHERE. Whether a supplier cleared four thousand is a property of a
    //     TOTAL, and that number does not exist until the grouping has happened — so
    //     it is a HAVING. Swap them and the database refuses the query.
    //     (Four rows. Pavlova Ltd was first in koan 6 and does not appear at all here
    //      — 2055.08 once the sausage is gone. Predict who else misses out.)
    def "write the whole query: the same report, on what we can still sell"() {
        expect:
        shouldReturn([["Tokyo Traders", 10667.44],
                      ["Pasta Buttini s.r.l.", 9248.50],
                      ["Grandma Kellys Homestead", 5522.57],
                      ["Exotic Liquids", 4465.75]], '''
            ___
        ''')
    }
}
