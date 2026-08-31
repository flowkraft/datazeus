package datazeus.learnsql.series1._20

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 1 · 20 Aliases, Expressions & DISTINCT   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series1 _20  (Windows)   ./zeus.sh koans learnsql series1 _20  (macOS/Linux)
 *
 * The koan runs YOUR query and compares the result to the goal. PREDICT the answer
 * first (that's the skill) — if it comes back wrong, the hint shows what your query
 * returned vs what it should, so you can fix the SQL, not guess a value.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * Same ten ideas, in the same order, asked about DIFFERENT tables. The lesson names
 * columns on the product catalogue and counts countries on the customer list; here
 * you price up ORDER LINES and tidy up the SUPPLIER list. Copying a query across
 * from the lesson will not work — which is the point. You learn the idea by applying
 * it somewhere new, not by retyping an answer you just watched.
 *
 * TEN KOANS, EASIEST FIRST, IN THE ORDER THE LESSON BUILDS THEM:
 *   1    give a column a heading of your own
 *   2    an expression: a column the table has not got
 *   3    name the expression, and sort by the name
 *   4    ROUND, so a calculation is money again
 *   5-6  join text end to end, and mind the spaces
 *   7    DISTINCT: one row per value
 *   8    THE TRAP: DISTINCT looks at the whole row
 *   9    …and the same shape written on purpose
 *   10   the whole query, from scratch
 *
 * ── WHY THERE IS NO KOAN FOR "THE ALIAS IN WHERE" ───────────────────────────
 *
 * The lesson spends five slides on it: `ORDER BY` can use a name you invented and
 * `WHERE` cannot, because WHERE runs before the select list is built. It is one of
 * the most useful things in the episode and it is deliberately NOT a koan.
 *
 * The reason is mechanical. These koans run on DuckDB, and DuckDB ACCEPTS
 * `WHERE "Line total" > 1500` — PostgreSQL rejects it with SQLSTATE 42703, and so
 * does the SQL standard. A koan asking you to fix that would go GREEN on DuckDB for
 * the very answer CloudBeaver refuses, which is the worst thing a koan can do: it
 * would teach you that something works, and the database you actually use would then
 * disagree. So that rule lives in the video, the written lesson and the flashcards,
 * where nothing has to be executed for it to be true.
 *
 * Same reasoning, same engines, for `SELECT DISTINCT … ORDER BY <a column you did
 * not select>`: PostgreSQL rejects it (42P10), DuckDB allows it. Article only.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 * The two tables these koans use, in full, so you can write a query without leaving
 * this file.
 *
 *   "Order Details" — 193 rows, 5 columns. ONE ROW PER PRODUCT ON AN ORDER, so an
 *   order with three products has three rows here. Note the SPACE in the table name:
 *   that is exactly why every identifier in this course is wrapped in double quotes.
 *   "UnitPrice" here is what the product cost ON THAT ORDER, which is not necessarily
 *   today's price in "Products". "Discount" is a fraction: 0.0000, 0.0500 or 0.1000.
 *     "OrderID"    INTEGER        "ProductID"  INTEGER
 *     "UnitPrice"  DECIMAL(19,4)  "Quantity"   SMALLINT
 *     "Discount"   DECIMAL(8,4)
 *
 *   "Suppliers" — 6 rows, 12 columns. The companies we buy from. Two of the six are
 *   in the same country, which is what koans 7 to 9 are built on.
 *     "SupplierID"   INTEGER   "CompanyName"  VARCHAR
 *     "ContactName"  VARCHAR   "ContactTitle" VARCHAR
 *     "Address"      VARCHAR   "City"         VARCHAR
 *     "Region"       VARCHAR   "PostalCode"   VARCHAR
 *     "Country"      VARCHAR   "Phone"        VARCHAR
 *     "Fax"          VARCHAR   "HomePage"     VARCHAR
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class DistinctAliasesExpressionsKoans extends KoanBase {

    // 1) A heading of your own. `AS` renames a column on the way out — the data does not
    //    change, only what it is called. Fill in the alias this query is already sorting by.
    //    Note that it has a SPACE and a capital letter in it, which is only possible because
    //    it is wrapped in double quotes; write it without them and the query will not parse.
    //    (Predict first: sorting by country puts Australia at the top.)
    def "a heading with spaces needs double quotes"() {
        expect:
        shouldReturn([["Pavlova Ltd", "Australia"],
                      ["Pasta Buttini s.r.l.", "Italy"],
                      ["Tokyo Traders", "Japan"]], '''
            SELECT "CompanyName" AS "Supplier",
                   "Country" AS ___
            FROM "Suppliers"
            ORDER BY "Home country", "Supplier"
            LIMIT 3
        ''')
    }

    // 2) A column the table has not got. There is no line-total column in "Order Details" —
    //    you build one out of two columns that ARE there. Fill in the operator that turns a
    //    price and a quantity into what that line cost.
    //    (Order 4 bought 12 of product 1 at 18.0000 each. Work it out before you run it.)
    def "an expression makes a column the table has not got"() {
        expect:
        shouldReturn 216.0000, '''
            SELECT "UnitPrice" ___ "Quantity"
            FROM "Order Details"
            WHERE "OrderID" = 4 AND "ProductID" = 1
        '''
    }

    // 3) Name it, and the sort can use the name. Give the calculation a heading with `AS` and
    //    `ORDER BY` will take that heading instead of making you write the whole calculation
    //    out a second time. Fill in what belongs after ORDER BY.
    //    (The three dearest lines in the whole table are all the same product at the same
    //     price, which is why "OrderID" is there as a second sort key — episode 15's lesson,
    //     still earning its keep.)
    def "name it, and the sort can use that name"() {
        expect:
        // A ROW SET, not a single value: every one of these rows has the same line total, so
        // checking one cell would leave the blank free to be almost anything.
        shouldReturn([[25, 2352.0100], [45, 2352.0100], [65, 2352.0100]], '''
            SELECT "OrderID", "UnitPrice" * "Quantity" AS "Line total"
            FROM "Order Details"
            ORDER BY ___ DESC, "OrderID"
            LIMIT 3
        ''')
    }

    // 4) Money again. Take the discount off and the answer arrives with EIGHT decimal places —
    //    59.37500000 is not something anybody can be charged. Fill in how many decimal places
    //    a price has.
    //    (Order 10 has two lines. One is a round number; the other is the one that needs
    //     rounding, and it rounds UP.)
    def "ROUND turns a calculation back into money"() {
        expect:
        shouldReturn([[136.00], [59.38]], '''
            SELECT ROUND("UnitPrice" * "Quantity" * (1 - "Discount"), ___) AS "Net total"
            FROM "Order Details"
            WHERE "OrderID" = 10
            ORDER BY "ProductID"
        ''')
    }

    // 5) An expression does not have to be arithmetic. Two of the same character, side by side,
    //    glue text end to end. Fill in that operator so the town and the country arrive as one
    //    readable column.
    def "two of the same character glue text end to end"() {
        expect:
        shouldReturn([["London, UK"], ["Ann Arbor, USA"], ["New Orleans, USA"]], '''
            SELECT "City" ___ ', ' || "Country" AS "Based in"
            FROM "Suppliers"
            ORDER BY "CompanyName"
            LIMIT 3
        ''')
    }

    // 6) The text between the pieces is a string of its own, and the spaces inside its quotes
    //    are yours to get right. Fill in the piece that turns a name and a company into a line
    //    somebody could read down the phone. Miss the spaces and the whole line closes up:
    //    "Charlotte CooperatExotic Liquids".
    def "the text between the pieces is a string of its own"() {
        expect:
        shouldReturn([["Charlotte Cooper at Exotic Liquids"],
                      ["Giovanni Giudici at Pasta Buttini s.r.l."]], '''
            SELECT "ContactName" || ___ || "CompanyName" AS "Who to call"
            FROM "Suppliers"
            ORDER BY "ContactName"
            LIMIT 2
        ''')
    }

    // 7) One row per VALUE, not one row per record. Six suppliers, and asking for their
    //    countries plainly gives you six rows with one country in it twice. Fill in the one
    //    word that collapses the copies.
    //    (Checkable fact: there are five answers, and the doubled one is the USA.)
    def "DISTINCT gives one row per value, not one per record"() {
        expect:
        shouldReturn([["Australia"], ["Italy"], ["Japan"], ["UK"], ["USA"]], '''
            SELECT ___ "Country"
            FROM "Suppliers"
            ORDER BY "Country"
        '''
        )
    }

    // 8) THE ONE THIS LESSON EXISTS FOR — and this one is a DIAGNOSIS, not a fill-in.
    //
    //    Somebody wanted the list of discount rates the company actually gives, and wrote:
    //
    //        SELECT DISTINCT "Discount", "OrderID" FROM "Order Details"
    //
    //    They got 155 rows back. There are three discount rates. Nothing errored, the word
    //    DISTINCT was right there in the query, and the report was wrong.
    //
    //    Work out WHY before you fix it: DISTINCT is not a function on the column beside it —
    //    it looks at the WHOLE ROW, and almost every row has a different "OrderID", so almost
    //    every row is unique and survives. Every column you add is one more way for a row to
    //    be different, and one more way for it to stay.
    //
    //    Write the select list that answers the question that was actually asked.
    def "DISTINCT looks at the whole row, not the first column"() {
        expect:
        shouldReturn([[0.0000], [0.0500], [0.1000]], '''
            SELECT DISTINCT ___
            FROM "Order Details"
            ORDER BY "Discount"
        ''')
    }

    // 9) …and now the same shape, written ON PURPOSE. Koan 8 is not a rule that two columns
    //    after DISTINCT are wrong — it is a rule that they answer a different question. Here
    //    the question really is "which town, in which country, do we buy from", so the second
    //    column belongs. Fill it in.
    //    (Six suppliers, five countries — and this time six rows is the RIGHT answer, because
    //     the two American suppliers are in two different towns.)
    def "two columns, when two columns is what you meant"() {
        expect:
        shouldReturn([["Australia", "Melbourne"], ["Italy", "Salerno"], ["Japan", "Tokyo"],
                      ["UK", "London"], ["USA", "Ann Arbor"], ["USA", "New Orleans"]], '''
            SELECT DISTINCT "Country", ___
            FROM "Suppliers"
            ORDER BY "Country", "City"
        ''')
    }

    // 10) The whole query — no scaffolding, and everything above it in one go.
    //     THE QUESTION: price up order 11 as a report somebody could read.
    //       · only the lines on order 11        -> "OrderID" is 11
    //       · the product, under the heading    -> "Product"
    //       · what the line came to after the
    //         discount, rounded to two places,
    //         under the heading                 -> "Line total"
    //       · dearest line first
    //     Five clauses, in the only order SQL accepts them: SELECT, FROM, WHERE, ORDER BY.
    //     (The headings are not checked — the koan compares values — but write them anyway.
    //      A report is the thing this whole lesson is about, and koan 3 showed you that
    //      naming the calculation is what lets ORDER BY stay short.)
    def "write the whole query: one order, priced up"() {
        expect:
        shouldReturn([[12, 823.20], [11, 552.90], [10, 35.00]], '''
            ___
        ''')
    }
}
