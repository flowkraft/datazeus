package datazeus.datamodeling.series2

import datazeus._internal.SchemaKoanBase
import spock.lang.Stepwise

/**
 * ╔════════════════════════════════════════════════════════════════════════════════════════╗
 * ║  THE CHECKS — Data Modeling · Series 2 · Modeling for the Real World
 * ╚════════════════════════════════════════════════════════════════════════════════════════╝
 *
 * BOILERPLATE — the shape is settled, the checks are not written yet. Brief below.
 *
 *     zeus.bat koans datamodeling series2     (Windows)
 *     ./zeus.sh koans datamodeling series2    (macOS/Linux)
 *
 * ── WHAT THIS FILE IS, AND WHY IT IS NOT KOANS ──────────────────────────────────────────
 * Decided with the owner 2026-09-14. A modelling decision has more than one right answer, so
 * Data Modeling has no fill-in-the-blank koans. The learner builds their own model in their
 * working file; this ONE file checks it, the way a team's CI checks a schema. It grows episode
 * by episode, in order, so a learner at episode 20 sees everything up to 20 green and the rest
 * waiting. The judgement questions that used to be predict / diagnose / choose koans are Anki
 * cards now (cards/cards.yaml in each lesson).
 *
 * THIS SERIES' CHECKS: fits · refuses · answers · survives
 *
 * ── THE PROPERTY TO PROTECT, ABOVE ALL ───────────────────────────────────────────────────
 * ANY MODEL THAT PASSES IS A CORRECT ANSWER, INCLUDING ONES WE DID NOT THINK OF. A check that
 * can only pass on OUR table names tests conformance, not modelling: rewrite it or drop it.
 * Name every check after the BUSINESS RULE ("that copy is already out with someone else"),
 * never after the constraint ("unique violation on loan_copy_idx").
 *
 * ── HOW THAT IS ACHIEVED: THE LEARNER WRITES FOUR VIEWS ──────────────────────────────────
 * The suite never touches their tables. It goes through an adapter THEY write, at the end of
 * their schema.sql — four views with fixed names and fixed columns:
 *
 *     CREATE VIEW practice.v_title  AS ...  -- (title_id, title)         one row per WORK
 *     CREATE VIEW practice.v_copy   AS ...  -- (copy_id, title_id, isbn) one row per PHYSICAL copy
 *     CREATE VIEW practice.v_member AS ...  -- (member_id, email)
 *     CREATE VIEW practice.v_loan   AS ...  -- (loan_id, copy_id, member_id, borrowed, returned)
 *
 * Everything behind those views is theirs: table count, key strategy, naming, how far they
 * normalise. Two very different schemas can both go green, which is exactly right.
 *
 * And the adapter is not a testing trick — it is the first time the learner meets the idea
 * that a model has an INTERFACE separate from its implementation. Say so in the lesson; it
 * pays off again in Data Warehousing and in dbt.
 *
 * NOTE THE SHAPE OF v_title AND v_copy. Being asked for both is the strongest hint in the
 * whole series, and it is meant to be: nearly everyone arrives here with a single `book`
 * table and cannot write the second view. That is the title-vs-copy trap detonating on their
 * own schema, and it is the entire point of episode 10. DO NOT soften it into one view.
 *
 * WHY isbn IS ON v_copy AND NOT v_title (2026-09-14): an ISBN identifies an EDITION, not a work —
 * The Pragmatic Programmer has two, Dune's film tie-in has its own, and two titles have none.
 * A copy is always of one edition, so the copy view can carry it (nullable). Episode 10 is
 * where the learner decides whether edition deserves its own table.
 *
 * ── THE PINCER ───────────────────────────────────────────────────────────────────────────
 * Pair every "must refuse" with a "must still accept". A model that makes everything impossible
 * is not a good model, and a learner who only sees rejections learns to over-constrain.
 */
@Stepwise // walk the checks in episode order — once one fails, the rest wait
class LibraryChecks extends SchemaKoanBase {

    // TODO: checks, one section per episode, in episode order.

    // ── Episode 05 · Design the Library — fits, refuses, answers
    //   the learner's turn: draw and build your first library schema, add the four views, load the spreadsheet — and set aside the lent reference-only book.
    //   FITS: all 26 rows of lending-log.csv must land. One cannot land honestly: "The Illustrated
    //     Riverside" is "reference only — do not lend", yet Priya borrowed it (2026-05-05 to
    //     05-12). In Series 1 · 50 the RULE was wrong and changed; here the rule is right and the
    //     DATA is wrong — so the row is set aside for a person, and the rule stays. Name that
    //     contrast out loud.

    // ── Episode 10 · Title, Edition, Copy & Keys — answers
    //   the learner's turn: make "how many copies of Dune?" and "who has copy #3?" answerable, and decide whether edition earns its own table.

    // ── Episode 15 · Relationships That Are Events — refuses
    //   the learner's turn: give a loan its own identity, so the same copy lent twice to the same member loads.
    //   Check: with PRIMARY KEY(member, copy), the second, perfectly valid loan of the same copy to
    //     the same member is refused.

    // ── Episode 20 · Hierarchies & Classification — answers
    //   the learner's turn: store the subject tree and the age bands, and answer "everything under History".
    //   Check (answers): one book can sit under a subject AND an age band, and both questions come
    //     back.

    // ── Episode 25 · Temporal Data — answers
    //   the learner's turn: model membership periods, and answer "was Danny a member on 1 June?".
    //   Check (answers): the model says whether Danny (lapsed) was a member on a given date.

    // ── Episode 27 · Rules Across Rows — refuses
    //   the learner's turn: enforce "one open loan per copy", and write down where that rule lives and why.
    //   Checks: two open loans on one copy; a loan of a reference-only book; a second renewal while
    //     someone is waiting — each with its paired accept. "One open loan per copy" three ways:
    //     UNIQUE(copy) WHERE returned IS NULL (PostgreSQL), a separate current-loans table with a
    //     plain UNIQUE, and an application check. Say what each costs.

    // ── Episode 30 · created_at, updated_by & deleted_at — refuses
    //   the learner's turn: add audit columns, decide on soft delete for members, and mark the personal-data columns.
    //   Check: a new member re-using a soft-deleted member's email.

    // ── Episode 40 · Indexes & Denormalization — answers
    //   the learner's turn: index for the three reads, add one deliberate copy, and name who keeps it in sync.

    // ── Episode 42 · JSON Columns — answers
    //   the learner's turn: decide what in `notes` becomes columns and what stays a document.

    // ── Episode 50 · Schema Evolution — survives
    //   the learner's turn: move fines to a payments table with expand and contract, keeping every earlier check green.

    // ── Episode 60 · Project — survives, refuses, answers
    //   the learner's turn: three change requests against your live schema, each written down first as additive or a rewrite.

    // THE EPISODE 05 ACCEPTANCE SUITE, carried over from DesignTheLibraryKoans:

    // ─────────────────────────────────────────────────────────────────────────────────────
    // TODO 1 — THE ADAPTER EXISTS
    //   Before anything else, the four views must be there. This koan failing means the
    //   learner has not finished the interface, and its hint should say WHICH view is missing.
    //   If v_copy is the one missing, that is the diagnosis worth naming gently: they have
    //   modelled the work but not the physical thing on the shelf.
    // ─────────────────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────────────────
    // TODO 2 — MUST BE IMPOSSIBLE (shouldReject)
    //   - a loan pointing at a member who does not exist
    //   - a loan pointing at a copy that does not exist
    //   - the same copy out on two loans that overlap in time
    //   - two members sharing one email address
    //   Each one needs a `because` that names the business rule, never the constraint:
    //   "that copy is already out with someone else", not "unique violation on loan_copy_idx".
    // ─────────────────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────────────────
    // TODO 3 — MUST STAY POSSIBLE (shouldAccept)  ← the other jaw; do not skip these
    //   - one member holding several DIFFERENT copies at the same time
    //   - the same copy loaned again AFTER it came back
    //   - a title with no ISBN at all           (two exist in lending-log.csv)
    //   - a title with three authors            (Good Omens has two; keep the third open)
    //   - a loan with no return date            (blank in the CSV means STILL OUT, not unknown)
    // ─────────────────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────────────────
    // TODO 4 — MUST BE ANSWERABLE (canAnswer)
    //   These catch the failure constraints cannot: information that was never stored. A
    //   model can satisfy every rule above and still be unable to answer these.
    //     - "how many copies of Dune do we own?"        ← dies on the one-book-table model
    //     - "who has copy #3 right now?"                ← dies on the same model
    //     - "which titles are entirely out on loan?"
    //     - "what has this member borrowed, ever?"
    //   The first two are the trap. Let them fail here and be diagnosed in episode 10 —
    //   the failure has to happen on the learner's own schema to be worth anything.
    // ─────────────────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────────────────
    // TODO 5 — THE REAL DATA LOADS
    //   The spreadsheet is the acceptance data: every row of brief/lending-log.csv must land
    //   in their model without losing anything. Same oracle as Series 1, on a schema they
    //   invented rather than one they recovered.
    //
    //   The CSV is deliberately dirty, and each defect is a lesson rather than a nuisance:
    //     "Dune" on five rows with copy 1..4        title vs copy
    //     author2                                   a repeating group, discovered not taught
    //     ISBN 020161622X next to 9780135957059     ISBN-10 vs -13, same work, two editions
    //     two rows with no ISBN                     the natural key that is not always there
    //     blank `returned`                          real optionality with real meaning
    //     `member` repeated with the same email     no member id; identity has to be derived
    //     "The Illustrated Riverside" lent anyway   a CORRECT rule (reference only) the data
    //                                               breaks: set the row aside, keep the rule
    // ─────────────────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────────────────
    // WHAT THIS SUITE MUST NEVER TEST
    //   - table or column names behind the views          (that is their design, not ours)
    //   - how many tables they used
    //   - surrogate vs natural keys                        ← episode 10 argues this properly,
    //                                                        and pre-judging it here would
    //                                                        rob that episode of its bite
    //   - normal form. A denormalised model that answers everything and refuses everything it
    //     should is a PASS. S2/35 and S2/40 are where "how far" gets decided, on purpose.
    // ─────────────────────────────────────────────────────────────────────────────────────
}
