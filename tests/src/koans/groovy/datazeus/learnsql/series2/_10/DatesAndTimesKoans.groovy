package datazeus.learnsql.series2._10

import datazeus._internal.KoanBase
import spock.lang.Stepwise

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SQL KOANS — Learn SQL · Series 2 · 10   Dates & Times                   ║
 * ║                                          Getting Ranges and Boundaries   ║
 * ║                                          Right                           ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * You don't fill in a number here — you WRITE THE QUERY. Each koan blanks the one
 * piece that is the lesson; replace the `___`, then run
 *
 *     zeus.bat koans learnsql series2 _10  (Windows)   ./zeus.sh koans learnsql series2 _10  (macOS/Linux)
 *
 * PREDICT the answer first — if it comes back wrong, the hint shows what your query
 * returned against what it should, so you fix the SQL rather than guess a value.
 *
 * SOME OF THESE ARE DIAGNOSES. The comment shows a query somebody really wrote, and
 * what it returned. It runs, or it nearly does, and it is wrong. Say why before you
 * write the fix.
 *
 * ── THE FOUR HABITS ─────────────────────────────────────────────────────────
 *
 *   1. GROUP BY date_trunc, NOT EXTRACT. EXTRACT(MONTH …) throws the year away, so
 *      two Mays land in one group. date_trunc('month', …) keeps the year.
 *   2. RANGES ARE HALF-OPEN:  >= the first day  AND  < the first day after.
 *      BETWEEN includes both ends, so it counts a boundary twice, or it misses the
 *      afternoon of the last day.
 *   3. ANCHOR "RECENT" ON THE DATA when the data is not live. This dataset ends in
 *      June 2024, so CURRENT_DATE - INTERVAL '30 days' finds nothing.
 *   4. CAST TO DATE BEFORE YOU SUBTRACT. A timestamp minus a timestamp is an
 *      INTERVAL — a length of time, which you cannot compare with a plain number.
 *      A DATE minus a DATE is a whole number of days.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, on DIFFERENT COLUMNS. The lesson counts orders by
 * "OrderDate", ships July 2023 and ages the orders with no ship date. Here you work with
 * "ShippedDate", "RequiredDate", three made-up delivery times, and the employees.
 *
 * TEN KOANS, EASIEST FIRST:
 *   1    date_trunc keeps the year: how many quarters had a shipment
 *   2    diagnose: two Mays in one count
 *   3    a half-open range: shipped in the first quarter of 2024
 *   4    diagnose: BETWEEN misses the evening of the last day
 *   5    anchor "recent" on the data: the last week of shipping
 *   6    a date plus an INTERVAL: two weeks to deliver
 *   7    a date minus a date: whole days from order to required date
 *   8    diagnose: an INTERVAL is not a number
 *   9    write the whole query: every day of May 2024, with its shipments
 *  10    write the whole query: days since each employee's last order
 *
 * These run on DuckDB, and every one returns the SAME answer on the PostgreSQL in
 * CloudBeaver. KEEP THE DOUBLE QUOTES on every name, spelled as the schema spells them:
 * DuckDB forgives a wrong capital letter and PostgreSQL does not.
 *
 * ── RELEVANT SCHEMA ─────────────────────────────────────────────────────────
 *
 *   "Orders" — 79 rows. The columns these koans use:
 *     "OrderID"         INTEGER
 *     "EmployeeID"      INTEGER
 *     "OrderDate"       TIMESTAMP   — 2022-12-05 to 2024-06-12
 *     "RequiredDate"    TIMESTAMP   — when the customer needs the order
 *     "ShippedDate"     TIMESTAMP   — 2022-12-16 to 2024-06-13; empty for the 27
 *                                     orders not shipped yet
 *   Every timestamp in "Orders" is at midnight. Koan 4 shows why you still cannot
 *   rely on that.
 *
 *   "Employees" — 3 rows. "EmployeeID" INTEGER, "FirstName" VARCHAR, and name,
 *     address and contact columns these koans do not need.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class DatesAndTimesKoans extends KoanBase {

    // 1) date_trunc KEEPS THE YEAR. Group the shipments by quarter and count the groups.
    //    Fill in which part of the date to truncate to.
    //    (Predict: shipments run from December 2022 to June 2024. With
    //     EXTRACT(QUARTER FROM "ShippedDate") you would get 4 — quarter numbers, every
    //     year folded together. How many real quarters is that?)
    def "date_trunc keeps the year: how many quarters had a shipment"() {
        expect:
        shouldReturn 7, '''
            SELECT count(*)
            FROM (SELECT date_trunc(___, "ShippedDate") AS "Quarter"
                  FROM "Orders"
                  WHERE "ShippedDate" IS NOT NULL
                  GROUP BY "Quarter") AS t
        '''
    }

    // 2) DIAGNOSE: TWO MAYS IN ONE COUNT. The question was "how many orders shipped in
    //    May 2024?", and somebody wrote
    //        WHERE EXTRACT(MONTH FROM "ShippedDate") = 5
    //    It returned 9. What else did it count? Write a WHERE that keeps the year.
    //    (date_trunc('month', …) = DATE '2024-05-01' works; so does a half-open range.)
    def "diagnose: two Mays in one count"() {
        expect:
        shouldReturn 5, '''
            SELECT count(*)
            FROM "Orders"
            WHERE ___
        '''
    }

    // 3) A HALF-OPEN RANGE. Shipped in the first quarter of 2024: on or after January 1,
    //    and before the first day AFTER the quarter. Fill in the second half.
    //    (No 31, no 23:59:59 — the next quarter's first day is always the right edge.)
    def "a half-open range: shipped in the first quarter of 2024"() {
        expect:
        shouldReturn 9, '''
            SELECT count(*)
            FROM "Orders"
            WHERE "ShippedDate" >= DATE '2024-01-01'
              AND ___
        '''
    }

    // 4) DIAGNOSE: BETWEEN MISSES THE EVENING OF THE LAST DAY. Three deliveries, written
    //    straight into the query. Which of them happened in the first quarter of 2024?
    //    Somebody wrote
    //        WHERE delivered BETWEEN DATE '2024-01-01' AND DATE '2024-03-31'
    //    and got 1. Against a timestamp, DATE '2024-03-31' is midnight at the START of
    //    March 31, so the delivery at 18:30 that evening is later than the end of the
    //    range. Write the half-open version.
    //    (Predict how many of the three are in the quarter before you run it.)
    def "diagnose: BETWEEN misses the evening of the last day"() {
        expect:
        shouldReturn 2, '''
            SELECT count(*)
            FROM (VALUES (TIMESTAMP '2024-01-01 00:00:00'),
                         (TIMESTAMP '2024-03-31 18:30:00'),
                         (TIMESTAMP '2024-04-01 00:00:00')) AS t(delivered)
            WHERE ___
        '''
    }

    // 5) ANCHOR "RECENT" ON THE DATA. Which orders shipped in the last 7 days of shipping?
    //    CURRENT_DATE would find none — this data stops in June 2024. Fill in the latest
    //    shipped date, worked out by the database.
    //    (A subquery in brackets, from the lesson on subqueries. Two orders.)
    def "anchor recent on the data: the last week of shipping"() {
        expect:
        shouldReturn([[4], [6]], '''
            SELECT "OrderID"
            FROM "Orders"
            WHERE "ShippedDate" >= ___ - INTERVAL '7 days'
            ORDER BY "OrderID"
        ''')
    }

    // 6) A DATE PLUS AN INTERVAL. Most customers get two weeks: their required date is the
    //    order date plus 14 days. Fill in what to add.
    //    (Predict: 79 orders. Is it most of them, or nearly all?)
    def "a date plus an INTERVAL: two weeks to deliver"() {
        expect:
        shouldReturn 72, '''
            SELECT count(*)
            FROM "Orders"
            WHERE "RequiredDate" = "OrderDate" + ___
        '''
    }

    // 7) A DATE MINUS A DATE. How many days did each order give the customer, from the
    //    order date to the required date — and how many orders gave each? Fill in what to
    //    subtract so the answer is a whole number of days.
    //    (Six different answers. Koan 6 already told you the most common one.)
    def "a date minus a date: whole days from order to required date"() {
        expect:
        shouldReturn([[5, 1], [7, 1], [10, 3], [14, 72], [15, 1], [18, 1]], '''
            SELECT CAST("RequiredDate" AS DATE) - ___ AS "Days",
                   count(*) AS "Orders"
            FROM "Orders"
            GROUP BY "Days"
            ORDER BY "Days"
        ''')
    }

    // 8) DIAGNOSE: AN INTERVAL IS NOT A NUMBER. Which orders gave the customer MORE than
    //    two weeks? Somebody wrote
    //        WHERE "RequiredDate" - "OrderDate" > 14
    //    and neither database runs it: a timestamp minus a timestamp is an INTERVAL, and an
    //    interval cannot be compared with the number 14. Write a WHERE that compares days
    //    with days.
    //    (Koan 7's answer tells you how many.)
    def "diagnose: an INTERVAL is not a number"() {
        expect:
        shouldReturn 2, '''
            SELECT count(*)
            FROM "Orders"
            WHERE ___
        '''
    }

    // 9) The whole query — no scaffolding.
    //    THE QUESTION: for May 2024, how many days are there, how many shipments, and how
    //    many days had no shipment at all?
    //      · one row, three columns, in that order: "Days", "Shipments", "Days with none"
    //      · build every day of May 2024 first:
    //          generate_series(TIMESTAMP '2024-05-01', TIMESTAMP '2024-05-31', INTERVAL '1 day')
    //      · LEFT JOIN the orders onto the days by the DAY they shipped
    //                                        -> date_trunc('day', "ShippedDate")
    //    THE TRAP FROM THE LESSON: count(*) counts every day's row, empty or not. Count
    //    something that is NULL on an empty day for the shipments.
    //    (No day in May 2024 had two shipments, so days with a shipment = shipments.)
    def "write the whole query: every day of May 2024, with its shipments"() {
        expect:
        shouldReturn([[31, 5, 26]], '''
            ___
        ''')
    }

    // 10) The whole query again.
    //     THE QUESTION: for each employee, how many orders did they take, and how many days
    //     before the last order in the data was their own last order?
    //       · one row per "Employees"."FirstName", ordered by "FirstName"
    //       · return the first name, the orders, the days — in that order
    //       · anchor on the data: the latest "OrderDate" in "Orders", in a subquery
    //       · the days are a whole number: CAST both sides to DATE before subtracting
    //     CHECK IT: whoever took the very last order has 0.
    //     (Three rows.)
    def "write the whole query: days since each employee's last order"() {
        expect:
        shouldReturn([["Andrew", 24, 24],
                      ["Janet", 27, 0],
                      ["Nancy", 28, 2]], '''
            ___
        ''')
    }
}
