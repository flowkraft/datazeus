package datazeus.learnsql.series2._10

import datazeus._internal.NorthwindCoKoanBase
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
 *      five Marches land in one group. date_trunc('month', …) keeps the year.
 *   2. RANGES ARE HALF-OPEN:  >= the first day  AND  < the first day after.
 *      BETWEEN includes both ends, so it counts a boundary twice, or it misses the
 *      afternoon of the last day.
 *   3. ANCHOR "RECENT" ON THE DATA when the data is not live. This dataset ends on
 *      31 December 2024, so CURRENT_DATE - INTERVAL '7 days' finds nothing.
 *   4. CAST TO DATE BEFORE YOU SUBTRACT. A timestamp minus a timestamp is an
 *      INTERVAL — a length of time, which you cannot compare with a plain number.
 *      A DATE minus a DATE is a whole number of days.
 *
 * ── THESE ARE NOT THE LESSON'S QUERIES ──────────────────────────────────────
 *
 * The same ideas, in the same order, on DIFFERENT TABLES. The lesson counts orders by
 * "OrderDate", ships November 2024, fills one customer's months and counts the orders
 * with no ship date week by week. Here you work with the invoices, the web shop's
 * orders and the employees.
 *
 * TEN KOANS, EASIEST FIRST:
 *   1    date_trunc keeps the year: how many months had an invoice
 *   2    diagnose: five Marches in one count
 *   3    a half-open range: invoices issued in the first quarter of 2024
 *   4    diagnose: BETWEEN misses the evening of the last day
 *   5    anchor "recent" on the data: the last week of invoicing
 *   6    a date plus an INTERVAL: five years' service when the data starts
 *   7    a date minus a date: the quickest and the slowest payment
 *   8    diagnose: an INTERVAL is not a number
 *   9    write the whole query: every day of January 2020, with its web orders
 *  10    write the whole query: the invoices still unpaid, and the oldest of them
 *
 * These run on DuckDB, and every one returns the SAME answer on the PostgreSQL in
 * CloudBeaver (after SET search_path TO northwind_co_s). KEEP THE DOUBLE QUOTES on every
 * name, spelled as the schema spells them: DuckDB forgives a wrong capital letter and
 * PostgreSQL does not.
 *
 * ── RELEVANT SCHEMA (Northwind Company, 2020-01-01 to 2024-12-31) ──────────
 *
 *   "Invoices" — 9532 rows. ONE INVOICE PER ORDER, created when the order's ship date
 *     is recorded.
 *     "InvoiceID"       INTEGER        "OrderID"         INTEGER
 *     "InvoiceDate"     DATE           — 2020-01-04 to 2024-12-29
 *     "Amount"          DECIMAL(19,2)  "Freight"         DECIMAL(19,2)
 *     "PaidDate"        DATE           — empty for the 223 invoices not paid yet
 *   Both dates are DATEs, not timestamps: no time of day.
 *
 *   "WebOrders" — 2765 rows. The web shop's copy of every order placed on the web.
 *     "WebOrderID"      INTEGER        "OrderID"         INTEGER — the same order in "Orders"
 *     "ReceivedAt"      TIMESTAMP      — WITH a time of day: 2024-12-31 15:33:00
 *     "Payload"         VARCHAR        — the raw JSON the shop sent (not needed here)
 *
 *   "Orders" — 10000 rows. The one column these koans use: "OrderDate" TIMESTAMP, always
 *     at midnight.
 *
 *   "Employees" — 12 rows. "EmployeeID" INTEGER, "FirstName" VARCHAR, "LastName" VARCHAR,
 *     "HireDate" DATE, and title, address and contact columns these koans do not need.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait (the path to enlightenment)
class DatesAndTimesKoans extends NorthwindCoKoanBase {

    // 1) date_trunc KEEPS THE YEAR. Group the invoices by month and count the groups.
    //    Fill in which part of the date to truncate to.
    //    (Predict: invoices run from January 2020 to December 2024. With
    //     EXTRACT(MONTH FROM "InvoiceDate") you would get 12 — month numbers, every year
    //     folded together. How many real months is that?)
    def "date_trunc keeps the year: how many months had an invoice"() {
        expect:
        shouldReturn 60, '''
            SELECT count(*)
            FROM (SELECT date_trunc(___, "InvoiceDate") AS "Month"
                  FROM "Invoices"
                  GROUP BY "Month") AS t
        '''
    }

    // 2) DIAGNOSE: FIVE MARCHES IN ONE COUNT. The question was "how many invoices were paid
    //    in March 2024?", and somebody wrote
    //        WHERE EXTRACT(MONTH FROM "PaidDate") = 3
    //    It returned 839. What else did it count? Write a WHERE that keeps the year.
    //    (A half-open range works; so does date_trunc('month', "PaidDate") = DATE '2024-03-01'.)
    def "diagnose: five Marches in one count"() {
        expect:
        shouldReturn 191, '''
            SELECT count(*)
            FROM "Invoices"
            WHERE ___
        '''
    }

    // 3) A HALF-OPEN RANGE. Invoices issued in the first quarter of 2024: on or after
    //    January 1, and before the first day AFTER the quarter. Fill in the second half.
    //    (No 31, no 23:59:59 — the next quarter's first day is always the right edge.)
    def "a half-open range: invoices issued in the first quarter of 2024"() {
        expect:
        shouldReturn 526, '''
            SELECT count(*)
            FROM "Invoices"
            WHERE "InvoiceDate" >= DATE '2024-01-01'
              AND ___
        '''
    }

    // 4) DIAGNOSE: BETWEEN MISSES THE EVENING OF THE LAST DAY. How many web orders reached
    //    the web shop in December 2024? Somebody wrote
    //        WHERE "ReceivedAt" BETWEEN DATE '2024-12-01' AND DATE '2024-12-31'
    //    and got 100. "ReceivedAt" has a time of day, and against a timestamp
    //    DATE '2024-12-31' is midnight at the START of December 31 — so every order received
    //    later that day is past the end of the range. Write the half-open version.
    //    (Predict how many were received on the 31st before you run it.)
    def "diagnose: BETWEEN misses the evening of the last day"() {
        expect:
        shouldReturn 104, '''
            SELECT count(*)
            FROM "WebOrders"
            WHERE ___
        '''
    }

    // 5) ANCHOR "RECENT" ON THE DATA. How many invoices were issued in the last 7 days of
    //    invoicing? CURRENT_DATE would find none — this data stops at the end of 2024. Fill in
    //    the latest invoice date, worked out by the database.
    //    (A subquery in brackets, from the lesson on subqueries.)
    def "anchor recent on the data: the last week of invoicing"() {
        expect:
        shouldReturn 67, '''
            SELECT count(*)
            FROM "Invoices"
            WHERE "InvoiceDate" >= ___ - INTERVAL '7 days'
        '''
    }

    // 6) A DATE PLUS AN INTERVAL. How many employees had worked for the company for at least
    //    five years on the day the data starts, 1 January 2020? Fill in what to add to the
    //    hire date.
    //    (An INTERVAL is not only days: '5 years' and '3 months' work too.)
    def "a date plus an INTERVAL: five years' service when the data starts"() {
        expect:
        shouldReturn 3, '''
            SELECT count(*)
            FROM "Employees"
            WHERE "HireDate" + ___ <= DATE '2020-01-01'
        '''
    }

    // 7) A DATE MINUS A DATE. How many days did the quickest and the slowest payment take,
    //    from the invoice date to the paid date? Both columns are already DATEs, so the
    //    difference is a whole number of days. Fill in both blanks.
    //    (Invoices with no "PaidDate" give NULL, and min and max skip it.)
    def "a date minus a date: the quickest and the slowest payment"() {
        expect:
        shouldReturn([[10, 49]], '''
            SELECT min("PaidDate" - ___) AS "Quickest",
                   max("PaidDate" - ___) AS "Slowest"
            FROM "Invoices"
        ''')
    }

    // 8) DIAGNOSE: AN INTERVAL IS NOT A NUMBER. How many web orders reached the web shop
    //    MORE than one day after their order date? Somebody wrote
    //        WHERE w."ReceivedAt" - o."OrderDate" > 1
    //    and neither database runs it: a timestamp minus a timestamp is an INTERVAL, and an
    //    interval cannot be compared with the number 1. Write a WHERE that compares whole
    //    days with days.
    //    (Most arrive on the day itself. A few were keyed in late.)
    def "diagnose: an INTERVAL is not a number"() {
        expect:
        shouldReturn 31, '''
            SELECT count(*)
            FROM "WebOrders" w
            JOIN "Orders" o ON o."OrderID" = w."OrderID"
            WHERE ___
        '''
    }

    // 9) The whole query — no scaffolding.
    //    THE QUESTION: in January 2020, the web shop's first month in the data, how many days
    //    were there, how many web orders, and how many days had no web order at all?
    //      · one row, three columns, in that order: "Days", "Web orders", "Days with none"
    //      · build every day of January 2020 first:
    //          generate_series(TIMESTAMP '2020-01-01', TIMESTAMP '2020-01-31', INTERVAL '1 day')
    //      · LEFT JOIN the web orders onto the days by the DAY they were received
    //                                        -> date_trunc('day', "ReceivedAt")
    //    THE TRAP FROM THE LESSON: count(*) counts every row of the join, an empty day's
    //    row included — and a day with two orders has two rows. Count the days with
    //    count(DISTINCT …), the orders with something that is NULL on an empty day, and
    //    the days with none as the difference between count(*) and the orders.
    def "write the whole query: every day of January 2020, with its web orders"() {
        expect:
        shouldReturn([[31, 15, 18]], '''
            ___
        ''')
    }

    // 10) The whole query again.
    //     THE QUESTION: how many invoices have no "PaidDate" yet, and how many days before the
    //     last invoice date in the data was the OLDEST of them issued?
    //       · one row, two columns, in that order: "Unpaid", "Oldest, in days"
    //       · anchor on the data: the latest "InvoiceDate", in a subquery
    //       · both columns are DATEs, so subtracting gives whole days
    //     CHECK IT: the oldest unpaid invoice has the SMALLEST invoice date.
    def "write the whole query: the invoices still unpaid, and the oldest of them"() {
        expect:
        shouldReturn([[223, 44]], '''
            ___
        ''')
    }
}
