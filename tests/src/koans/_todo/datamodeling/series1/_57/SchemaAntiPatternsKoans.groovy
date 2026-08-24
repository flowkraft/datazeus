package datazeus.datamodeling.series1._57

import datazeus._internal.SchemaKoanBase
import spock.lang.Stepwise

/**
 * ╔════════════════════════════════════════════════════════════════════════╗
 * ║  SCHEMA KOANS — Data Modeling · Series 1 · 57 EAV, God Tables & CSV-in-a-Column
 * ╚════════════════════════════════════════════════════════════════════════╝
 *
 * BOILERPLATE — no koans written yet. The brief is below; delete it as you write them.
 *
 *     zeus.bat koans datamodeling series1 _57     (Windows)
 *     ./zeus.sh koans datamodeling series1 _57    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:diagnose
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ───────────────────────────────────────
 * GOAL: recognise a schema that went wrong, and survive inheriting one.
 * MODELS: EAV, the God table, CSV-in-a-column, polymorphic FK, the boolean that should be a status.
 *
 * The catalogue of what is WRONG - the track spends fifty-seven episodes on what to do and
 * never names the shapes you will actually inherit. EAV, the God table, CSV-in-a-column,
 * the polymorphic FK, the boolean that should have been a status, the 'misc' column.
 * Last in the reading movement because recognising a smell needs the whole S1 toolkit: you
 * cannot see that EAV destroyed the grain until you know what grain is.
 * TEACH IT FAIRLY OR NOT AT ALL. Every one of these was a reasonable decision under some
 * pressure - a deadline, an unknown requirement, a vendor schema. An episode that sneers
 * teaches contempt, which is useless, instead of diagnosis, which is the job. For each:
 * what it solved, what it costs, and WHAT YOU DO WHEN YOU INHERIT ONE AND CANNOT REWRITE
 * IT. That last part is the most valuable and the most commonly omitted.
 * Boundary: Data Model Patterns S3/15 argues the abstraction end properly. Point at it.
 *
 * ── THE RULE THAT KEEPS THESE HONEST ─────────────────────────────────────
 * Pair every shouldReject with a shouldAccept. A learner who only ever sees rejections
 * learns to bolt constraints onto everything and call it rigour; the pincer — this must
 * bounce, AND this must still get through — is the judgement actually being taught.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class SchemaAntiPatternsKoans extends SchemaKoanBase {

    // TODO: koans, in the same order as the lesson's beats.
    //
    // Sketches for the rungs this lesson uses (koan:diagnose):
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
