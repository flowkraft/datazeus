package datazeus.learnsql.series1._45

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 45   NULL & Three-Valued Logic       ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _45  (Windows)   ./zeus.sh koans learnsql series1 _45  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * Same ten ideas, in the same order, asked about a different part of the business.
 * The lesson works on ORDERS that have not shipped and CUSTOMERS with no region;
 * here you work on the SUPPLIER list, the staff list and the shelf. Copying a query
 * across from the video will not work — which is the point. You learn the idea by
 * applying it somewhere new, not by retyping an answer you just watched.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1    the test for an empty cell is two words, and it is not an equals sign
 *   2    PREDICT: what an equals sign against NULL actually returns
 *   3    the other half of the pair — the rows that DO have a value
 *   4    THE TRAP: a not-equals filter silently leaves the empty rows out
 *   5    PREDICT: NOT does not rescue you either
 *   6    COALESCE — give the empty cell something to stand in for it
 *   7    one missing piece empties the whole line
 *   8    count(*) counts ROWS; count(column) counts VALUES
 *   9    NULLIF — COALESCE backwards
 *  10    the whole query, written from scratch
 *
 * These run on DuckDB. Every one is written so it returns the SAME answer against the
 * PostgreSQL in CloudBeaver.
 *
 * ── ONE THING TO KNOW BEFORE YOU START ──────────────────────────────────────
 *
 * A NULL is not a value. It is the absence of one — nobody ever wrote anything in
 * that cell. So a NULL is not zero, and it is not an empty piece of text: a stock
 * level of 0 is a real answer ("none left"), while a stock level of NULL would mean
 * "nobody has counted". Every koan below turns on that distinction.
 *
 * In Groovy an empty cell is written `null` in the expected rows.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The three tables these koans use, in full, so you can write a query without leaving
 * this file. The counts of what is MISSING are given because they are the whole point
 * of the lesson — you cannot reason about a filter until you know how many rows have
 * nothing in the column it tests.
 *
 *   "Suppliers" — 6 rows, 13 columns. "Region" is EMPTY on 3 of the 6 and filled on
 *   the other 3 ('LA', 'MI', 'Victoria'). "PostalCode" is empty on 3. "Fax" and
 *   "HomePage" are empty on ALL SIX — not one supplier has given us either.
 *     "SupplierID"      INTEGER        "CompanyName"     VARCHAR
 *     "ContactName"     VARCHAR        "ContactTitle"    VARCHAR
 *     "Address"         VARCHAR        "City"            VARCHAR
 *     "Region"          VARCHAR        "PostalCode"      VARCHAR
 *     "Country"         VARCHAR        "Phone"           VARCHAR
 *     "Fax"             VARCHAR        "HomePage"        VARCHAR
 *     "Email"           VARCHAR
 *
 *   "Employees" — 3 rows, 20 columns. Only the ones you need are listed. "ReportsTo"
 *   holds the "EmployeeID" of that person's manager, and it is EMPTY on exactly one
 *   row — because that person is the boss and reports to nobody.
 *     "EmployeeID"      INTEGER        "FirstName"       VARCHAR
 *     "LastName"        VARCHAR        "Title"           VARCHAR
 *     "ReportsTo"       INTEGER        "Country"         VARCHAR
 *
 *   "Products" — 20 rows, 10 columns. "UnitsInStock" is how many we have on the shelf
 *   right now. It is never empty — but it IS zero on exactly two lines, which is a
 *   different thing, and koan 9 is about turning one into the other.
 *     "ProductID"       INTEGER        "ProductName"     VARCHAR
 *     "SupplierID"      INTEGER        "CategoryID"      INTEGER
 *     "QuantityPerUnit" VARCHAR        "UnitPrice"       DECIMAL(19,4)
 *     "UnitsInStock"    SMALLINT       "UnitsOnOrder"    SMALLINT
 *     "ReorderLevel"    SMALLINT       "Discontinued"    BOOLEAN
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class NullAndThreeValuedLogicKoans extends KoanBase {

    // 1) Which of our suppliers have never told us their region? The obvious way to write
    //    that is `= NULL`, and it runs, and it returns nothing at all. Fill in the test that
    //    actually works — TWO WORDS, and neither of them is an equals sign.
    //    (Predict first: three of the six. Try the equals sign in this blank before you
    //     settle on the right answer, and notice that nothing warns you — you get an empty
    //     result and no error to explain it.)
    def "the test for an empty cell is two words, not an equals sign"() {
        expect:
        shouldReturn([["Exotic Liquids"], ["Pasta Buttini s.r.l."], ["Tokyo Traders"]], '''
            SELECT s."CompanyName"
            FROM "Suppliers" s
            WHERE s."Region" ___
            ORDER BY s."CompanyName"
        ''')
    }

    // 2) PREDICT — the size of the silence. Both queries below are already written and both
    //    run without complaint. Say how many rows each one returns BEFORE you look, then put
    //    your two numbers in.
    //    (An equals sign asks "is the thing in this cell the same as the thing on the right?"
    //     When the cell holds nothing, that question has no yes and no no — the answer is
    //     UNKNOWN, and WHERE keeps only the rows where the answer is TRUE.)
    def "predict: what an equals sign against NULL really returns"() {
        given: "the equals sign — which looks perfectly reasonable"
        int withEquals = rows('''
            SELECT s."CompanyName" FROM "Suppliers" s WHERE s."Region" = NULL
        ''').size()

        and: "and the test that means what you meant"
        int withIsNull = rows('''
            SELECT s."CompanyName" FROM "Suppliers" s WHERE s."Region" IS NULL
        ''').size()

        expect:
        withEquals == ___
        withIsNull == ___
    }

    // 3) The other half of the pair. Now the suppliers whose region we DO have — name and
    //    region together, so you can see what a filled-in cell looks like beside the three
    //    empty ones from koan 1. Fill in the test for "there is something here".
    //    (Three words this time. Predict first: three suppliers, and the regions are 'MI',
    //     'LA' and 'Victoria' — one of which is about to become important.)
    def "the other half of the pair: the rows that do have a value"() {
        expect:
        shouldReturn([["Grandma Kellys Homestead", "MI"],
                      ["New Orleans Cajun Delights", "LA"],
                      ["Pavlova Ltd", "Victoria"]], '''
            SELECT s."CompanyName", s."Region"
            FROM "Suppliers" s
            WHERE s."Region" ___
            ORDER BY s."CompanyName"
        ''')
    }

    // 4) THE ONE THIS LESSON EXISTS FOR, and it is the mistake that ships in real reports.
    //
    //    THE JOB: every supplier who is NOT in Victoria. There are six suppliers and exactly
    //    one of them is in Victoria, so the answer is obviously five.
    //
    //    Write `WHERE s."Region" <> 'Victoria'` on its own and you get TWO ROWS. Not five.
    //    The three suppliers with no region at all are in neither list: asked whether their
    //    empty cell is different from 'Victoria', the database answers UNKNOWN, and UNKNOWN
    //    is not TRUE, so the row is dropped. It is dropped just as silently by the opposite
    //    filter, `= 'Victoria'`, which returns one. Two plus one is three, out of six.
    //
    //    Fill in the keyword that joins the rescue on. It is not AND — AND would ask for a
    //    region that is both not-Victoria and missing, which nothing can be.
    //    (Predict first: five rows, and three of them are the ones you found in koan 1.)
    def "the trap: a not-equals filter leaves the empty rows out"() {
        expect:
        shouldReturn([["Exotic Liquids"], ["Grandma Kellys Homestead"],
                      ["New Orleans Cajun Delights"], ["Pasta Buttini s.r.l."],
                      ["Tokyo Traders"]], '''
            SELECT s."CompanyName"
            FROM "Suppliers" s
            WHERE s."Region" <> 'Victoria'
               ___ s."Region" IS NULL
            ORDER BY s."CompanyName"
        ''')
    }

    // 5) PREDICT — and this is the one people get wrong even after koan 4.
    //    The natural next thought is "fine, I'll wrap the whole thing in NOT". Below, the
    //    first query does exactly that. Say what each one returns before you run it.
    //    (NOT flips TRUE to FALSE and FALSE to TRUE. It does NOT flip UNKNOWN — the opposite
    //     of "I don't know" is still "I don't know" — so the empty rows stay dropped. The
    //     second query is the same test with the rescue from koan 4 added.)
    def "predict: NOT does not rescue you either"() {
        given: "the whole condition wrapped in NOT, which changes nothing that matters"
        int wrappedInNot = rows('''
            SELECT s."CompanyName" FROM "Suppliers" s
            WHERE NOT (s."Region" = 'Victoria')
        ''').size()

        and: "and the same query, saying out loud what should happen to the empty cells"
        int andSayingWhatToDo = rows('''
            SELECT s."CompanyName" FROM "Suppliers" s
            WHERE NOT (s."Region" = 'Victoria') OR s."Region" IS NULL
        ''').size()

        expect:
        wrappedInNot == ___
        andSayingWhatToDo == ___
    }

    // 6) Filtering is one problem; PRINTING is another. A report with three blank cells in it
    //    looks broken even when it is correct. Fill in the function that hands back the first
    //    of its arguments that is not empty — so an empty region is printed as words instead
    //    of as nothing.
    //    (Predict first: six rows, three of them reading 'no region'. This does NOT change
    //     the data — it changes what this one query returns.)
    def "give the empty cell something to stand in for it"() {
        expect:
        shouldReturn([["Exotic Liquids", "no region"],
                      ["Grandma Kellys Homestead", "MI"],
                      ["New Orleans Cajun Delights", "LA"],
                      ["Pasta Buttini s.r.l.", "no region"],
                      ["Pavlova Ltd", "Victoria"],
                      ["Tokyo Traders", "no region"]], '''
            SELECT s."CompanyName", ___(s."Region", 'no region')
            FROM "Suppliers" s
            ORDER BY s."CompanyName"
        ''')
    }

    // 7) NOW THE ONE THAT BITES HARDEST, because the damage is not where you are looking.
    //    Glue a city and a region together into one address line and the three suppliers with
    //    no region do not lose their region — THEY LOSE THE WHOLE LINE. City included.
    //    Anything combined with nothing is nothing, so one empty piece empties the result.
    //    Write the whole middle of that expression: the part between the comma and the AS.
    //    (Predict first: six rows, and 'London, no region' is one of them. Write it without
    //     the fix first and count how many of the six come back with a line at all — the
    //     answer is three, and the other three are not blank in the region, they are blank
    //     from end to end.)
    def "one missing piece empties the whole line"() {
        expect:
        shouldReturn([["Exotic Liquids", "London, no region"],
                      ["Grandma Kellys Homestead", "Ann Arbor, MI"],
                      ["New Orleans Cajun Delights", "New Orleans, LA"],
                      ["Pasta Buttini s.r.l.", "Salerno, no region"],
                      ["Pavlova Ltd", "Melbourne, Victoria"],
                      ["Tokyo Traders", "Tokyo, no region"]], '''
            SELECT s."CompanyName",
                   s."City" || ', ' || ___ AS "Where"
            FROM "Suppliers" s
            ORDER BY s."CompanyName"
        ''')
    }

    // 8) THE ONE THAT QUIETLY CHANGES A NUMBER IN A REPORT. count(*) counts ROWS. count of a
    //    COLUMN counts the values in it, and skips every empty cell — so the two disagree by
    //    exactly the number of blanks.
    //    Here: how many people work here, and how many of them have a manager? Fill in the
    //    column that holds the manager — find it in the schema at the top of this file.
    //    (Predict first: three and two. The person who is missing from the second number is
    //     not missing from the company — they are the boss, and a boss reports to nobody.
    //     A count that quietly excludes your boss is how a headcount goes wrong.)
    def "count(*) counts rows, count(column) counts values"() {
        expect:
        shouldReturn([[3, 2]], '''
            SELECT count(*) AS "Employees",
                   count(e.___) AS "WithAManager"
            FROM "Employees" e
        ''')
    }

    // 9) COALESCE BACKWARDS. That one swapped an empty cell for a value; this one swaps a
    //    value for an empty cell — it returns NULL when its two arguments are equal, and the
    //    original value otherwise.
    //    Two lines in the catalogue are at zero stock. Turn that zero into a genuine "nothing
    //    here" so a later calculation cannot divide by it. Fill in the function.
    //    (Predict first: two empty cells and a 6. And be clear about what you are doing —
    //     zero and empty are NOT the same thing, which is exactly why the swap has to be
    //     asked for by name rather than happening on its own.)
    def "NULLIF is COALESCE backwards"() {
        expect:
        shouldReturn([["Gorgonzola Telino", null],
                      ["Thuringer Rostbratwurst", null],
                      ["Scottish Longbreads", 6]], '''
            SELECT p."ProductName", ___(p."UnitsInStock", 0) AS "InStock"
            FROM "Products" p
            ORDER BY p."UnitsInStock", p."ProductName"
            LIMIT 3
        ''')
    }

    // 10) The whole query — no scaffolding, and everything above it in one go.
    //     THE QUESTION: which of our suppliers are NOT in Victoria, and where are they?
    //       · not in Victoria                     -> and the ones with no region count too,
    //                                                because we do not know that they ARE
    //       · print the region                    -> 'no region' where we have none
    //       · return "CompanyName" and the region, in that order
    //       · company name, ascending
    //     TWO OF TODAY'S IDEAS IN ONE QUERY, and that is the exam. The filter has to say out
    //     loud what happens to the empty cells, or you lose three of the five rows; and the
    //     output has to stand in for them, or three cells come back blank.
    //     (Five rows. Get four or two and you have made exactly one of the two mistakes.)
    def "write the whole query: the suppliers outside Victoria"() {
        expect:
        shouldReturn([["Exotic Liquids", "no region"],
                      ["Grandma Kellys Homestead", "MI"],
                      ["New Orleans Cajun Delights", "LA"],
                      ["Pasta Buttini s.r.l.", "no region"],
                      ["Tokyo Traders", "no region"]], '''
            ___
        ''')
    }
}
