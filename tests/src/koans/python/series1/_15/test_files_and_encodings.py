"""
╔══════════════════════════════════════════════════════════════════════════╗
║  PYTHON KOANS — Python for Data · Series 1 · 15                          ║
║  Files, Paths & Encodings — Reading and Writing Text in the Right Encoding║
╚══════════════════════════════════════════════════════════════════════════╝

    zeus.bat koans python series1 _15     (Windows)
    ./zeus.sh koans python series1 _15    (macOS/Linux)

THIS FILE ALSO EXISTS TO PROVE THE DATASET MECHANISM WORKS. See datasets/README.md for the
three tiers; this is tier 2 — a small CSV committed as text because its defects ARE the
lesson. `datasets/messy/orders-export.csv` is fourteen rows carrying eight distinct problems,
one per thing this track teaches, and this episode owns the first three of them.

── WHY A COMMITTED CSV AND NOT A GENERATED OR PARQUET FIXTURE ───────────
Parquet is typed, so it has already solved encoding, ragged rows and numbers-stored-as-text
FOR you — storing this exercise in Parquet would delete the exercise. And a generated file
would put the defects behind a script instead of in a diff a reviewer can read.
A learner can open this one in a text editor and SEE each problem, which is what makes the
fix feel earned rather than magical.

── THE BLANK IS A DATA DECISION, NEVER A LANGUAGE FACT ──────────────────
Nothing below asks what `encoding=` does. Every blank is a value that comes out right or
comes out quietly, plausibly wrong.
"""

import pandas as pd
import pytest

from conftest import ___

MESSY = "/datasets/messy/orders-export.csv"


def test_utf8_is_not_a_safe_default():
    """1) The file is Latin-1. Reading it as UTF-8 does not silently mangle — it RAISES.

    That is the good case, and it is worth naming: an exception you can see beats a string
    that looks nearly right. The next koan is the bad case.
    """
    with pytest.raises(UnicodeDecodeError):
        # on_bad_lines="skip" on purpose: without it the RAGGED ROW raises first and you
        # never reach the encoding problem. Two defects in one file, and the parser hits
        # whichever comes first — which is itself worth knowing.
        pd.read_csv(MESSY, sep=";", encoding="utf-8", on_bad_lines="skip")

    # Read correctly, how many data rows are there?
    df = pd.read_csv(MESSY, sep=";", encoding="latin-1", on_bad_lines="skip")
    assert len(df) == ___


def test_the_wrong_encoding_that_does_not_raise():
    """2) cp437 decodes these bytes without complaining, and gets a customer name wrong.

    This is the failure that reaches production: no error, no warning, just a name that is
    subtly not the customer's name — and it will not match anything you join it against.

    WORTH KNOWING, and it is why this koan uses cp437 rather than the obvious cp1252: on THIS
    file cp1252 gives byte-for-byte the same answer as latin-1, because the two only disagree
    in the 0x80-0x9F range and these names do not use it. So "it looked fine on my machine"
    is not evidence that your encoding is right — it is evidence that you have not yet met a
    byte where it is wrong.
    """
    correct = pd.read_csv(MESSY, sep=";", encoding="latin-1", on_bad_lines="skip")
    plausible = pd.read_csv(MESSY, sep=";", encoding="cp437", on_bad_lines="skip")
    lookalike = pd.read_csv(MESSY, sep=";", encoding="cp1252", on_bad_lines="skip")

    def name(df, oid):
        return df.loc[df["OrderID"] == oid, "Customer"].iloc[0]

    # Spell out the German customer's name, read correctly.
    assert name(correct, 10249) == ___

    # cp437 did not raise. What did it hand you instead?
    assert name(plausible, 10249) == ___

    # And the trap inside the trap: cp1252 is ALSO the wrong encoding here.
    # Did it give you anything you could have noticed?
    assert (name(lookalike, 10249) == name(correct, 10249)) == ___


def test_the_separator_is_not_a_comma_and_one_field_contains_it():
    """3) `;` delimited, and order 10251's Notes field contains a quoted semicolon.

    Splitting on `;` by hand — which is the obvious first instinct — breaks that row. The csv
    machinery honours the quoting; a `.split(";")` does not. That difference is the lesson.
    """
    df = pd.read_csv(MESSY, sep=";", encoding="latin-1", on_bad_lines="skip")
    note = df.loc[df["OrderID"] == 10251, "Notes"].iloc[0]

    # The whole note survived as ONE field. How many characters is it?
    assert note == ___


def test_one_row_is_ragged_and_you_get_to_choose_what_that_means():
    """4) Row 10254 has an extra column. `on_bad_lines` decides what happens to it.

    There is no right answer here, which is why it is the last koan: 'skip' loses data
    silently, 'error' stops the whole load for one bad row. Series 2 · 30 is the episode about
    choosing between them — reject the row, quarantine it, or fail the job.
    """
    kept = pd.read_csv(MESSY, sep=";", encoding="latin-1", on_bad_lines="skip")

    with pytest.raises(pd.errors.ParserError):
        pd.read_csv(MESSY, sep=";", encoding="latin-1", on_bad_lines="error")

    # How many rows did 'skip' quietly drop? (The file has 13 data rows on disk.)
    assert 13 - len(kept) == ___
