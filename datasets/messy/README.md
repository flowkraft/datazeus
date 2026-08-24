# messy/ — the defects are the lesson

Small CSVs committed as plain text. **Do not clean these.** Every problem in them is an episode,
and a helpful tidy-up deletes the exercise.

## orders-export.csv

The kind of file a colleague exports from a system nobody maintains and emails to you. Fourteen
rows, and eight distinct problems — one per thing the track teaches:

| what is wrong | the episode it belongs to |
|---|---|
| **Latin-1 encoded**, so `read_csv` with UTF-8 raises and cp1252 silently mangles `Spezialitäten` | S1 · 15 Files, Paths & Encodings |
| **CRLF line endings** | S1 · 15 |
| **`;` delimiter**, not `,` — and one field *contains* a quoted `;` | S1 · 15, S2 · 30 |
| **Two date formats**: `2026-07-04` and `04/07/2026` (is that July 4th or April 7th?) | S2 · 25 Dates, Times & Time Zones |
| **Decimal comma** — `32,38` is not a thousands separator, but `1.140,51` uses both | S1 · 40 Missing Data & dtypes |
| **A missing value** (`Qty` blank) and **a missing date** | S1 · 40, S2 · 30 |
| **A ragged row** — row 8 has one column too many | S2 · 30 Cleaning & Validation |
| **An exact duplicate** of row 1 | S2 · 30, and the merge episodes |

Fourteen rows on purpose: small enough that a learner can open it in a text editor and *see*
each defect, which is what makes the fix feel earned rather than magical.

## Adding another

Keep it under ~100KB, keep it text, and write the defect table above for it. If the file needs
to be big to make its point, it belongs in `generated/` instead — see the parent README for
the three tiers and why.
