package datazeus.datamodeling.series2._05

import datazeus._internal.SchemaKoanBase
import spock.lang.Stepwise

/**
 * ╔════════════════════════════════════════════════════════════════════════════════════════╗
 * ║  THE ACCEPTANCE SUITE — Data Modeling · Series 2 · 05 Design the Library                ║
 * ╚════════════════════════════════════════════════════════════════════════════════════════╝
 *
 * BOILERPLATE — the shape is settled, the koans are not written yet. Brief below.
 *
 *     zeus.bat koans datamodeling series2 _05     (Windows)
 *     ./zeus.sh koans datamodeling series2 _05    (macOS/Linux)
 *
 * ── WHAT THIS FILE IS ────────────────────────────────────────────────────────────────────
 * The third of the three artifacts handed to the learner in courses/datamodeling/brief/
 * (prose brief · messy spreadsheet · THIS). It is a DEFINITION OF DONE THAT IS NOT AN ANSWER
 * KEY: it states what must be impossible, what must stay possible, and which questions the
 * model has to be able to answer — and says nothing whatsoever about how many tables to use
 * or what to call them.
 *
 * ── THE PROPERTY TO PROTECT, ABOVE ALL ───────────────────────────────────────────────────
 * ANY MODEL THAT PASSES IS A CORRECT ANSWER, INCLUDING ONES WE DID NOT THINK OF.
 *
 * If a koan added here can only pass against OUR table names, it has stopped testing modeling
 * and started testing conformance. Rewrite it or drop it. This is the single rule that keeps
 * episode 05 a design exercise instead of a transcription exercise.
 *
 * ── HOW THAT IS ACHIEVED: THE LEARNER WRITES FOUR VIEWS ──────────────────────────────────
 * The suite never touches their tables. It goes through an adapter THEY write, at the end of
 * their schema.sql — four views with fixed names and fixed columns:
 *
 *     CREATE VIEW practice.v_title  AS ...  -- (title_id, title, isbn)   one row per WORK
 *     CREATE VIEW practice.v_copy   AS ...  -- (copy_id, title_id)       one row per PHYSICAL copy
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
 * ── THE PINCER ───────────────────────────────────────────────────────────────────────────
 * Pair every shouldReject with a shouldAccept. A model that makes EVERYTHING impossible is
 * not a good model, and a learner who only ever sees rejections learns to bolt constraints
 * onto everything and call it rigour.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class DesignTheLibraryKoans extends SchemaKoanBase {

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
