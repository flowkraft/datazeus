package datazeus.datamodeling.series3

import datazeus._internal.SchemaKoanBase
import spock.lang.Stepwise

/**
 * ╔════════════════════════════════════════════════════════════════════════════════════════╗
 * ║  THE CHECKS — Data Modeling · Series 3 · Dimensional Modeling
 * ╚════════════════════════════════════════════════════════════════════════════════════════╝
 *
 * BOILERPLATE — the shape is settled, the checks are not written yet. Brief below.
 *
 *     zeus.bat koans datamodeling series3     (Windows)
 *     ./zeus.sh koans datamodeling series3    (macOS/Linux)
 *
 * ── WHAT THIS FILE IS, AND WHY IT IS NOT KOANS ──────────────────────────────────────────
 * Decided with the owner 2026-09-14. A modelling decision has more than one right answer, so
 * Data Modeling has no fill-in-the-blank koans. The learner builds their own model in their
 * working file; this ONE file checks it, the way a team's CI checks a schema. It grows episode
 * by episode, in order, so a learner at episode 20 sees everything up to 20 green and the rest
 * waiting. The judgement questions that used to be predict / diagnose / choose koans are Anki
 * cards now (cards/cards.yaml in each lesson).
 *
 * THIS SERIES' CHECKS: answers · reconciles
 *
 * ── THE PROPERTY TO PROTECT, ABOVE ALL ───────────────────────────────────────────────────
 * ANY MODEL THAT PASSES IS A CORRECT ANSWER, INCLUDING ONES WE DID NOT THINK OF. A check that
 * can only pass on OUR table names tests conformance, not modelling: rewrite it or drop it.
 * Name every check after the BUSINESS RULE ("that copy is already out with someone else"),
 * never after the constraint ("unique violation on loan_copy_idx").
 *
 * ── THE VIEWS THE LEARNER WRITES ─────────────────────────────────────────────────────────
 * star.sql ends with these; the stars behind them are the learner's own design.
 *
 *     star.v_sales_line            (order_id, product_id, date_key, quantity, net_amount)
 *     star.v_order_pipeline        (order_id, order_date, required_date, shipped_date, days_to_ship)
 *     star.v_stock_month_end       (month, product_id, units)
 *     star.v_budget_vs_actual      (month, category_id, target_amount, actual_amount)
 *     star.v_revenue_by_territory  (territory_id, revenue)
 *
 * RECONCILES — the numbers, measured:
 *   sum(net_amount) = 58153.31 over 193 lines · 79 orders, exactly one shipped after its
 *   required date · last month of v_stock_month_end = main."Products"."UnitsInStock" ·
 *   sum(revenue) by territory = 58153.31 · actual_amount summed = 58153.31.
 *
 * ── THE PINCER ───────────────────────────────────────────────────────────────────────────
 * Pair every "must refuse" with a "must still accept". A model that makes everything impossible
 * is not a good model, and a learner who only sees rejections learns to over-constrain.
 */
@Stepwise // walk the checks in episode order — once one fails, the rest wait
class StarChecks extends SchemaKoanBase {

    // TODO: checks, one section per episode, in episode order.

    // ── Episode 10 · The Star Schema — reconciles
    //   the learner's turn: build the first star from your Series 1 views, and write the reconciliation query beside it.
    //   RECONCILE: sum of the fact's net amount = 58153.31, row count = 193. Write the check next
    //     to the DDL.

    // ── Episode 15 · Fact Grain & Additivity — reconciles
    //   the learner's turn: store the parts of every ratio, and compute month-end open orders without summing across months.

    // ── Episode 20 · Fact Table Types — reconciles, answers
    //   the learner's turn: build the accumulating snapshot and the month-end stock snapshot, reconcile stock to UnitsInStock, and find the late order.
    //   Check (answers): "orders by shipper" still counts all 79 orders — with a NULL shipper key
    //     the unshipped ones would silently vanish.

    // ── Episode 25 · The Date Dimension & Role-Playing — answers
    //   the learner's turn: generate dim_date for the whole range and join it as order date, required date and ship date.

    // ── Episode 30 · Degenerate & Junk Dimensions — answers
    //   the learner's turn: keep the order number in the fact, and give the stray flags one junk dimension.

    // ── Episode 35 · Slowly Changing Dimensions — answers
    //   the learner's turn: move a customer to a new city and keep last year's revenue by city unchanged.

    // ── Episode 40 · Bridge Tables — reconciles
    //   the learner's turn: report revenue by territory through a bridge that still totals 58153.31.
    //   Reconciliation check: weighted revenue by territory totals 58153.31. The allocation weights
    //     sum to 1.00 per employee in the seed; say who would own those weights in a real business.

    // ── Episode 45 · Conformed Dimensions & the Bus Matrix — reconciles
    //   the learner's turn: put orders, stock and targets on conformed dimensions, and draw Northwind's bus matrix.
    //   Reconciliation check: actual revenue per category per month from the order-line fact vs
    //     seed.sales_targets — drill across at the month x category grain; the actuals must still
    //     total 58153.31.

    // ── Episode 55 · One Big Table & Wide Tables — reconciles
    //   the learner's turn: build one wide view over your star — it must still reconcile.

    // ── Episode 60 · Project — reconciles, answers
    //   the learner's turn: all the stars, every reconciliation query and the grain assertions, in one run.
    //   Reconciliation checks: revenue 58153.31 and 193 lines; 79 orders in the accumulating
    //     snapshot with the one late shipment identified; month-end stock ending at
    //     Products.UnitsInStock; targets and actuals side by side per category per month.
    //   Checks: grain assertions — one row per order line, per order, per product per month.
}
