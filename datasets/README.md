# Datasets

Three tiers, and which one a dataset belongs in is decided by **what it is teaching**, not by
how big it is. Decided 2026-08-24 — the rationale is here so it is not re-argued per episode.

| tier | lives where | committed? |
|---|---|---|
| **1. Relational, shared across tracks** | `northwind/northwind.duckdb` (+ the PostgreSQL Northwind DataPallas ships) | yes, already |
| **2. Small and deliberately messy** | `messy/*.csv` | **yes — the defects are the lesson** |
| **3. Large** | `generated/` | **no — generated from a seed** |

## Tier 1 — Northwind, and don't duplicate it

Learn SQL, Data Modeling, Java & Groovy and Python all use the same Northwind. That is
deliberate and it is worth protecting: a learner who found 11 German customers with `WHERE`
should find 11 with a DataFrame filter, and 11 with `Collectors.groupingBy`. Same numbers,
different tool — which is the whole argument for the tracks being siblings.

Anything relational belongs here. Do not copy a Northwind table into a CSV to make an episode
easier to write.

## Tier 2 — messy CSVs, committed as text

Small files (keep them under ~100KB) whose **defects are the teaching material**. Committed as
plain CSV, on purpose, for three reasons:

- **The defect is visible in a diff.** A reviewer can see that row 14 has a ragged column count
  and that the file is Latin-1. In Parquet none of that is reviewable.
- **Parquet would erase the lesson.** The encoding, BOM, ragged-row and number-as-text episodes
  are *about* text problems. A typed columnar format has already solved them for you, so
  storing the exercise in Parquet would delete the exercise.
- **It is what actually arrives at work.** Nobody emails you Parquet.

Parquet still gets taught — Series 2 · 00 is the episode where it is the subject, and there the
file is generated (tier 3), because there the point is the format rather than the mess.

## Tier 3 — generated, never committed

Two different reasons land a file here, and only the first is about size.

**Because it is large.** Anything that needs volume: chunking, memory, `polars` lazy
evaluation, query plans. A million rows does not belong in git.

**Because it is binary.** Added 2026-08-24 with the Excel episode. Tier 2's real rationale is
not "small" — it is *reviewable*: a defect you can see in a diff. An `.xlsx` is a zip of XML,
so committing one keeps the letter of tier 2 and loses the whole point of it. Nobody can review
a binary, and six months later nobody remembers which cells were deliberately broken.

So `make_messy_excel.py` **authors the four defects as named constants in Python** — the title
row, the merged span, which rows carry date serials, which rows carry numbers-as-text — and
builds the workbook from them. The syllabus stays in a diff; only the artefact is generated.
It also prints the true total freight, so a koan author never has to open Excel.

Every generator takes a **fixed seed** and is deterministic, so every learner gets byte-identical
data and a koan's expected answer is stable. Run them with:

```bash
python datasets/generated/make_big_orders.py     # Series 2 · 00, Series 3 · 30 and · 35
python datasets/generated/make_messy_excel.py    # Series 2 · 02
```

`generated/` is git-ignored apart from the scripts themselves.

## Why not seaborn / sklearn / statsmodels built-in datasets

They are the obvious Python answer and they are wrong for this course, three times over:

- **They download from the internet.** A course that stops working on a train, or when a URL
  moves, is a course that stops working.
- **They are clean.** Their whole value is that somebody already did the tidying — which is
  precisely the work Series 1 and 2 exist to teach.
- **They are not Northwind.** Using them would break the cross-track property above, which is
  the most valuable thing this academy has.

Use them in an episode only if the episode is *about* those libraries. None currently is.
