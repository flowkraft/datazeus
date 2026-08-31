package datazeus.learnsql.series1._40

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 40   JOINs: INNER, LEFT, RIGHT, FULL ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _40  (Windows)   ./zeus.sh koans learnsql series1 _40  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * Same ten ideas, in the same order, asked about DIFFERENT TABLES. The lesson joins
 * ORDERS to CUSTOMERS and then EMPLOYEES to ORDERS; here you join PRODUCTS to their
 * CATEGORIES and SUPPLIERS, and then ask which of our suppliers has a line we have
 * run out of. Copying a query across from the video will not work — which is the
 * point. You learn the idea by applying it somewhere new, not by retyping an answer.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1    match two tables on the column they share
 *   2    say WHICH table a column comes from
 *   3    narrow the match: a second condition in ON
 *   4    THE TRAP: an INNER JOIN drops the rows that did not match, silently
 *   5    LEFT JOIN keeps every row on the left, match or no match
 *   6    ON, not WHERE — the trap that undoes a LEFT JOIN
 *   7    the anti-join: WHERE ... IS NULL finds the rows that matched nothing
 *   8    RIGHT JOIN is LEFT JOIN with the tables written the other way round
 *   9    FULL OUTER keeps the unmatched rows from both sides
 *  10    the whole query, written from scratch
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver.
 *
 * A NOTE ON THE EMPTY CELLS you are about to create. From koan 5 onwards, some rows
 * come back with nothing in the product column. That is not missing data and it is
 * not a bug: the database looked for a match, found none, and put NULL there. In
 * Groovy those cells are written `null` in the expected rows below. WHAT NULL DOES to
 * a comparison is the next lesson in this series; koan 7 borrows `IS NULL` on trust.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The three tables these koans use, in full, so you can write a query without leaving
 * this file. Every foreign key in this database is satisfied — there are no orphan
 * rows anywhere — so an unmatched row only ever appears because YOU narrowed the
 * match with a second condition in ON. That is exactly what koans 3 onwards do.
 *
 *   "Products" — 20 rows, 10 columns. "UnitsInStock" is how many we have on the shelf
 *   right now, and it is 0 on exactly two lines. "Discontinued" is true on exactly two
 *   (a different two).
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 *
 *   "Categories" — 8 rows, 4 columns. Every category has at least two products.
 *     "CategoryID"      INTEGER        "CategoryName"    VARCHAR
 *     "Description"     VARCHAR        "Picture"         BLOB
 *
 *   "Suppliers" — 6 rows, 13 columns. Two of them are in the USA.
 *     "SupplierID"      INTEGER        "CompanyName"     VARCHAR
 *     "ContactName"     VARCHAR        "ContactTitle"    VARCHAR
 *     "Address"         VARCHAR        "City"            VARCHAR
 *     "Region"          VARCHAR        "PostalCode"      VARCHAR
 *     "Country"         VARCHAR        "Phone"           VARCHAR
 *     "Fax"             VARCHAR        "HomePage"        VARCHAR
 *     "Email"           VARCHAR
 *
 * The link between them: "Products"."CategoryID" points at "Categories"."CategoryID",
 * and "Products"."SupplierID" points at "Suppliers"."SupplierID".
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class JoinsKoans extends KoanBase {

    // 1) A product row knows its category as a NUMBER. The name of that category lives in
    //    the other table. Fill in the column on the PRODUCTS side that ON should match
    //    against — the one that holds the pointer.
    //    (Predict first: three products, alphabetically, each beside its category name.)
    def "the shared column is what you match on"() {
        expect:
        shouldReturn([["Aniseed Syrup", "Condiments"],
                      ["Boston Crab Meat", "Seafood"],
                      ["Camembert Pierrot", "Dairy Products"]], '''
            SELECT p."ProductName", c."CategoryName"
            FROM "Products" p
            JOIN "Categories" c ON c."CategoryID" = p.___
            ORDER BY p."ProductName"
            LIMIT 3
        ''')
    }

    // 2) Which of our lines come from Japan? Once two tables are in play, every column
    //    belongs to one of them, and you have to say which. Fill in the ALIAS — one
    //    letter — in front of "Country".
    //    (Only one of the two tables above has a "Country" column at all. Find it in the
    //     schema; guessing the other one gives you an error, not a wrong answer.)
    def "say which table a column comes from"() {
        expect:
        shouldReturn([["Filo Mix"], ["Genen Shouyu"], ["Ikura"],
                      ["Mishi Kobe Niku"], ["Tofu"]], '''
            SELECT p."ProductName"
            FROM "Products" p
            JOIN "Suppliers" s ON s."SupplierID" = p."SupplierID"
            WHERE ___."Country" = 'Japan'
            ORDER BY p."ProductName"
        ''')
    }

    // 3) ON is not limited to the key — it is the whole matching rule, so you can narrow
    //    it. We want the suppliers we need to chase: match a supplier to a product of
    //    theirs, AND only when we have run out of that product. Fill in the column that
    //    says how many we have on the shelf.
    //    (Exactly two lines in the catalogue are at zero. Predict which suppliers.)
    def "narrow the match with a second condition in ON"() {
        expect:
        shouldReturn([["Pasta Buttini s.r.l.", "Gorgonzola Telino"],
                      ["Pavlova Ltd", "Thuringer Rostbratwurst"]], '''
            SELECT s."CompanyName", p."ProductName"
            FROM "Suppliers" s
            JOIN "Products" p
              ON p."SupplierID" = s."SupplierID"
             AND p.___ = 0
            ORDER BY s."CompanyName"
        ''')
    }

    // 4) THE ONE THIS LESSON EXISTS FOR. That last query answered a narrower question than
    //    the one you were asked. We have SIX suppliers; it returned two rows — the other
    //    four vanished without a word, because a row that matches nothing produces no row
    //    at all. Fill in the count you get back when you ask for every supplier, matched
    //    or not, so you can see the size of what an INNER JOIN was throwing away.
    //    (Run koan 3's query again and count: 2. There are 6 suppliers. Predict, then fill.)
    def "an INNER JOIN drops the rows that did not match"() {
        expect:
        // A COUNT here, deliberately, because the number IS the lesson: 6 against 2. The
        // blank cannot be fudged — LEFT is the only join type that answers 6 (INNER
        // answers 2, RIGHT answers 20, FULL answers 24), and JoinsSpec asserts all four.
        shouldReturn 6, '''
            SELECT count(*)
            FROM "Suppliers" s
            ___ JOIN "Products" p
              ON p."SupplierID" = s."SupplierID"
             AND p."UnitsInStock" = 0
        '''
    }

    // 5) Now see them. Same join, same condition — this time list every supplier and what,
    //    if anything, they owe us. Fill in the one word that means "keep every row of the
    //    table in the FROM line, match or no match".
    //    (Four of the six rows come back with NOTHING in the product column. The database
    //     made those blanks: it looked for a match, found none, and put null there. Those
    //     four are the suppliers you do NOT have to ring.)
    def "LEFT JOIN keeps every row on the left"() {
        expect:
        shouldReturn([["Exotic Liquids", null],
                      ["Grandma Kellys Homestead", null],
                      ["New Orleans Cajun Delights", null],
                      ["Pasta Buttini s.r.l.", "Gorgonzola Telino"],
                      ["Pavlova Ltd", "Thuringer Rostbratwurst"],
                      ["Tokyo Traders", null]], '''
            SELECT s."CompanyName", p."ProductName"
            FROM "Suppliers" s
            ___ JOIN "Products" p
              ON p."SupplierID" = s."SupplierID"
             AND p."UnitsInStock" = 0
            ORDER BY s."CompanyName"
        ''')
    }

    // 6) THE TRAP THAT COSTS PEOPLE DAYS. The stock condition has to be part of the
    //    MATCHING RULE, joined to the line above it. Fill in the keyword that continues an
    //    ON clause.
    //    (Try WHERE in that blank before you settle on the right answer, and look at what
    //     you get: two rows, not six — the same two as koan 3. A condition on the RIGHT-hand
    //     table, put in a WHERE, runs AFTER the join and deletes exactly the blank rows the
    //     LEFT JOIN just went to the trouble of keeping. The words LEFT JOIN stay on the
    //     screen the whole time, which is what makes it so hard to spot.)
    def "the condition belongs in ON, not in WHERE"() {
        expect:
        shouldReturn([["Exotic Liquids", null],
                      ["Grandma Kellys Homestead", null],
                      ["New Orleans Cajun Delights", null],
                      ["Pasta Buttini s.r.l.", "Gorgonzola Telino"],
                      ["Pavlova Ltd", "Thuringer Rostbratwurst"],
                      ["Tokyo Traders", null]], '''
            SELECT s."CompanyName", p."ProductName"
            FROM "Suppliers" s
            LEFT JOIN "Products" p
              ON p."SupplierID" = s."SupplierID"
             ___ p."UnitsInStock" = 0
            ORDER BY s."CompanyName"
        ''')
    }

    // 7) THE ONE LEGITIMATE WHERE ON THE RIGHT-HAND TABLE, and it is genuinely useful:
    //    asking for ONLY the rows that found no match. That is called an anti-join, and it
    //    is how you answer "which of these has none?" — here, the suppliers with nothing
    //    out of stock. Fill in the test for an empty cell.
    //    (It is two words, and it is NOT an equals sign. Comparing anything to nothing with
    //     = matches nothing at all, quietly — that is the whole of the next lesson.)
    def "the anti-join: find the rows that matched nothing"() {
        expect:
        shouldReturn([["Exotic Liquids"], ["Grandma Kellys Homestead"],
                      ["New Orleans Cajun Delights"], ["Tokyo Traders"]], '''
            SELECT s."CompanyName"
            FROM "Suppliers" s
            LEFT JOIN "Products" p
              ON p."SupplierID" = s."SupplierID"
             AND p."UnitsInStock" = 0
            WHERE p."ProductID" ___
            ORDER BY s."CompanyName"
        ''')
    }

    // 8) The mirror. Here the tables are written the other way round — products first,
    //    suppliers second — and we still want every supplier. Fill in the direction word
    //    that means "keep every row of the SECOND table".
    //    (Same six rows as koan 5, in the same order. That is the point: you can always
    //     swap the tables instead, which is why almost nobody writes this one. You will
    //     still READ it in other people's queries.)
    def "RIGHT JOIN is LEFT JOIN with the tables swapped"() {
        expect:
        shouldReturn([["Exotic Liquids", null],
                      ["Grandma Kellys Homestead", null],
                      ["New Orleans Cajun Delights", null],
                      ["Pasta Buttini s.r.l.", "Gorgonzola Telino"],
                      ["Pavlova Ltd", "Thuringer Rostbratwurst"],
                      ["Tokyo Traders", null]], '''
            SELECT s."CompanyName", p."ProductName"
            FROM "Products" p
            ___ JOIN "Suppliers" s
              ON p."SupplierID" = s."SupplierID"
             AND p."UnitsInStock" = 0
            ORDER BY s."CompanyName"
        ''')
    }

    // 9) ONE ROW PER MATCH — the thing that surprises everybody the first time. Eight
    //    categories go into this query. Fill in the column that is true for the lines we no
    //    longer sell, and count the rows that come out.
    //    (It is NOT eight. One category holds two discontinued lines, so it gets two rows —
    //     a join gives you one row per match, not one row per category. Find the category
    //     that appears twice and you have understood joins. It is a BOOLEAN, so it stands on
    //     its own as a condition: no `= true` needed.)
    def "one row per match, not one row per table row"() {
        expect:
        shouldReturn([["Beverages", null],
                      ["Condiments", null],
                      ["Confections", null],
                      ["Dairy Products", null],
                      ["Grains/Cereals", null],
                      ["Meat/Poultry", "Mishi Kobe Niku"],
                      ["Meat/Poultry", "Thuringer Rostbratwurst"],
                      ["Produce", null],
                      ["Seafood", null]], '''
            SELECT c."CategoryName", p."ProductName"
            FROM "Categories" c
            LEFT JOIN "Products" p
              ON p."CategoryID" = c."CategoryID"
             AND p.___
            ORDER BY c."CategoryName", p."ProductName"
        ''')
    }

    // 10) The whole query — no scaffolding, and everything above it in one go.
    //     THE QUESTION: which of our suppliers OUTSIDE THE USA has a line we have run out of?
    //       · every such supplier listed          -> even the ones with nothing out of stock
    //       · out of stock                        -> "UnitsInStock" is 0
    //       · outside the USA                     -> "Country" is not 'USA'
    //       · supplier name, then product name    -> both ascending
    //       · return "CompanyName" and "ProductName"
    //     TWO CONDITIONS, AND THEY GO IN DIFFERENT PLACES — that is the whole exam.
    //     The stock test is about the RIGHT-hand table, so it belongs in ON. The country
    //     test is about the LEFT-hand table, the one you are keeping every row of, so WHERE
    //     is safe: it cannot take a row away that you meant to keep.
    //     (Four rows, two of them with an empty product cell. Put the stock test in WHERE
    //      by mistake and you get two rows — go back and read koan 6.)
    def "write the whole query: who we still have to chase"() {
        expect:
        shouldReturn([["Exotic Liquids", null],
                      ["Pasta Buttini s.r.l.", "Gorgonzola Telino"],
                      ["Pavlova Ltd", "Thuringer Rostbratwurst"],
                      ["Tokyo Traders", null]], '''
            ___
        ''')
    }
}
