"""
╔══════════════════════════════════════════════════════════════════════════╗
║  PYTHON KOANS — Python for Data · Series 1 · 35                          ║
║  merge & join — Combining Two DataFrames on a Key                        ║
╚══════════════════════════════════════════════════════════════════════════╝

    zeus.bat koans python series1 _35     (Windows)
    ./zeus.sh koans python series1 _35    (macOS/Linux)

THE WORKED EXAMPLE for this track. If you are writing koans for another Python episode, read
conftest.py first and then this file — between them they carry every decision the track made
about exercises.

── WHY THIS EPISODE GOT THE WORKED EXAMPLE ──────────────────────────────
Because it is the same bug three times, and saying so is worth more than any new content:

    Learn SQL      Series 2 · 06   join fan-out, where the database at least has row counts
    Java & Groovy  Series 1 · 40   the same, in memory, where nothing warns you
    HERE                           the same again, in pandas, where it is easiest of all to
                                   miss because `.merge()` looks like a spreadsheet operation

A reader who has done Learn SQL has met this. Say so out loud — it is the strongest argument
in the academy for why SQL comes first, and for why these tracks are siblings rather than
alternatives.

── THE RULE THIS FILE EXISTS TO DEMONSTRATE ─────────────────────────────
THE BLANK IS A DATA DECISION, NEVER A LANGUAGE FACT. Nothing below asks what `.merge()`
returns or what `how="left"` means — this reader can look that up. Every blank is a number
that is either right or quietly, plausibly wrong.
"""

from conftest import ___


def test_the_merge_looks_fine(nw_df):
    """1) Orders and their line items. Predict the row count before you run it.

    Orders has one row per order. "Order Details" has one row per PRODUCT on an order. Merging
    them is correct and normal — this koan is not a trap, it is the setup for the next one.
    """
    orders = nw_df("Orders")
    lines = nw_df("Order Details")

    merged = orders.merge(lines, on="OrderID", how="inner")

    # Orders is 79 rows. Line items are 193. What is `merged`?
    assert len(merged) == ___


def test_and_this_is_where_the_money_goes_wrong(nw_df):
    """2) Now sum a column that belongs to the ORDER, not to the line.

    Freight is charged once per order. After the merge, an order with four line items appears
    four times — so summing Freight counts that order's shipping four times over.

    This is the bug. It does not raise. It does not look wrong. It produces a number somebody
    puts in a report.
    """
    orders = nw_df("Orders")
    lines = nw_df("Order Details")
    merged = orders.merge(lines, on="OrderID", how="inner")

    true_freight = round(orders["Freight"].sum(), 2)
    after_merge = round(merged["Freight"].sum(), 2)

    # One of these is the real shipping bill. Which, and is the other bigger or smaller?
    assert after_merge > true_freight
    assert round(after_merge / true_freight, 1) == ___


def test_two_ways_to_get_it_right(nw_df):
    """3) Fix it. Both of these work; they are not the same fix.

    Fill in ONE of the two below — then read the other, because the difference is the lesson:
      - dedupe AFTER the merge:      you still built the wide frame, then threw rows away
      - aggregate BEFORE the merge:  you never built it, which is what scales
    """
    orders = nw_df("Orders")
    lines = nw_df("Order Details")
    merged = orders.merge(lines, on="OrderID", how="inner")

    # (a) one row per order again, then sum
    deduped = merged.drop_duplicates(subset="OrderID")["Freight"].sum()

    # (b) collapse the many side first, then merge onto orders
    per_order = lines.groupby("OrderID", as_index=False).agg(items=("ProductID", "count"))
    narrow = orders.merge(per_order, on="OrderID", how="left")
    aggregated = narrow["Freight"].sum()

    assert round(deduped, 2) == round(aggregated, 2)
    assert round(deduped, 2) == ___


def test_the_left_join_that_silently_drops_customers(nw_df):
    """4) The other half of the pincer: a merge that loses rows instead of multiplying them.

    Not every customer has placed an order. An inner merge quietly drops them, and "customers
    with no orders" is exactly the question somebody eventually asks.
    """
    customers = nw_df("Customers")
    orders = nw_df("Orders")

    inner = customers.merge(orders, on="CustomerID", how="inner")
    left = customers.merge(orders, on="CustomerID", how="left")

    # How many customers appear in `left` but never in `inner`?
    kept = set(left["CustomerID"]) - set(inner["CustomerID"])
    assert len(kept) == ___

    # And the check worth building a habit around: after ANY merge, did your key count change?
    assert customers["CustomerID"].nunique() == left["CustomerID"].nunique()
