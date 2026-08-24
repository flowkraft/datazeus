"""
Tier 3 dataset: a file too big to commit, generated deterministically instead.

    python datasets/generated/make_big_orders.py

WHY GENERATED AND NOT COMMITTED
Series 3 · 30 (chunking, memory) and Series 3 · 35 (polars lazy evaluation) need a file that
does not fit comfortably in memory. A million rows is ~60MB of CSV, and a repo that a learner
clones should not carry that — especially one that gets re-generated whenever the shape changes.

WHY A FIXED SEED, AND WHY IT MATTERS MORE THAN IT LOOKS
Every learner must get a byte-identical file, because koans assert on exact numbers. A dataset
that differs per machine turns "your answer is wrong" into "your data is different", which is
the single most demoralising failure a koan can produce. `random.Random(SEED)` with an explicit
seed, never the global `random` module, so nothing else in the process can perturb the stream.

WHY CSV HERE AND NOT PARQUET
Because Series 3 · 30 is about the cost of reading a big text file — the thing that makes
chunking necessary in the first place. A Parquet version is generated too (it is smaller and
typed), so Series 2 · 00 can show the same data both ways and let the reader see the
difference in size and load time for themselves. That comparison IS that episode.
"""

import csv
import os
import random
from datetime import date, timedelta

SEED = 20260824          # never change this without re-checking every koan that asserts on it
ROWS = 1_000_000
OUT = os.path.dirname(os.path.abspath(__file__))

COUNTRIES = ["Germany", "France", "Brazil", "USA", "Austria", "Sweden", "Mexico", "UK"]
PRODUCTS = [f"P{n:04d}" for n in range(1, 201)]
START = date(2024, 1, 1)


def main():
    rnd = random.Random(SEED)
    path = os.path.join(OUT, "big_orders.csv")
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["order_id", "order_date", "country", "product", "qty", "unit_price"])
        for i in range(1, ROWS + 1):
            w.writerow([
                i,
                (START + timedelta(days=rnd.randrange(0, 730))).isoformat(),
                rnd.choice(COUNTRIES),
                rnd.choice(PRODUCTS),
                rnd.randrange(1, 25),
                f"{rnd.uniform(1.5, 250.0):.2f}",
            ])
    print(f"wrote {path}  ({ROWS:,} rows, {os.path.getsize(path) / 1e6:.0f} MB)")

    # The Parquet twin, for Series 2 · 00. Written through DuckDB rather than pandas so this
    # script needs no pandas — DuckDB is already a dependency of the koan image.
    try:
        import duckdb
        pq = os.path.join(OUT, "big_orders.parquet")
        duckdb.sql(f"COPY (SELECT * FROM read_csv_auto('{path}')) TO '{pq}' (FORMAT PARQUET)")
        print(f"wrote {pq}  ({os.path.getsize(pq) / 1e6:.0f} MB — same data, and that gap is the episode)")
    except ImportError:
        print("duckdb not installed here; skipped the Parquet twin (the koan image has it)")


if __name__ == "__main__":
    main()
