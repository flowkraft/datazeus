package datazeus.datamodeling.series2._60

import datazeus._internal.SchemaKoanBase
import spock.lang.Stepwise

/**
 * ╔════════════════════════════════════════════════════════════════════════╗
 * ║  SCHEMA KOANS — Data Modeling · Series 2 · 60
 * ╚════════════════════════════════════════════════════════════════════════╝
 *
 * BOILERPLATE — no koans written yet. The brief is below; delete it as you write them.
 *
 *     zeus.bat koans datamodeling series2 _60     (Windows)
 *     ./zeus.sh koans datamodeling series2 _60    (macOS/Linux)
 *
 * ── WHAT A MODELING KOAN IS, AND IS NOT ──────────────────────────────────
 * A SQL koan blanks a token inside a query and checks the RESULT. A modeling koan is the
 * other way round: the ORACLE is what the schema refuses, and what real data does when you
 * try to load it into it.
 *
 * Do NOT ask the learner to type CREATE TABLE here. If a koan says "write the schema", their
 * effort goes into DDL syntax, which is not the subject. Only the project rung authors a
 * whole model. See SchemaKoanBase for the five rungs and the helper each one uses.
 *
 * THIS LESSON'S RUNGS: koan:migration, koan:author
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ───────────────────────────────────────
 * GOAL: the Series 2 project — three change requests against your own schema.
 * MODELS: migrations that are additive, and the suite that proves they were.
 *
 * SERIES 2 PROJECT. Six months on, the business wants three things — and one of them is
 * EBOOKS, which have no physical copy and detonate the copy model the learner spent the whole
 * series getting right.
 * That is deliberate and must not be softened. The lesson is NOT 'avoid the mistake' — no
 * model survives every change. It is 'here is what an additive migration looks like versus a
 * rewrite', and the old suite still passing is how they know which one they wrote.
 * The other two: a second branch (copies gain a location) and reservations (a new entity that
 * competes with loans for the same copy).
 *
 * ── THE RULE THAT KEEPS THESE HONEST ─────────────────────────────────────
 * Pair every shouldReject with a shouldAccept. A learner who only ever sees rejections
 * learns to bolt constraints onto everything and call it rigour; the pincer — this must
 * bounce, AND this must still get through — is the judgement actually being taught.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class ProjectSurviveTheChangeKoans extends SchemaKoanBase {

    // TODO: koans, in the same order as the lesson's beats.
    //
    // Sketches for the rungs this lesson uses (koan:migration, koan:author):
    //
    //   predict   the schema is GIVEN; they decide what the database will do
    //     def "the same product can't be on one order twice"() {
    //         expect: attempting("INSERT INTO order_line VALUES (10248, 42, 5)") == ___
    //     }
    //
    //   diagnose  they name the thing, they don't build it
    //     def "what is one row of Order Details?"() {
    //         expect: grainOf('"Order Details"') == ___          // ['OrderID','ProductID']
    //     }
    //     def "can this model say how many COPIES of Dune we own?"() {
    //         expect: canAnswer("...") == ___                    // false — and that IS the lesson
    //     }
    //
    //   choose    three finished models, given; which one survives?
    //     def "which model still knows what we charged last March?"() {
    //         expect: modelThatSurvives("the price changed on 2026-03-15") == ___   // 'B'
    //     }
    //
    //   complete  boilerplate given, ONE blanked line — the decision itself
    //     def "the same product can't appear twice on one order"() {
    //         expect:
    //         ddlMakesImpossible('''
    //             CREATE TABLE practice.order_line (
    //               "OrderID"   INTEGER NOT NULL,
    //               "ProductID" INTEGER NOT NULL,
    //               "Quantity"  INTEGER NOT NULL CHECK ("Quantity" > 0),
    //               ___                                  -- one line: the grain of a junction
    //             )
    //         ''', 'INSERT INTO practice.order_line VALUES (10248, 42, 5)')
    //     }
    //
    //   author    the whole model — projects only
    //     def "your schema.sql holds the real Northwind rows"() {
    //         expect: loadsRealData('"Order Details"') == ___
    //     }
    //
    //   migration the change is applied and EVERY earlier koan must still pass
    //   reconcile the star's totals must equal the OLTP totals
}
